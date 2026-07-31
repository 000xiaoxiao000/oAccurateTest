package com.oAT.web.coveragecore.report;

import com.alibaba.excel.EasyExcel;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.MethodCoverageExportVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CoverageReportExportService {
    private static final int EXPORT_PAGE_SIZE = 100;

    private final ClassCoverageRepository classCoverageRepository;

    public CoverageReportExportService(ClassCoverageRepository classCoverageRepository) {
        this.classCoverageRepository = classCoverageRepository;
    }

    public void exportReport(String reportId, HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "CoverageReport_" + reportId);

        try (com.alibaba.excel.ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), ClassCoverageIndex.class).build()) {
            com.alibaba.excel.write.metadata.WriteSheet writeSheet = EasyExcel.writerSheet("Class Coverage").build();
            int pageNum = 0;
            Page<ClassCoverageIndex> page;
            do {
                page = classCoverageRepository.findByReportId(reportId, PageRequest.of(pageNum, EXPORT_PAGE_SIZE));
                excelWriter.write(page.getContent(), writeSheet);
                pageNum++;
            } while (page.hasNext());
        }
    }

    public void exportMethodReport(String reportId, HttpServletResponse response) throws IOException {
        prepareExcelResponse(response, "MethodCoverageReport_" + reportId);

        try (com.alibaba.excel.ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream(), MethodCoverageExportVo.class).build()) {
            com.alibaba.excel.write.metadata.WriteSheet writeSheet = EasyExcel.writerSheet("Method Coverage").build();
            int pageNum = 0;
            Page<ClassCoverageIndex> page;
            do {
                page = classCoverageRepository.findByReportIdWithMethods(reportId, PageRequest.of(pageNum, EXPORT_PAGE_SIZE));
                excelWriter.write(toMethodExportRows(page.getContent()), writeSheet);
                pageNum++;
            } while (page.hasNext());
        }
    }

    private void prepareExcelResponse(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + encodedFileName + ".xlsx");
    }

    private List<MethodCoverageExportVo> toMethodExportRows(List<ClassCoverageIndex> classCoverages) {
        List<MethodCoverageExportVo> rows = new ArrayList<>();
        for (ClassCoverageIndex classCoverage : classCoverages) {
            if (classCoverage.getMethods() == null) {
                continue;
            }
            for (ClassCoverageIndex.MethodCoverageDetail method : classCoverage.getMethods()) {
                rows.add(toMethodExportRow(classCoverage, method));
            }
        }
        return rows;
    }

    private MethodCoverageExportVo toMethodExportRow(ClassCoverageIndex classCoverage, ClassCoverageIndex.MethodCoverageDetail method) {
        MethodCoverageExportVo row = new MethodCoverageExportVo();
        row.setClassName(classCoverage.getClassName());
        row.setClassMethodCoverage(classCoverage.getCoveredMethods() + " / " + classCoverage.getTotalMethods());
        row.setClassMethodCoverageRate(rate(classCoverage.getCoveredMethods(), classCoverage.getTotalMethods()));
        row.setClassLineCoverage(classCoverage.getCoveredLines() + " / " + classCoverage.getTotalLines());
        row.setClassLineCoverageRate(rate(classCoverage.getCoveredLines(), classCoverage.getTotalLines()));
        row.setMethodName(method.getMethodName());
        row.setMethodDesc(method.getMethodDesc());
        row.setLineCoverage(method.getCoveredLines() + " / " + method.getTotalLines());
        row.setLineCoverageRate(rate(method.getCoveredLines(), method.getTotalLines()));
        row.setBranchCoverage(method.getCoveredBranchTargets() + " / " + method.getTotalBranchTargets());
        row.setBranchCoverageRate(rate(method.getCoveredBranchTargets(), method.getTotalBranchTargets()));
        row.setComplexity(method.getComplexity());
        row.setIsCovered(method.isCovered() ? "是" : "否");
        return row;
    }

    private String rate(long covered, long total) {
        return total > 0 ? String.format("%.2f%%", (double) covered / total * 100) : "0.00%";
    }
}
