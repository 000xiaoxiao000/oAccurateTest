package com.oAT.web.api.snapshot;

import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UsecaseVo;

import java.util.List;

public class MySnapshotListPayload {
    private List<SnapshotVo> snapshots;
    private List<UsecaseVo> allUsecases;
    private List<LabelGroup.Label> snapshotLabels;
    private String apiCoverageSummaryText;
    private String currentUserRole;

    public List<SnapshotVo> getSnapshots() { return snapshots; }
    public void setSnapshots(List<SnapshotVo> snapshots) { this.snapshots = snapshots; }
    public List<UsecaseVo> getAllUsecases() { return allUsecases; }
    public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
    public List<LabelGroup.Label> getSnapshotLabels() { return snapshotLabels; }
    public void setSnapshotLabels(List<LabelGroup.Label> snapshotLabels) { this.snapshotLabels = snapshotLabels; }
    public String getApiCoverageSummaryText() { return apiCoverageSummaryText; }
    public void setApiCoverageSummaryText(String apiCoverageSummaryText) { this.apiCoverageSummaryText = apiCoverageSummaryText; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
