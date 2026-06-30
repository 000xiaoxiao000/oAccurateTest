package com.oAT.web.service.impl;

import com.oAT.web.esDao.ApiEndpointRepository;
import com.oAT.web.esDao.entity.ApiEndpointIndex;
import com.oAT.web.domain.apiendpoint.ApiEndpointArtifactScanner;
import com.oAT.web.domain.apiendpoint.ApiEndpointCoverageMatcher;
import com.oAT.web.domain.apiendpoint.ApiEndpointCoverageContextService;
import com.oAT.web.domain.apiendpoint.ApiEndpointPersistenceService;
import com.oAT.web.service.ApiEndpointAnalysisService;
import com.oAT.web.service.entity.ApiEndpointCoverageVo;
import com.oAT.web.service.entity.ApiEndpointViewVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApiEndpointAnalysisServiceImpl implements ApiEndpointAnalysisService {
    private static final Logger logger = LoggerFactory.getLogger(ApiEndpointAnalysisServiceImpl.class);

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;
    @Autowired
    private ApiEndpointCoverageContextService coverageContextService;
    @Autowired
    private ApiEndpointArtifactScanner apiEndpointArtifactScanner;
    @Autowired
    private ApiEndpointPersistenceService apiEndpointPersistenceService;

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
        apiEndpointPersistenceService.refreshCoverage(appId);
        Map<String, List<ApiEndpointViewVo.UsecaseLinkVo>> usecaseLinks = coverageContextService.buildUsecaseLinksByCoverageKey(appId);
        return apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(appId)
                .stream().map(index -> toViewVo(index, usecaseLinks)).collect(Collectors.toList());
    }

    @Override
    public ApiEndpointCoverageVo calculateCoverage(String appId, String traceId) {
        return calculateCoverage(appId, StringUtils.hasText(traceId) ? Collections.singletonList(traceId) : Collections.emptyList());
    }

    @Override
    public ApiEndpointCoverageVo calculateCoverage(String appId, java.util.Collection<String> traceIds) {
        List<ApiEndpointIndex> endpoints = apiEndpointRepository.findByAppIdOrderByEndpointTypeAscUrlAsc(appId);
        if (endpoints == null || endpoints.isEmpty()) {
            return new ApiEndpointCoverageVo(0, 0);
        }
        Map<String, Integer> hitMap = coverageContextService.buildCoverageMap(appId, traceIds);
        int coveredCount = 0;
        for (ApiEndpointIndex endpoint : endpoints) {
            if (ApiEndpointCoverageMatcher.resolveHitCount(hitMap, endpoint.getEndpointType(), endpoint.getHttpMethod(), endpoint.getUrl()) > 0) {
                coveredCount++;
            }
        }
        return new ApiEndpointCoverageVo(coveredCount, endpoints.size());
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
        boolean tempFile = sourceName != null && !Objects.equals(file.getName(), sourceName);
        try {
            apiEndpointPersistenceService.persist(appId, originalName,
                    apiEndpointArtifactScanner.scanArtifact(file, originalName));
        } finally {
            if (tempFile && !file.delete()) {
                logger.debug("Temp file cleanup skipped: {}", file.getAbsolutePath());
            }
        }
    }

    private ApiEndpointViewVo toViewVo(ApiEndpointIndex index, Map<String, List<ApiEndpointViewVo.UsecaseLinkVo>> usecaseLinks) {
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
        vo.setLinkedUsecases(resolveLinkedUsecases(index, usecaseLinks));
        return vo;
    }

    private List<ApiEndpointViewVo.UsecaseLinkVo> resolveLinkedUsecases(ApiEndpointIndex index,
                                                                         Map<String, List<ApiEndpointViewVo.UsecaseLinkVo>> usecaseLinks) {
        if (usecaseLinks == null || usecaseLinks.isEmpty()) {
            return Collections.emptyList();
        }
        List<ApiEndpointViewVo.UsecaseLinkVo> result = new ArrayList<>();
        Set<String> existingIds = new LinkedHashSet<>();
        String method = ApiEndpointCoverageMatcher.normalizeHttpMethod(index.getHttpMethod());
        String endpointTarget = "RPC".equals(index.getEndpointType())
                ? (index.getUrl() == null ? "" : index.getUrl().trim())
                : ApiEndpointCoverageMatcher.simplifyCoverageTarget(index.getUrl());
        for (Map.Entry<String, List<ApiEndpointViewVo.UsecaseLinkVo>> entry : usecaseLinks.entrySet()) {
            ApiEndpointCoverageMatcher.CoverageKey key = ApiEndpointCoverageMatcher.CoverageKey.parse(entry.getKey());
            if (key == null || !Objects.equals(index.getEndpointType(), key.endpointType())) {
                continue;
            }
            if (!ApiEndpointCoverageMatcher.isHttpMethodMatched(method, key.httpMethod())) {
                continue;
            }
            boolean matched = "RPC".equals(index.getEndpointType())
                    ? Objects.equals(endpointTarget, key.target())
                    : ApiEndpointCoverageMatcher.isPathMatched(endpointTarget, key.target());
            if (!matched) {
                continue;
            }
            for (ApiEndpointViewVo.UsecaseLinkVo link : entry.getValue()) {
                if (link != null && StringUtils.hasText(link.getId()) && existingIds.add(link.getId())) {
                    result.add(link);
                }
            }
        }
        return result;
    }

    private String suffix(String lowerName) {
        if (lowerName.endsWith(".zip")) return ".zip";
        if (lowerName.endsWith(".jar")) return ".jar";
        if (lowerName.endsWith(".war")) return ".war";
        return ".tmp";
    }

    private String defaultIfBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private List<String> splitLines(String preferred, String fallback) {
        String value = defaultIfBlank(preferred, fallback);
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split("\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }


}
