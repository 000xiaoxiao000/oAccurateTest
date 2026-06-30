package com.oAT.web.api.snapshot;

import java.util.List;

public class BatchBindUsecasesRequest {
    private List<String> snapshotIds;
    private List<String> usecaseIds;

    public List<String> getSnapshotIds() { return snapshotIds; }
    public void setSnapshotIds(List<String> snapshotIds) { this.snapshotIds = snapshotIds; }
    public List<String> getUsecaseIds() { return usecaseIds; }
    public void setUsecaseIds(List<String> usecaseIds) { this.usecaseIds = usecaseIds; }
}
