package com.oAT.web.esDao;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.JsonData;
import com.oAT.web.esDao.entity.TraceSummaryIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.IndicesOptions;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Repository
public class TraceSummaryRepository {

    private static final Logger logger = LoggerFactory.getLogger(TraceSummaryRepository.class);

    private static final String INDEX_ALIAS = "trace_summary";
    private static final String INDEX_PATTERN = "trace_summary-*";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");
    private static final int RECENT_INDEX_MONTHS = 12;
    private static final IndicesOptions LENIENT_INDICES = IndicesOptions.of(
            EnumSet.of(IndicesOptions.Option.IGNORE_UNAVAILABLE, IndicesOptions.Option.ALLOW_NO_INDICES),
            EnumSet.of(IndicesOptions.WildcardStates.OPEN));
    private static final String[] LIST_INCLUDE_FIELDS = {
            "traceId", "projectId", "appId", "appName", "sessionId", "createTime",
            "beginTime", "endTime", "useTime", "status", "hasError",
            "httpMethod", "httpUrl", "httpUrlPath", "httpResponseCode",
            "httpClientIp", "httpServerIp", "httpServerPort", "httpAjax",
            "entryType", "entryName", "entryProtocol", "entryAppId", "entryAppName",
            "entryClientIp", "entryTopic", "entryInterface", "entryMethod",
            "nodeCount", "sqlCount", "remoteCount", "redisCount", "mqCount",
            "errorCount", "slowNodeCount"
    };

    private final ElasticsearchOperations elasticsearchOperations;

    public TraceSummaryRepository(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public Optional<TraceSummaryIndex> findById(String traceId) {
        try {
            TraceSummaryIndex summary = elasticsearchOperations.get(traceId, TraceSummaryIndex.class,
                    IndexCoordinates.of(INDEX_PATTERN));
            return Optional.ofNullable(summary);
        } catch (Exception e) {
            logger.debug("Trace summary not found: {}", traceId);
            return Optional.empty();
        }
    }

    public Page<TraceSummaryIndex> findByAppId(String appId, Pageable pageable) {
        Query query = TermQuery.of(q -> q.field("appId").value(appId))._toQuery();
        Page<TraceSummaryIndex> recentPage = searchPage(
                buildQuery(query, pageable, Sort.Direction.DESC, false),
                recentMonthlyIndices(),
                pageable,
                "appId=" + appId + ", recent monthly indices");
        if (recentPage != null && !recentPage.isEmpty()) {
            return recentPage;
        }

        Page<TraceSummaryIndex> allPage = searchPage(
                buildQuery(query, pageable, Sort.Direction.DESC, true),
                IndexCoordinates.of(INDEX_PATTERN),
                pageable,
                "appId=" + appId + ", all monthly indices");
        if (allPage != null) {
            return allPage;
        }

        Page<TraceSummaryIndex> unsortedPage = searchPage(
                buildQuery(query, pageable, null, false),
                IndexCoordinates.of(INDEX_PATTERN),
                pageable,
                "appId=" + appId + ", all monthly indices without sort");
        return unsortedPage == null ? emptyPage(pageable) : unsortedPage;
    }

    public Page<TraceSummaryIndex> findByProjectId(String projectId, Pageable pageable) {
        Query query = TermQuery.of(q -> q.field("projectId").value(projectId))._toQuery();
        Page<TraceSummaryIndex> allPage = searchPage(
                buildQuery(query, pageable, Sort.Direction.DESC, true),
                IndexCoordinates.of(INDEX_PATTERN),
                pageable,
                "projectId=" + projectId + ", all monthly indices");
        if (allPage != null) {
            return allPage;
        }

        Page<TraceSummaryIndex> recentPage = searchPage(
                buildQuery(query, pageable, Sort.Direction.DESC, false),
                recentMonthlyIndices(),
                pageable,
                "projectId=" + projectId + ", recent monthly indices");
        return recentPage == null ? emptyPage(pageable) : recentPage;
    }

    public Page<TraceSummaryIndex> findByAppIdAndCreateTimeGreaterThan(String appId, Date createTime, Pageable pageable) {
        Query query = BoolQuery.of(b -> b
                .must(TermQuery.of(t -> t.field("appId").value(appId))._toQuery())
                .must(RangeQuery.of(r -> r.field("createTime").gt(JsonData.of(createTime.getTime())))._toQuery())
        )._toQuery();

        Page<TraceSummaryIndex> page = searchPage(
                buildQuery(query, pageable, Sort.Direction.ASC, false),
                monthlyIndicesSince(createTime),
                pageable,
                "appId=" + appId + ", createTime>" + createTime);
        return page == null ? emptyPage(pageable) : page;
    }

    public TraceSummaryIndex save(TraceSummaryIndex summary) {
        return elasticsearchOperations.save(summary, indexFor(summary));
    }

    private List<TraceSummaryIndex> toContent(SearchHits<TraceSummaryIndex> hits) {
        List<TraceSummaryIndex> content = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<TraceSummaryIndex> hit : hits.getSearchHits()) {
            content.add(hit.getContent());
        }
        return content;
    }

    private NativeQuery buildQuery(Query query, Pageable pageable, Sort.Direction sortDirection, boolean trackTotalHits) {
        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .withTrackTotalHits(trackTotalHits)
                .withSourceFilter(new FetchSourceFilter(LIST_INCLUDE_FIELDS, null))
                .withIndicesOptions(LENIENT_INDICES)
                .withAllowNoIndices(true);
        if (sortDirection != null) {
            builder.withSort(Sort.by(sortDirection, "createTime"));
        }
        return builder.build();
    }

    private Page<TraceSummaryIndex> searchPage(NativeQuery searchQuery, IndexCoordinates indices, Pageable pageable,
                                               String context) {
        try {
            SearchHits<TraceSummaryIndex> hits = elasticsearchOperations.search(searchQuery,
                    TraceSummaryIndex.class,
                    indices);
            return new PageImpl<>(toContent(hits), pageable, hits.getTotalHits());
        } catch (Exception e) {
            logger.warn("Trace summary search failed for {} on indices {}: {}",
                    context, String.join(",", indices.getIndexNames()), e.getMessage());
            logger.debug("Trace summary search failure detail", e);
            return null;
        }
    }

    private Page<TraceSummaryIndex> emptyPage(Pageable pageable) {
        return new PageImpl<>(new ArrayList<>(), pageable, 0);
    }

    private IndexCoordinates recentMonthlyIndices() {
        ZoneId zoneId = ZoneId.systemDefault();
        YearMonth current = YearMonth.now(zoneId);
        List<String> indexNames = new ArrayList<>(RECENT_INDEX_MONTHS);
        for (int i = 0; i < RECENT_INDEX_MONTHS; i++) {
            indexNames.add(monthlyIndexName(current.minusMonths(i)));
        }
        return IndexCoordinates.of(indexNames.toArray(new String[0]));
    }

    private IndexCoordinates monthlyIndicesSince(Date createTime) {
        ZoneId zoneId = ZoneId.systemDefault();
        YearMonth current = YearMonth.now(zoneId);
        YearMonth start = createTime == null
                ? current.minusMonths(RECENT_INDEX_MONTHS - 1)
                : YearMonth.from(createTime.toInstant().atZone(zoneId));
        YearMonth oldestAllowed = current.minusMonths(RECENT_INDEX_MONTHS - 1);
        if (start.isBefore(oldestAllowed)) {
            start = oldestAllowed;
        }

        List<String> indexNames = new ArrayList<>(RECENT_INDEX_MONTHS);
        YearMonth cursor = start;
        while (!cursor.isAfter(current)) {
            indexNames.add(monthlyIndexName(cursor));
            cursor = cursor.plusMonths(1);
        }
        return IndexCoordinates.of(indexNames.toArray(new String[0]));
    }

    private String monthlyIndexName(YearMonth month) {
        return INDEX_ALIAS + "-" + month.format(INDEX_SUFFIX);
    }

    private IndexCoordinates indexFor(TraceSummaryIndex summary) {
        Date createTime = summary.getCreateTime() == null ? new Date() : summary.getCreateTime();
        String suffix = createTime.toInstant().atZone(ZoneId.systemDefault()).format(INDEX_SUFFIX);
        return IndexCoordinates.of(INDEX_ALIAS + "-" + suffix);
    }
}
