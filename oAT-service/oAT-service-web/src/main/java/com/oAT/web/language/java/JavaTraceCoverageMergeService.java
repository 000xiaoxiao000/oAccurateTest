package com.oAT.web.language.java;

import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.coverage.CoverageStorage;
import com.oAT.web.coveragecore.diff.CoverageDiffService;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.calculateBranchRate;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.copyBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.countBranchTargets;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.mergeBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.normalizeCoveredBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.normalizeMethodBranchTargetProbeMap;

@Service
public class JavaTraceCoverageMergeService {
    private static final Logger logger = LoggerFactory.getLogger(JavaTraceCoverageMergeService.class);

    private final CoverageStorage coverageStorage;
    private final TraceNodeRepository traceNodeRepository;
    private final JavaCoverageClassMatcher javaCoverageClassMatcher;
    private final CoverageDiffService coverageDiffService;

    public JavaTraceCoverageMergeService(CoverageStorage coverageStorage,
                                         TraceNodeRepository traceNodeRepository,
                                         JavaCoverageClassMatcher javaCoverageClassMatcher,
                                         CoverageDiffService coverageDiffService) {
        this.coverageStorage = coverageStorage;
        this.traceNodeRepository = traceNodeRepository;
        this.javaCoverageClassMatcher = javaCoverageClassMatcher;
        this.coverageDiffService = coverageDiffService;
    }

    public void mergeSnapshotTraceCoverage(String traceId, Map<String, ClassCoverageIndex> coverageMap) {
        if (!StringUtils.hasText(traceId)) {
            logger.debug("跳过覆盖率合并：traceId 为空");
            return;
        }

        List<StackNodeVo> codeNodes = coverageStorage.load(traceId);

        if (codeNodes.isEmpty()) {
            List<TraceNodeIndex> traceNodes = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 200));
            for (TraceNodeIndex traceNodeIndex : traceNodes) {
                TraceNode node = traceNodeIndex.toTraceNode();
                if (node instanceof CodeNodeBean) {
                    StackNodeVo[] legacyCodeNodes = ((CodeNodeBean) node).getCodeNodes();
                    if (legacyCodeNodes != null && legacyCodeNodes.length > 0) {
                        codeNodes = Arrays.asList(legacyCodeNodes);
                        break;
                    }
                }
            }
        }

        if (codeNodes.isEmpty()) {
            logger.warn("跳过覆盖率合并：traceId={} 的 codeNodes 为空，可能是 Java Agent 未正确采集覆盖率数据", traceId);
            return;
        }

        logger.info("开始合并覆盖率数据：traceId={}, codeNodes 数量={}, coverageMap 大小={}",
                traceId, codeNodes.size(), coverageMap.size());

        int matchedCount = 0;
        int unmatchedCount = 0;
        for (StackNodeVo sn : codeNodes) {
            String originalClassName = sn.getClassName();
            String resolvedClassName = javaCoverageClassMatcher.resolveCoverageOwnerClassName(originalClassName);
            ClassCoverageIndex classCov = javaCoverageClassMatcher.findClassCoverage(coverageMap, originalClassName);

            if (classCov != null) {
                mergeStackNode(classCov, sn);
                matchedCount++;
            } else {
                unmatchedCount++;
                if (unmatchedCount <= 5) {
                    logger.warn("未匹配到类覆盖率数据：原始类名={}, 解析后={}, 可用类名示例={}",
                            originalClassName, resolvedClassName,
                            coverageMap.keySet().stream().limit(5).collect(Collectors.joining(", ")));
                }
            }
        }

        logger.info("覆盖率合并完成：traceId={}, 匹配成功={}, 未匹配={}", traceId, matchedCount, unmatchedCount);

        if (matchedCount == 0 && unmatchedCount > 0) {
            logger.error("严重警告：所有 codeNodes 都未能匹配到静态源码数据！请检查类名格式是否一致。");
            logger.error("codeNodes 中的类名示例：{}",
                    codeNodes.stream().limit(3).map(StackNodeVo::getClassName).collect(Collectors.joining(", ")));
            logger.error("coverageMap 中的类名示例：{}",
                    coverageMap.keySet().stream().limit(10).collect(Collectors.joining(", ")));
        }
    }

    public void reuseMatchedCoverageData(ClassCoverageIndex currentCc,
                                         ClassCoverageIndex lastCc,
                                         Map<String, List<Integer>> diffMap) {
        List<Integer> changedLines = coverageDiffService.getChangedLinesForClass(diffMap, currentCc.getClassName());
        Set<String> processedMethods = new HashSet<>();

        for (MethodCoverageDetail currentMd : currentCc.getMethods()) {
            String methodKey = currentMd.getMethodName() + "#" + currentMd.getMethodDesc();
            processedMethods.add(methodKey);

            Optional<MethodCoverageDetail> lastMdOpt = lastCc.getMethods().stream()
                    .filter(m -> methodKey.equals(m.getMethodName() + "#" + m.getMethodDesc()))
                    .findFirst();

            if (lastMdOpt.isPresent()) {
                MethodCoverageDetail lastMd = lastMdOpt.get();

                if (changedLines == null || changedLines.isEmpty()) {
                    currentMd.setCoveredLineNumbers(new ArrayList<>(lastMd.getCoveredLineNumbers() != null ? lastMd.getCoveredLineNumbers() : Collections.emptyList()));
                    currentMd.setCoveredLines(lastMd.getCoveredLines());
                    currentMd.setCoveredBranchLines(new ArrayList<>(lastMd.getCoveredBranchLines() != null ? lastMd.getCoveredBranchLines() : Collections.emptyList()));
                    currentMd.setCoveredBranches(lastMd.getCoveredBranches());
                    currentMd.setCoveredBranchTargetProbeMap(copyBranchTargetProbeMap(lastMd.getCoveredBranchTargetProbeMap()));
                    currentMd.setCoveredBranchTargets(lastMd.getCoveredBranchTargets());
                    currentMd.setBranchRate(lastMd.getBranchRate());
                    currentMd.setCovered(lastMd.isCovered());
                } else {
                    boolean methodChanged = false;
                    if (currentMd.getTotalLineNumbers() != null) {
                        for (Integer ln : currentMd.getTotalLineNumbers()) {
                            if (changedLines.contains(ln)) {
                                methodChanged = true;
                                break;
                            }
                        }
                    }

                    if (!methodChanged) {
                        currentMd.setCoveredLineNumbers(new ArrayList<>(lastMd.getCoveredLineNumbers() != null ? lastMd.getCoveredLineNumbers() : Collections.emptyList()));
                        currentMd.setCoveredLines(lastMd.getCoveredLines());
                        currentMd.setCoveredBranchLines(new ArrayList<>(lastMd.getCoveredBranchLines() != null ? lastMd.getCoveredBranchLines() : Collections.emptyList()));
                        currentMd.setCoveredBranches(lastMd.getCoveredBranches());
                        currentMd.setCoveredBranchTargetProbeMap(copyBranchTargetProbeMap(lastMd.getCoveredBranchTargetProbeMap()));
                        currentMd.setCoveredBranchTargets(lastMd.getCoveredBranchTargets());
                        currentMd.setBranchRate(lastMd.getBranchRate());
                        currentMd.setCovered(lastMd.isCovered());
                    }
                }
            }
        }
        recalculateClassStats(currentCc);
    }

    private void recalculateClassStats(ClassCoverageIndex cc) {
        int coveredMethods = (int) cc.getMethods().stream().filter(MethodCoverageDetail::isCovered).count();
        int coveredLines = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredLines).sum();
        int coveredBranches = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranches).sum();
        int coveredBranchTargets = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranchTargets).sum();
        int totalBranchTargets = cc.getMethods().stream().mapToInt(MethodCoverageDetail::getTotalBranchTargets).sum();

        cc.setCoveredMethods(coveredMethods);
        cc.setCoveredLines(coveredLines);
        cc.setCoveredBranches(coveredBranches);
        cc.setCoveredBranchTargets(coveredBranchTargets);
        cc.setTotalBranchTargets(totalBranchTargets);
        cc.setBranchRate(calculateBranchRate(coveredBranchTargets, totalBranchTargets));
    }

    private void mergeStackNode(ClassCoverageIndex classCov, StackNodeVo sn) {
        Optional<MethodCoverageDetail> methodOpt = findBestMethodCoverage(classCov, sn);

        if (methodOpt.isPresent()) {
            MethodCoverageDetail md = methodOpt.get();

            Set<Integer> staticLineNumbers = new HashSet<>(md.getTotalLineNumbers() != null ? md.getTotalLineNumbers() : Collections.emptyList());
            Set<Integer> coveredLines = new HashSet<>(md.getCoveredLineNumbers() != null ? md.getCoveredLineNumbers() : Collections.emptyList());
            if (sn.getDoLines() != null) {
                for (Integer line : sn.getDoLines()) {
                    if (staticLineNumbers.contains(line)) {
                        coveredLines.add(line);
                    }
                }
            }
            md.setCoveredLineNumbers(new ArrayList<>(coveredLines));
            md.setCoveredLines(md.getCoveredLineNumbers().size());

            Set<Integer> coveredBranchLines = new HashSet<>(md.getCoveredBranchLines() != null ? md.getCoveredBranchLines() : Collections.emptyList());
            if (sn.getExecuteBranch() != null) {
                coveredBranchLines.addAll(sn.getExecuteBranch());
            }
            if (coveredBranchLines.size() > md.getTotalBranches()) {
                md.setCoveredBranches(md.getTotalBranches());
                List<Integer> list = new ArrayList<>(coveredBranchLines);
                md.setCoveredBranchLines(list.subList(0, md.getTotalBranches()));
            } else {
                md.setCoveredBranchLines(new ArrayList<>(coveredBranchLines));
                md.setCoveredBranches(md.getCoveredBranchLines().size());
            }
            Map<String, List<Integer>> coveredBranchTargetProbeMap = mergeBranchTargetProbeMap(
                    md.getCoveredBranchTargetProbeMap(), sn.getExecuteBranchTargetProbeMap());
            Map<String, List<Integer>> normalizedTotalBranchTargetProbeMap = normalizeMethodBranchTargetProbeMap(
                    md.getTotalBranchTargetProbeMap(), coveredBranchTargetProbeMap);
            coveredBranchTargetProbeMap = normalizeCoveredBranchTargetProbeMap(
                    normalizedTotalBranchTargetProbeMap, coveredBranchTargetProbeMap);
            md.setTotalBranchTargetProbeMap(normalizedTotalBranchTargetProbeMap);
            md.setTotalBranchTargets(countBranchTargets(normalizedTotalBranchTargetProbeMap));
            md.setCoveredBranchTargetProbeMap(coveredBranchTargetProbeMap);
            md.setCoveredBranchTargets(countBranchTargets(coveredBranchTargetProbeMap));
            md.setBranchRate(calculateBranchRate(md.getCoveredBranchTargets(), md.getTotalBranchTargets()));

            md.setCovered(md.getCoveredLines() > 0);
        }

        int classCoveredMethods = (int) classCov.getMethods().stream().filter(MethodCoverageDetail::isCovered).count();
        int classCoveredLines = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredLines).sum();
        int classCoveredBranches = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranches).sum();
        int classCoveredBranchTargets = classCov.getMethods().stream().mapToInt(MethodCoverageDetail::getCoveredBranchTargets).sum();

        classCov.setCoveredMethods(classCoveredMethods);
        classCov.setCoveredLines(classCoveredLines);
        classCov.setCoveredBranches(classCoveredBranches);
        classCov.setCoveredBranchTargets(classCoveredBranchTargets);
        classCov.setBranchRate(calculateBranchRate(classCoveredBranchTargets, classCov.getTotalBranchTargets()));
    }

    private Optional<MethodCoverageDetail> findBestMethodCoverage(ClassCoverageIndex classCov, StackNodeVo sn) {
        List<MethodCoverageDetail> candidates = classCov.getMethods().stream()
                .filter(m -> m.getMethodName().equals(sn.getMethodName()) && m.getMethodDesc().equals(sn.getMethodDescriptor()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.get(0));
        }

        Set<Integer> executedLines = new HashSet<>(sn.getDoLines() != null ? sn.getDoLines() : Collections.emptyList());
        Set<Integer> executedBranches = new HashSet<>(sn.getExecuteBranch() != null ? sn.getExecuteBranch() : Collections.emptyList());

        MethodCoverageDetail best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MethodCoverageDetail candidate : candidates) {
            int score = 0;
            if (candidate.getTotalLineNumbers() != null) {
                for (Integer line : candidate.getTotalLineNumbers()) {
                    if (executedLines.contains(line)) {
                        score += 2;
                    }
                    if (executedBranches.contains(line)) {
                        score += 1;
                    }
                }
            }
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }
}
