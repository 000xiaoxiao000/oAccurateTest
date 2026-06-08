package com.oAT.web.service.impl;

import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.Remote;
import com.oAT.web.esDao.entity.Sql;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.SnapshotSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SnapshotSearchServiceImpl implements SnapshotSearchService {

    @Autowired
    private SystemSnapshotRepository systemSnapshotRepository;

    @Override
    public SearchPage<SnapshotSearchResult> doSearch(String projectId, String keyWords) {
        String keyword = normalize(keyWords);
        List<SnapshotSearchResult> results = systemSnapshotRepository.findByProjectId(projectId).stream()
                .filter(this::isEnabled)
                .filter(snapshot -> !StringUtils.hasText(keyword) || matchesSnapshot(snapshot, keyword))
                .map(this::toSearchResult)
                .collect(Collectors.toList());
        SearchPage<SnapshotSearchResult> searchPage = new SearchPage<>();
        searchPage.setContents(results);
        searchPage.setTotal(results.size());
        return searchPage;
    }

    private SnapshotSearchResult toSearchResult(SystemSnapshot snapshot) {
        SnapshotSearchResult result = new SnapshotSearchResult();
        result.setId(snapshot.getId());
        result.setProjectId(snapshot.getProjectId());
        result.setAppId(snapshot.getAppId());
        result.setTitle(snapshot.getTitle());
        result.setSubTitle(snapshot.getSubTitle());
        result.setDirectoryId(snapshot.getDirectory());
        result.setHeadImage(snapshot.getTopicImage());
        result.setUpdateTime(snapshot.getUpdateTime());
        return result;
    }

    @Override
    public List<SystemSnapshot> searchByTable(String projectId, String databaseName, String tableName) {
        Assert.hasText(projectId, "projectId 不能为空");
        Assert.hasText(databaseName, "databaseName 不能为空");
        Assert.hasText(tableName, "tableName 不能为空");
        String normalizedDatabase = normalize(databaseName);
        String normalizedTable = normalize(tableName);
        return systemSnapshotRepository.findByProjectId(projectId).stream()
                .filter(this::isEnabled)
                .filter(snapshot -> hasSqlTable(snapshot, normalizedDatabase, normalizedTable))
                .collect(Collectors.toList());
    }

    @Override
    public List<SystemSnapshot> searchByCode(String projectId, String className, String... methodName) {
        return searchByCode(projectId, null, className, methodName);
    }

    @Override
    public List<SystemSnapshot> searchByCode(String projectId, String appId, String className, String... methodName) {
        Assert.hasText(projectId, "projectId 不能为空");
        Assert.hasText(className, "className 不能为空");
        List<String> candidates = buildCodeQueryCandidates(className, normalizeMethods(methodName));
        List<String> patterns = buildCodeQueryPatterns(className, normalizeMethods(methodName));
        String classKeyword = normalize(buildClassPrefix(className));
        return systemSnapshotRepository.findByProjectId(projectId).stream()
                .filter(this::isEnabled)
                .filter(snapshot -> !StringUtils.hasText(appId) || appId.equals(snapshot.getAppId()))
                .filter(snapshot -> matchesCode(snapshot, classKeyword, candidates, patterns))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> buildCodeSearchCandidates(String className, String... methodName) {
        return buildCodeQueryCandidates(className, normalizeMethods(methodName));
    }

    @Override
    public List<String> buildCodeSearchPatterns(String className, String... methodName) {
        return buildCodeQueryPatterns(className, normalizeMethods(methodName));
    }

    @Override
    public List<SystemSnapshot> searchByDubbo(String projectId, String interfaceName, String... methodName) {
        Assert.hasText(projectId, "projectId 不能为空");
        Assert.hasText(interfaceName, "interfaceName 不能为空");
        List<String> methods = methodName == null ? Collections.emptyList() : Arrays.stream(methodName)
                .filter(StringUtils::hasText)
                .map(method -> normalize(interfaceName + "#" + method))
                .collect(Collectors.toList());
        String interfacePrefix = normalize(interfaceName);
        return systemSnapshotRepository.findByProjectId(projectId).stream()
                .filter(this::isEnabled)
                .filter(snapshot -> hasRemote(snapshot, interfacePrefix, methods))
                .collect(Collectors.toList());
    }

    private boolean matchesSnapshot(SystemSnapshot snapshot, String keyword) {
        return contains(snapshot.getTitle(), keyword)
                || contains(snapshot.getSubTitle(), keyword)
                || contains(snapshot.getDescribe(), keyword)
                || containsAny(snapshot.getCodes(), keyword)
                || hasSqlContent(snapshot, keyword)
                || hasRemoteKeyword(snapshot, keyword);
    }

    private boolean hasSqlTable(SystemSnapshot snapshot, String databaseName, String tableName) {
        if (snapshot.getSqls() == null) {
            return false;
        }
        for (Sql sql : snapshot.getSqls()) {
            if (sql == null || !Objects.equals(normalize(sql.getDatabase()), databaseName) || sql.getActions() == null) {
                continue;
            }
            for (Sql.Action action : sql.getActions()) {
                if (action != null && Objects.equals(normalize(action.getTable()), tableName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesCode(SystemSnapshot snapshot, String classKeyword, List<String> candidates, List<String> patterns) {
        if (snapshot.getCodes() == null) {
            return false;
        }
        for (String code : snapshot.getCodes()) {
            String normalizedCode = normalize(code);
            if (StringUtils.hasText(classKeyword) && normalizedCode.contains(classKeyword)) {
                if (candidates.isEmpty() && patterns.isEmpty()) {
                    return true;
                }
                if (containsCandidate(normalizedCode, candidates) || containsPattern(normalizedCode, patterns)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasRemote(SystemSnapshot snapshot, String interfacePrefix, List<String> methods) {
        if (snapshot.getRemotes() == null) {
            return false;
        }
        for (Remote remote : snapshot.getRemotes()) {
            if (remote == null || !StringUtils.hasText(remote.getInvokerInterface())) {
                continue;
            }
            String invoker = normalize(remote.getInvokerInterface());
            if (methods.isEmpty()) {
                if (invoker.startsWith(interfacePrefix)) {
                    return true;
                }
            } else if (methods.contains(invoker)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSqlContent(SystemSnapshot snapshot, String keyword) {
        if (snapshot.getSqls() == null) {
            return false;
        }
        for (Sql sql : snapshot.getSqls()) {
            if (sql != null && contains(sql.getContent(), keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRemoteKeyword(SystemSnapshot snapshot, String keyword) {
        if (snapshot.getRemotes() == null) {
            return false;
        }
        for (Remote remote : snapshot.getRemotes()) {
            if (remote != null && (contains(remote.getInvokerInterface(), keyword) || contains(remote.getUrl(), keyword))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCandidate(String normalizedCode, List<String> candidates) {
        for (String candidate : candidates) {
            if (normalizedCode.contains(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPattern(String normalizedCode, List<String> patterns) {
        for (String pattern : patterns) {
            String normalizedPattern = normalize(pattern).replace("*", "");
            if (StringUtils.hasText(normalizedPattern) && normalizedCode.contains(normalizedPattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String[] values, String keyword) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (contains(value, keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && StringUtils.hasText(keyword) && normalize(value).contains(keyword);
    }

    private boolean isEnabled(SystemSnapshot snapshot) {
        return snapshot != null;
    }

    private List<String> normalizeMethods(String... methodName) {
        if (methodName == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(methodName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private List<String> buildCodeQueryPatterns(String className, List<String> methodNames) {
        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        if (methodNames == null || methodNames.isEmpty()) {
            String classWildcard = buildClassWildcardPattern(className);
            if (StringUtils.hasText(classWildcard)) {
                patterns.add(classWildcard);
            }
            return new ArrayList<>(patterns);
        }
        for (String method : methodNames) {
            String wildcardPattern = buildMethodWildcardPattern(className, method);
            if (StringUtils.hasText(wildcardPattern)) {
                patterns.add(wildcardPattern);
            }
        }
        return new ArrayList<>(patterns);
    }

    private List<String> buildCodeQueryCandidates(String className, List<String> methodNames) {
        if (methodNames == null || methodNames.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String method : methodNames) {
            if (!StringUtils.hasText(method)) {
                continue;
            }
            String trimmedMethod = method.trim();
            candidates.add(className + " " + trimmedMethod);

            String simpleMethod = extractSimpleMethodName(trimmedMethod);
            if (StringUtils.hasText(simpleMethod)) {
                candidates.add(className + " " + simpleMethod);
            }

            String bareMethod = stripMethodDescriptor(simpleMethod);
            if (StringUtils.hasText(bareMethod)) {
                candidates.add(className + " " + bareMethod);
            }
        }
        return new ArrayList<>(candidates);
    }

    private String stripMethodDescriptor(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            return methodName;
        }
        String normalized = methodName.trim();
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex > 0) {
            normalized = normalized.substring(0, spaceIndex);
        }
        int bracketIndex = normalized.indexOf('(');
        if (bracketIndex > 0) {
            normalized = normalized.substring(0, bracketIndex);
        }
        return normalized;
    }

    private String buildMethodWildcardPattern(String className, String methodName) {
        if (!StringUtils.hasText(className) || !StringUtils.hasText(methodName)) {
            return null;
        }
        String bareMethod = stripMethodDescriptor(extractSimpleMethodName(methodName));
        if (!StringUtils.hasText(bareMethod)) {
            return null;
        }
        return className + " *" + bareMethod + "*";
    }

    private String buildClassPrefix(String className) {
        if (!StringUtils.hasText(className)) {
            return className;
        }
        String normalized = className.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String buildClassWildcardPattern(String className) {
        String prefix = buildClassPrefix(className);
        if (!StringUtils.hasText(prefix)) {
            return prefix;
        }
        return prefix + " *";
    }

    private String extractSimpleMethodName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            return methodName;
        }
        int dotIndex = methodName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < methodName.length() - 1) {
            return methodName.substring(dotIndex + 1);
        }
        return methodName;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
