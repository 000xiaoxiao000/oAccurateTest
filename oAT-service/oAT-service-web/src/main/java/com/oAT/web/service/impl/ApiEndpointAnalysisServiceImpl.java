package com.oAT.web.service.impl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.oAT.agent.model.DubboTraceNode;
import com.oAT.agent.model.FeignTraceNode;
import com.oAT.agent.model.HttpClientTraceNode;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.ApiEndpointIndex;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import com.oAT.web.service.ApiEndpointAnalysisService;
import com.oAT.web.service.entity.ApiEndpointViewVo;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ApiEndpointAnalysisServiceImpl implements ApiEndpointAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(ApiEndpointAnalysisServiceImpl.class);
    private static final Set<String> HTTP_MAPPING_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "HeadMapping"
    ));
    private static final Set<String> FEIGN_HTTP_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
            "GET", "POST", "PUT", "DELETE", "PATCH", "HeadMapping", "RequestLine", "Headers",
            "HttpExchange", "GetExchange", "PostExchange", "PutExchange", "DeleteExchange", "PatchExchange"
    ));
    private static final Set<String> FEIGN_CLIENT_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "FeignClient", "Client", "HttpExchange"
    ));

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;
    @Autowired
    private TraceNodeRepository traceNodeRepository;
    @Autowired
    private CaseCenterRepository caseCenterRepository;
    @Autowired
    private SystemSnapshotRepository systemSnapshotRepository;

    @Override
    public void analyzeUploadedArtifact(String appId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        analyzeArtifactFile(appId, toTempArtifact(file), file.getOriginalFilename());
    }

    @Override
    public void analyzeArtifactFile(String appId, File file) throws IOException {
        analyzeArtifactFile(appId, file, file == null ? null : file.getName());
    }

    @Override
    public List<ApiEndpointViewVo> listByAppId(String appId) {
        refreshCoverage(appId);
        return apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(appId)
                .stream().map(this::toViewVo).collect(Collectors.toList());
    }

    private File toTempArtifact(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String lowerName = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        File tempFile = File.createTempFile("api-endpoint-", suffix(lowerName));
        file.transferTo(tempFile);
        return tempFile;
    }

    private void analyzeArtifactFile(String appId, File file, String sourceName) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("扫描文件不存在");
        }
        String originalName = sourceName == null ? file.getName() : sourceName;
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        boolean tempFile = sourceName != null && !Objects.equals(file.getName(), sourceName);
        try {
            Map<String, EndpointRecord> endpointMap = new LinkedHashMap<>();
            if (lowerName.endsWith(".zip")) {
                scanZip(file, endpointMap);
            } else if (lowerName.endsWith(".jar") || lowerName.endsWith(".war")) {
                scanArchiveFile(file, lowerName.endsWith(".war"), endpointMap);
            } else {
                throw new IllegalArgumentException("仅支持 zip、jar、war 文件");
            }
            persist(appId, originalName, endpointMap.values());
        } finally {
            if (tempFile && !file.delete()) {
                logger.debug("Temp file cleanup skipped: {}", file.getAbsolutePath());
            }
        }
    }

    private void scanZip(File zipFile, Map<String, EndpointRecord> endpointMap) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                byte[] bytes = zis.readAllBytes();
                if (name.endsWith(".java")) {
                    scanJavaSource(name, new String(bytes, StandardCharsets.UTF_8), endpointMap, "zip");
                } else if (name.endsWith(".class")) {
                    scanClassBytes(name, bytes, endpointMap, "zip");
                } else if (name.endsWith(".jar") || name.endsWith(".war")) {
                    scanNestedArchive(name, bytes, endpointMap);
                }
            }
        }
    }

    private void scanArchiveFile(File archiveFile, boolean war, Map<String, EndpointRecord> endpointMap) throws IOException {
        try (JarFile jarFile = new JarFile(archiveFile)) {
            jarFile.stream().filter(e -> !e.isDirectory()).forEach(entry -> {
                try (InputStream in = jarFile.getInputStream(entry)) {
                    byte[] bytes = in.readAllBytes();
                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        scanClassBytes(name, bytes, endpointMap, war ? "war" : "jar");
                    } else if (name.endsWith(".jar")) {
                        scanNestedArchive(name, bytes, endpointMap);
                    }
                } catch (Exception ex) {
                    logger.warn("scan archive entry failed: {}", entry.getName(), ex);
                }
            });
        }
    }

    private void scanNestedArchive(String name, byte[] bytes, Map<String, EndpointRecord> endpointMap) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry nested;
            while ((nested = zis.getNextEntry()) != null) {
                if (nested.isDirectory()) {
                    continue;
                }
                String nestedName = name + "!/" + nested.getName();
                byte[] nestedBytes = zis.readAllBytes();
                if (nestedName.endsWith(".class")) {
                    scanClassBytes(nestedName, nestedBytes, endpointMap, "nested");
                } else if (nestedName.endsWith(".jar")) {
                    scanNestedArchive(nestedName, nestedBytes, endpointMap);
                }
            }
        }
    }

    private void scanJavaSource(String entryName, String source, Map<String, EndpointRecord> endpointMap, String sourceType) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(source);
            for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                String className = type.getFullyQualifiedName().orElseGet(type::getNameAsString);
                boolean feignClient = hasFeignClientAnnotation(type.getAnnotations());
                String classBasePath = extractRequestPath(type.getAnnotations());
                String feignBasePath = extractFeignClientPath(type.getAnnotations());
                String rpcServiceName = extractRpcService(type.getAnnotations(), className);
                for (MethodDeclaration method : type.getMethods()) {
                    MappingMeta mapping = extractHttpMapping(method.getAnnotations());
                    if (mapping != null) {
                        EndpointRecord record = new EndpointRecord();
                        record.endpointType = feignClient ? "FEIGN" : "HTTP";
                        record.httpMethod = StringUtils.hasText(mapping.httpMethod) ? mapping.httpMethod : "ALL";
                        record.url = normalizeUrl(feignClient ? feignBasePath : classBasePath, mapping.path);
                        record.className = className;
                        record.methodName = method.getNameAsString();
                        record.methodDesc = method.getSignature().asString();
                        record.sourceType = sourceType;
                        record.sourceName = entryName;
                        endpointMap.put(record.uniqueKey(), record);
                    }
                    if (hasRpcReferenceAnnotation(method.getAnnotations())) {
                        EndpointRecord rpcRecord = buildRpcRecord(className, method.getNameAsString(), method.getSignature().asString(),
                                sourceType, entryName, extractRpcService(method.getAnnotations(), rpcServiceName));
                        endpointMap.put(rpcRecord.uniqueKey(), rpcRecord);
                    }
                }
            }
        } catch (Exception ex) {
            logger.debug("parse java source failed: {}", entryName, ex);
        }
    }

    private void scanClassBytes(String entryName, byte[] bytes, Map<String, EndpointRecord> endpointMap, String sourceType) {
        try {
            ClassReader reader = new ClassReader(bytes);
            reader.accept(new EndpointClassVisitor(entryName, sourceType, endpointMap), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Exception ex) {
            logger.debug("parse class failed: {}", entryName, ex);
        }
    }

    private void persist(String appId, String sourceName, Iterable<EndpointRecord> records) {
        apiEndpointRepository.deleteByAppId(appId);
        Map<String, Integer> hitMap = buildCoverageMap(appId);
        Map<String, EndpointAggregate> aggregateMap = new LinkedHashMap<>();
        for (EndpointRecord record : records) {
            aggregateMap.computeIfAbsent(record.mergeKey(), key -> new EndpointAggregate(record)).merge(record);
        }
        List<ApiEndpointIndex> indexes = new ArrayList<>();
        Date now = new Date();
        for (EndpointAggregate aggregate : aggregateMap.values()) {
            ApiEndpointIndex index = new ApiEndpointIndex();
            index.setId(UUID.randomUUID().toString());
            index.setAppId(appId);
            index.setSourceName(sourceName + " :: " + aggregate.primarySourceName());
            index.setSourceNames(joinValues(aggregate.sourceNames));
            index.setSourceType(aggregate.primarySourceType());
            index.setSourceTypeNames(joinValues(aggregate.sourceTypes));
            index.setEndpointType(aggregate.endpointType);
            index.setUrl(aggregate.url);
            index.setHttpMethod(aggregate.httpMethod);
            index.setClassName(aggregate.primaryClassName());
            index.setClassNames(joinValues(aggregate.classNames));
            index.setMethodName(aggregate.primaryMethodName());
            index.setMethodNames(joinValues(aggregate.methodNames));
            index.setMethodDesc(aggregate.primaryMethodDesc());
            index.setMethodDescs(joinValues(aggregate.methodDescs));
            applyCoverage(index, hitMap);
            index.setMergedSourceCount(aggregate.sourceNames.size());
            index.setCreateTime(now);
            index.setUpdateTime(now);
            indexes.add(index);
        }
        apiEndpointRepository.saveAll(indexes);
    }

    private void refreshCoverage(String appId) {
        List<ApiEndpointIndex> indexes = apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(appId);
        if (indexes == null || indexes.isEmpty()) {
            return;
        }
        Map<String, Integer> hitMap = buildCoverageMap(appId);
        Date now = new Date();
        boolean changed = false;
        for (ApiEndpointIndex index : indexes) {
            Integer oldHitCount = index.getHitCount();
            Boolean oldCovered = index.getCovered();
            String oldStatus = index.getCoverageStatus();
            applyCoverage(index, hitMap);
            if (!Objects.equals(oldHitCount, index.getHitCount())
                    || !Objects.equals(oldCovered, index.getCovered())
                    || !Objects.equals(oldStatus, index.getCoverageStatus())) {
                index.setUpdateTime(now);
                changed = true;
            }
        }
        if (changed) {
            apiEndpointRepository.saveAll(indexes);
        }
    }

    private void applyCoverage(ApiEndpointIndex index, Map<String, Integer> hitMap) {
        int hitCount = resolveHitCount(hitMap, index.getEndpointType(), index.getHttpMethod(), index.getUrl());
        index.setHitCount(hitCount);
        index.setCovered(hitCount > 0);
        index.setCoverageStatus(hitCount > 0 ? "covered" : "uncovered");
    }

    private int resolveHitCount(Map<String, Integer> hitMap, String endpointType, String httpMethod, String url) {
        String method = normalizeHttpMethod(httpMethod);
        String directKey = coverageKey(endpointType, method, url);
        int hitCount = hitMap.getOrDefault(directKey, 0);
        if (hitCount > 0 || "RPC".equals(endpointType)) {
            return hitCount;
        }
        String endpointPath = simplifyCoverageTarget(url);
        for (Map.Entry<String, Integer> entry : hitMap.entrySet()) {
            CoverageKey key = CoverageKey.parse(entry.getKey());
            if (key == null || !Objects.equals(endpointType, key.endpointType)) {
                continue;
            }
            if (!isHttpMethodMatched(method, key.httpMethod)) {
                continue;
            }
            if (isPathMatched(endpointPath, key.target)) {
                hitCount += entry.getValue();
            }
        }
        return hitCount;
    }

    private boolean isPathMatched(String endpointPath, String tracePath) {
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

    private List<String> pathParts(String path) {
        return Arrays.stream(path.split("/"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private boolean isPathVariable(String value) {
        return StringUtils.hasText(value) && value.startsWith("{") && value.endsWith("}");
    }

    private String coverageKey(String endpointType, String httpMethod, String url) {
        if ("RPC".equals(endpointType)) {
            return endpointType + "|" + normalizeHttpMethod(httpMethod) + "|" + (url == null ? "" : url.trim());
        }
        return endpointType + "|" + normalizeHttpMethod(httpMethod) + "|" + simplifyCoverageTarget(url);
    }

    private String normalizeHttpMethod(String httpMethod) {
        if (!StringUtils.hasText(httpMethod)) {
            return "ALL";
        }
        return httpMethod.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isHttpMethodMatched(String endpointMethod, String traceMethod) {
        String normalizedEndpointMethod = normalizeHttpMethod(endpointMethod);
        String normalizedTraceMethod = normalizeHttpMethod(traceMethod);
        return "ALL".equals(normalizedEndpointMethod) || normalizedEndpointMethod.equals(normalizedTraceMethod);
    }

    private Map<String, Integer> buildCoverageMap(String appId) {
        Map<String, TraceNodeIndex> traceMap = new LinkedHashMap<>();
        addTraceIndexes(traceMap, traceNodeRepository.findByAppId(appId), appId);
        for (String traceId : findSnapshotTraceIds(appId)) {
            addTraceIndexes(traceMap, traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 10000)), appId);
        }
        Map<String, Integer> hitMap = new HashMap<>();
        for (TraceNodeIndex item : traceMap.values()) {
            TraceNode node;
            try {
                node = item.toTraceNode();
            } catch (Exception ignored) {
                continue;
            }
            if (node instanceof HttpTraceNode) {
                HttpTraceNode n = (HttpTraceNode) node;
                addHit(hitMap, "HTTP", n.getRequestMethod(), n.getRequestUrl());
            } else if (node instanceof HttpClientTraceNode) {
                HttpClientTraceNode n = (HttpClientTraceNode) node;
                addHit(hitMap, "HTTP_CLIENT", n.getServiceMethod(), n.getServiceURL());
            } else if (node instanceof FeignTraceNode) {
                FeignTraceNode n = (FeignTraceNode) node;
                addHit(hitMap, "FEIGN", n.getServiceMethod(), firstText(n.getServiceURL(), n.getRemoteUrl()));
            } else if (node instanceof DubboTraceNode) {
                DubboTraceNode n = (DubboTraceNode) node;
                addHit(hitMap, "RPC", "INVOKE", n.getServiceInterface() + "#" + n.getServiceMethodName());
            }
        }
        return hitMap;
    }

    private void addTraceIndexes(Map<String, TraceNodeIndex> traceMap, List<TraceNodeIndex> traces) {
        addTraceIndexes(traceMap, traces, null);
    }

    private void addTraceIndexes(Map<String, TraceNodeIndex> traceMap, List<TraceNodeIndex> traces, String appId) {
        if (traces == null) {
            return;
        }
        for (TraceNodeIndex trace : traces) {
            if (trace == null) {
                continue;
            }
            if (StringUtils.hasText(appId) && !appId.equals(trace.getAppId())) {
                continue;
            }
            String key = StringUtils.hasText(trace.getId()) ? trace.getId() : trace.getTraceId() + "_" + trace.getTraceNodeId();
            traceMap.putIfAbsent(key, trace);
        }
    }

    private Set<String> findSnapshotTraceIds(String appId) {
        Set<String> traceIds = new LinkedHashSet<>();
        List<CaseCenterIndex> caseCenters = caseCenterRepository.findBySnapshot_AppId(appId);
        for (CaseCenterIndex item : caseCenters) {
            if (item == null || item.getSnapshot() == null) {
                continue;
            }
            if (appId.equals(item.getSnapshot().getAppId()) && StringUtils.hasText(item.getSnapshot().getTraceId())) {
                traceIds.add(item.getSnapshot().getTraceId());
            }
        }
        List<SystemSnapshot> systemSnapshots = systemSnapshotRepository.findByAppId(appId);
        for (SystemSnapshot snapshot : systemSnapshots) {
            if (snapshot != null && appId.equals(snapshot.getAppId()) && StringUtils.hasText(snapshot.getTraceId())) {
                traceIds.add(snapshot.getTraceId());
            }
        }
        return traceIds;
    }

    private void addHit(Map<String, Integer> hitMap, String type, String httpMethod, String target) {
        if (!StringUtils.hasText(target)) {
            return;
        }
        String key = coverageKey(type, httpMethod, target);
        hitMap.put(key, hitMap.getOrDefault(key, 0) + 1);
    }

    private String firstText(String... values) {
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

    private ApiEndpointViewVo toViewVo(ApiEndpointIndex index) {
        ApiEndpointViewVo vo = new ApiEndpointViewVo();
        vo.setId(index.getId());
        vo.setEndpointType(index.getEndpointType());
        vo.setUrl(index.getUrl());
        vo.setHttpMethod(index.getHttpMethod());
        vo.setClassName(defaultIfBlank(index.getClassNames(), index.getClassName()));
        vo.setMethodName(defaultIfBlank(index.getMethodNames(), index.getMethodName()));
        vo.setSourceType(defaultIfBlank(index.getSourceTypeNames(), index.getSourceType()));
        vo.setSourceName(defaultIfBlank(index.getSourceNames(), index.getSourceName()));
        vo.setMethodDesc(defaultIfBlank(index.getMethodDescs(), index.getMethodDesc()));
        vo.setClassNameList(splitLines(index.getClassNames(), index.getClassName()));
        vo.setMethodNameList(splitLines(index.getMethodNames(), index.getMethodName()));
        vo.setMethodDescList(splitLines(index.getMethodDescs(), index.getMethodDesc()));
        vo.setSourceTypeList(splitLines(index.getSourceTypeNames(), index.getSourceType()));
        vo.setSourceNameList(splitLines(index.getSourceNames(), index.getSourceName()));
        vo.setEndpointKeyParts(Arrays.asList(
                defaultIfBlank(index.getEndpointType(), ""),
                defaultIfBlank(index.getHttpMethod(), ""),
                defaultIfBlank(index.getUrl(), "")
        ));
        vo.setMergedSourceCount(index.getMergedSourceCount() == null ? 0 : index.getMergedSourceCount());
        vo.setCoverageStatus(index.getCoverageStatus());
        vo.setCovered(Boolean.TRUE.equals(index.getCovered()));
        vo.setHitCount(index.getHitCount() == null ? 0 : index.getHitCount());
        return vo;
    }

    private String suffix(String lowerName) {
        if (lowerName.endsWith(".zip")) return ".zip";
        if (lowerName.endsWith(".jar")) return ".jar";
        if (lowerName.endsWith(".war")) return ".war";
        return ".tmp";
    }

    private String extractRequestPath(List<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            if ("RequestMapping".equals(annotation.getNameAsString())) {
                String path = readAnnotationPath(annotation);
                if (StringUtils.hasText(path)) {
                    return path;
                }
            }
        }
        return "";
    }

    private String extractFeignClientPath(List<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            if (FEIGN_CLIENT_ANNOTATIONS.contains(annotation.getNameAsString())) {
                String path = readAnnotationPath(annotation);
                if (StringUtils.hasText(path)) {
                    return path;
                }
            }
        }
        return "";
    }

    private boolean hasFeignClientAnnotation(List<AnnotationExpr> annotations) {
        return annotations.stream().anyMatch(annotation -> FEIGN_CLIENT_ANNOTATIONS.contains(annotation.getNameAsString()));
    }

    private String extractRpcService(List<AnnotationExpr> annotations, String defaultService) {
        for (AnnotationExpr annotation : annotations) {
            if (isRpcClassAnnotation(annotation.getNameAsString()) || isRpcMethodAnnotation(annotation.getNameAsString())) {
                String path = readAnnotationPath(annotation);
                if (StringUtils.hasText(path)) {
                    return path;
                }
            }
        }
        return defaultService;
    }

    private boolean hasRpcReferenceAnnotation(List<AnnotationExpr> annotations) {
        return annotations.stream().anyMatch(annotation -> isRpcMethodAnnotation(annotation.getNameAsString()));
    }

    private MappingMeta extractHttpMapping(List<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            String name = annotation.getNameAsString();
            if (!HTTP_MAPPING_ANNOTATIONS.contains(name) && !"RequestLine".equals(name)) {
                continue;
            }
            MappingMeta meta = new MappingMeta();
            if ("RequestLine".equals(name)) {
                applyRequestLineMeta(meta, readRequestLine(annotation));
            } else {
                meta.path = readAnnotationPath(annotation);
                meta.httpMethod = deduceHttpMethod(name, annotation);
            }
            return meta;
        }
        return null;
    }

    private String deduceHttpMethod(String annotationName, AnnotationExpr annotationExpr) {
        switch (annotationName) {
            case "GetMapping":
            case "GET": return "GET";
            case "PostMapping":
            case "POST": return "POST";
            case "PutMapping":
            case "PUT": return "PUT";
            case "DeleteMapping":
            case "DELETE": return "DELETE";
            case "PatchMapping":
            case "PATCH": return "PATCH";
            case "HEAD":
            case "HeadMapping": return "HEAD";
            default:
                if (annotationExpr instanceof NormalAnnotationExpr) {
                    for (MemberValuePair pair : ((NormalAnnotationExpr) annotationExpr).getPairs()) {
                        if ("method".equals(pair.getNameAsString())) {
                            String value = pair.getValue().toString();
                            int dot = value.lastIndexOf('.');
                            return dot >= 0 ? value.substring(dot + 1) : value;
                        }
                    }
                }
                return "ALL";
        }
    }

    private String readAnnotationPath(AnnotationExpr annotationExpr) {
        if (annotationExpr instanceof SingleMemberAnnotationExpr) {
            return trimQuotes(((SingleMemberAnnotationExpr) annotationExpr).getMemberValue().toString());
        }
        if (annotationExpr instanceof NormalAnnotationExpr) {
            for (MemberValuePair pair : ((NormalAnnotationExpr) annotationExpr).getPairs()) {
                if ("value".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString()) || "url".equals(pair.getNameAsString())) {
                    return extractExpressionValue(pair.getValue());
                }
            }
        }
        return "";
    }

    private String readRequestLine(AnnotationExpr annotationExpr) {
        String value = readAnnotationPath(annotationExpr);
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private void applyRequestLineMeta(MappingMeta meta, String requestLine) {
        if (!StringUtils.hasText(requestLine)) {
            meta.httpMethod = "ALL";
            meta.path = "";
            return;
        }
        String[] parts = requestLine.trim().split("\\s+", 2);
        if (parts.length == 2) {
            meta.httpMethod = parts[0].toUpperCase(Locale.ROOT);
            meta.path = parts[1].trim();
        } else {
            meta.httpMethod = "ALL";
            meta.path = requestLine.trim();
        }
    }

    private String extractExpressionValue(Expression expression) {
        if (expression instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) expression).getValue();
        }
        String raw = expression.toString();
        if (raw.startsWith("{") && raw.endsWith("}")) {
            raw = raw.substring(1, raw.length() - 1).split(",")[0].trim();
        }
        if (raw.endsWith(".class")) {
            return raw.substring(0, raw.length() - 6);
        }
        return trimQuotes(raw);
    }

    private String trimQuotes(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if (result.startsWith("\"") && result.endsWith("\"") && result.length() >= 2) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private String joinValues(Set<String> values) {
        return values.stream().filter(StringUtils::hasText).collect(Collectors.joining("\n"));
    }

    private String defaultIfBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private List<String> splitLines(String preferred, String fallback) {
        String value = defaultIfBlank(preferred, fallback);
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split("\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private String normalizeUrl(String base, String path) {
        String left = normalizeEndpointSegment(base);
        String right = normalizeEndpointSegment(path);
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return "/";
        }
        if (left.startsWith("http://") || left.startsWith("https://")) {
            if (StringUtils.hasText(right)) {
                if (right.startsWith("http://") || right.startsWith("https://")) {
                    return right;
                }
                return (left.replaceAll("/+$", "") + "/" + right.replaceAll("^/+", "")).replaceAll("(?<!:)/{2,}", "/");
            }
            return left;
        }
        String merged = (left + right).replaceAll("//+", "/");
        return StringUtils.hasText(merged) ? merged : "/";
    }

    private String normalizeEndpointSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value.trim();
        if ("/".equals(result)) {
            return result;
        }
        if (result.endsWith(".class")) {
            result = result.substring(0, result.length() - 6);
        }
        if (result.startsWith("http://") || result.startsWith("https://")) {
            return result.replaceAll("(?<!:)/{2,}", "/").replaceFirst("^http:/", "http://").replaceFirst("^https:/", "https://");
        }
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        return result;
    }

    private static String simplifyCoverageTarget(String value) {
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

    private EndpointRecord buildRpcRecord(String className, String methodName, String methodDesc,
                                          String sourceType, String sourceName, String rpcServiceName) {
        EndpointRecord record = new EndpointRecord();
        record.endpointType = "RPC";
        record.httpMethod = "INVOKE";
        String targetService = StringUtils.hasText(rpcServiceName) ? rpcServiceName : className;
        if (targetService.endsWith(".class")) {
            targetService = targetService.substring(0, targetService.length() - 6);
        }
        record.url = targetService.contains("#") ? targetService : targetService + "#" + methodName;
        record.className = className;
        record.methodName = methodName;
        record.methodDesc = methodDesc;
        record.sourceType = sourceType;
        record.sourceName = sourceName;
        return record;
    }

    private static class MappingMeta {
        private String path;
        private String httpMethod;
    }

    private static class EndpointAggregate {
        private final String endpointType;
        private final String url;
        private final String httpMethod;
        private final Set<String> classNames = new TreeSet<>();
        private final Set<String> methodNames = new TreeSet<>();
        private final Set<String> methodDescs = new TreeSet<>();
        private final Set<String> sourceTypes = new TreeSet<>();
        private final Set<String> sourceNames = new TreeSet<>();

        EndpointAggregate(EndpointRecord record) {
            this.endpointType = record.endpointType;
            this.url = record.url;
            this.httpMethod = record.httpMethod;
            merge(record);
        }

        void merge(EndpointRecord record) {
            if (StringUtils.hasText(record.className)) {
                classNames.add(record.className);
            }
            if (StringUtils.hasText(record.methodName)) {
                methodNames.add(record.methodName);
            }
            if (StringUtils.hasText(record.methodDesc)) {
                methodDescs.add(record.methodDesc);
            }
            if (StringUtils.hasText(record.sourceType)) {
                sourceTypes.add(record.sourceType);
            }
            if (StringUtils.hasText(record.sourceName)) {
                sourceNames.add(record.sourceName);
            }
        }

        String primaryClassName() { return classNames.isEmpty() ? "" : classNames.iterator().next(); }
        String primaryMethodName() { return methodNames.isEmpty() ? "" : methodNames.iterator().next(); }
        String primaryMethodDesc() { return methodDescs.isEmpty() ? "" : methodDescs.iterator().next(); }
        String primarySourceType() { return sourceTypes.isEmpty() ? "" : sourceTypes.iterator().next(); }
        String primarySourceName() { return sourceNames.isEmpty() ? "" : sourceNames.iterator().next(); }
        String coverageKey() { return endpointType + "|" + normalizeCoverageMethod(httpMethod) + "|" + simplifyCoverageTarget(url); }
    }

    private static class CoverageKey {
        private final String endpointType;
        private final String httpMethod;
        private final String target;

        private CoverageKey(String endpointType, String httpMethod, String target) {
            this.endpointType = endpointType;
            this.httpMethod = httpMethod;
            this.target = target;
        }

        private static CoverageKey parse(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            String[] parts = value.split("\\|", 3);
            if (parts.length == 3) {
                return new CoverageKey(parts[0], normalizeCoverageMethod(parts[1]), parts[2]);
            }
            if (parts.length == 2) {
                return new CoverageKey(parts[0], "ALL", parts[1]);
            }
            return null;
        }
    }

    private static String normalizeCoverageMethod(String httpMethod) {
        if (!StringUtils.hasText(httpMethod)) {
            return "ALL";
        }
        return httpMethod.trim().toUpperCase(Locale.ROOT);
    }

    private static class EndpointRecord {
        private String endpointType;
        private String url;
        private String httpMethod;
        private String className;
        private String methodName;
        private String methodDesc;
        private String sourceType;
        private String sourceName;

        private String uniqueKey() {
            return endpointType + "|" + httpMethod + "|" + url + "|" + className + "|" + methodName + "|" + methodDesc;
        }

        private String mergeKey() {
            return endpointType + "|" + normalizeCoverageMethod(httpMethod) + "|" + simplifyCoverageTarget(url);
        }

        private String coverageKey() {
            String target = url == null ? "" : url;
            if ("RPC".equals(endpointType)) {
                return endpointType + "|" + normalizeCoverageMethod(httpMethod) + "|" + target.trim();
            }
            return endpointType + "|" + normalizeCoverageMethod(httpMethod) + "|" + simplifyCoverageTarget(target);
        }
    }

    private class EndpointClassVisitor extends ClassVisitor {
        private final String entryName;
        private final String sourceType;
        private final Map<String, EndpointRecord> endpointMap;
        private String className;
        private String classBasePath = "";
        private boolean feignClient;
        private String feignBaseUrl = "";
        private String rpcServiceName = "";

        EndpointClassVisitor(String entryName, String sourceType, Map<String, EndpointRecord> endpointMap) {
            super(Opcodes.ASM5);
            this.entryName = entryName;
            this.sourceType = sourceType;
            this.endpointMap = endpointMap;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String ann = Type.getType(descriptor).getClassName();
            String simple = ann.substring(ann.lastIndexOf('.') + 1);
            if ("RequestMapping".equals(simple)) {
                return new MappingAnnotationVisitor(v -> classBasePath = v);
            }
            if (FEIGN_CLIENT_ANNOTATIONS.contains(simple)) {
                feignClient = true;
                return new MappingAnnotationVisitor(v -> feignBaseUrl = v);
            }
            if (isRpcClassAnnotation(simple)) {
                return new MappingAnnotationVisitor(v -> rpcServiceName = v);
            }
            return super.visitAnnotation(descriptor, visible);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new EndpointMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions), className,
                    classBasePath, feignClient, feignBaseUrl, rpcServiceName, name, descriptor, entryName, sourceType, endpointMap);
        }
    }

    private static class MappingAnnotationVisitor extends AnnotationVisitor {
        private final java.util.function.Consumer<String> consumer;

        MappingAnnotationVisitor(java.util.function.Consumer<String> consumer) {
            super(Opcodes.ASM5);
            this.consumer = consumer;
        }

        @Override
        public void visit(String name, Object value) {
            if (("value".equals(name) || "path".equals(name) || "url".equals(name) || "name".equals(name)
                    || "serviceInterface".equals(name) || name == null) && value != null) {
                consumer.accept(String.valueOf(value));
            }
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            if ("value".equals(name) || "path".equals(name) || "url".equals(name) || name == null) {
                return new AnnotationVisitor(Opcodes.ASM5) {
                    @Override
                    public void visit(String n, Object value) {
                        if (value != null) {
                            consumer.accept(String.valueOf(value));
                        }
                    }
                };
            }
            return super.visitArray(name);
        }
    }

    private class EndpointMethodVisitor extends MethodVisitor {
        private final String className;
        private final String classBasePath;
        private final boolean feignClient;
        private final String feignBaseUrl;
        private final String rpcServiceName;
        private final String methodName;
        private final String methodDesc;
        private final String entryName;
        private final String sourceType;
        private final Map<String, EndpointRecord> endpointMap;
        private final Set<String> stringConstants = new LinkedHashSet<>();
        private final Map<Integer, String> localStringValues = new HashMap<>();
        private final List<String> invocationTrail = new ArrayList<>();
        private final List<String> webClientPathSegments = new ArrayList<>();
        private final List<String> webClientQueryKeys = new ArrayList<>();
        private final Set<String> webClientHttpMethods = new LinkedHashSet<>();
        private final Set<String> rpcAnnotationTargets = new LinkedHashSet<>();
        private String mappingPath = "";
        private String mappingMethod = null;

        EndpointMethodVisitor(MethodVisitor mv, String className, String classBasePath, boolean feignClient,
                              String feignBaseUrl, String rpcServiceName, String methodName, String methodDesc, String entryName,
                              String sourceType, Map<String, EndpointRecord> endpointMap) {
            super(Opcodes.ASM5, mv);
            this.className = className;
            this.classBasePath = classBasePath;
            this.feignClient = feignClient;
            this.feignBaseUrl = feignBaseUrl;
            this.rpcServiceName = rpcServiceName;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.entryName = entryName;
            this.sourceType = sourceType;
            this.endpointMap = endpointMap;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            String ann = Type.getType(descriptor).getClassName();
            String simple = ann.substring(ann.lastIndexOf('.') + 1);
            if (!FEIGN_HTTP_ANNOTATIONS.contains(simple)) {
                if (isRpcMethodAnnotation(simple)) {
                    return new MappingAnnotationVisitor(v -> {
                        if (StringUtils.hasText(v)) {
                            rpcAnnotationTargets.add(v);
                            if (!StringUtils.hasText(mappingPath)) {
                                mappingPath = v;
                            }
                        }
                    });
                }
                return super.visitAnnotation(descriptor, visible);
            }
            return new AnnotationVisitor(Opcodes.ASM5) {
                @Override
                public void visit(String name, Object value) {
                    if (("value".equals(name) || "path".equals(name) || "url".equals(name) || name == null) && value != null) {
                        if ("RequestLine".equals(simple)) {
                            applyRequestLineText(String.valueOf(value));
                        } else {
                            mappingPath = String.valueOf(value);
                        }
                    }
                    if (("method".equals(name) || name == null) && value != null) {
                        String enumValue = String.valueOf(value);
                        if (enumValue.contains(".")) {
                            enumValue = enumValue.substring(enumValue.lastIndexOf('.') + 1);
                        }
                        mappingMethod = enumValue;
                    }
                    if ("serviceInterface".equals(name) && value != null) {
                        rpcAnnotationTargets.add(String.valueOf(value));
                    }
                }

                @Override
                public void visitEnum(String name, String descriptor, String value) {
                    if ("method".equals(name)) {
                        mappingMethod = value;
                    }
                }

                @Override
                public AnnotationVisitor visitArray(String name) {
                    if ("value".equals(name) || "path".equals(name)) {
                        return new AnnotationVisitor(Opcodes.ASM5) {
                            @Override
                            public void visit(String n, Object value) {
                                if (value != null && !StringUtils.hasText(mappingPath)) {
                                    mappingPath = String.valueOf(value);
                                }
                            }
                        };
                    }
                    if ("method".equals(name)) {
                        return new AnnotationVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitEnum(String n, String descriptor, String value) {
                                if (value != null && !StringUtils.hasText(mappingMethod)) {
                                    mappingMethod = value;
                                }
                            }

                            @Override
                            public void visit(String n, Object value) {
                                if (value != null && !StringUtils.hasText(mappingMethod)) {
                                    mappingMethod = String.valueOf(value);
                                }
                            }
                        };
                    }
                    return super.visitArray(name);
                }

                @Override
                public void visitEnd() {
                    if (StringUtils.hasText(mappingPath) || StringUtils.hasText(mappingMethod) || "Headers".equals(simple)) {
                        EndpointRecord record = new EndpointRecord();
                        record.endpointType = feignClient ? "FEIGN" : "HTTP";
                        record.httpMethod = StringUtils.hasText(mappingMethod) ? mappingMethod : inferHttpMethod(simple);
                        record.url = normalizeStaticUrl(feignClient ? feignBaseUrl : classBasePath, mappingPath);
                        record.className = className;
                        record.methodName = methodName;
                        record.methodDesc = methodDesc;
                        record.sourceType = sourceType;
                        record.sourceName = entryName;
                        endpointMap.put(record.uniqueKey(), record);
                    }
                }
            };
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String) {
                stringConstants.add((String) value);
            }
            super.visitLdcInsn(value);
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (opcode == Opcodes.ASTORE) {
                String latestString = latestStringConstant();
                if (StringUtils.hasText(latestString)) {
                    localStringValues.put(varIndex, latestString);
                }
            }
            super.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if ("makeConcatWithConstants".equals(name) && bootstrapMethodArguments != null) {
                for (Object arg : bootstrapMethodArguments) {
                    if (arg instanceof String) {
                        String joined = ((String) arg).replace("\u0001", "");
                        if (StringUtils.hasText(joined)) {
                            stringConstants.add(joined);
                        }
                    }
                }
            }
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            String ownerLower = owner.toLowerCase(Locale.ROOT);
            invocationTrail.add(owner + "#" + name);
            captureWebClientHttpMethod(ownerLower, name);
            captureWebClientUri(ownerLower, name);
            captureWebClientBuilderStep(ownerLower, name);
            if (owner.contains("RestTemplate") || owner.contains("HttpClient") || owner.contains("OkHttpClient")
                    || owner.contains("WebTarget") || owner.contains("Request$Builder") || owner.contains("WebClient")
                    || owner.contains("HttpRequest") || owner.contains("AsyncHttpClient")) {
                maybeAddHttpClientEndpoint(name, owner);
            }
            if (ownerLower.contains("dubbo") || ownerLower.contains("rpc") || ownerLower.contains("referenceconfig")
                    || ownerLower.contains("servicebean") || ownerLower.contains("referencebean")) {
                maybeAddRpcEndpoint();
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        private void maybeAddHttpClientEndpoint(String invokeName, String owner) {
            List<String> candidates = stringConstants.stream()
                    .filter(this::looksLikeHttpTarget)
                    .collect(Collectors.toList());
            if (candidates.isEmpty()) {
                return;
            }
            EndpointRecord record = new EndpointRecord();
            record.endpointType = "HTTP_CLIENT";
            record.httpMethod = guessClientHttpMethod(invokeName, owner, candidates);
            String candidateUrl = resolveBestHttpTarget(candidates);
            if (!StringUtils.hasText(candidateUrl)) {
                return;
            }
            record.url = simplifyCoverageTarget(normalizeUriTemplate(candidateUrl));
            record.className = className;
            record.methodName = methodName;
            record.methodDesc = methodDesc;
            record.sourceType = sourceType;
            record.sourceName = entryName;
            endpointMap.put(record.uniqueKey(), record);
        }

        private void maybeAddRpcEndpoint() {
            Set<String> candidates = new LinkedHashSet<>(rpcAnnotationTargets);
            stringConstants.stream()
                    .filter(this::looksLikeRpcTarget)
                    .forEach(candidates::add);
            String rpcTarget = resolveRpcTarget(candidates);
            if (!StringUtils.hasText(rpcTarget)) {
                return;
            }
            EndpointRecord record = new EndpointRecord();
            record.endpointType = "RPC";
            record.httpMethod = "INVOKE";
            record.url = buildRpcTarget(rpcTarget, methodName, new ArrayList<>(candidates));
            record.className = className;
            record.methodName = methodName;
            record.methodDesc = methodDesc;
            record.sourceType = sourceType;
            record.sourceName = entryName;
            endpointMap.put(record.uniqueKey(), record);
        }

        private void captureWebClientHttpMethod(String ownerLower, String invokeName) {
            if (!ownerLower.contains("webclient")) {
                return;
            }
            String method = invokeName == null ? "" : invokeName.toLowerCase(Locale.ROOT);
            if (Arrays.asList("get", "post", "put", "delete", "patch", "head", "options").contains(method)) {
                webClientHttpMethods.add(method.toUpperCase(Locale.ROOT));
            }
        }

        private void captureWebClientUri(String ownerLower, String invokeName) {
            if (!ownerLower.contains("webclient") || !"uri".equalsIgnoreCase(invokeName)) {
                return;
            }
            String latest = latestStringConstant();
            if (StringUtils.hasText(latest)) {
                stringConstants.add(latest);
            }
            localStringValues.values().stream().filter(StringUtils::hasText).forEach(stringConstants::add);
            String builderPath = buildWebClientBuilderPath();
            if (StringUtils.hasText(builderPath)) {
                stringConstants.add(builderPath);
            }
        }

        private void captureWebClientBuilderStep(String ownerLower, String invokeName) {
            if (!ownerLower.contains("uri") && !ownerLower.contains("webclient")) {
                return;
            }
            String method = invokeName == null ? "" : invokeName.toLowerCase(Locale.ROOT);
            String latest = latestStringConstant();
            if ("path".equals(method) && StringUtils.hasText(latest)) {
                webClientPathSegments.add(latest);
            }
            if ("queryparam".equals(method) && StringUtils.hasText(latest)) {
                webClientQueryKeys.add(latest);
            }
            if (("build".equals(method) || "touri".equals(method)) && StringUtils.hasText(buildWebClientBuilderPath())) {
                stringConstants.add(buildWebClientBuilderPath());
            }
        }

        private String buildWebClientBuilderPath() {
            String path = webClientPathSegments.stream()
                    .filter(StringUtils::hasText)
                    .map(this::normalizeUriTemplate)
                    .collect(Collectors.joining(""));
            if (!StringUtils.hasText(path)) {
                return "";
            }
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            if (webClientQueryKeys.isEmpty()) {
                return normalizedPath;
            }
            String query = webClientQueryKeys.stream()
                    .filter(StringUtils::hasText)
                    .map(key -> key + "={}")
                    .collect(Collectors.joining("&"));
            return normalizedPath + "?" + query;
        }

        private String resolveBestHttpTarget(List<String> candidates) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                String candidate = candidates.get(i);
                if (looksLikeHttpTarget(candidate) && !isLikelyHttpMethodLiteral(candidate)) {
                    return candidate;
                }
            }
            return "";
        }

        private String resolveRpcTarget(Set<String> candidates) {
            if (StringUtils.hasText(rpcServiceName)) {
                return rpcServiceName;
            }
            List<String> ordered = candidates.stream().filter(StringUtils::hasText).collect(Collectors.toList());
            for (int i = ordered.size() - 1; i >= 0; i--) {
                String candidate = ordered.get(i);
                if (candidate.endsWith(".class") || candidate.contains("#") || candidate.contains("com.")) {
                    return candidate;
                }
            }
            return ordered.isEmpty() ? "" : ordered.get(ordered.size() - 1);
        }

        private boolean isLikelyHttpMethodLiteral(String value) {
            String upper = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            return Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS").contains(upper);
        }

        private String latestStringConstant() {
            return stringConstants.stream().filter(StringUtils::hasText).reduce((first, second) -> second).orElse("");
        }

        private String normalizeUriTemplate(String value) {
            if (!StringUtils.hasText(value)) {
                return value;
            }
            String normalized = value.replaceAll("\\{[^/]+}", "{}");
            normalized = normalized.replaceAll("\\$\\{[^/]+}", "{}");
            return normalized;
        }

        private void applyRequestLineText(String requestLine) {
            if (!StringUtils.hasText(requestLine)) {
                return;
            }
            String[] parts = requestLine.trim().split("\\s+", 2);
            if (parts.length == 2) {
                mappingMethod = parts[0].toUpperCase(Locale.ROOT);
                mappingPath = parts[1].trim();
            } else {
                mappingPath = requestLine.trim();
            }
        }

        private String buildRpcTarget(String service, String methodName, List<String> candidates) {
            String targetService = service;
            if (targetService.endsWith(".class")) {
                targetService = targetService.substring(0, targetService.length() - 6);
            }
            if (targetService.contains("#")) {
                return targetService;
            }
            String candidateMethod = candidates.stream()
                    .filter(v -> v.contains("#"))
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (StringUtils.hasText(candidateMethod)) {
                return candidateMethod;
            }
            String resolvedMethod = extractRpcMethodCandidate(candidates);
            if (!StringUtils.hasText(resolvedMethod)) {
                resolvedMethod = StringUtils.hasText(methodName) ? methodName : this.methodName;
            }
            return targetService + "#" + resolvedMethod;
        }

        private String extractRpcMethodCandidate(List<String> candidates) {
            for (int i = candidates.size() - 1; i >= 0; i--) {
                String candidate = candidates.get(i);
                if (!StringUtils.hasText(candidate)) {
                    continue;
                }
                String cleaned = candidate.trim();
                if (cleaned.contains("(") && cleaned.endsWith(")")) {
                    int idx = cleaned.indexOf('(');
                    return cleaned.substring(0, idx);
                }
                if (cleaned.matches("[a-zA-Z_$][\\w$]*")) {
                    return cleaned;
                }
            }
            return "";
        }

        private String inferHttpMethod(String simple) {
            switch (simple) {
                case "GetMapping":
                case "GET":
                case "GetExchange": return "GET";
                case "PostMapping":
                case "POST":
                case "PostExchange": return "POST";
                case "PutMapping":
                case "PUT":
                case "PutExchange": return "PUT";
                case "DeleteMapping":
                case "DELETE":
                case "DeleteExchange": return "DELETE";
                case "PatchMapping":
                case "PATCH":
                case "PatchExchange": return "PATCH";
                case "HEAD":
                case "HeadMapping": return "HEAD";
                default: return "ALL";
            }
        }

        private boolean looksLikeHttpTarget(String value) {
            return StringUtils.hasText(value) && (value.startsWith("http://") || value.startsWith("https://")
                    || value.startsWith("/") || value.startsWith("${") || value.startsWith("#{"));
        }

        private boolean looksLikeRpcTarget(String value) {
            return StringUtils.hasText(value) && (value.contains("#") || value.endsWith(".class")
                    || value.contains(".") || value.contains("/"));
        }

        private String guessClientHttpMethod(String invokeName, String owner, List<String> candidates) {
            String name = invokeName == null ? "" : invokeName.toLowerCase(Locale.ROOT);
            String ownerName = owner == null ? "" : owner.toLowerCase(Locale.ROOT);
            if (name.contains("post")) return "POST";
            if (name.contains("put")) return "PUT";
            if (name.contains("delete")) return "DELETE";
            if (name.contains("patch")) return "PATCH";
            if (name.contains("head")) return "HEAD";
            if (name.contains("option")) return "OPTIONS";
            if (ownerName.contains("webclient") && !webClientHttpMethods.isEmpty()) {
                return webClientHttpMethods.stream().filter(Objects::nonNull).findFirst().orElse("GET");
            }
            if (name.contains("method") || name.contains("exchange")) {
                String upperCandidate = candidates.stream()
                        .map(String::trim)
                        .filter(this::isLikelyHttpMethodLiteral)
                        .reduce((first, second) -> second)
                        .orElse(null);
                if (StringUtils.hasText(upperCandidate)) {
                    return upperCandidate.toUpperCase(Locale.ROOT);
                }
            }
            if (ownerName.contains("webclient") && (name.contains("retrieve") || name.contains("exchange"))) {
                return !webClientHttpMethods.isEmpty() ? webClientHttpMethods.iterator().next() : "GET";
            }
            return "GET";
        }

        private String normalizeStaticUrl(String base, String path) {
            return normalizeUrl(base, path);
        }
    }

    private static boolean isRpcClassAnnotation(String simple) {
        return simple.startsWith("DubboService") || simple.startsWith("Service") || simple.startsWith("Provider")
                || simple.contains("RpcService") || simple.contains("RemoteService");
    }

    private static boolean isRpcMethodAnnotation(String simple) {
        return simple.startsWith("DubboReference") || simple.startsWith("Reference") || simple.contains("RpcReference")
                || simple.contains("Consumer") || simple.contains("ReferenceBean");
    }
}
