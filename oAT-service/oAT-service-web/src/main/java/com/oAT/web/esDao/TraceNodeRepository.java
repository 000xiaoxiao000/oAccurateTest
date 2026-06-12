package com.oAT.web.esDao;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.JsonData;
import com.oAT.web.esDao.entity.TraceNodeIndex;
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
import org.springframework.data.elasticsearch.core.query.BulkOptions;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TraceNodeRepository {

    private static final Logger logger = LoggerFactory.getLogger(TraceNodeRepository.class);

    private static final String MONTHLY_INDEX_PATTERN = "trace_node-*";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    // Minimal fields for trace list view - uses flat fields, avoids all nested payloads
    private static final String[] LIST_INCLUDE_FIELDS = {
            "id", "traceId", "traceNodeId", "appId", "appName", "type", "createTime",
            "root", "hasError", "status", "useTime",
            "httpClientIp", "httpServerIp", "httpServerPort",
            "httpMethod", "httpUrl", "httpResponseCode", "httpAjax"
    };

    // For existence-check queries - only need identity fields
    private static final String[] EXISTENCE_FIELDS = {
            "id", "traceId", "appId", "createTime"
    };

    private final ElasticsearchOperations elasticsearchOperations;

    public TraceNodeRepository(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public Optional<TraceNodeIndex> findById(String id) {
        Query query = IdsQuery.of(q -> q.values(id))._toQuery();
        NativeQuery searchQuery = NativeQuery.builder().withQuery(query).build();
        return findFirst(searchQuery, monthlyIndices(), "trace node", id);
    }

    /**
     * Retrieves all nodes belonging to a trace for detail/graph rendering.
     * Uses a higher page size since one trace can have many nodes (SQL, RPC, etc.).
     */
    public List<TraceNodeIndex> findByTraceId(String traceId, Pageable pageable) {
        Query query = TermQuery.of(q -> q.field("traceId").value(traceId))._toQuery();
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        List<TraceNodeIndex> nodes = searchTraceNodes(searchQuery, monthlyIndices(), traceId);
        nodes.sort(this::compareTraceNodes);
        return nodes;
    }

    /**
     * Returns recent root HTTP nodes for an appId using flat fields.
     * Capped at 200 results - prefer with explicit paging for large datasets.
     */
    public List<TraceNodeIndex> findByAppId(String appId) {
        Query query = BoolQuery.of(b -> b
                .must(TermQuery.of(t -> t.field("appId").value(appId))._toQuery())
                .must(TermQuery.of(t -> t.field("root").value(true))._toQuery())
        )._toQuery();
        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query)
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .withSourceFilter(new FetchSourceFilter(LIST_INCLUDE_FIELDS, null))
                .withPageable(org.springframework.data.domain.PageRequest.of(0, 200))
                .build();

        return searchTraceNodes(searchQuery, monthlyIndices(), appId);
    }

    /**
     * Checks whether there is a newer trace after the given time for a specific app.
     * Uses the flat `root` field instead of traceNodeId string comparison.
     * Only fetches the minimal fields needed to determine existence.
     */
    public Page<TraceNodeIndex> findByAppIdAndCreateTimeGreaterThanOrderByCreateTimeAsc(String appId, Date createTime,
                                                                                         Pageable pageable) {
        Query query = BoolQuery.of(b -> b
                .must(TermQuery.of(t -> t.field("appId").value(appId))._toQuery())
                .must(TermQuery.of(t -> t.field("root").value(true))._toQuery())
                .must(RangeQuery.of(r -> r.field("createTime").gt(JsonData.of(createTime.getTime())))._toQuery())
        )._toQuery();

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query)
                .withSort(Sort.by(Sort.Direction.ASC, "createTime"))
                .withPageable(pageable)
                .withSourceFilter(new FetchSourceFilter(EXISTENCE_FIELDS, null))
                .withTrackTotalHits(false)
                .build();

        return searchTraceNodePage(searchQuery, monthlyIndices(), appId, createTime, pageable);
    }
    public TraceNodeIndex save(TraceNodeIndex node) {
        return elasticsearchOperations.save(node, indexFor(node));
    }

    /**
     * Bulk-saves a batch of nodes into the correct monthly index.
     * Groups nodes by target index first so each bulk call targets a single index.
     */
    public void saveAll(Iterable<TraceNodeIndex> nodes) {
        Map<String, List<TraceNodeIndex>> byIndex = new HashMap<>();
        for (TraceNodeIndex node : nodes) {
            String indexName = indexFor(node).getIndexName();
            byIndex.computeIfAbsent(indexName, k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<String, List<TraceNodeIndex>> entry : byIndex.entrySet()) {
            String indexName = entry.getKey();
            List<TraceNodeIndex> batch = entry.getValue();
            List<IndexQuery> queries = new ArrayList<>(batch.size());
            for (TraceNodeIndex node : batch) {
                queries.add(new IndexQueryBuilder()
                        .withId(node.getId())
                        .withObject(node)
                        .build());
            }
            try {
                elasticsearchOperations.bulkIndex(queries, BulkOptions.defaultOptions(),
                        IndexCoordinates.of(indexName));
            } catch (Exception e) {
                logger.error("Bulk index failed for index {}, falling back to single save: {}", indexName, e.getMessage());
                for (TraceNodeIndex node : batch) {
                    try {
                        elasticsearchOperations.save(node, IndexCoordinates.of(indexName));
                    } catch (Exception ex) {
                        logger.error("Single save also failed for traceId={} nodeId={}: {}",
                                node.getTraceId(), node.getTraceNodeId(), ex.getMessage());
                    }
                }
            }
        }
    }

    private List<TraceNodeIndex> toContent(SearchHits<TraceNodeIndex> hits) {
        List<TraceNodeIndex> content = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<TraceNodeIndex> hit : hits.getSearchHits()) {
            content.add(hit.getContent());
        }
        return content;
    }

    private Optional<TraceNodeIndex> findFirst(NativeQuery searchQuery, IndexCoordinates index, String indexLabel, String id) {
        try {
            SearchHits<TraceNodeIndex> hits = elasticsearchOperations.search(searchQuery, TraceNodeIndex.class, index);
            return hits.getSearchHits().stream().findFirst().map(SearchHit::getContent);
        } catch (Exception e) {
            logger.warn("Failed to find {} by id={}: {}", indexLabel, id, e.getMessage());
            return Optional.empty();
        }
    }

    private List<TraceNodeIndex> searchTraceNodes(NativeQuery searchQuery, IndexCoordinates index, String key) {
        try {
            SearchHits<TraceNodeIndex> hits = elasticsearchOperations.search(searchQuery, TraceNodeIndex.class, index);
            return toContent(hits);
        } catch (Exception e) {
            logger.warn("Failed to search trace nodes for key={}: {}", key, e.getMessage());
            return new ArrayList<>();
        }
    }

    private Page<TraceNodeIndex> searchTraceNodePage(NativeQuery searchQuery, IndexCoordinates index, String appId,
                                                     Date createTime, Pageable pageable) {
        try {
            SearchHits<TraceNodeIndex> hits = elasticsearchOperations.search(searchQuery, TraceNodeIndex.class, index);
            return new PageImpl<>(toContent(hits), pageable, hits.getTotalHits());
        } catch (Exception e) {
            logger.warn("Failed to search trace nodes for appId={} after createTime={}: {}", appId, createTime, e.getMessage());
            return new PageImpl<>(new ArrayList<>(), pageable, 0);
        }
    }

    private int compareTraceNodes(TraceNodeIndex left, TraceNodeIndex right) {
        Date leftTime = left == null ? null : left.getCreateTime();
        Date rightTime = right == null ? null : right.getCreateTime();
        if (leftTime != null && rightTime != null) {
            int timeCompare = leftTime.compareTo(rightTime);
            if (timeCompare != 0) {
                return timeCompare;
            }
        } else if (leftTime != null) {
            return -1;
        } else if (rightTime != null) {
            return 1;
        }

        String leftNodeId = left == null || left.getTraceNodeId() == null ? "" : left.getTraceNodeId();
        String rightNodeId = right == null || right.getTraceNodeId() == null ? "" : right.getTraceNodeId();
        return leftNodeId.compareTo(rightNodeId);
    }

    private IndexCoordinates monthlyIndices() {
        return IndexCoordinates.of(MONTHLY_INDEX_PATTERN);
    }

    private IndexCoordinates indexFor(TraceNodeIndex node) {
        Date createTime = node.getCreateTime() == null ? new Date() : node.getCreateTime();
        String suffix = createTime.toInstant().atZone(ZoneId.systemDefault()).format(INDEX_SUFFIX);
        return IndexCoordinates.of("trace_node-" + suffix);
    }
}
