package com.oAT.web.language.java;

import com.oAT.web.coveragecore.diff.CoverageDiffService;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.ClassCoverageIndex.MethodCoverageDetail;
import com.oAT.web.esDao.entity.StaticSourceClassInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.calculateBranchRate;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.copyBranchTargetProbeMap;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.countBranchTargets;
import static com.oAT.web.coveragecore.diff.BranchTargetProbeMaps.filterBranchTargetProbeMap;

@Service
public class JavaStaticCoverageStructureService {

    private final CoverageDiffService coverageDiffService;

    public JavaStaticCoverageStructureService(CoverageDiffService coverageDiffService) {
        this.coverageDiffService = coverageDiffService;
    }

    public ClassCoverageIndex createInitialClassCoverage(String appId, String className) {
        ClassCoverageIndex classCov = new ClassCoverageIndex();
        classCov.setAppId(appId);
        classCov.setClassName(className);
        classCov.setMethods(new ArrayList<>());
        return classCov;
    }

    public void appendStaticClassCoverage(ClassCoverageIndex classCov,
                                          StaticSourceClassInfo classInfo,
                                          Map<String, List<Integer>> incrementalDiffMap) {
        List<Integer> changedLinesInClass = coverageDiffService.getChangedLinesForClass(incrementalDiffMap, classCov.getClassName());
        if (classInfo.getMethodMaps() == null) {
            classCov.setTotalMethods(classCov.getMethods().size());
            return;
        }
        for (StaticSourceMethodInfo mInfo : classInfo.getMethodMaps().values()) {
            MethodCoverageDetail md = new MethodCoverageDetail();
            md.setMethodName(mInfo.getMethodName());
            md.setMethodDesc(mInfo.getMethodDesc());

            List<Integer> methodLines = mInfo.getMethodLineNumberMap();
            Map<String, List<Integer>> totalBranchTargetProbeMap = changedLinesInClass != null
                    ? filterBranchTargetProbeMap(mInfo.getBranchLineAndTargetProbeMap(), changedLinesInClass)
                    : copyBranchTargetProbeMap(mInfo.getBranchLineAndTargetProbeMap());
            if (changedLinesInClass != null) {
                List<Integer> filteredLines = new ArrayList<>();
                if (methodLines != null) {
                    for (Integer ln : methodLines) {
                        if (changedLinesInClass.contains(ln)) {
                            filteredLines.add(ln);
                        }
                    }
                }
                if (filteredLines.isEmpty()) {
                    continue;
                }
                md.setTotalLineNumbers(filteredLines);

                List<Integer> branchLines = mInfo.getBranchLineNumberSet();
                if (branchLines != null && !branchLines.isEmpty()) {
                    int filteredBranches = 0;
                    for (Integer bl : branchLines) {
                        if (changedLinesInClass.contains(bl)) {
                            filteredBranches++;
                        }
                    }
                    md.setTotalBranches(filteredBranches);
                } else {
                    md.setTotalBranches(0);
                }
            } else {
                md.setTotalLineNumbers(methodLines);
                md.setTotalBranches(mInfo.getTotalBranchCount() != null ? mInfo.getTotalBranchCount() : 0);
            }

            md.setTotalLines(md.getTotalLineNumbers() != null ? md.getTotalLineNumbers().size() : 0);
            md.setComplexity(mInfo.getCyclomaticComplexityMap() != null ? mInfo.getCyclomaticComplexityMap() : 0);
            md.setCoveredLineNumbers(new ArrayList<>());
            md.setCoveredBranchLines(new ArrayList<>());
            md.setTotalBranchTargetProbeMap(totalBranchTargetProbeMap);
            md.setCoveredBranchTargetProbeMap(new LinkedHashMap<>());
            md.setTotalBranchTargets(countBranchTargets(totalBranchTargetProbeMap));
            md.setCoveredBranchTargets(0);
            md.setBranchRate(calculateBranchRate(0, md.getTotalBranchTargets()));
            classCov.getMethods().add(md);
            classCov.setTotalLines(classCov.getTotalLines() + md.getTotalLines());
            classCov.setTotalBranches(classCov.getTotalBranches() + md.getTotalBranches());
            classCov.setTotalBranchTargets(classCov.getTotalBranchTargets() + md.getTotalBranchTargets());
            classCov.setTotalComplexity(classCov.getTotalComplexity() + md.getComplexity());
        }
        classCov.setTotalMethods(classCov.getMethods().size());
    }
}
