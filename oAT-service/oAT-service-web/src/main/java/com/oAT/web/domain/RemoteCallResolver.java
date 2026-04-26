package com.oAT.web.domain;

import com.oAT.agent.model.Application;
import com.oAT.agent.model.DubboTraceNode;
import com.oAT.agent.model.FeignTraceNode;
import com.oAT.agent.model.HttpClientTraceNode;
import com.oAT.agent.model.KafkaMQTraceNode;
import com.oAT.agent.model.RabbitMQTraceNode;
import com.oAT.agent.model.RemoteInvokeNode;
import com.oAT.agent.model.RocketMQProducerTraceNode;
import com.oAT.agent.model.SofaRpcTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.entity.ApiEndpointIndex;
import com.oAT.web.service.entity.AppVo;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RemoteCallResolver {

    private final Map<String, AppVo> appsById;
    private final List<ApiEndpointIndex> endpoints;

    public RemoteCallResolver(Collection<AppVo> apps, Collection<ApiEndpointIndex> endpoints) {
        this.appsById = Optional.ofNullable(apps).orElse(Collections.emptyList()).stream()
                .filter(app -> StringUtils.hasText(app.getId()))
                .collect(Collectors.toMap(AppVo::getId, Function.identity(), (left, right) -> left));
        this.endpoints = new ArrayList<>(Optional.ofNullable(endpoints).orElse(Collections.emptyList()));
    }

    public Optional<AppVo> getApp(String appId) {
        return Optional.ofNullable(appsById.get(appId));
    }

    public Optional<RemoteCallRelation> resolve(TraceNode traceNode, Collection<TraceNode> traceNodes) {
        if (traceNode == null || traceNode.getApp() == null || !StringUtils.hasText(traceNode.getApp().getAppId())) {
            return Optional.empty();
        }
        String sourceAppId = traceNode.getApp().getAppId();
        if (!appsById.containsKey(sourceAppId)) {
            return Optional.empty();
        }
        Optional<String> targetAppId = resolveRemoteApp(traceNode, traceNodes, sourceAppId);
        if (!targetAppId.isPresent() || sourceAppId.equals(targetAppId.get()) || !appsById.containsKey(targetAppId.get())) {
            return Optional.empty();
        }
        return Optional.of(new RemoteCallRelation(sourceAppId, targetAppId.get(), traceNode.toType(), remoteTarget(traceNode)));
    }

    private Optional<String> resolveRemoteApp(TraceNode traceNode, Collection<TraceNode> traceNodes, String sourceAppId) {
        Optional<String> byRemoteApp = getRemoteApp(traceNode).map(Application::getAppId).filter(StringUtils::hasText);
        if (byRemoteApp.isPresent()) {
            return byRemoteApp;
        }
        Optional<String> byRemoteNode = Optional.ofNullable(traceNodes).orElse(Collections.emptyList()).stream()
                .filter(node -> Objects.equals(node.getTraceNodeId(), traceNode.getTraceNodeId() + ".remote"))
                .filter(node -> node.getApp() != null)
                .map(node -> node.getApp().getAppId())
                .filter(StringUtils::hasText)
                .findFirst();
        if (byRemoteNode.isPresent()) {
            return byRemoteNode;
        }
        Optional<String> byUrl = resolveByUrl(traceNode, sourceAppId);
        if (byUrl.isPresent()) {
            return byUrl;
        }
        return resolveByRpcSignature(traceNode, sourceAppId);
    }

    private Optional<Application> getRemoteApp(TraceNode traceNode) {
        if (traceNode instanceof RemoteInvokeNode) {
            return Optional.ofNullable(((RemoteInvokeNode) traceNode).getRemoteApp());
        }
        return Optional.empty();
    }

    private Optional<String> resolveByUrl(TraceNode traceNode, String sourceAppId) {
        String url = remoteUrl(traceNode);
        if (!StringUtils.hasText(url)) {
            return Optional.empty();
        }
        String path = normalizePath(extractPath(url));
        if (!StringUtils.hasText(path)) {
            return Optional.empty();
        }
        String method = normalizeMethod(remoteMethod(traceNode));
        return endpoints.stream()
                .filter(endpoint -> StringUtils.hasText(endpoint.getAppId()))
                .filter(endpoint -> !sourceAppId.equals(endpoint.getAppId()))
                .filter(endpoint -> isHttpEndpoint(endpoint.getEndpointType()))
                .filter(endpoint -> methodMatches(method, endpoint.getHttpMethod()))
                .filter(endpoint -> pathMatches(path, normalizePath(endpoint.getUrl())))
                .map(ApiEndpointIndex::getAppId)
                .findFirst();
    }

    private Optional<String> resolveByRpcSignature(TraceNode traceNode, String sourceAppId) {
        String signature = rpcSignature(traceNode);
        if (!StringUtils.hasText(signature)) {
            return Optional.empty();
        }
        return endpoints.stream()
                .filter(endpoint -> StringUtils.hasText(endpoint.getAppId()))
                .filter(endpoint -> !sourceAppId.equals(endpoint.getAppId()))
                .filter(endpoint -> "RPC".equalsIgnoreCase(endpoint.getEndpointType()))
                .filter(endpoint -> signature.equalsIgnoreCase(Optional.ofNullable(endpoint.getUrl()).orElse("")))
                .map(ApiEndpointIndex::getAppId)
                .findFirst();
    }

    private boolean isHttpEndpoint(String endpointType) {
        return "HTTP".equalsIgnoreCase(endpointType) || "FEIGN".equalsIgnoreCase(endpointType) || "HTTP_CLIENT".equalsIgnoreCase(endpointType);
    }

    private boolean methodMatches(String callMethod, String endpointMethod) {
        String normalizedEndpointMethod = normalizeMethod(endpointMethod);
        return !StringUtils.hasText(callMethod)
                || !StringUtils.hasText(normalizedEndpointMethod)
                || "ALL".equals(normalizedEndpointMethod)
                || callMethod.equals(normalizedEndpointMethod);
    }

    private boolean pathMatches(String callPath, String endpointPath) {
        if (!StringUtils.hasText(callPath) || !StringUtils.hasText(endpointPath)) {
            return false;
        }
        if (callPath.equals(endpointPath)) {
            return true;
        }
        String[] callParts = trimSlash(callPath).split("/");
        String[] endpointParts = trimSlash(endpointPath).split("/");
        if (callParts.length != endpointParts.length) {
            return false;
        }
        for (int i = 0; i < callParts.length; i++) {
            String endpointPart = endpointParts[i];
            if (isPathVariable(endpointPart) || "*".equals(endpointPart)) {
                continue;
            }
            if (!callParts[i].equals(endpointPart)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPathVariable(String part) {
        return (part.startsWith("{") && part.endsWith("}")) || part.startsWith(":");
    }

    private String trimSlash(String path) {
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public String remoteUrl(TraceNode traceNode) {
        if (traceNode instanceof FeignTraceNode) {
            FeignTraceNode node = (FeignTraceNode) traceNode;
            return firstText(node.getRemoteUrl(), node.getServiceURL());
        }
        if (traceNode instanceof HttpClientTraceNode) {
            return ((HttpClientTraceNode) traceNode).getServiceURL();
        }
        if (traceNode instanceof DubboTraceNode) {
            return ((DubboTraceNode) traceNode).getRemoteUrl();
        }
        if (traceNode instanceof SofaRpcTraceNode) {
            return ((SofaRpcTraceNode) traceNode).getDirectUrl();
        }
        return "";
    }

    public String remoteMethod(TraceNode traceNode) {
        if (traceNode instanceof FeignTraceNode) {
            FeignTraceNode node = (FeignTraceNode) traceNode;
            return firstText(node.getRemoteMethod(), node.getServiceMethod());
        }
        if (traceNode instanceof HttpClientTraceNode) {
            return ((HttpClientTraceNode) traceNode).getServiceMethod();
        }
        return "";
    }

    public String rpcSignature(TraceNode traceNode) {
        if (traceNode instanceof DubboTraceNode) {
            DubboTraceNode node = (DubboTraceNode) traceNode;
            return joinSignature(node.getServiceInterface(), node.getServiceMethodName());
        }
        if (traceNode instanceof SofaRpcTraceNode) {
            SofaRpcTraceNode node = (SofaRpcTraceNode) traceNode;
            return joinSignature(node.getInterfaceName(), node.getMethodName());
        }
        return "";
    }

    public String remoteTarget(TraceNode traceNode) {
        String url = remoteUrl(traceNode);
        if (StringUtils.hasText(url)) {
            return url;
        }
        String signature = rpcSignature(traceNode);
        if (StringUtils.hasText(signature)) {
            return signature;
        }
        if (traceNode instanceof RabbitMQTraceNode) {
            RabbitMQTraceNode node = (RabbitMQTraceNode) traceNode;
            return firstText(node.getExchange(), node.getRoutingKey());
        }
        if (traceNode instanceof RocketMQProducerTraceNode) {
            return ((RocketMQProducerTraceNode) traceNode).getProducer();
        }
        if (traceNode instanceof KafkaMQTraceNode) {
            return ((KafkaMQTraceNode) traceNode).getProducerRecord();
        }
        return "";
    }

    public String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String joinSignature(String interfaceName, String methodName) {
        if (!StringUtils.hasText(interfaceName)) {
            return "";
        }
        return interfaceName + (StringUtils.hasText(methodName) ? "#" + methodName : "");
    }

    private String extractPath(String url) {
        try {
            URI uri = URI.create(url);
            if (StringUtils.hasText(uri.getPath())) {
                return uri.getPath();
            }
        } catch (Exception ignore) {
        }
        int schemeIndex = url.indexOf("://");
        String withoutHost = schemeIndex >= 0 ? url.substring(schemeIndex + 3) : url;
        int slashIndex = withoutHost.indexOf('/');
        return slashIndex >= 0 ? withoutHost.substring(slashIndex) : withoutHost;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String result = path.split("\\?")[0].trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        while (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String normalizeMethod(String method) {
        return StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "";
    }

    public static class RemoteCallRelation {
        private final String sourceAppId;
        private final String targetAppId;
        private final String type;
        private final String target;

        public RemoteCallRelation(String sourceAppId, String targetAppId, String type, String target) {
            this.sourceAppId = sourceAppId;
            this.targetAppId = targetAppId;
            this.type = type;
            this.target = target;
        }

        public String getSourceAppId() {
            return sourceAppId;
        }

        public String getTargetAppId() {
            return targetAppId;
        }

        public String getType() {
            return type;
        }

        public String getTarget() {
            return target;
        }
    }
}
