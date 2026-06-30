package com.oAT.web.api.snapshot;

import com.oAT.web.coveragecore.model.SourceCoveragePayload;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.service.AppService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UserVo;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MySnapshotPayloadService {

    private final SnapshotService snapshotService;
    private final UserService userService;
    private final AppService appService;
    private final UsecaseService usecaseService;
    private final SnapshotApiSupportService snapshotApiSupport;
    private final SnapshotCoverageReportService snapshotCoverageReportService;
    private final SnapshotMyReportAggregationService snapshotMyReportAggregationService;
    private final SnapshotTraceCodeRelationshipService snapshotTraceCodeRelationshipService;

    public MySnapshotPayloadService(SnapshotService snapshotService,
                                    UserService userService,
                                    AppService appService,
                                    UsecaseService usecaseService,
                                    SnapshotApiSupportService snapshotApiSupport,
                                    SnapshotCoverageReportService snapshotCoverageReportService,
                                    SnapshotMyReportAggregationService snapshotMyReportAggregationService,
                                    SnapshotTraceCodeRelationshipService snapshotTraceCodeRelationshipService) {
        this.snapshotService = snapshotService;
        this.userService = userService;
        this.appService = appService;
        this.usecaseService = usecaseService;
        this.snapshotApiSupport = snapshotApiSupport;
        this.snapshotCoverageReportService = snapshotCoverageReportService;
        this.snapshotMyReportAggregationService = snapshotMyReportAggregationService;
        this.snapshotTraceCodeRelationshipService = snapshotTraceCodeRelationshipService;
    }

    public MySnapshotListPayload buildListPayload(String projectId, UserVo user, String sort, String keyword, String labels) {
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), StringUtils.hasText(sort) ? sort : null, keyword);
        String[] labelFilters = StringUtils.hasText(labels) ? labels.split(",") : new String[0];
        if (ArrayUtils.isNotEmpty(labelFilters)) {
            snapshots = snapshots.stream()
                    .filter(snapshot -> containsAllLabels(snapshot.getLabels(), labelFilters))
                    .collect(Collectors.toList());
        }
        snapshotApiSupport.enrichSnapshotCommitInfo(snapshots);

        MySnapshotListPayload payload = new MySnapshotListPayload();
        payload.setSnapshots(snapshots);
        payload.setAllUsecases(snapshotApiSupport.collectAllProjectUsecases(projectId));
        payload.setSnapshotLabels(snapshotApiSupport.mergeSnapshotLabels(projectId, snapshots));
        payload.setApiCoverageSummaryText(snapshotApiSupport.buildSnapshotApiCoverageSummaryText(snapshots));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    public MySnapshotDetailPayload buildDetailPayload(String projectId, String snapshotId, UserVo user) {
        SnapshotVo snapshot = requireMySnapshot(projectId, snapshotId, "我的快照不存在");
        snapshotApiSupport.enrichSnapshotCommitInfo(Collections.singletonList(snapshot));

        MySnapshotDetailPayload payload = new MySnapshotDetailPayload();
        payload.setSnapshot(snapshot);
        payload.setCreateUser(snapshotApiSupport.toUserSummary(userService.getUser(snapshot.getCreateUser())));
        payload.setLabels(snapshotApiSupport.mergeSnapshotLabels(projectId, Collections.singletonList(snapshot)));
        payload.setSelectedLabelNames(snapshotApiSupport.optionalArray(snapshot.getLabels()));
        payload.setUsecases(usecaseService.getUsecasesBySnapshot(projectId, snapshotId));
        payload.setAllUsecases(snapshotApiSupport.collectAllProjectUsecases(projectId));
        payload.setShareUrl("/share/snapshot/" + snapshotId);
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    public MySnapshotReportPayload buildReportPayload(String projectId, String snapshotId, UserVo user) {
        SnapshotVo snapshot = requireMySnapshot(projectId, snapshotId, "快照不存在");
        MySnapshotReportAggregate aggregate = snapshotMyReportAggregationService.buildMySnapshotReportAggregate(snapshot);

        MySnapshotReportPayload payload = new MySnapshotReportPayload();
        payload.setSnapshot(snapshot);
        payload.setReport(aggregate.getSummary());
        payload.setClassStats(aggregate.getClassStats());
        payload.setCodeRelationships(snapshotTraceCodeRelationshipService.buildTraceCodeRelationships(
                snapshot.getTraceId(),
                snapshotMyReportAggregationService.resolveMySnapshotAppId(snapshot)));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    public MySnapshotCodePayload buildCodePayload(String projectId, String snapshotId, UserVo user, String className) {
        Assert.hasText(className, "className不能为空");
        SnapshotVo snapshot = requireMySnapshot(projectId, snapshotId, "快照不存在");
        String appId = snapshotMyReportAggregationService.resolveMySnapshotAppId(snapshot);
        Assert.hasText(appId, "无法识别当前快照所属应用");
        AppVo app = requireApp(appId);

        ClassCoverageIndex classCoverage = snapshotCoverageReportService.buildMySnapshotClassCoverage(appId, snapshot, className);
        MySnapshotCodePayload payload = new MySnapshotCodePayload();
        payload.setSnapshot(snapshot);
        payload.setApp(snapshotApiSupport.toAppSummary(app));
        payload.setClassName(className);
        payload.setMethods(snapshotCoverageReportService.toMethodCoverageSummaries(classCoverage.getMethods()));
        SourceCoveragePayload sourceCoverage = snapshotCoverageReportService.toSourceCoveragePayload(
                classCoverage,
                snapshotCoverageReportService.toSourceReportContext(app, snapshot),
                app);
        payload.setSourceCoverage(sourceCoverage);
        payload.setColoredSourceHtml(snapshotCoverageReportService.toLegacyColoredSource(sourceCoverage));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    public MySnapshotCodeReportPayload buildCodeReportPayload(String projectId, UserVo user, String sort, String keyword) {
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), StringUtils.hasText(sort) ? sort : null, keyword);
        MySnapshotCodeReportAggregate aggregate = snapshotMyReportAggregationService.buildMySnapshotsCodeReportAggregate(snapshots);

        MySnapshotCodeReportPayload payload = new MySnapshotCodeReportPayload();
        payload.setAppId(aggregate.getAppId());
        payload.setReport(aggregate.getSummary());
        payload.setClassStats(aggregate.getClassStats());
        payload.setCodeRelationships(aggregate.getCodeRelationships());
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    public MySnapshotAggregateCodePayload buildAggregateCodePayload(String projectId, UserVo user, String appId, String className) {
        Assert.hasText(className, "className不能为空");
        AppVo app = requireApp(appId);
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId(), null);
        ClassCoverageIndex classCoverage = snapshotCoverageReportService.buildMySnapshotsClassCoverage(appId, snapshots, className);

        MySnapshotAggregateCodePayload payload = new MySnapshotAggregateCodePayload();
        payload.setApp(snapshotApiSupport.toAppSummary(app));
        payload.setClassName(className);
        payload.setMethods(snapshotCoverageReportService.toMethodCoverageSummaries(classCoverage.getMethods()));
        SourceCoveragePayload sourceCoverage = snapshotCoverageReportService.toSourceCoveragePayload(
                classCoverage,
                snapshotCoverageReportService.toSourceReportContext(app, null),
                app);
        payload.setSourceCoverage(sourceCoverage);
        payload.setColoredSourceHtml(snapshotCoverageReportService.toLegacyColoredSource(sourceCoverage));
        payload.setCurrentUserRole(snapshotApiSupport.resolveUserRole(projectId, user));
        return payload;
    }

    private SnapshotVo requireMySnapshot(String projectId, String snapshotId, String missingMessage) {
        SnapshotVo snapshot = snapshotService.get(snapshotId);
        Assert.notNull(snapshot, missingMessage);
        if (snapshot.getProjectId() != null && !projectId.equals(snapshot.getProjectId())) {
            throw new IllegalArgumentException(String.format(
                    "快照不属于当前项目 (URL projectId: %s, 快照 projectId: %s, snapshotId: %s)",
                    projectId, snapshot.getProjectId(), snapshotId));
        }
        return snapshot;
    }

    private AppVo requireApp(String appId) {
        AppVo app = appService.getApp(appId);
        Assert.notNull(app, "应用不存在");
        return app;
    }

    private boolean containsAllLabels(String[] source, String[] filters) {
        if (ArrayUtils.isEmpty(filters)) {
            return true;
        }
        if (ArrayUtils.isEmpty(source)) {
            return false;
        }
        Set<String> labelSet = java.util.Arrays.stream(source)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return java.util.Arrays.stream(filters)
                .filter(StringUtils::hasText)
                .allMatch(labelSet::contains);
    }
}
