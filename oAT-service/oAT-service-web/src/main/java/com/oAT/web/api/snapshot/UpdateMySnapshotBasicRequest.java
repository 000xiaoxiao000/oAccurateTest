package com.oAT.web.api.snapshot;

import java.util.List;

public class UpdateMySnapshotBasicRequest {
    private String name;
    private String describe;
    private List<String> labels;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
}
