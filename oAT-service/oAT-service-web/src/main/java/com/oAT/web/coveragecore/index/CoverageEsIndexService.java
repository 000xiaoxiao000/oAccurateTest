package com.oAT.web.coveragecore.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.BulkOptions;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoverageEsIndexService {
    private static final Logger logger = LoggerFactory.getLogger(CoverageEsIndexService.class);
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    public CoverageEsIndexService(ElasticsearchOperations elasticsearchOperations,
                                  ElasticsearchClient elasticsearchClient) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchClient = elasticsearchClient;
    }

    public void indexTrend(CoverageReportIndex report) {
        if (report == null || !StringUtils.hasText(report.getId())) {
            return;
        }
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", report.getId());
            doc.put("reportId", report.getId());
            doc.put("appId", report.getAppId());
            doc.put("language", firstText(report.getLanguage(), report.getSourceType()));
            doc.put("versionNumber", report.getVersionNumber());
            doc.put("commitId", report.getRepoCommitId());
            doc.put("branch", report.getRepoBranch());
            doc.put("buildId", report.getBuildId());
            doc.put("testStage", report.getTestStage());
            doc.put("reportType", report.getReportType());
            doc.put("timestamp", report.getCreateTime());
            doc.put("date", report.getCreateTime());
            doc.put("totalClasses", report.getTotalClasses());
            doc.put("coveredClasses", report.getCoveredClasses());
            doc.put("totalMethods", report.getTotalMethods());
            doc.put("coveredMethods", report.getCoveredMethods());
            doc.put("totalLines", report.getTotalLines());
            doc.put("coveredLines", report.getCoveredLines());
            doc.put("totalBranches", report.getTotalBranches());
            doc.put("coveredBranches", report.getCoveredBranches());
            doc.put("totalBranchTargets", report.getTotalBranchTargets());
            doc.put("coveredBranchTargets", report.getCoveredBranchTargets());
            doc.put("lineRate", rate(report.getCoveredLines(), report.getTotalLines()));
            doc.put("branchRate", rate(report.getCoveredBranchTargets(), report.getTotalBranchTargets()));
            doc.put("methodRate", rate(report.getCoveredMethods(), report.getTotalMethods()));
            elasticsearchOperations.save(doc, IndexCoordinates.of("coverage_trends-" + suffix(report.getCreateTime())));
        } catch (Exception e) {
            logger.warn("Failed to index coverage trend: reportId={}, error={}", report.getId(), e.getMessage());
        }
    }

    public void indexClassCoverage(Iterable<ClassCoverageIndex> classes) {
        if (classes == null) {
            return;
        }
        List<IndexQuery> queries = new ArrayList<>();
        Date now = new Date();
        for (ClassCoverageIndex index : classes) {
            if (index == null || !StringUtils.hasText(index.getId())) {
                continue;
            }
            queries.add(new IndexQueryBuilder()
                    .withId(index.getId())
                    .withObject(classDoc(index, now))
                    .build());
            if (index.getMethods() != null) {
                for (int i = 0; i < index.getMethods().size(); i++) {
                    ClassCoverageIndex.MethodCoverageDetail method = index.getMethods().get(i);
                    if (method == null) {
                        continue;
                    }
                    queries.add(new IndexQueryBuilder()
                            .withId(index.getId() + "_" + i)
                            .withObject(methodDoc(index, method, i, now))
                            .build());
                }
            }
        }
        if (queries.isEmpty()) {
            return;
        }
        try {
            elasticsearchOperations.bulkIndex(queries, BulkOptions.defaultOptions(),
                    IndexCoordinates.of("coverage_method_search-" + suffix(now)));
        } catch (Exception e) {
            logger.warn("Failed to bulk index coverage search docs: docs={}, error={}", queries.size(), e.getMessage());
        }
    }

    public void deleteByReportId(String reportId) {
        if (!StringUtils.hasText(reportId)) {
            return;
        }
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index("coverage_method_search-*")
                    .ignoreUnavailable(true)
                    .conflicts(Conflicts.Proceed)
                    .query(q -> q.term(t -> t.field("reportId").value(reportId)))
                    .refresh(true));
        } catch (Exception e) {
            logger.warn("Failed to delete coverage search docs: reportId={}, error={}", reportId, e.getMessage());
        }
    }

    private Map<String, Object> classDoc(ClassCoverageIndex index, Date now) {
        Map<String, Object> doc = baseDoc(index, now);
        doc.put("docType", "class");
        doc.put("classNameText", index.getClassName());
        doc.put("methodName", null);
        doc.put("methodNameKeyword", null);
        doc.put("methodDesc", null);
        doc.put("lineRate", index.getLineRate());
        doc.put("branchRate", index.getBranchRate());
        doc.put("methodRate", index.getMethodRate());
        doc.put("covered", index.getCoveredMethods() >= index.getTotalMethods() && index.getTotalMethods() > 0);
        doc.put("hasCodeChanges", index.getHasCodeChanges());
        return doc;
    }

    private Map<String, Object> methodDoc(ClassCoverageIndex index, ClassCoverageIndex.MethodCoverageDetail method, int order, Date now) {
        Map<String, Object> doc = baseDoc(index, now);
        doc.put("docType", "method");
        doc.put("methodOrder", order);
        doc.put("methodName", method.getMethodName());
        doc.put("methodNameKeyword", method.getMethodName());
        doc.put("methodDesc", method.getMethodDesc());
        doc.put("lineRate", rate(method.getCoveredLines(), method.getTotalLines()));
        doc.put("branchRate", method.getBranchRate());
        doc.put("covered", method.isCovered());
        doc.put("hasCodeChanges", method.isHasCodeChanges());
        doc.put("totalLines", method.getTotalLines());
        doc.put("coveredLines", method.getCoveredLines());
        doc.put("totalBranches", method.getTotalBranches());
        doc.put("coveredBranches", method.getCoveredBranches());
        doc.put("totalBranchTargets", method.getTotalBranchTargets());
        doc.put("coveredBranchTargets", method.getCoveredBranchTargets());
        doc.put("complexity", method.getComplexity());
        return doc;
    }

    private Map<String, Object> baseDoc(ClassCoverageIndex index, Date now) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", index.getId());
        doc.put("reportId", index.getReportId());
        doc.put("appId", index.getAppId());
        doc.put("language", firstText(index.getLanguage(), index.getSourceType()));
        doc.put("className", index.getClassName());
        doc.put("displayName", index.getDisplayName());
        doc.put("sourcePath", index.getSourcePath());
        doc.put("createTime", now);
        return doc;
    }

    private String suffix(Date date) {
        Date effective = date == null ? new Date() : date;
        return effective.toInstant().atZone(ZoneId.systemDefault()).format(INDEX_SUFFIX);
    }

    private Double rate(long covered, long total) {
        return total <= 0 ? 0D : (double) covered / total;
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
