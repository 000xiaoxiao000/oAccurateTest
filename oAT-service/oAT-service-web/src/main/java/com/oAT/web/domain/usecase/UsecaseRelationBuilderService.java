package com.oAT.web.domain.usecase;

import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import com.oAT.web.esDao.entity.Usecase;
import com.oAT.web.exceptions.DirtyDataException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UsecaseRelationBuilderService {
    private static final Logger logger = LoggerFactory.getLogger(UsecaseRelationBuilderService.class);

    private final CaseCenterRepository centerRepository;
    private final TraceNodeRepository traceNodeRepository;
    private final SystemSnapshotRepository systemSnapshotRepository;
    private final com.oAT.web.coverage.CoverageStorage coverageStorage;

    public UsecaseRelationBuilderService(CaseCenterRepository centerRepository,
                                         TraceNodeRepository traceNodeRepository,
                                         SystemSnapshotRepository systemSnapshotRepository,
                                         com.oAT.web.coverage.CoverageStorage coverageStorage) {
        this.centerRepository = centerRepository;
        this.traceNodeRepository = traceNodeRepository;
        this.systemSnapshotRepository = systemSnapshotRepository;
        this.coverageStorage = coverageStorage;
    }

    public Usecase buildUsecaseRelations(String[] snapshotIds, String[] systemSnapshotIds) {
        String[] validSnapshotIds = filterValidSnapshotIds(snapshotIds);
        String[] validSystemSnapshotIds = filterValidSystemSnapshotIds(systemSnapshotIds);
        boolean noSnapshots = ObjectUtils.isEmpty(validSnapshotIds);
        boolean noSystemSnapshots = ObjectUtils.isEmpty(validSystemSnapshotIds);
        if (noSnapshots && noSystemSnapshots) {
            logger.info("buildUsecaseRelations skipped: no snapshot relations");
            return new Usecase();
        }

        List<StackNodeVo> codeNodes = new ArrayList<>();

        collectMySnapshotTraceNodes(validSnapshotIds, codeNodes);
        collectSystemSnapshotTraceNodes(validSystemSnapshotIds, codeNodes);

        Usecase usecase = new Usecase();
        String[] srcStack = parseCoeStack(codeNodes);
        usecase.setSrcStack(srcStack);
        logger.debug("buildUsecaseRelations finished: snapshotCount={}, systemSnapshotCount={}, codeNodeCount={}, " +
                        "srcStackCount={}, srcStackPreview={}",
                validSnapshotIds.length,
                validSystemSnapshotIds.length,
                codeNodes.size(),
                srcStack == null ? 0 : srcStack.length,
                previewSrcStack(srcStack));
        return usecase;
    }

    private String[] filterValidSnapshotIds(String[] snapshotIds) {
        if (ObjectUtils.isEmpty(snapshotIds)) {
            return new String[0];
        }
        List<String> validIds = new ArrayList<>();
        for (CaseCenterIndex snapshot : centerRepository.findAllById(Arrays.asList(snapshotIds))) {
            if (snapshot != null && snapshot.getSnapshot() != null) {
                validIds.add(snapshot.getId());
            }
        }
        return validIds.toArray(new String[0]);
    }

    private String[] filterValidSystemSnapshotIds(String[] systemSnapshotIds) {
        if (ObjectUtils.isEmpty(systemSnapshotIds)) {
            return new String[0];
        }
        List<String> validIds = new ArrayList<>();
        for (SystemSnapshot snapshot : systemSnapshotRepository.findAllById(Arrays.asList(systemSnapshotIds))) {
            if (snapshot != null && StringUtils.hasText(snapshot.getId())) {
                validIds.add(snapshot.getId());
            }
        }
        return validIds.toArray(new String[0]);
    }

    private void collectMySnapshotTraceNodes(String[] snapshotIds, List<StackNodeVo> codeNodes) {
        if (ObjectUtils.isEmpty(snapshotIds)) {
            return;
        }
        Iterable<CaseCenterIndex> snapshots = centerRepository.findAllById(Arrays.asList(snapshotIds));
        for (CaseCenterIndex snapshot : snapshots) {
            if (snapshot == null || snapshot.getSnapshot() == null) {
                continue;
            }
            collectTraceNodes(snapshot.getSnapshot().getTraceId(), codeNodes);
        }
    }

    private void collectSystemSnapshotTraceNodes(String[] systemSnapshotIds, List<StackNodeVo> codeNodes) {
        if (ObjectUtils.isEmpty(systemSnapshotIds)) {
            return;
        }
        Iterable<SystemSnapshot> snapshots = systemSnapshotRepository.findAllById(Arrays.asList(systemSnapshotIds));
        for (SystemSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            String[] codes = snapshot.getCodes();
            if (!ObjectUtils.isEmpty(codes)) {
                logger.debug("collectSystemSnapshotTraceNodes use codes: snapshotId={}, traceId={}, codesCount={}, " +
                                "codesPreview={}",
                        snapshot.getId(), snapshot.getTraceId(), codes.length, previewSrcStack(codes));
                collectSystemSnapshotCodes(codes, codeNodes);
                continue;
            }
            logger.warn("collectSystemSnapshotTraceNodes fallback to trace nodes: snapshotId={}, traceId={}, codes empty",
                    snapshot.getId(), snapshot.getTraceId());
            collectTraceNodes(snapshot.getTraceId(), codeNodes);
        }
    }

    private void collectSystemSnapshotCodes(String[] codes, List<StackNodeVo> codeNodes) {
        if (ObjectUtils.isEmpty(codes)) {
            return;
        }
        for (String code : codes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            codeNodes.add(buildCodeNodeFromSnapshotCode(code));
        }
    }

    private void collectTraceNodes(String traceId, List<StackNodeVo> codeNodes) {
        List<StackNodeVo> stored = coverageStorage.load(traceId);
        if (!stored.isEmpty()) {
            codeNodes.addAll(stored);
            return;
        }

        List<TraceNodeIndex> nodeIndexs = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 200));
        if (CollectionUtils.isEmpty(nodeIndexs)) {
            throw new DirtyDataException("找不到Trace Node traceId=" + traceId);
        }
        for (TraceNodeIndex nodeIndex : nodeIndexs) {
            TraceNode node = nodeIndex.toTraceNode();
            if (node instanceof CodeNodeBean && ((CodeNodeBean) node).getCodeNodes() != null) {
                codeNodes.addAll(Arrays.asList(((CodeNodeBean) node).getCodeNodes()));
            }
        }
    }

    private String buildUsecaseSrc(@NotNull StackNodeVo nodeVo) {
        StringBuilder result = new StringBuilder(nodeVo.getClassName().replace('/', '.'));
        String methodName = nodeVo.getMethodName();
        if (StringUtils.hasText(methodName)) {
            result.append(" ").append(methodName.trim());
        }
        return result.toString();
    }

    private StackNodeVo buildCodeNodeFromSnapshotCode(String code) {
        StackNodeVo nodeVo = new StackNodeVo();
        String normalizedCode = code.trim();
        int splitIndex = normalizedCode.indexOf(' ');
        if (splitIndex < 0) {
            nodeVo.setClassName(normalizedCode.replace('.', '/'));
            return nodeVo;
        }
        String className = normalizedCode.substring(0, splitIndex).trim();
        String methodName = normalizedCode.substring(splitIndex + 1).trim();
        nodeVo.setClassName(className.replace('.', '/'));
        nodeVo.setMethodName(methodName);
        return nodeVo;
    }

    private String[] parseCoeStack(List<StackNodeVo> nodeVos) {
        Set<String> result = new LinkedHashSet<>(nodeVos.size());
        for (StackNodeVo nodeVo : nodeVos) {
            if (nodeVo == null) {
                continue;
            }
            result.add(buildUsecaseSrc(nodeVo));
        }
        return result.toArray(new String[0]);
    }

    private String previewSrcStack(String[] values) {
        if (ObjectUtils.isEmpty(values)) {
            return "[]";
        }
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .limit(5)
                .collect(Collectors.joining(" | ", "[", values.length > 5 ? " | ...]" : "]"));
    }
}
