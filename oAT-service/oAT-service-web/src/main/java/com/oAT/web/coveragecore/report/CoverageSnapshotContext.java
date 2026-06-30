package com.oAT.web.coveragecore.report;

import java.util.ArrayList;
import java.util.List;

public class CoverageSnapshotContext {
    private List<String> snapshotIds = new ArrayList<>();
    private List<String> traceIds = new ArrayList<>();
    private String snapshotFingerprint;
    private String lastSnapshotTime;

    public List<String> getSnapshotIds() { return snapshotIds; }
    public void setSnapshotIds(List<String> snapshotIds) { this.snapshotIds = snapshotIds; }
    public List<String> getTraceIds() { return traceIds; }
    public void setTraceIds(List<String> traceIds) { this.traceIds = traceIds; }
    public String getSnapshotFingerprint() { return snapshotFingerprint; }
    public void setSnapshotFingerprint(String snapshotFingerprint) { this.snapshotFingerprint = snapshotFingerprint; }
    public String getLastSnapshotTime() { return lastSnapshotTime; }
    public void setLastSnapshotTime(String lastSnapshotTime) { this.lastSnapshotTime = lastSnapshotTime; }
}
