package com.oAT.relay;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oat.relay")
public class RelayProperties {

    private String targetBaseUrl = "http://127.0.0.1:8899";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
    private int maxBodySizeMb = 20;
    private String authToken = "";

    public String getTargetBaseUrl() {
        return targetBaseUrl;
    }

    public void setTargetBaseUrl(String targetBaseUrl) {
        this.targetBaseUrl = targetBaseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxBodySizeMb() {
        return maxBodySizeMb;
    }

    public void setMaxBodySizeMb(int maxBodySizeMb) {
        this.maxBodySizeMb = maxBodySizeMb;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}
