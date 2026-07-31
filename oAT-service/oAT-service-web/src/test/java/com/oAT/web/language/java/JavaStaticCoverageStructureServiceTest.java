package com.oAT.web.language.java;

import com.oAT.web.coveragecore.diff.CoverageDiffService;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.StaticSourceClassInfo;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JavaStaticCoverageStructureServiceTest {

    @Test
    void appendStaticClassCoverageDoesNotCountClosingBraceAsExecutableLine() {
        CoverageDiffService coverageDiffService = mock(CoverageDiffService.class);
        when(coverageDiffService.getChangedLinesForClass(null, "web3Server.service.impl.Web302Service")).thenReturn(null);
        JavaStaticCoverageStructureService service = new JavaStaticCoverageStructureService(coverageDiffService);
        ClassCoverageIndex classCoverage = service.createInitialClassCoverage("app-1", "web3Server.service.impl.Web302Service");
        StaticSourceClassInfo classInfo = new StaticSourceClassInfo();
        classInfo.setClassName("web3Server.service.impl.Web302Service");
        classInfo.setSourceCode(String.join("\n",
                "public class Web302Service {",
                "    public void setSharingDTO(SharingDto sharingDTO) {",
                "        this.sharingDTO = sharingDTO;",
                "    }",
                "}"));

        StaticSourceMethodInfo methodInfo = new StaticSourceMethodInfo();
        methodInfo.setMethodName("setSharingDTO");
        methodInfo.setMethodDesc("(Lweb3Server/dto/SharingDto;)V");
        methodInfo.setMethodLineNumberMap(List.of(3, 4));
        methodInfo.setBranchLineNumberSet(List.of());
        methodInfo.setBranchLineAndTargetProbeMap(Map.of());
        methodInfo.setTotalBranchCount(0);
        methodInfo.setCyclomaticComplexityMap(1);

        Map<String, StaticSourceMethodInfo> methods = new LinkedHashMap<>();
        methods.put("setSharingDTO", methodInfo);
        classInfo.setMethodMaps(methods);

        service.appendStaticClassCoverage(classCoverage, classInfo, null);

        assertThat(classCoverage.getTotalLines()).isEqualTo(1);
        assertThat(classCoverage.getMethods()).hasSize(1);
        ClassCoverageIndex.MethodCoverageDetail method = classCoverage.getMethods().get(0);
        assertThat(method.getTotalLineNumbers()).containsExactly(3);
        assertThat(method.getTotalLines()).isEqualTo(1);
    }
}
