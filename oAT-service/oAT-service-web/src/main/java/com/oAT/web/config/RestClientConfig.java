package com.oAT.web.config;

import java.time.Duration;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

/**
 * Elasticsearch 客户端配置
 */
@Configuration
public class RestClientConfig extends AbstractElasticsearchConfiguration {

    @Value(value = "${elasticsearch.gatewayIpPorts}")
    private String gatewayIpPorts;

    @Value(value = "${elasticsearch.username}")
    private String username;

    @Value(value = "${elasticsearch.password}")
    private String password;

    @Override
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(gatewayIpPorts)
                .withConnectTimeout(Duration.ofSeconds(10))
                .withSocketTimeout(Duration.ofSeconds(30))
                .withBasicAuth(username, password)
                .build();
        return RestClients.create(clientConfiguration).rest();
    }

    @Bean
    public ElasticsearchRestTemplate restTemplate() {
        return new ElasticsearchRestTemplate(elasticsearchClient());
    }
}
