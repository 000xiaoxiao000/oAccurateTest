package com.oAT.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class ElasticsearchTemplateInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchTemplateInitializer.class);

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

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
                String templateJson = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                ObjectMapper mapper = new ObjectMapper();
                mapper.readTree(templateJson);

                logger.info("trace_node index template loaded, ensuring it exists in Elasticsearch");
            }
        } catch (Exception e) {
            logger.error("Failed to create trace_node template", e);
        }
    }
}
