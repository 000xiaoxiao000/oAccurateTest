package com.oAT.web.analytics.tia;

import com.oAT.web.common.Job;
import com.oAT.web.common.compare.CompareResult;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.UsecaseSearchService;
import com.oAT.web.service.entity.CompareJobVo;
import com.oAT.web.service.entity.UsecaseVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class VersionCompareImpactService {
    private final SnapshotSearchService snapshotSearchService;
    private final UsecaseSearchService usecaseSearchService;
    private final CaseCenterRepository caseCenterRepository;

    public VersionCompareImpactService(SnapshotSearchService snapshotSearchService,
                                       UsecaseSearchService usecaseSearchService,
                                       CaseCenterRepository caseCenterRepository) {
        this.snapshotSearchService = snapshotSearchService;
        this.usecaseSearchService = usecaseSearchService;
        this.caseCenterRepository = caseCenterRepository;
    }

    public void findUsecaseImpact(Job<CompareJobVo> job, CompareResult compareResult) {
        Map<String, CompareJobVo.SnapshotUnion> cases = job.getData().getImpactSnapshot();
        List<SystemSnapshot> list = Collections.emptyList();
        String projectId = job.getData().getProjectId();
        String appId = job.getData().getAppId();
        String originalName = compareResult.getClassName();
        if (originalName != null && originalName.startsWith("/")) {
            originalName = originalName.substring(1);
        }
        String classDot = Optional.ofNullable(originalName).orElse("");
        classDot = classDot.replace('/', '.');

        int appSnapshotCount = Optional.ofNullable(job.getData().getAppSnapshotCount()).orElse(0);

        if (compareResult.getModel() == CompareResult.Model.delete || compareResult.getModel() == CompareResult.Model.add) {
            List<String> classCandidates = snapshotSearchService.buildCodeSearchCandidates(classDot);
            List<String> classPatterns = snapshotSearchService.buildCodeSearchPatterns(classDot);
            job.getLogger().info(String.format("查找快照影响 类名：%s 类级候选：%s 模式：%s（当前应用快照数：%s）",
                    classDot, String.join(" | ", classCandidates), String.join(" | ", classPatterns), appSnapshotCount));
            list = snapshotSearchService.searchByCode(projectId, appId, classDot, new String[0]);
            String titles = list.stream().map(SystemSnapshot::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
            job.getLogger().info(String.format("查找快照影响 类名：%s 类级检索影响数：%s，命中快照：%s",
                    classDot, list.size(), StringUtils.hasText(titles) ? titles : "-"));
        } else if (compareResult.getModel() == CompareResult.Model.update) {
            List<String> filteredNames = normalizeMethodNamesForSearch(classDot, compareResult);
            List<String> fallbackMethodNames = buildMethodFallbackCandidates(filteredNames);

            if (filteredNames.isEmpty()) {
                List<String> classCandidates = snapshotSearchService.buildCodeSearchCandidates(classDot);
                List<String> classPatterns = snapshotSearchService.buildCodeSearchPatterns(classDot);
                job.getLogger().info(String.format("查找快照影响 类名：%s (方法未解析或仅占位) 类级候选：%s 模式：%s（当前应用快照数：%s）",
                        classDot, String.join(" | ", classCandidates), String.join(" | ", classPatterns), appSnapshotCount));
                list = snapshotSearchService.searchByCode(projectId, appId, classDot, new String[0]);
                String titles = list.stream().map(SystemSnapshot::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                job.getLogger().info(String.format("查找快照影响 类名：%s (方法未解析或仅占位) 影响数：%s，命中快照：%s",
                        classDot, list.size(), StringUtils.hasText(titles) ? titles : "-"));
            } else {
                List<String> methodCandidates = snapshotSearchService.buildCodeSearchCandidates(classDot, StringUtils.toStringArray(fallbackMethodNames));
                List<String> methodPatterns = snapshotSearchService.buildCodeSearchPatterns(classDot, StringUtils.toStringArray(fallbackMethodNames));
                job.getLogger().info(String.format("查找快照影响 类名：%s 方法候选：%s 检索候选：%s 模式：%s（当前应用快照数：%s）",
                        classDot, String.join(", ", fallbackMethodNames), String.join(" | ", methodCandidates), String.join(" | ", methodPatterns), appSnapshotCount));
                list = snapshotSearchService.searchByCode(projectId, appId, classDot, StringUtils.toStringArray(fallbackMethodNames));
                if (list.isEmpty()) {
                    List<String> classCandidates = snapshotSearchService.buildCodeSearchCandidates(classDot);
                    List<String> classPatterns = snapshotSearchService.buildCodeSearchPatterns(classDot);
                    list = snapshotSearchService.searchByCode(projectId, appId, classDot, new String[0]);
                    String titles = list.stream().map(SystemSnapshot::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                    job.getLogger().info(String.format("查找快照影响 类名：%s 方法：%s 未找到，回退到类级别检索；类级候选：%s 模式：%s 影响数：%s，命中快照：%s",
                            classDot, String.join(", ", fallbackMethodNames), String.join(" | ", classCandidates), String.join(" | ", classPatterns), list.size(), StringUtils.hasText(titles) ? titles : "-"));
                } else {
                    String titles = list.stream().map(SystemSnapshot::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                    job.getLogger().info(String.format("查找快照影响 类名：%s 方法：%s 影响数：%s，命中快照：%s",
                            classDot, String.join(", ", fallbackMethodNames), list.size(), StringUtils.hasText(titles) ? titles : "-"));
                }
            }
        }
        Optional.ofNullable(list).orElse(Collections.emptyList()).stream().filter(a -> !cases.containsKey(a.getId())).forEach(a -> {
            if (!cases.containsKey(a.getId())) {
                cases.put(a.getId(), new CompareJobVo.SnapshotUnion(a));
            }
            cases.get(a.getId()).getClasses().add(compareResult.getClassName());
        });

        collectUsecaseImpact(job, compareResult, classDot, list);
    }

    private void collectUsecaseImpact(Job<CompareJobVo> job, CompareResult compareResult, String classDot, List<SystemSnapshot> matchedSnapshots) {
        Map<String, CompareJobVo.UsecaseUnion> cases = job.getData().getImpactUsecases();
        if (cases == null) {
            return;
        }
        String projectId = job.getData().getProjectId();
        List<UsecaseVo> usecases = Collections.emptyList();

        if (compareResult.getModel() == CompareResult.Model.delete || compareResult.getModel() == CompareResult.Model.add) {
            usecases = usecaseSearchService.getBySrcClass(projectId, "/" + classDot.replace('.', '/'));
            String usecaseIds = usecases.stream().map(UsecaseVo::getId).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
            String titles = usecases.stream().map(UsecaseVo::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
            job.getLogger().info(String.format("查找影响用例 类名：%s 类级检索影响数：%s，命中用例ID：%s，命中用例：%s",
                    classDot, usecases.size(), StringUtils.hasText(usecaseIds) ? usecaseIds : "-", StringUtils.hasText(titles) ? titles : "-"));
        } else if (compareResult.getModel() == CompareResult.Model.update) {
            List<String> filteredNames = normalizeMethodNamesForSearch(classDot, compareResult);
            List<String> fallbackMethodNames = buildMethodFallbackCandidates(filteredNames);
            if (fallbackMethodNames.isEmpty()) {
                usecases = usecaseSearchService.getBySrcClass(projectId, "/" + classDot.replace('.', '/'));
                String usecaseIds = usecases.stream().map(UsecaseVo::getId).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                String titles = usecases.stream().map(UsecaseVo::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                job.getLogger().info(String.format("查找影响用例 类名：%s (方法未解析或仅占位) 影响数：%s，命中用例ID：%s，命中用例：%s",
                        classDot, usecases.size(), StringUtils.hasText(usecaseIds) ? usecaseIds : "-", StringUtils.hasText(titles) ? titles : "-"));
            } else {
                LinkedHashSet<String> srcMethods = new LinkedHashSet<>();
                for (String methodName : fallbackMethodNames) {
                    if (StringUtils.hasText(methodName)) {
                        srcMethods.add(classDot.replace('.', '/') + " " + methodName);
                    }
                }
                if (!srcMethods.isEmpty()) {
                    LinkedHashSet<String> queryMethods = new LinkedHashSet<>(srcMethods);
                    for (String methodName : fallbackMethodNames) {
                        if (StringUtils.hasText(methodName)) {
                            queryMethods.add(classDot + " " + methodName);
                        }
                    }
                    job.getLogger().info(String.format("查找影响用例 类名：%s 方法源码键：%s",
                            classDot, String.join(" | ", queryMethods)));
                    usecases = usecaseSearchService.getBySrcMethod(projectId, queryMethods.toArray(new String[0]));
                }
                if (usecases.isEmpty()) {
                    usecases = usecaseSearchService.getBySrcClass(projectId, "/" + classDot.replace('.', '/'));
                    String usecaseIds = usecases.stream().map(UsecaseVo::getId).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                    String titles = usecases.stream().map(UsecaseVo::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                    job.getLogger().info(String.format("查找影响用例 类名：%s 方法：%s 未找到，回退到类级别检索，影响数：%s，命中用例ID：%s，命中用例：%s",
                            classDot, String.join(", ", fallbackMethodNames), usecases.size(), StringUtils.hasText(usecaseIds) ? usecaseIds : "-", StringUtils.hasText(titles) ? titles : "-"));
                } else {
                    String usecaseIds = usecases.stream().map(UsecaseVo::getId).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                    String titles = usecases.stream().map(UsecaseVo::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                    job.getLogger().info(String.format("查找影响用例 类名：%s 方法：%s 影响数：%s，命中用例ID：%s，命中用例：%s",
                            classDot, String.join(", ", fallbackMethodNames), usecases.size(), StringUtils.hasText(usecaseIds) ? usecaseIds : "-", StringUtils.hasText(titles) ? titles : "-"));
                }
            }
        }

        if (usecases.isEmpty()) {
            List<UsecaseVo> fallbackUsecases = collectUsecasesByMatchedSnapshots(projectId, cases, matchedSnapshots);
            if (!fallbackUsecases.isEmpty()) {
                String snapshotIds = Optional.ofNullable(matchedSnapshots).orElse(Collections.emptyList()).stream()
                        .map(SystemSnapshot::getId)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.joining(", "));
                String usecaseIds = fallbackUsecases.stream().map(UsecaseVo::getId).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                String titles = fallbackUsecases.stream().map(UsecaseVo::getTitle).filter(StringUtils::hasText).distinct().collect(Collectors.joining(", "));
                job.getLogger().info(String.format("查找影响用例 类名：%s 直接源码检索未命中，改为基于当前命中系统快照反推，命中快照ID：%s，影响数：%s，命中用例ID：%s，命中用例：%s",
                        classDot,
                        StringUtils.hasText(snapshotIds) ? snapshotIds : "-",
                        fallbackUsecases.size(),
                        StringUtils.hasText(usecaseIds) ? usecaseIds : "-",
                        StringUtils.hasText(titles) ? titles : "-"));
                usecases = fallbackUsecases;
            }
        }

        Optional.ofNullable(usecases).orElse(Collections.emptyList()).stream().filter(a -> !cases.containsKey(a.getId())).forEach(a -> {
            if (!cases.containsKey(a.getId())) {
                cases.put(a.getId(), new CompareJobVo.UsecaseUnion(a));
            }
            cases.get(a.getId()).getClasses().add(compareResult.getClassName());
        });
    }

    private List<UsecaseVo> collectUsecasesByMatchedSnapshots(String projectId,
                                                              Map<String, CompareJobVo.UsecaseUnion> existingCases,
                                                              List<SystemSnapshot> matchedSnapshots) {
        if (matchedSnapshots == null || matchedSnapshots.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<String, UsecaseVo> collected = new LinkedHashMap<>();
        for (SystemSnapshot matchedSnapshot : matchedSnapshots) {
            if (matchedSnapshot == null || !StringUtils.hasText(matchedSnapshot.getId())) {
                continue;
            }
            String snapshotId = matchedSnapshot.getId();
            List<CaseCenterIndex> indexes = caseCenterRepository.findByUsecase_ProjectIdAndUsecase_SystemSnapshotsContaining(projectId, snapshotId);
            for (CaseCenterIndex index : indexes) {
                if (index == null || index.getUsecase() == null || !StringUtils.hasText(index.getId())) {
                    continue;
                }
                if (existingCases != null && existingCases.containsKey(index.getId())) {
                    continue;
                }
                collected.putIfAbsent(index.getId(), convertUsecaseIndex(index));
            }
        }
        return new ArrayList<>(collected.values());
    }

    private UsecaseVo convertUsecaseIndex(CaseCenterIndex index) {
        UsecaseVo vo = new UsecaseVo();
        BeanUtils.copyProperties(index.getUsecase(), vo);
        vo.setId(index.getId());
        vo.setCreateTime(index.getCreateTime());
        vo.setUpdateTime(index.getUpdateTime());
        return vo;
    }

    private List<String> normalizeMethodNamesForSearch(String classDot, CompareResult compareResult) {
        final String simpleClassName = classDot.contains(".") ? classDot.substring(classDot.lastIndexOf('.') + 1) : classDot;
        final String nestedPrefix = simpleClassName + "$";
        LinkedHashSet<String> filteredNames = Arrays.stream(compareResult.getMethods())
                .map(CompareResult.Method::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(n -> !n.isEmpty() && !n.startsWith("("))
                .map(n -> {
                    if (n.startsWith(simpleClassName + ".")) {
                        return n.substring(simpleClassName.length() + 1);
                    }
                    if (n.startsWith(nestedPrefix)) {
                        int methodSeparator = n.lastIndexOf('.');
                        if (methodSeparator >= 0 && methodSeparator < n.length() - 1) {
                            return n.substring(methodSeparator + 1);
                        }
                    }
                    return n;
                })
                .filter(n -> !n.contains("$"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(filteredNames);
    }

    private List<String> buildMethodFallbackCandidates(List<String> methodNames) {
        if (methodNames == null || methodNames.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String methodName : methodNames) {
            if (!StringUtils.hasText(methodName)) {
                continue;
            }
            String trimmed = methodName.trim();
            candidates.add(trimmed);
            int dotIndex = trimmed.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < trimmed.length() - 1) {
                candidates.add(trimmed.substring(dotIndex + 1));
            }
        }
        return new ArrayList<>(candidates);
    }
}
