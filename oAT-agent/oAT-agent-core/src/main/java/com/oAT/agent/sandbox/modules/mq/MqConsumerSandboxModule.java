package com.oAT.agent.sandbox.modules.mq;

import com.oAT.agent.collect.KafkaMqReceiveCollects;
import com.oAT.agent.collect.RabbitMqReceiveCollects;
import com.oAT.agent.collect.RocketMqReceiveCollects;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MqConsumerSandboxModule implements OatModule, EventListener {
    private static final Log logger = LogFactory.getLog(MqConsumerSandboxModule.class);

    private final ConcurrentMap<Long, Wrapper> wrappers = new ConcurrentHashMap<Long, Wrapper>();
    private final List<WatchId> watchIds = new ArrayList<WatchId>();
    private ModuleContext context;
    private KafkaMqReceiveCollects kafkaCollects;
    private RabbitMqReceiveCollects rabbitCollects;
    private RocketMqReceiveCollects rocketCollects;

    @Override
    public String id() {
        return "mq-consumer";
    }

    @Override
    public void load(ModuleContext context) {
        this.context = context;
        TraceContext traceContext = (TraceContext) context.traceContext();
        this.kafkaCollects = new KafkaMqReceiveCollects(traceContext);
        this.rabbitCollects = new RabbitMqReceiveCollects(traceContext);
        this.rocketCollects = new RocketMqReceiveCollects(traceContext);
        watch("org.apache.kafka.clients.consumer.internals.ConsumerInterceptors", "onConsume");
        watch("com.rabbitmq.client.impl.ConsumerDispatcher", "handleDelivery");
        watch("org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer$DefaultMessageListenerConcurrently",
                "consumeMessage");
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
        logger.info("[Sandbox-MQConsumer] active");
    }

    @Override
    public void frozen() {
        logger.info("[Sandbox-MQConsumer] frozen");
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
            logger.error("[Sandbox-MQConsumer] event failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
        return null;
    }

    private Wrapper begin(BeforeEvent event) {
        if ("org.apache.kafka.clients.consumer.internals.ConsumerInterceptors".equals(event.className())) {
            return new Wrapper("kafka", kafkaCollects.begin(event.args()), event.args());
        }
        if ("com.rabbitmq.client.impl.ConsumerDispatcher".equals(event.className())) {
            return new Wrapper("rabbit", rabbitCollects.begin(event.args()), event.args());
        }
        if ("org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer$DefaultMessageListenerConcurrently"
                .equals(event.className())) {
            return new Wrapper("rocket", rocketCollects.begin(event.args()), event.args());
        }
        return null;
    }

    private void end(Wrapper wrapper, Throwable throwable) {
        if (wrapper == null) {
            return;
        }
        if ("kafka".equals(wrapper.kind)) {
            KafkaMqReceiveCollects.KafkaMqReceiveTraceNodeWrapper node =
                    (KafkaMqReceiveCollects.KafkaMqReceiveTraceNodeWrapper) wrapper.node;
            if (throwable != null) {
                kafkaCollects.error(node, throwable);
            }
            kafkaCollects.end(node, wrapper.args);
        } else if ("rabbit".equals(wrapper.kind)) {
            RabbitMqReceiveCollects.RabbitMqReceiveTraceNodeWrapper node =
                    (RabbitMqReceiveCollects.RabbitMqReceiveTraceNodeWrapper) wrapper.node;
            if (throwable != null) {
                rabbitCollects.error(node, throwable);
            }
            rabbitCollects.end(node, wrapper.args);
        } else if ("rocket".equals(wrapper.kind)) {
            RocketMqReceiveCollects.RocketMqReceiveTraceNodeWrapper node =
                    (RocketMqReceiveCollects.RocketMqReceiveTraceNodeWrapper) wrapper.node;
            if (throwable != null) {
                rocketCollects.error(node, throwable);
            }
            rocketCollects.end(node, wrapper.args);
        }
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
