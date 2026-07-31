package com.oAT.web.esDao;

import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.oAT.web.esDao.entity.ProbeAlertEvent;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public class ProbeAlertEventRepository {
    private static final String INDEX_ALIAS = "probe_alert";
    private static final String INDEX_PATTERN = "probe_alert-*";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;

    public ProbeAlertEventRepository(ElasticsearchOperations elasticsearchOperations,
                                     ElasticsearchClient elasticsearchClient) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchClient = elasticsearchClient;
    }

    public List<ProbeAlertEvent> findByAppId(String appId) {
        return searchByTerm("appId", appId);
    }

    public List<ProbeAlertEvent> findByProjectId(String projectId) {
        return searchByTerm("projectId", projectId);
    }

    public ProbeAlertEvent save(ProbeAlertEvent event) {
        normalize(event);
        elasticsearchOperations.save(event, indexFor(event));
        return event;
    }

    public void deleteById(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        try {
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(INDEX_PATTERN)
                    .ignoreUnavailable(true)
                    .query(q -> q.ids(i -> i.values(id)))));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete probe alert event from ES: " + id, e);
        }
    }

    private void normalize(ProbeAlertEvent event) {
        if (!StringUtils.hasText(event.getId())) {
            event.setId(UUID.randomUUID().toString());
        }
        Date now = new Date();
        if (event.getEventTime() == null) {
            event.setEventTime(now);
        }
        if (event.getCreateTime() == null) {
            event.setCreateTime(now);
        }
        if (event.getUpdateTime() == null) {
            event.setUpdateTime(now);
        }
    }

    private List<ProbeAlertEvent> searchByTerm(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        NativeQuery query = NativeQuery.builder()
                .withQuery(TermQuery.of(t -> t.field(field).value(value))._toQuery())
                .withSort(Sort.by(Sort.Direction.DESC, "eventTime").and(Sort.by(Sort.Direction.DESC, "createTime")))
                .withPageable(PageRequest.of(0, 500))
                .build();
        SearchHits<ProbeAlertEvent> hits = elasticsearchOperations.search(query,
                ProbeAlertEvent.class,
                IndexCoordinates.of(INDEX_PATTERN));
        List<ProbeAlertEvent> events = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<ProbeAlertEvent> hit : hits.getSearchHits()) {
            events.add(hit.getContent());
        }
        return events;
    }

    private IndexCoordinates indexFor(ProbeAlertEvent event) {
        Date createTime = event.getCreateTime() == null ? new Date() : event.getCreateTime();
        String suffix = createTime.toInstant().atZone(ZoneId.systemDefault()).format(INDEX_SUFFIX);
        return IndexCoordinates.of(INDEX_ALIAS + "-" + suffix);
    }
}
