package com.oAT.web.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleRequest;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Component
public class ElasticsearchTemplateInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTemplateInitializer.class);
    private static final String TRACE_NODE_TEMPLATE_NAME = "trace_node_template";
    private static final String TRACE_NODE_ILM_POLICY_NAME = "trace_node_ilm_policy";
    private static final String TRACE_NODE_WRITE_ALIAS = "trace_node_write";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchTemplateInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTemplates() {
        try {
            createIlmPolicy();
            createTraceNodeTemplate();
            ensureWriteAlias();
            logger.info("Elasticsearch templates and ILM policy initialized successfully");
        } catch (Exception e) {
            logger.warn("Failed to initialize Elasticsearch templates: {}", e.getMessage());
        }
    }

    private void createIlmPolicy() {
        try {
            ClassPathResource resource = new ClassPathResource("elasticsearch/trace_node_ilm_policy.json");
            if (!resource.exists()) {
                logger.warn("trace_node_ilm_policy.json not found, skipping ILM policy creation");
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                PutLifecycleRequest request = PutLifecycleRequest.of(b -> b
                        .name(TRACE_NODE_ILM_POLICY_NAME)
                        .withJson(is));
                elasticsearchClient.ilm().putLifecycle(request);
                logger.info("trace_node ILM policy ensured in Elasticsearch: {}", TRACE_NODE_ILM_POLICY_NAME);
            }
        } catch (Exception e) {
            logger.error("Failed to create trace_node ILM policy", e);
        }
    }

    private void createTraceNodeTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("elasticsearch/trace_node_template.json");
            if (!resource.exists()) {
                logger.warn("trace_node_template.json not found, skipping template creation");
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                PutIndexTemplateRequest request = PutIndexTemplateRequest.of(builder -> builder
                        .name(TRACE_NODE_TEMPLATE_NAME)
                        .withJson(is));
                elasticsearchClient.indices().putIndexTemplate(request);
                logger.info("trace_node index template ensured in Elasticsearch: {}", TRACE_NODE_TEMPLATE_NAME);
            }
        } catch (Exception e) {
            logger.error("Failed to create trace_node template", e);
        }
    }

    /**
     * Ensures the rollover write alias points to the current monthly index.
     * ILM requires at least one index with the write alias set to is_write_index=true
     * before rollover can function. This is idempotent — skipped if the alias already exists.
     */
    private void ensureWriteAlias() {
        try {
            ExistsAliasRequest existsRequest = ExistsAliasRequest.of(b -> b.name(TRACE_NODE_WRITE_ALIAS));
            boolean aliasExists = elasticsearchClient.indices().existsAlias(existsRequest).value();
            if (aliasExists) {
                logger.debug("trace_node write alias already exists, skipping bootstrap");
                return;
            }

            String currentIndex = "trace_node-" + new Date().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(INDEX_SUFFIX);

            elasticsearchClient.indices().create(c -> c
                    .index(currentIndex)
                    .aliases(TRACE_NODE_WRITE_ALIAS, a -> a.isWriteIndex(true)));

            logger.info("Bootstrapped trace_node write alias '{}' on index '{}'",
                    TRACE_NODE_WRITE_ALIAS, currentIndex);
        } catch (Exception e) {
            logger.warn("Failed to ensure trace_node write alias (may already exist): {}", e.getMessage());
        }
    }
}
