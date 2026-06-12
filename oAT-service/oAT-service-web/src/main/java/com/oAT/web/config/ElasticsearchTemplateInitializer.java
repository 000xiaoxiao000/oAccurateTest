package com.oAT.web.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class ElasticsearchTemplateInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTemplateInitializer.class);
    private static final String TRACE_NODE_TEMPLATE_NAME = "trace_node_template";

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchTemplateInitializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeTemplates() {
        try {
            createTraceNodeTemplate();
            logger.info("Elasticsearch templates initialized successfully");
        } catch (Exception e) {
            logger.warn("Failed to initialize Elasticsearch templates: {}", e.getMessage());
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
}
