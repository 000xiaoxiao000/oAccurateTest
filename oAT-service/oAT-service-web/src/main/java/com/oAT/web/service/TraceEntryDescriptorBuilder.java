package com.oAT.web.service;

import com.oAT.agent.model.*;
import org.springframework.util.StringUtils;

public final class TraceEntryDescriptorBuilder {
    private TraceEntryDescriptorBuilder() {
    }

    public static TraceEntryDescriptor build(TraceNode node) {
        TraceEntryDescriptor descriptor = new TraceEntryDescriptor();
        if (node == null) {
            descriptor.setEntryType("unknown");
            descriptor.setEntryName("unknown");
            descriptor.setDisplayName("unknown");
            return descriptor;
        }
        descriptor.setEntryType(node.toType());
        descriptor.setEntryName(node.toType());
        descriptor.setEntryProtocol(node.toType());
        descriptor.setDisplayName(node.toType());
        descriptor.setEntryClientIp(node.getAddressIp());
        if (node.getApp() != null) {
            descriptor.setEntryAppId(node.getApp().getAppId());
            descriptor.setEntryAppName(node.getApp().getAppName());
        }

        if (node instanceof HttpTraceNode) {
            HttpTraceNode http = (HttpTraceNode) node;
            descriptor.setEntryType("http");
            descriptor.setEntryProtocol("http");
            descriptor.setEntryName(firstText(http.getRequestUrl(), "http"));
            descriptor.setDisplayName(firstText(http.getRequestUrl(), "http"));
            descriptor.setEntryClientIp(firstText(http.getClientIp(), node.getAddressIp()));
        } else if (node instanceof SofaRpcRemoteTraceNode) {
            SofaRpcRemoteTraceNode sofa = (SofaRpcRemoteTraceNode) node;
            descriptor.setEntryType("sofa-rpc");
            descriptor.setEntryProtocol("sofa-rpc");
            descriptor.setEntryInterface(sofa.getInterfaceName());
            descriptor.setEntryMethod(sofa.getMethodName());
            descriptor.setEntryName(joinInterfaceMethod(sofa.getInterfaceName(), sofa.getMethodName(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof SofaRpcTraceNode) {
            SofaRpcTraceNode sofa = (SofaRpcTraceNode) node;
            descriptor.setEntryType("sofa-rpc");
            descriptor.setEntryProtocol("sofa-rpc");
            descriptor.setEntryInterface(sofa.getInterfaceName());
            descriptor.setEntryMethod(sofa.getMethodName());
            descriptor.setEntryName(joinInterfaceMethod(sofa.getInterfaceName(), sofa.getMethodName(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof DubboTraceNode) {
            DubboTraceNode dubbo = (DubboTraceNode) node;
            descriptor.setEntryType("dubbo");
            descriptor.setEntryProtocol("dubbo");
            descriptor.setEntryInterface(dubbo.getServiceInterface());
            descriptor.setEntryMethod(dubbo.getServiceMethodName());
            descriptor.setEntryName(joinInterfaceMethod(dubbo.getServiceInterface(), dubbo.getServiceMethodName(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof DubboRemoteTraceNode) {
            descriptor.setEntryType("dubbo");
            descriptor.setEntryProtocol("dubbo");
            descriptor.setEntryName(firstText(descriptor.getEntryAppName(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof RabbitMQRemoteTraceNode) {
            RabbitMQRemoteTraceNode rabbit = (RabbitMQRemoteTraceNode) node;
            descriptor.setEntryType("rabbitmq");
            descriptor.setEntryProtocol("mq");
            descriptor.setEntryTopic(firstText(rabbit.getExchange(), rabbit.getRoutingKey()));
            descriptor.setEntryName(firstText(descriptor.getEntryTopic(), rabbit.getConsumerTag(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof RabbitMQTraceNode) {
            RabbitMQTraceNode rabbit = (RabbitMQTraceNode) node;
            descriptor.setEntryType("rabbitmq");
            descriptor.setEntryProtocol("mq");
            descriptor.setEntryTopic(firstText(rabbit.getExchange(), rabbit.getRoutingKey()));
            descriptor.setEntryName(firstText(descriptor.getEntryTopic(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof RocketMQConsumerTraceNode) {
            RocketMQConsumerTraceNode rocket = (RocketMQConsumerTraceNode) node;
            descriptor.setEntryType("rocketmq");
            descriptor.setEntryProtocol("mq");
            descriptor.setEntryTopic(rocket.getConsumer());
            descriptor.setEntryName(firstText(rocket.getConsumer(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof RocketMQProducerTraceNode) {
            RocketMQProducerTraceNode rocket = (RocketMQProducerTraceNode) node;
            descriptor.setEntryType("rocketmq");
            descriptor.setEntryProtocol("mq");
            descriptor.setEntryTopic(rocket.getProducer());
            descriptor.setEntryName(firstText(rocket.getProducer(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof KafkaMQRemoteTraceNode) {
            KafkaMQRemoteTraceNode kafka = (KafkaMQRemoteTraceNode) node;
            descriptor.setEntryType("kafka");
            descriptor.setEntryProtocol("mq");
            descriptor.setEntryTopic(kafka.getConsumerRecords());
            descriptor.setEntryName(firstText(kafka.getConsumerRecords(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof KafkaMQTraceNode) {
            KafkaMQTraceNode kafka = (KafkaMQTraceNode) node;
            descriptor.setEntryType("kafka");
            descriptor.setEntryProtocol("mq");
            descriptor.setEntryTopic(kafka.getProducerRecord());
            descriptor.setEntryName(firstText(kafka.getProducerRecord(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        } else if (node instanceof ServiceTraceNode) {
            ServiceTraceNode service = (ServiceTraceNode) node;
            descriptor.setEntryType("service");
            descriptor.setEntryProtocol("job");
            descriptor.setEntryInterface(firstText(service.getServiceName(), service.getSimpleName()));
            descriptor.setEntryMethod(service.getMethodName());
            descriptor.setEntryName(joinInterfaceMethod(descriptor.getEntryInterface(), service.getMethodName(), node.toType()));
            descriptor.setDisplayName(descriptor.getEntryName());
        }
        return descriptor;
    }

    private static String joinInterfaceMethod(String interfaceName, String methodName, String fallback) {
        if (StringUtils.hasText(interfaceName) && StringUtils.hasText(methodName)) {
            return interfaceName + "#" + methodName;
        }
        return firstText(interfaceName, methodName, fallback);
    }

    private static String firstText(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return "";
    }
}
