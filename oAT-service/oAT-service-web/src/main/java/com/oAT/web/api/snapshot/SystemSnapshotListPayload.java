package com.oAT.web.api.snapshot;

import com.oAT.web.api.common.ApiSummaries.*;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.UsecaseVo;

import java.util.List;

public class SystemSnapshotListPayload {
    private AppSummary app;
    private List<AppSummary> apps;
    private String currentDirectory;
    private String sort;
    private String keyword;
    private List<SnapshotDirectorySummary> directories;
    private List<SnapshotDirectorySummary> directoryTiers;
    private List<SystemSnapshotSummary> snapshots;
    private List<UsecaseVo> allUsecases;
    private List<ProjectMemberVo> members;
    private String currentUserRole;

    public AppSummary getApp() { return app; }
    public void setApp(AppSummary app) { this.app = app; }
    public List<AppSummary> getApps() { return apps; }
    public void setApps(List<AppSummary> apps) { this.apps = apps; }
    public String getCurrentDirectory() { return currentDirectory; }
    public void setCurrentDirectory(String currentDirectory) { this.currentDirectory = currentDirectory; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public List<SnapshotDirectorySummary> getDirectories() { return directories; }
    public void setDirectories(List<SnapshotDirectorySummary> directories) { this.directories = directories; }
    public List<SnapshotDirectorySummary> getDirectoryTiers() { return directoryTiers; }
    public void setDirectoryTiers(List<SnapshotDirectorySummary> directoryTiers) { this.directoryTiers = directoryTiers; }
    public List<SystemSnapshotSummary> getSnapshots() { return snapshots; }
    public void setSnapshots(List<SystemSnapshotSummary> snapshots) { this.snapshots = snapshots; }
    public List<UsecaseVo> getAllUsecases() { return allUsecases; }
    public void setAllUsecases(List<UsecaseVo> allUsecases) { this.allUsecases = allUsecases; }
    public List<ProjectMemberVo> getMembers() { return members; }
    public void setMembers(List<ProjectMemberVo> members) { this.members = members; }
    public String getCurrentUserRole() { return currentUserRole; }
    public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
}
