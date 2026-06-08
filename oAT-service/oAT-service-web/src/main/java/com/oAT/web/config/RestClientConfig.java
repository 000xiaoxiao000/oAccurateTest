package com.oAT.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

import java.time.Duration;

/**
 * Elasticsearch 客户端配置
 */
@Configuration
public class RestClientConfig extends ElasticsearchConfiguration {

    @Value(value = "${elasticsearch.gatewayIpPorts}")
    private String gatewayIpPorts;

    @Value(value = "${elasticsearch.username}")
    private String username;

    @Value(value = "${elasticsearch.password}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        if (username != null && !username.isBlank()) {
            return ClientConfiguration.builder()
                    .connectedTo(gatewayIpPorts)
                    .withConnectTimeout(Duration.ofSeconds(10))
                    .withSocketTimeout(Duration.ofSeconds(30))
                    .withBasicAuth(username, password)
                    .build();
        } else {
            return ClientConfiguration.builder()
                    .connectedTo(gatewayIpPorts)
                    .withConnectTimeout(Duration.ofSeconds(10))
                    .withSocketTimeout(Duration.ofSeconds(30))
                    .build();
        }
    }
}
