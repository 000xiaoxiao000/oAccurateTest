package com.oAT.web.coveragecore.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.common.UtilJson;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoverageEsIndexService {
    private static final Logger logger = LoggerFactory.getLogger(CoverageEsIndexService.class);
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");
    private static final int ES_MAX_WINDOW = 10_000;

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
            doc.put("baseVersionNumber", report.getBaseVersionNumber());
            doc.put("baseRepoCommitId", report.getBaseRepoCommitId());
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
            IndexQuery query = new IndexQueryBuilder()
                    .withId(report.getId())
                    .withObject(doc)
                    .build();
            elasticsearchOperations.index(query, IndexCoordinates.of("coverage_trends-" + suffix(report.getCreateTime())));
        } catch (Exception e) {
            logger.warn("Failed to index coverage trend: reportId={}, error={}", report.getId(), e.getMessage());
        }
    }

    public void indexClassCoverage(Iterable<ClassCoverageIndex> classes) {
        if (classes == null) {
            return;
        }
        List<String> classIds = new ArrayList<>();
        List<IndexQuery> queries = new ArrayList<>();
        Date now = new Date();
        for (ClassCoverageIndex index : classes) {
            if (index == null || !StringUtils.hasText(index.getId())) {
                continue;
            }
            classIds.add(index.getId());
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
            deleteMethodDetailsByClassIds(classIds);
            elasticsearchOperations.bulkIndex(queries, BulkOptions.defaultOptions(),
                    IndexCoordinates.of("coverage_method_search-" + suffix(now)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bulk index coverage search docs: " + queries.size(), e);
        }
    }

    public List<ClassCoverageIndex.MethodCoverageDetail> loadMethodDetails(String classCoverageId) {
        if (!StringUtils.hasText(classCoverageId)) {
            return Collections.emptyList();
        }
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index("coverage_method_search-*")
                            .ignoreUnavailable(true)
                            .size(10_000)
                            .query(q -> q.bool(b -> b
                                    .filter(termQuery("docType", "method"))
                                    .filter(termQuery("id", classCoverageId))))
                            .sort(sort -> sort.field(f -> f.field("methodOrder").order(SortOrder.Asc))),
                    Map.class);
            List<ClassCoverageIndex.MethodCoverageDetail> methods = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source == null || source.get("detailJson") == null) {
                    continue;
                }
                methods.add(UtilJson.convertValue(String.valueOf(source.get("detailJson")),
                        ClassCoverageIndex.MethodCoverageDetail.class));
            }
            return methods;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load method coverage details from ES: " + classCoverageId, e);
        }
    }

    public void deleteMethodDetailsByClassIds(List<String> classCoverageIds) {
        List<co.elastic.clients.elasticsearch._types.FieldValue> ids = classCoverageIds == null
                ? Collections.emptyList()
                : classCoverageIds.stream()
                .filter(StringUtils::hasText)
                .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index("coverage_method_search-*")
                    .ignoreUnavailable(true)
                    .conflicts(Conflicts.Proceed)
                    .query(q -> q.bool(b -> b
                            .filter(termQuery("docType", "method"))
                            .filter(f -> f.terms(t -> t.field("id")
                                    .terms(v -> v.value(ids))))))
                    .refresh(true));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete method coverage details from ES", e);
        }
    }

    public List<Map<String, Object>> searchTrendData(String appId, String versionNumber) {
        if (!StringUtils.hasText(appId)) {
            return Collections.emptyList();
        }
        List<Query> filters = new ArrayList<>();
        filters.add(termQuery("appId", appId));
        if (StringUtils.hasText(versionNumber)) {
            filters.add(termQuery("versionNumber", versionNumber));
        }
        try {
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                            .index("coverage_trends-*")
                            .ignoreUnavailable(true)
                            .size(500)
                            .query(q -> q.bool(b -> b.filter(filters)))
                            .sort(sort -> sort.field(f -> f.field("timestamp").order(SortOrder.Desc))),
                    Map.class);
            List<Map<String, Object>> trend = new ArrayList<>();
            SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm");
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source == null) {
                    continue;
                }
                Map<String, Object> point = new LinkedHashMap<>();
                Date timestamp = toDate(source.get("timestamp"));
                point.put("time", timestamp == null ? null : formatter.format(timestamp));
                point.put("timestamp", timestamp == null ? null : timestamp.getTime());
                point.put("reportId", source.get("reportId"));
                point.put("versionNumber", source.get("versionNumber"));
                point.put("repoBranch", source.get("branch"));
                point.put("repoCommitId", source.get("commitId"));
                point.put("reportType", normalizeReportType(source.get("reportType")));
                point.put("baseVersionNumber", source.get("baseVersionNumber"));
                point.put("baseRepoCommitId", source.get("baseRepoCommitId"));
                point.put("lineCoverage", percent(source.get("lineRate")));
                point.put("methodCoverage", percent(source.get("methodRate")));
                point.put("branchCoverage", percent(source.get("branchRate")));
                trend.add(point);
            }
            return trend;
        } catch (Exception e) {
            logger.warn("Failed to query coverage trend from ES: appId={}, versionNumber={}, error={}",
                    appId, versionNumber, e.getMessage());
            return Collections.emptyList();
        }
    }

    public CoverageClassIdSearchResult searchClassCoverageIds(String reportId,
                                                              String className,
                                                              String methodName,
                                                              Double minLineRate,
                                                              Double maxLineRate,
                                                              Double minBranchRate,
                                                              Double maxBranchRate,
                                                              Double minMethodRate,
                                                              Double maxMethodRate,
                                                              Integer minComplexity,
                                                              Integer maxComplexity,
                                                              String classNamePrefix,
                                                              int page,
                                                              int size) {
        if (!StringUtils.hasText(reportId) || !hasSearchFilter(className, methodName, minLineRate, maxLineRate,
                minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity, classNamePrefix)) {
            return CoverageClassIdSearchResult.unavailable();
        }
        try {
            return searchClassCoverageIdsByClassDocs(reportId, className, methodName, minLineRate, maxLineRate,
                    minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity,
                    classNamePrefix, page, size);
        } catch (Exception e) {
            logger.warn("Failed to query coverage class ids from ES: reportId={}, error={}", reportId, e.getMessage());
            return CoverageClassIdSearchResult.unavailable();
        }
    }

    public CoverageClassIdSearchResult searchTreeClassCoverageIds(String reportId,
                                                                  String className,
                                                                  String methodName,
                                                                  Double minLineRate,
                                                                  Double maxLineRate,
                                                                  Double minBranchRate,
                                                                  Double maxBranchRate,
                                                                  Double minMethodRate,
                                                                  Double maxMethodRate,
                                                                  Integer minComplexity,
                                                                  Integer maxComplexity,
                                                                  String classNamePrefix) {
        return searchClassCoverageIds(reportId, className, methodName, minLineRate, maxLineRate, minBranchRate,
                maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity, classNamePrefix, 0, ES_MAX_WINDOW);
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
            throw new IllegalStateException("Failed to delete coverage search docs: " + reportId, e);
        }
    }

    private Map<String, Object> classDoc(ClassCoverageIndex index, Date now) {
        Map<String, Object> doc = baseDoc(index, now);
        doc.put("docType", "class");
        doc.put("classNameText", index.getClassName());
        doc.put("methodNamesText", methodNamesText(index));
        doc.put("methodName", null);
        doc.put("methodNameKeyword", null);
        doc.put("methodDesc", null);
        doc.put("lineRate", index.getLineRate());
        doc.put("branchRate", index.getBranchRate());
        doc.put("methodRate", index.getMethodRate());
        doc.put("totalLines", index.getTotalLines());
        doc.put("coveredLines", index.getCoveredLines());
        doc.put("totalBranches", index.getTotalBranches());
        doc.put("coveredBranches", index.getCoveredBranches());
        doc.put("totalBranchTargets", index.getTotalBranchTargets());
        doc.put("coveredBranchTargets", index.getCoveredBranchTargets());
        doc.put("complexity", index.getTotalComplexity());
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
        doc.put("detailJson", UtilJson.writeValueAsString(method));
        return doc;
    }

    private Map<String, Object> baseDoc(ClassCoverageIndex index, Date now) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", index.getId());
        doc.put("reportId", index.getReportId());
        doc.put("appId", index.getAppId());
        doc.put("language", firstText(index.getLanguage(), index.getSourceType()));
        doc.put("className", index.getClassName());
        doc.put("classNameText", index.getClassName());
        doc.put("displayName", index.getDisplayName());
        doc.put("sourcePath", index.getSourcePath());
        doc.put("createTime", now);
        return doc;
    }

    private String methodNamesText(ClassCoverageIndex index) {
        if (index.getMethods() == null || index.getMethods().isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (ClassCoverageIndex.MethodCoverageDetail method : index.getMethods()) {
            if (method == null) {
                continue;
            }
            appendText(text, method.getMethodName());
            appendText(text, method.getMethodDesc());
        }
        return text.isEmpty() ? null : text.toString();
    }

    private void appendText(StringBuilder text, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(value);
    }

    private CoverageClassIdSearchResult searchClassCoverageIdsByClassDocs(String reportId,
                                                                          String className,
                                                                          String methodName,
                                                                          Double minLineRate,
                                                                          Double maxLineRate,
                                                                          Double minBranchRate,
                                                                          Double maxBranchRate,
                                                                          Double minMethodRate,
                                                                          Double maxMethodRate,
                                                                          Integer minComplexity,
                                                                          Integer maxComplexity,
                                                                          String classNamePrefix,
                                                                          int page,
                                                                          int size) throws Exception {
        int effectivePage = Math.max(page, 0);
        int effectiveSize = normalizeSize(size);
        if ((long) effectivePage * effectiveSize + effectiveSize > ES_MAX_WINDOW) {
            return CoverageClassIdSearchResult.unavailable();
        }
        List<Query> filters = classSearchFilters(reportId, "class", className, methodName, minLineRate, maxLineRate,
                minBranchRate, maxBranchRate, minMethodRate, maxMethodRate, minComplexity, maxComplexity, classNamePrefix);
        SearchResponse<Map> response = elasticsearchClient.search(s -> s
                        .index("coverage_method_search-*")
                        .ignoreUnavailable(true)
                        .from(effectivePage * effectiveSize)
                        .size(effectiveSize)
                        .query(q -> q.bool(b -> b.filter(filters)))
                        .sort(sort -> sort.field(f -> f.field("className").order(SortOrder.Asc))),
                Map.class);
        long total = response.hits().total() == null ? 0 : response.hits().total().value();
        List<String> ids = extractClassIds(response.hits().hits());
        return CoverageClassIdSearchResult.available(ids, total);
    }

    private List<Query> classSearchFilters(String reportId,
                                           String docType,
                                           String className,
                                           String methodName,
                                           Double minLineRate,
                                           Double maxLineRate,
                                           Double minBranchRate,
                                           Double maxBranchRate,
                                           Double minMethodRate,
                                           Double maxMethodRate,
                                           Integer minComplexity,
                                           Integer maxComplexity,
                                           String classNamePrefix) {
        List<Query> filters = new ArrayList<>();
        filters.add(termQuery("reportId", reportId));
        filters.add(termQuery("docType", docType));
        if (StringUtils.hasText(className)) {
            filters.add(matchQuery("classNameText", className));
        }
        if (StringUtils.hasText(methodName)) {
            filters.add(matchQuery("methodNamesText", methodName));
        }
        if (StringUtils.hasText(classNamePrefix)) {
            filters.add(wildcardQuery("className", classNamePrefix + "*"));
        }
        appendRange(filters, "lineRate", minLineRate, maxLineRate);
        appendRange(filters, "branchRate", minBranchRate, maxBranchRate);
        appendRange(filters, "methodRate", minMethodRate, maxMethodRate);
        appendRange(filters, "complexity", minComplexity, maxComplexity);
        return filters;
    }

    private List<String> extractClassIds(List<Hit<Map>> hits) {
        List<String> ids = new ArrayList<>();
        for (Hit<Map> hit : hits) {
            Map source = hit.source();
            if (source == null) {
                continue;
            }
            Object id = source.get("id");
            if (id != null && StringUtils.hasText(String.valueOf(id))) {
                ids.add(String.valueOf(id));
            }
        }
        return ids;
    }

    private void appendRange(List<Query> filters, String field, Number min, Number max) {
        if (min == null && max == null) {
            return;
        }
        filters.add(Query.of(q -> q.range(r -> {
            r.field(field);
            if (min != null) {
                r.gte(JsonData.of(min));
            }
            if (max != null) {
                r.lte(JsonData.of(max));
            }
            return r;
        })));
    }

    private Query termQuery(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private Query matchQuery(String field, String value) {
        return Query.of(q -> q.match(m -> m.field(field).query(value)));
    }

    private Query wildcardQuery(String field, String value) {
        return Query.of(q -> q.wildcard(w -> w.field(field).value(value)));
    }

    private boolean hasSearchFilter(String className,
                                    String methodName,
                                    Double minLineRate,
                                    Double maxLineRate,
                                    Double minBranchRate,
                                    Double maxBranchRate,
                                    Double minMethodRate,
                                    Double maxMethodRate,
                                    Integer minComplexity,
                                    Integer maxComplexity,
                                    String classNamePrefix) {
        return StringUtils.hasText(className)
                || StringUtils.hasText(methodName)
                || minLineRate != null
                || maxLineRate != null
                || minBranchRate != null
                || maxBranchRate != null
                || minMethodRate != null
                || maxMethodRate != null
                || minComplexity != null
                || maxComplexity != null
                || StringUtils.hasText(classNamePrefix);
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 500);
    }

    private Date toDate(Object value) {
        if (value instanceof Date date) {
            return date;
        }
        if (value instanceof Number number) {
            return new Date(number.longValue());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Date.from(java.time.Instant.parse(text));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private int normalizeReportType(Object reportType) {
        if (reportType instanceof Number number) {
            return number.intValue();
        }
        if (reportType != null) {
            try {
                return Integer.parseInt(String.valueOf(reportType));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private double percent(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue() * 100;
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value)) * 100;
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
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

    public static class CoverageClassIdSearchResult {
        private final boolean available;
        private final List<String> ids;
        private final long total;

        private CoverageClassIdSearchResult(boolean available, List<String> ids, long total) {
            this.available = available;
            this.ids = ids == null ? Collections.emptyList() : ids;
            this.total = total;
        }

        public static CoverageClassIdSearchResult unavailable() {
            return new CoverageClassIdSearchResult(false, Collections.emptyList(), 0);
        }

        public static CoverageClassIdSearchResult available(List<String> ids, long total) {
            return new CoverageClassIdSearchResult(true, ids, total);
        }

        public boolean isAvailable() {
            return available;
        }

        public List<String> getIds() {
            return ids;
        }

        public long getTotal() {
            return total;
        }
    }
}
