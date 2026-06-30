package com.oAT.web.api.map;

import com.oAT.web.common.ClassUtil;
import com.oAT.web.domain.ImageData;
import com.oAT.web.domain.ImageElement;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MapCoverageCodeLayerService {

    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;

    public MapCoverageCodeLayerService(CoverageReportRepository coverageReportRepository,
                                       ClassCoverageRepository classCoverageRepository) {
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
    }

    public List<ImageElement> buildCoverageCodeLayerElements(String appId) {
        CoverageReportIndex latestReport = latestCoverageReport(appId);
        if (latestReport == null || !StringUtils.hasText(latestReport.getId())) {
            return Collections.emptyList();
        }
        List<ClassCoverageIndex> classCoverages = classCoverageRepository.findByReportId(latestReport.getId());
        if (classCoverages.isEmpty()) {
            return Collections.emptyList();
        }
        List<ImageElement> elements = new ArrayList<>();
        String appNodeId = "coverage-app:" + appId;
        ImageData appData = new ImageData(appNodeId);
        appData.name = firstText(latestReport.getLanguage(), latestReport.getSourceType(), "coverage") + " 覆盖率图";
        appData.describe = "版本 " + firstText(latestReport.getVersionNumber(), "-")
                + " / Commit " + firstText(latestReport.getRepoCommitId(), "-");
        appData.weight = 45;
        ImageElement appNode = new ImageElement(appData);
        appNode.group = "nodes";
        appNode.classes = new String[]{"app", "coverage-root", "batch-coverage"};
        elements.add(appNode);

        Map<String, ImageElement> moduleNodes = new LinkedHashMap<>();
        for (ClassCoverageIndex classCoverage : classCoverages) {
            String unitKey = firstText(classCoverage.getSourcePath(), classCoverage.getDisplayName(), classCoverage.getClassName());
            if (!StringUtils.hasText(unitKey)) {
                continue;
            }
            String moduleKey = moduleKey(unitKey);
            boolean newModule = !moduleNodes.containsKey(moduleKey);
            ImageElement moduleNode = moduleNodes.computeIfAbsent(moduleKey, key -> buildCoverageModuleNode(appNodeId, key));
            if (newModule) {
                elements.add(moduleNode);
                elements.add(buildEdge(appNodeId + "->" + moduleNode.data.id, appNodeId, moduleNode.data.id, "module"));
            }
            ImageElement fileNode = buildCoverageFileNode(classCoverage, unitKey);
            elements.add(fileNode);
            elements.add(buildEdge(moduleNode.data.id + "->" + fileNode.data.id, moduleNode.data.id, fileNode.data.id, "file"));
            elements.addAll(buildCoverageMethodElements(classCoverage, unitKey, fileNode.data.id));
        }
        return elements;
    }

    private ImageElement buildCoverageModuleNode(String appNodeId, String moduleKey) {
        ImageData data = new ImageData("coverage-module:" + appNodeId + ":" + moduleKey);
        data.name = moduleKey;
        data.describe = "覆盖率模块 / 目录";
        data.weight = 30;
        ImageElement element = new ImageElement(data);
        element.group = "nodes";
        element.classes = new String[]{"code_module", "coverage-module", "batch-coverage"};
        return element;
    }

    private ImageElement buildCoverageFileNode(ClassCoverageIndex classCoverage, String unitKey) {
        ImageData data = new ImageData("coverage-file:" + classCoverage.getReportId() + ":" + unitKey);
        data.name = firstText(classCoverage.getDisplayName(), simplePathName(unitKey));
        data.describe = unitKey;
        data.packageAndClassName = unitKey;
        data.weight = 20;
        data.lineTotal = new ArrayList<>(Collections.singletonList(classCoverage.getTotalLines()));
        data.doLines = new ArrayList<>(Collections.singletonList(classCoverage.getCoveredLines()));
        data.methodTotal = new ArrayList<>(Collections.singletonList(classCoverage.getTotalMethods()));
        data.executeMethodTotal = new ArrayList<>(Collections.singletonList(classCoverage.getCoveredMethods()));
        data.coverageRate = classCoverage.getLineRate() == null
                ? calculateRate(classCoverage.getCoveredLines(), classCoverage.getTotalLines())
                : classCoverage.getLineRate().floatValue();
        data.cyclo = classCoverage.getTotalComplexity();
        ImageElement element = new ImageElement(data);
        element.group = "nodes";
        element.classes = new String[]{"code_class", "coverage-file", "batch-coverage"};
        return element;
    }

    private List<ImageElement> buildCoverageMethodElements(ClassCoverageIndex classCoverage, String unitKey, String fileNodeId) {
        if (classCoverage.getMethods() == null || classCoverage.getMethods().isEmpty()) {
            return Collections.emptyList();
        }
        List<ImageElement> elements = new ArrayList<>();
        int index = 0;
        for (ClassCoverageIndex.MethodCoverageDetail method : classCoverage.getMethods()) {
            if (method == null || !StringUtils.hasText(method.getMethodName())) {
                index++;
                continue;
            }
            ImageElement methodNode = buildCoverageMethodNode(classCoverage, method, unitKey, index);
            elements.add(methodNode);
            elements.add(buildEdge(fileNodeId + "->" + methodNode.data.id, fileNodeId, methodNode.data.id, "method"));
            index++;
        }
        return elements;
    }

    private ImageElement buildCoverageMethodNode(ClassCoverageIndex classCoverage,
                                                 ClassCoverageIndex.MethodCoverageDetail method,
                                                 String unitKey,
                                                 int index) {
        ImageData data = new ImageData("coverage-method:" + classCoverage.getReportId() + ":" + unitKey + ":" + method.getMethodName() + ":" + index);
        data.name = method.getMethodName();
        data.describe = firstText(method.getMethodDesc(), unitKey);
        data.packageAndClassName = unitKey;
        data.methodName = method.getMethodName();
        data.weight = method.isCovered() ? 15 : 12;
        data.lineTotal = new ArrayList<>(Collections.singletonList(method.getTotalLines()));
        data.doLines = new ArrayList<>(Collections.singletonList(method.getCoveredLines()));
        data.methodTotal = new ArrayList<>(Collections.singletonList(1));
        data.executeMethodTotal = new ArrayList<>(Collections.singletonList(method.isCovered() ? 1 : 0));
        int totalBranches = method.getTotalBranchTargets() > 0 ? method.getTotalBranchTargets() : method.getTotalBranches();
        int coveredBranches = method.getCoveredBranchTargets() > 0 ? method.getCoveredBranchTargets() : method.getCoveredBranches();
        data.branchTotal = new ArrayList<>(Collections.singletonList(totalBranches));
        data.executebranch = new ArrayList<>(Collections.singletonList(coveredBranches));
        data.coverageRate = calculateRate(method.getCoveredLines(), method.getTotalLines());
        data.cyclo = method.getComplexity();
        ImageElement element = new ImageElement(data);
        element.group = "nodes";
        element.classes = new String[]{"code_method", "coverage-method", "batch-coverage"};
        return element;
    }

    private ImageElement buildEdge(String id, String source, String target, String name) {
        ImageData data = new ImageData(id);
        data.source = source;
        data.target = target;
        data.name = name;
        data.weight = 10;
        ImageElement edge = new ImageElement(data);
        edge.group = "edges";
        edge.classes = new String[]{"coverage"};
        return edge;
    }

    private String moduleKey(String unitKey) {
        String normalized = unitKey.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        if (index > 0) {
            String module = normalized.substring(0, index);
            int second = module.lastIndexOf('/');
            return second > 0 ? module.substring(second + 1) : module;
        }
        String dotted = unitKey.replace('/', '.');
        int dot = dotted.lastIndexOf('.');
        return dot > 0 ? dotted.substring(0, dot) : "root";
    }

    private String simplePathName(String unitKey) {
        String normalized = unitKey.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        if (index >= 0 && index < normalized.length() - 1) {
            return normalized.substring(index + 1);
        }
        return ClassUtil.getClassSimpleName(unitKey);
    }

    private CoverageReportIndex latestCoverageReport(String appId) {
        return coverageReportRepository.findByAppId(appId).stream()
                .max(Comparator.comparing(CoverageReportIndex::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private float calculateRate(int covered, int total) {
        if (total <= 0) {
            return 0F;
        }
        return Math.round(covered * 10000F / total) / 100F;
    }
}
