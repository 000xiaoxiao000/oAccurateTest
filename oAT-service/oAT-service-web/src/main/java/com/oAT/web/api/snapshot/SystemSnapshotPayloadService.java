package com.oAT.web.api.snapshot;

import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.SnapshotCommitMapping;
import com.oAT.web.esDao.entity.SnapshotDirectory;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.UserVo;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemSnapshotPayloadService {

    private final AppService appService;
    private final SystemSnapshotService systemSnapshotService;
    private final ProjectService projectService;
    private final UsecaseService usecaseService;
    private final SnapshotApiSupportService snapshotApiSupport;
    private final SnapshotCoverageReportService snapshotCoverageReportService;
    private final SnapshotTraceCodeRelationshipService snapshotTraceCodeRelationshipService;
    private final CoverageFootprintSnapshotService coverageFootprintSnapshotService;

    public SystemSnapshotPayloadService(AppService appService,
                                        SystemSnapshotService systemSnapshotService,
                                        ProjectService projectService,
                                        UsecaseService usecaseService,
                                        SnapshotApiSupportService snapshotApiSupport,
                                        SnapshotCoverageReportService snapshotCoverageReportService,
                                        SnapshotTraceCodeRelationshipService snapshotTraceCodeRelationshipService,
                                        CoverageFootprintSnapshotService coverageFootprintSnapshotService) {
        this.appService = appService;
        this.systemSnapshotService = systemSnapshotService;
        this.projectService = projectService;
        this.usecaseService = usecaseService;
        this.snapshotApiSupport = snapshotApiSupport;
        this.snapshotCoverageReportService = snapshotCoverageReportService;
        this.snapshotTraceCodeRelationshipService = snapshotTraceCodeRelationshipService;
        this.coverageFootprintSnapshotService = coverageFootprintSnapshotService;
    }

    public SystemSnapshotListPayload buildListPayload(String projectId,
                                                      String appId,
                                                      UserVo user,
                                                      String directoryId,
                                                      String sort,
                                                      String keyword) {
        AppVo app = requireApp(appId);
        String currentDirectory = StringUtils.hasText(directoryId) ? directoryId : "root";
        String effectiveSort = StringUtils.hasText(sort) ? sort : "updateTime";

        List<SnapshotDirectory> dirs = Arrays.stream(ObjectUtils.isEmpty(app.getSnapshotDirs()) ? new SnapshotDirectory[0] : app.getSnapshotDirs())
                .filter(dir -> currentDirectory.equals(dir.getParentId()))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .collect(Collectors.toList());

        List<SystemSnapshot> snapshots = new ArrayList<>(systemSnapshotService.findBy(projectId, appId, currentDirectory, keyword));
        snapshots = snapshots.stream().sorted((a, b) -> {
            if ("name".equals(effectiveSort)) {
                return safeString(a.getTitle()).compareToIgnoreCase(safeString(b.getTitle()));
            }
            Date left = a.getVersionLastUpdate();
            Date right = b.getVersionLastUpdate();
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            return right.compareTo(left);
        }).collect(Collectors.toList());

        SystemSnapshotListPayload payload = new SystemSnapshotListPayload();
        payload.setApp(snapshotApiSupport.toAppSummary(app));
        payload.setApps(snapshotApiSupport.toAppSummaries(projectId));
        payload.setCurrentDirectory(currentDirectory);
        payload.setSort(effectiveSort);
        payload.setKeyword(keyword);
        payload.setDirectories(dirs.stream().map(snapshotApiSupport::toSnapshotDirectorySummary).collect(Collectors.toList()));
        payload.setDirectoryTiers(snapshotApiSupport.toSnapshotDirectorySummaries(appService.getDirectoryTiers(appId, currentDirectory)));
        Map<String, SnapshotCommitMapping> commitMappings = snapshotApiSupport.findLatestCommitMappingMap(
                snapshots.stream().map(SystemSnapshot::getId).collect(Collectors.toList()));
        payload.setSnapshots(snapshots.stream()
                .map(snapshot -> snapshotApiSupport.toSystemSnapshotSummary(snapshot, commitMappings.get(snapshot.getId())))
                .collect(Collectors.toList()));
        payload.setMembers(projectService.getProjectMembers(projectId));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        payload.setAllUsecases(snapshotApiSupport.collectAllProjectUsecases(projectId));
        return payload;
    }

    public SystemSnapshotDetailPayload buildDetailPayload(String projectId, String appId, String snapshotId, UserVo user) {
        AppVo app = requireApp(appId);
        SystemSnapshot snapshot = requireScopedSnapshot(projectId, appId, snapshotId);
        SystemSnapshotSummary summary = snapshotApiSupport.toSystemSnapshotSummary(snapshot);

        SystemSnapshotDetailPayload payload = new SystemSnapshotDetailPayload();
        payload.setApp(snapshotApiSupport.toAppSummary(app));
        payload.setSnapshot(summary);
        payload.setLabels(projectService.getLables(projectId, LableType.snapshot));
        payload.setSelectedLabelNames(snapshotApiSupport.optionalArray(snapshot.getLabels()));
        payload.setMembers(projectService.getProjectMembers(projectId));
        payload.setSelectedPrincipalIds(snapshotApiSupport.optionalArray(snapshot.getPrincipals()));
        payload.setUsecases(usecaseService.getUsecasesBySystemSnapshot(projectId, snapshotId));
        payload.setAllUsecases(snapshotApiSupport.collectAllProjectUsecases(projectId));
        payload.setCoverageFootprints(coverageFootprintSnapshotService.listFootprints(
                projectId,
                appId,
                null,
                summary.getVersionNumber(),
                summary.getRepoCommitId()));
        payload.setDynamics(snapshotApiSupport.buildDynamics(snapshot));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    public SystemSnapshotReportPayload buildReportPayload(String projectId, String appId, String snapshotId, UserVo user) {
        AppVo app = requireApp(appId);
        SystemSnapshot snapshot = requireScopedSnapshot(projectId, appId, snapshotId);
        SystemSnapshotSummary summary = snapshotApiSupport.toSystemSnapshotSummary(snapshot);

        SystemSnapshotReportPayload payload = new SystemSnapshotReportPayload();
        payload.setApp(snapshotApiSupport.toAppSummary(app));
        payload.setSnapshot(summary);
        payload.setReport(snapshotApiSupport.toCoverageReportSummary(snapshot.getCoverageReport()));
        payload.setClassStats(snapshotCoverageReportService.buildSystemSnapshotClassStats(app.getId(), snapshot));
        payload.setCodeRelationships(snapshotTraceCodeRelationshipService.buildTraceCodeRelationships(snapshot.getTraceId(), app.getId()));
        payload.setCoverageFootprints(matchingFootprints(projectId, appId, summary));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    public SystemSnapshotCodePayload buildCodePayload(String projectId,
                                                      String appId,
                                                      String snapshotId,
                                                      UserVo user,
                                                      String className) {
        Assert.hasText(className, "className不能为空");
        AppVo app = requireApp(appId);
        SystemSnapshot snapshot = requireScopedSnapshot(projectId, appId, snapshotId);
        SystemSnapshotSummary summary = snapshotApiSupport.toSystemSnapshotSummary(snapshot);

        ClassCoverageIndex classCoverage = snapshotCoverageReportService.buildSystemSnapshotClassCoverage(app.getId(), snapshot, className);
        SystemSnapshotCodePayload payload = new SystemSnapshotCodePayload();
        payload.setApp(snapshotApiSupport.toAppSummary(app));
        payload.setSnapshot(summary);
        payload.setClassName(className);
        payload.setMethods(snapshotCoverageReportService.toMethodCoverageSummaries(classCoverage.getMethods()));
        SourceCoveragePayload sourceCoverage = snapshotCoverageReportService.toSourceCoveragePayload(classCoverage, snapshot.getCoverageReport(), app);
        payload.setSourceCoverage(sourceCoverage);
        payload.setColoredSourceHtml(snapshotCoverageReportService.toLegacyColoredSource(sourceCoverage));
        payload.setCoverageFootprints(matchingFootprints(projectId, appId, summary));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    private List<CoverageFootprintSnapshotService.CoverageFootprintSnapshot> matchingFootprints(String projectId,
                                                                                               String appId,
                                                                                               SystemSnapshotSummary summary) {
        return coverageFootprintSnapshotService.listFootprints(
                projectId,
                appId,
                null,
                summary == null ? null : summary.getVersionNumber(),
                summary == null ? null : summary.getRepoCommitId());
    }

    private AppVo requireApp(String appId) {
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        return app;
    }

    private SystemSnapshot requireScopedSnapshot(String projectId, String appId, String snapshotId) {
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        snapshotApiSupport.assertSystemSnapshotScope(projectId, appId, snapshot);
        return snapshot;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
