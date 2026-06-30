package com.oAT.web.api.snapshot;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UsecaseVo;

import java.util.List;

public class MySnapshotDetailPayload {
    private SnapshotVo snapshot;
    private UserSummary createUser;
    private List<LabelGroup.Label> labels;
    private List<String> selectedLabelNames;
    private List<UsecaseVo> usecases;
    private List<UsecaseVo> allUsecases;
    private String shareUrl;
    private String currentUserRole;

    public SnapshotVo getSnapshot() { return snapshot; }
    public void setSnapshot(SnapshotVo snapshot) { this.snapshot = snapshot; }
    public UserSummary getCreateUser() { return createUser; }
    public void setCreateUser(UserSummary createUser) { this.createUser = createUser; }
    public List<LabelGroup.Label> getLabels() { return labels; }
    public void setLabels(List<LabelGroup.Label> labels) { this.labels = labels; }
    public List<String> getSelectedLabelNames() { return selectedLabelNames; }
    public void setSelectedLabelNames(List<String> selectedLabelNames) { this.selectedLabelNames = selectedLabelNames; }
    public List<UsecaseVo> getUsecases() { return usecases; }
    public void setUsecases(List<UsecaseVo> usecases) { this.usecases = usecases; }
    public List<UsecaseVo> getAllUsecases() { return allUsecases; }
    public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
    public String getShareUrl() { return shareUrl; }
    public void setShareUrl(String shareUrl) { this.shareUrl = shareUrl; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
