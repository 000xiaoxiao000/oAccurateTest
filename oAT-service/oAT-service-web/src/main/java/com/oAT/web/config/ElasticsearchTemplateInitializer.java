package com.oAT.web.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleRequest;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
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
    private static final String COVERAGE_METHOD_SEARCH_TEMPLATE_NAME = "coverage_method_search_template";
    private static final String COVERAGE_TRENDS_TEMPLATE_NAME = "coverage_trends_template";
    private static final String SYSTEM_LOG_TEMPLATE_NAME = "system_log_template";
    private static final String PROBE_ALERT_TEMPLATE_NAME = "probe_alert_template";
    private static final String TRACE_NODE_ILM_POLICY_NAME = "trace_node_ilm_policy";
    private static final String COVERAGE_METHOD_SEARCH_ILM_POLICY_NAME = "coverage_method_search_ilm_policy";
    private static final String COVERAGE_TRENDS_ILM_POLICY_NAME = "coverage_trends_ilm_policy";
    private static final String TRACE_NODE_WRITE_ALIAS = "trace_node_write";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchTemplateInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @EventListener(ApplicationStartedEvent.class)
    public void initializeTemplates() {
        try {
            createIlmPolicy(TRACE_NODE_ILM_POLICY_NAME, "elasticsearch/trace_node_ilm_policy.json");
            createIlmPolicy(COVERAGE_METHOD_SEARCH_ILM_POLICY_NAME, "elasticsearch/coverage_method_search_ilm_policy.json");
            createIlmPolicy(COVERAGE_TRENDS_ILM_POLICY_NAME, "elasticsearch/coverage_trends_ilm_policy.json");
            createTemplate(TRACE_NODE_TEMPLATE_NAME, "elasticsearch/trace_node_template.json");
            createTemplate(COVERAGE_METHOD_SEARCH_TEMPLATE_NAME, "elasticsearch/coverage_method_search_template.json");
            createTemplate(COVERAGE_TRENDS_TEMPLATE_NAME, "elasticsearch/coverage_trends_template.json");
            createTemplate(SYSTEM_LOG_TEMPLATE_NAME, "elasticsearch/system_log_template.json");
            createTemplate(PROBE_ALERT_TEMPLATE_NAME, "elasticsearch/probe_alert_template.json");
            updateCoverageIndexMappings();
            ensureWriteAlias();
            logger.info("Elasticsearch templates and ILM policy initialized successfully");
        } catch (Exception e) {
            logger.warn("Failed to initialize Elasticsearch templates: {}", e.getMessage());
        }
    }

    private void createIlmPolicy(String policyName, String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                logger.warn("{} not found, skipping ILM policy creation", resourcePath);
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                PutLifecycleRequest request = PutLifecycleRequest.of(b -> b
                        .name(policyName)
                        .withJson(is));
                elasticsearchClient.ilm().putLifecycle(request);
                logger.info("ILM policy ensured in Elasticsearch: {}", policyName);
            }
        } catch (Exception e) {
            logger.error("Failed to create ILM policy: {}", policyName, e);
        }
    }

    private void createTemplate(String templateName, String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                logger.warn("{} not found, skipping template creation", resourcePath);
                return;
            }
            try (InputStream is = resource.getInputStream()) {
                PutIndexTemplateRequest request = PutIndexTemplateRequest.of(builder -> builder
                        .name(templateName)
                        .withJson(is));
                elasticsearchClient.indices().putIndexTemplate(request);
                logger.info("index template ensured in Elasticsearch: {}", templateName);
            }
        } catch (Exception e) {
            logger.error("Failed to create index template: {}", templateName, e);
        }
    }

    private void updateCoverageIndexMappings() {
        try {
            elasticsearchClient.indices().putMapping(m -> m
                    .index("coverage_method_search-*")
                    .ignoreUnavailable(true)
                    .properties("methodNamesText", Property.of(p -> p.text(t -> t))));
            elasticsearchClient.indices().putMapping(m -> m
                    .index("coverage_trends-*")
                    .ignoreUnavailable(true)
                    .properties("baseVersionNumber", Property.of(p -> p.keyword(k -> k)))
                    .properties("baseRepoCommitId", Property.of(p -> p.keyword(k -> k))));
            logger.info("Coverage index mappings ensured in Elasticsearch");
        } catch (Exception e) {
            logger.warn("Failed to update coverage index mappings: {}", e.getMessage());
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
