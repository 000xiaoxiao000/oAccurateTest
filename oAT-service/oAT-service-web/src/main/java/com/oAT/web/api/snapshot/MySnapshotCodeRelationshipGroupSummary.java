package com.oAT.web.api.snapshot;

import java.util.List;

public class MySnapshotCodeRelationshipGroupSummary {
    private String requestUrl;
    private List<MySnapshotCodeRelationshipMethodSummary> methods;

    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
    public List<MySnapshotCodeRelationshipMethodSummary> getMethods() { return methods; }
    public void setMethods(List<MySnapshotCodeRelationshipMethodSummary> methods) { this.methods = methods; }
}
