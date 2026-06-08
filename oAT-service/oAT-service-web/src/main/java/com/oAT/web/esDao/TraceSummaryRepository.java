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
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class TraceSummaryRepository {

    private static final Logger logger = LoggerFactory.getLogger(TraceSummaryRepository.class);

    private static final String INDEX_ALIAS = "trace_summary";
    private static final String INDEX_PATTERN = "trace_summary*";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

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
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query)
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .withPageable(pageable)
                .withTrackTotalHits(true)
                .build();

        SearchHits<TraceSummaryIndex> hits = elasticsearchOperations.search(searchQuery,
                TraceSummaryIndex.class,
                IndexCoordinates.of(INDEX_PATTERN));
        return new PageImpl<>(toContent(hits), pageable, hits.getTotalHits());
    }

    public Page<TraceSummaryIndex> findByProjectId(String projectId, Pageable pageable) {
        Query query = TermQuery.of(q -> q.field("projectId").value(projectId))._toQuery();
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query)
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .withPageable(pageable)
                .withTrackTotalHits(true)
                .build();

        SearchHits<TraceSummaryIndex> hits = elasticsearchOperations.search(searchQuery,
                TraceSummaryIndex.class,
                IndexCoordinates.of(INDEX_PATTERN));
        return new PageImpl<>(toContent(hits), pageable, hits.getTotalHits());
    }

    public Page<TraceSummaryIndex> findByAppIdAndCreateTimeGreaterThan(String appId, Date createTime, Pageable pageable) {
        Query query = BoolQuery.of(b -> b
                .must(TermQuery.of(t -> t.field("appId").value(appId))._toQuery())
                .must(RangeQuery.of(r -> r.field("createTime").gt(JsonData.of(createTime.getTime())))._toQuery())
        )._toQuery();

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query)
                .withSort(Sort.by(Sort.Direction.ASC, "createTime"))
                .withPageable(pageable)
                .withTrackTotalHits(false)
                .build();

        SearchHits<TraceSummaryIndex> hits = elasticsearchOperations.search(searchQuery,
                TraceSummaryIndex.class,
                IndexCoordinates.of(INDEX_PATTERN));
        return new PageImpl<>(toContent(hits), pageable, hits.getTotalHits());
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

    private IndexCoordinates indexFor(TraceSummaryIndex summary) {
        Date createTime = summary.getCreateTime() == null ? new Date() : summary.getCreateTime();
        String suffix = createTime.toInstant().atZone(ZoneId.systemDefault()).format(INDEX_SUFFIX);
        return IndexCoordinates.of(INDEX_ALIAS + "-" + suffix);
    }
}
