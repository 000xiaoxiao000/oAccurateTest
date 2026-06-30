package com.oAT.web.api.snapshot;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService.CoverageFootprintSnapshot;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.UsecaseVo;

import java.util.List;

public class SystemSnapshotDetailPayload {
    private AppSummary app;
    private SystemSnapshotSummary snapshot;
    private List<LabelGroup.Label> labels;
    private List<String> selectedLabelNames;
    private List<ProjectMemberVo> members;
    private List<String> selectedPrincipalIds;
    private List<UsecaseVo> usecases;
    private List<UsecaseVo> allUsecases;
    private List<CoverageFootprintSnapshot> coverageFootprints;
    private List<DynamicItemSummary> dynamics;
    private String currentUserRole;

    public AppSummary getApp() { return app; }
    public void setApp(AppSummary app) { this.app = app; }
    public SystemSnapshotSummary getSnapshot() { return snapshot; }
    public void setSnapshot(SystemSnapshotSummary snapshot) { this.snapshot = snapshot; }
    public List<LabelGroup.Label> getLabels() { return labels; }
    public void setLabels(List<LabelGroup.Label> labels) { this.labels = labels; }
    public List<String> getSelectedLabelNames() { return selectedLabelNames; }
    public void setSelectedLabelNames(List<String> selectedLabelNames) { this.selectedLabelNames = selectedLabelNames; }
    public List<ProjectMemberVo> getMembers() { return members; }
    public void setMembers(List<ProjectMemberVo> members) { this.members = members; }
    public List<String> getSelectedPrincipalIds() { return selectedPrincipalIds; }
    public void setSelectedPrincipalIds(List<String> selectedPrincipalIds) { this.selectedPrincipalIds = selectedPrincipalIds; }
    public List<UsecaseVo> getUsecases() { return usecases; }
    public void setUsecases(List<UsecaseVo> usecases) { this.usecases = usecases; }
    public List<UsecaseVo> getAllUsecases() { return allUsecases; }
    public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
    public List<CoverageFootprintSnapshot> getCoverageFootprints() { return coverageFootprints; }
    public void setCoverageFootprints(List<CoverageFootprintSnapshot> coverageFootprints) { this.coverageFootprints = coverageFootprints; }
    public List<DynamicItemSummary> getDynamics() { return dynamics; }
    public void setDynamics(List<DynamicItemSummary> dynamics) { this.dynamics = dynamics; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
