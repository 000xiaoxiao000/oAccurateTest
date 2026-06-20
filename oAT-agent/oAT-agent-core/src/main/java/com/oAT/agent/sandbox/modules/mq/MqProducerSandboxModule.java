package com.oAT.agent.sandbox.modules.mq;

import com.oAT.agent.collect.KafkaMqCollects;
import com.oAT.agent.collect.RabbitMqCollects;
import com.oAT.agent.collect.RocketMqCollects;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.KafkaMQTraceNode;
import com.oAT.agent.model.RabbitMQTraceNode;
import com.oAT.agent.model.RocketMQProducerTraceNode;
import com.oAT.agent.sandbox.api.BeforeEvent;
import com.oAT.agent.sandbox.api.EventListener;
import com.oAT.agent.sandbox.api.EventType;
import com.oAT.agent.sandbox.api.ExactClassMatcher;
import com.oAT.agent.sandbox.api.ExactMethodMatcher;
import com.oAT.agent.sandbox.api.ModuleContext;
import com.oAT.agent.sandbox.api.OatModule;
import com.oAT.agent.sandbox.api.ReturnEvent;
import com.oAT.agent.sandbox.api.SandboxEvent;
import com.oAT.agent.sandbox.api.ThrowsEvent;
import com.oAT.agent.sandbox.api.WatchId;
import com.oAT.agent.trace.TraceContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MqProducerSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(MqProducerSandboxModule.class);

    private final ConcurrentMap<Long, Wrapper> wrappers = new ConcurrentHashMap<Long, Wrapper>();
    private final List<WatchId> watchIds = new ArrayList<WatchId>();
    private ModuleContext context;
    private RocketMqCollects rocketMqCollects;
    private KafkaMqCollects kafkaMqCollects;
    private RabbitMqCollects rabbitMqCollects;

    @Override
    public String id() {
        return "mq-producer";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        TraceContext traceContext = (TraceContext) context.traceContext();
        this.rocketMqCollects = new RocketMqCollects(traceContext);
        this.kafkaMqCollects = new KafkaMqCollects(traceContext);
        this.rabbitMqCollects = new RabbitMqCollects(traceContext);
        watch("org.apache.rocketmq.client.producer.DefaultMQProducer", "send");
        watch("org.apache.rocketmq.client.producer.DefaultMQProducer", "sendOneway");
        watch("org.apache.kafka.clients.producer.internals.ProducerInterceptors", "onSend");
        watch("com.rabbitmq.client.impl.ChannelN", "basicPublish");
    }

    private void watch(String className, String methodName) {
        watchIds.add(context.eventWatcher().watch(
                new ExactClassMatcher(className),
                new ExactMethodMatcher(methodName, null),
                EnumSet.of(EventType.BEFORE, EventType.RETURN, EventType.THROWS),
                this));
    }

    @Override
    public void active() {
        logger.info("[Sandbox-MQProducer] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-MQProducer] frozen");
    }

    @Override
    public void unload() {
        if (context != null) {
            for (WatchId watchId : watchIds) {
                context.eventWatcher().delete(watchId);
            }
        }
        wrappers.clear();
    }

    @Override
    public Object onEvent(SandboxEvent event) {
        try {
            if (event instanceof BeforeEvent) {
                wrappers.put(event.invokeId(), begin((BeforeEvent) event));
            } else if (event instanceof ReturnEvent) {
                end(wrappers.remove(event.invokeId()), null);
            } else if (event instanceof ThrowsEvent) {
                end(wrappers.remove(event.invokeId()), ((ThrowsEvent) event).throwable());
            }
        } catch (Throwable t) {
            logger.error("[Sandbox-MQProducer] event failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private Wrapper begin(BeforeEvent event) {
        if ("org.apache.rocketmq.client.producer.DefaultMQProducer".equals(event.className())) {
            Object defaultMqProducerImpl = getField(event.target(), "defaultMQProducerImpl");
            return new Wrapper("rocket", rocketMqCollects.begin(event.args(), defaultMqProducerImpl), event.args());
        }
        if ("org.apache.kafka.clients.producer.internals.ProducerInterceptors".equals(event.className())) {
            return new Wrapper("kafka", kafkaMqCollects.begin(event.args()), event.args());
        }
        if ("com.rabbitmq.client.impl.ChannelN".equals(event.className())) {
            return new Wrapper("rabbit", rabbitMqCollects.begin(event.args()), event.args());
        }
        return null;
    }

    private void end(Wrapper wrapper, Throwable throwable) {
        if (wrapper == null) {
            return;
        }
        if ("rocket".equals(wrapper.kind)) {
            RocketMQProducerTraceNode node = (RocketMQProducerTraceNode) wrapper.node;
            if (throwable != null) {
                rocketMqCollects.error(node, throwable);
            }
            rocketMqCollects.end(node, wrapper.args);
        } else if ("kafka".equals(wrapper.kind)) {
            KafkaMQTraceNode node = (KafkaMQTraceNode) wrapper.node;
            if (throwable != null) {
                kafkaMqCollects.error(node, throwable);
            }
            kafkaMqCollects.end(node, wrapper.args);
        } else if ("rabbit".equals(wrapper.kind)) {
            RabbitMQTraceNode node = (RabbitMQTraceNode) wrapper.node;
            if (throwable != null) {
                rabbitMqCollects.error(node, throwable);
            }
            rabbitMqCollects.end(node, wrapper.args);
        }
    }

    private Object getField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                logger.warn("[Sandbox-MQProducer] field read failed: " + fieldName, t);
                return null;
            }
        }
        return null;
    }

    private static class Wrapper {
        private final String kind;
        private final Object node;
        private final Object[] args;

        private Wrapper(String kind, Object node, Object[] args) {
            this.kind = kind;
            this.node = node;
            this.args = args;
        }
    }
}
