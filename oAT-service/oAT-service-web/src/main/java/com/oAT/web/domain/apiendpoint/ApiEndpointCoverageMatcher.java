package com.oAT.web.domain.apiendpoint;

import com.oAT.web.esDao.entity.TraceNodeIndex;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ApiEndpointCoverageMatcher {

    private ApiEndpointCoverageMatcher() {
    }

    public static int resolveHitCount(Map<String, Integer> hitMap, String endpointType, String httpMethod, String url) {
        String method = normalizeHttpMethod(httpMethod);
        String directKey = coverageKey(endpointType, method, url);
        int hitCount = hitMap.getOrDefault(directKey, 0);
        if (hitCount > 0 || "RPC".equals(endpointType)) {
            return hitCount;
        }
        String endpointPath = simplifyCoverageTarget(url);
        for (Map.Entry<String, Integer> entry : hitMap.entrySet()) {
            CoverageKey key = CoverageKey.parse(entry.getKey());
            if (key == null || !Objects.equals(endpointType, key.endpointType())) {
                continue;
            }
            if (!isHttpMethodMatched(method, key.httpMethod())) {
                continue;
            }
            if (isPathMatched(endpointPath, key.target())) {
                hitCount += entry.getValue();
            }
        }
        return hitCount;
    }

    public static String coverageKey(TraceNodeIndex item) {
        if (item == null || item.getType() == null) {
            return null;
        }
        String type = item.getType();
        if ("http".equals(type)) {
            if (!StringUtils.hasText(item.getHttpUrl())) {
                return null;
            }
            return coverageKey("HTTP", item.getHttpMethod(), item.getHttpUrl());
        }
        if ("httpClient".equals(type)) {
            if (!StringUtils.hasText(item.getRemoteUrl())) {
                return null;
            }
            return coverageKey("HTTP_CLIENT", item.getRemoteMethod(), item.getRemoteUrl());
        }
        if ("feign".equals(type)) {
            if (!StringUtils.hasText(item.getRemoteUrl())) {
                return null;
            }
            return coverageKey("FEIGN", item.getRemoteMethod(), item.getRemoteUrl());
        }
        if ("dubbo".equals(type)) {
            if (!StringUtils.hasText(item.getRemoteInterface()) || !StringUtils.hasText(item.getRemoteMethod())) {
                return null;
            }
            return coverageKey("RPC", "INVOKE", item.getRemoteInterface() + "#" + item.getRemoteMethod());
        }
        return null;
    }

    public static String coverageKey(String endpointType, String httpMethod, String url) {
        if ("RPC".equals(endpointType)) {
            return endpointType + "|" + normalizeHttpMethod(httpMethod) + "|" + (url == null ? "" : url.trim());
        }
        return endpointType + "|" + normalizeHttpMethod(httpMethod) + "|" + simplifyCoverageTarget(url);
    }

    public static boolean isHttpMethodMatched(String endpointMethod, String traceMethod) {
        String normalizedEndpointMethod = normalizeHttpMethod(endpointMethod);
        String normalizedTraceMethod = normalizeHttpMethod(traceMethod);
        return "ALL".equals(normalizedEndpointMethod) || normalizedEndpointMethod.equals(normalizedTraceMethod);
    }

    public static boolean isPathMatched(String endpointPath, String tracePath) {
        if (!StringUtils.hasText(endpointPath) || !StringUtils.hasText(tracePath)) {
            return false;
        }
        if (endpointPath.equals(tracePath)) {
            return true;
        }
        List<String> endpointParts = pathParts(endpointPath);
        List<String> traceParts = pathParts(tracePath);
        if (endpointParts.isEmpty() || endpointParts.size() > traceParts.size()) {
            return false;
        }
        int offset = traceParts.size() - endpointParts.size();
        for (int i = 0; i < endpointParts.size(); i++) {
            String endpointPart = endpointParts.get(i);
            String tracePart = traceParts.get(i + offset);
            if (isPathVariable(endpointPart)) {
                continue;
            }
            if (!endpointPart.equals(tracePart)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeHttpMethod(String httpMethod) {
        if (!StringUtils.hasText(httpMethod)) {
            return "ALL";
        }
        return httpMethod.trim().toUpperCase(Locale.ROOT);
    }

    public static String simplifyCoverageTarget(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value.trim();
        int idx = result.indexOf('?');
        if (idx >= 0) {
            result = result.substring(0, idx);
        }
        result = extractPath(result);
        result = result.replaceAll("\\{[^/]+}", "{}");
        result = result.replaceAll("//+", "/");
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        result = result.replaceAll("/+$", "");
        return StringUtils.hasText(result) ? result : "/";
    }

    private static List<String> pathParts(String path) {
        return Arrays.stream(path.split("/"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private static boolean isPathVariable(String value) {
        return StringUtils.hasText(value) && value.startsWith("{") && value.endsWith("}");
    }

    private static String extractPath(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                String path = new URI(value).getPath();
                return StringUtils.hasText(path) ? path : "/";
            } catch (URISyntaxException ignored) {
                int schemeIdx = value.indexOf("://");
                int slashIdx = schemeIdx >= 0 ? value.indexOf('/', schemeIdx + 3) : -1;
                return slashIdx >= 0 ? value.substring(slashIdx) : "/";
            }
        }
        return value;
    }

    public record CoverageKey(String endpointType, String httpMethod, String target) {
        public static CoverageKey parse(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            String[] parts = value.split("\\|", 3);
            if (parts.length == 3) {
                return new CoverageKey(parts[0], normalizeHttpMethod(parts[1]), parts[2]);
            }
            if (parts.length == 2) {
                return new CoverageKey(parts[0], "ALL", parts[1]);
            }
            return null;
        }
    }
}
