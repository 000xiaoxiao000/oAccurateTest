package com.oAT.web.coveragecore.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.oAT.web.common.UtilJson;
import org.springframework.util.Assert;

public class UniversalCoverageAliasReportRequest {
    private String commitId;
    private String versionNumber;
    private String branch;
    private String caseName;
    private String buildId;
    private String testStage;
    private Long timestamp;
    private String coverageData;

    public static UniversalCoverageAliasReportRequest parse(String requestBody) {
        Assert.hasText(requestBody, "请求体不能为空");
        try {
            JsonNode root = UtilJson.getObjectMapper().readTree(requestBody);
            UniversalCoverageAliasReportRequest request = new UniversalCoverageAliasReportRequest();
            request.setCommitId(text(root, "commitId", "commit_id", "commitSha"));
            request.setVersionNumber(text(root, "versionNumber", "version", "version_number"));
            request.setBranch(text(root, "branch", "repoBranch"));
            request.setCaseName(text(root, "caseName", "case_name"));
            request.setBuildId(text(root, "buildId", "build_id"));
            request.setTestStage(text(root, "testStage", "test_stage"));
            request.setTimestamp(longValue(root, "timestamp", "time"));

            JsonNode coverageNode = first(root, "coverageData", "coverage", "data", "profile");
            if (coverageNode == null) {
                request.setCoverageData(requestBody);
            } else if (coverageNode.isTextual()) {
                request.setCoverageData(coverageNode.asText());
            } else {
                request.setCoverageData(UtilJson.writeValueAsString(coverageNode));
            }
            return request;
        } catch (Exception ignored) {
            UniversalCoverageAliasReportRequest request = new UniversalCoverageAliasReportRequest();
            request.setCoverageData(requestBody);
            return request;
        }
    }

    private static JsonNode first(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null ? null : value.asText();
    }

    private static Long longValue(JsonNode node, String... names) {
        JsonNode value = first(node, names);
        return value == null || value.isNull() ? null : value.asLong();
    }

    public String getCommitId() { return commitId; }
    public void setCommitId(String commitId) { this.commitId = commitId; }
    public String getVersionNumber() { return versionNumber; }
    public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getBuildId() { return buildId; }
    public void setBuildId(String buildId) { this.buildId = buildId; }
    public String getTestStage() { return testStage; }
    public void setTestStage(String testStage) { this.testStage = testStage; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    public String getCoverageData() { return coverageData; }
    public void setCoverageData(String coverageData) { this.coverageData = coverageData; }
}
