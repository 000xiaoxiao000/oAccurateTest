package com.oAT.web.api.snapshot;

import com.oAT.web.api.common.ApiSummaries.AppSummary;
import com.oAT.web.api.common.ApiSummaries.UserSummary;
import com.oAT.web.common.DateUtil;
import com.oAT.web.esDao.SnapshotCommitMappingRepository;
import com.oAT.web.esDao.entity.ChangeLog;
import com.oAT.web.esDao.entity.Comment;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SnapshotCommitMapping;
import com.oAT.web.esDao.entity.SnapshotDirectory;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.ApiEndpointAnalysisService;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.ApiEndpointCoverageVo;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UsecaseVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SnapshotApiSupportService {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final ProjectService projectService;
    private final AppService appService;
    private final UsecaseService usecaseService;
    private final UserService userService;
    private final SnapshotCommitMappingRepository snapshotCommitMappingRepository;
    private final ApiEndpointAnalysisService apiEndpointAnalysisService;

    public SnapshotApiSupportService(ProjectService projectService,
                                     AppService appService,
                                     UsecaseService usecaseService,
                                     UserService userService,
                                     SnapshotCommitMappingRepository snapshotCommitMappingRepository,
                                     ApiEndpointAnalysisService apiEndpointAnalysisService) {
        this.projectService = projectService;
        this.appService = appService;
        this.usecaseService = usecaseService;
        this.userService = userService;
        this.snapshotCommitMappingRepository = snapshotCommitMappingRepository;
        this.apiEndpointAnalysisService = apiEndpointAnalysisService;
    }

    public ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    public void assertSystemSnapshotScope(String projectId, String appId, SystemSnapshot snapshot) {
        Assert.notNull(snapshot, "系统快照不存在");
        Assert.isTrue(projectId.equals(snapshot.getProjectId()), "系统快照不属于当前项目");
        Assert.isTrue(appId.equals(snapshot.getAppId()), "系统快照不属于当前应用");
    }

    public String resolveUserRole(String projectId, UserVo user) {
        List<ProjectMemberVo> members = projectService.getProjectMembers(projectId);
        for (ProjectMemberVo member : members) {
            if (user.getName() != null && user.getName().equals(member.getMemberName()) && member.getRole() != null) {
                return member.getRole().name();
            }
        }
        return ProjectMemberVo.Role.visitor.name();
    }

    public List<UsecaseVo> collectAllProjectUsecases(String projectId) {
        LinkedHashMap<String, UsecaseVo> result = new LinkedHashMap<>();
        Deque<String> directoryQueue = new ArrayDeque<>();
        directoryQueue.add("root");
        while (!directoryQueue.isEmpty()) {
            String directoryId = directoryQueue.poll();
            for (UsecaseVo usecaseVo : usecaseService.getUsecases(projectId, directoryId, "updateTime", null)) {
                result.putIfAbsent(usecaseVo.getId(), usecaseVo);
            }
            List<com.oAT.web.service.entity.UsecaseDirectoryVo> childDirectories = usecaseService.getDirectory(projectId, directoryId);
            if (childDirectories == null) {
                continue;
            }
            for (com.oAT.web.service.entity.UsecaseDirectoryVo directoryVo : childDirectories) {
                if (directoryVo != null && StringUtils.hasText(directoryVo.getId())) {
                    directoryQueue.add(directoryVo.getId());
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    public AppSummary toAppSummary(AppVo app) {
        AppSummary summary = new AppSummary();
        summary.setId(app.getId());
        summary.setName(app.getName());
        summary.setSrcName(app.getSrcName());
        summary.setDescribe(app.getDescribe());
        summary.setRange(app.getRange());
        summary.setOnlineCount(app.getOnlineCount());
        summary.setCurrentVersion(app.getCurrentVersion());
        summary.setCurrentBranch(app.getCurrentBranch());
        summary.setCurrentCommitId(app.getCurrentCommitId());
        summary.setRepoConfigured(StringUtils.hasText(app.getRepoAddress()));
        summary.setProbeAlertEnabled(Boolean.TRUE.equals(app.getProbeAlertEnabled()));
        return summary;
    }

    public List<AppSummary> toAppSummaries(String projectId) {
        return appService.getAppList(projectId).stream().map(this::toAppSummary).collect(Collectors.toList());
    }

    public SnapshotDirectorySummary toSnapshotDirectorySummary(SnapshotDirectory directory) {
        SnapshotDirectorySummary summary = new SnapshotDirectorySummary();
        summary.setId(directory.getId() == null ? null : String.valueOf(directory.getId()));
        summary.setName(directory.getName());
        summary.setParentId(directory.getParentId());
        return summary;
    }

    public List<SnapshotDirectorySummary> toSnapshotDirectorySummaries(List<SnapshotDirectory> directories) {
        if (directories == null) {
            return new ArrayList<>();
        }
        return directories.stream().filter(Objects::nonNull).map(this::toSnapshotDirectorySummary).collect(Collectors.toList());
    }

    public SystemSnapshotSummary toSystemSnapshotSummary(SystemSnapshot snapshot) {
        List<SnapshotCommitMapping> mappings = snapshotCommitMappingRepository.findBySnapshotId(snapshot.getId());
        return toSystemSnapshotSummary(snapshot, mappings.isEmpty() ? null : mappings.get(0));
    }

    public SystemSnapshotSummary toSystemSnapshotSummary(SystemSnapshot snapshot, SnapshotCommitMapping commitMapping) {
        SystemSnapshotSummary summary = new SystemSnapshotSummary();
        summary.setId(snapshot.getId());
        summary.setProjectId(snapshot.getProjectId());
        summary.setAppId(snapshot.getAppId());
        summary.setTraceId(snapshot.getTraceId());
        summary.setTitle(snapshot.getTitle());
        summary.setSubTitle(snapshot.getSubTitle());
        summary.setTopicImage(snapshot.getTopicImage());
        summary.setDescribe(snapshot.getDescribe());
        summary.setDirectory(snapshot.getDirectory());
        summary.setVersion(snapshot.getVersion());
        summary.setVersionCycle(snapshot.getVersionCycle());
        summary.setVersionLastUpdate(snapshot.getVersionLastUpdate());
        summary.setVersionLastUpdateText(formatDateTime(snapshot.getVersionLastUpdate()));
        summary.setVersionLastUpdateRelativeText(formatRelativeTime(snapshot.getVersionLastUpdate()));
        summary.setLabels(optionalArray(snapshot.getLabels()));
        summary.setPrincipals(optionalArray(snapshot.getPrincipals()));
        summary.setReportStatus(snapshot.getReportStatus());
        if (commitMapping != null) {
            summary.setVersionNumber(commitMapping.getVersionNumber());
            summary.setRepoBranch(commitMapping.getRepoBranch());
            summary.setRepoCommitId(commitMapping.getRepoCommitId());
        }
        summary.setCreateTime(snapshot.getCreateTime());
        summary.setUpdateTime(snapshot.getUpdateTime());
        return summary;
    }

    public void enrichSnapshotCommitInfo(List<SnapshotVo> snapshots) {
        Map<String, SnapshotCommitMapping> commitMappings = findLatestCommitMappingMap(
                snapshots.stream().map(SnapshotVo::getId).collect(Collectors.toList()));
        Map<String, AppVo> appMap = new HashMap<>();
        snapshots.forEach(snapshot -> {
            SnapshotCommitMapping mapping = commitMappings.get(snapshot.getId());
            if (mapping != null) {
                snapshot.setVersionNumber(mapping.getVersionNumber());
                snapshot.setRepoBranch(mapping.getRepoBranch());
                snapshot.setRepoCommitId(mapping.getRepoCommitId());
                return;
            }
            if (!StringUtils.hasText(snapshot.getAppId())) {
                return;
            }
            AppVo app = appMap.computeIfAbsent(snapshot.getAppId(), appService::getApp);
            if (app != null) {
                snapshot.setVersionNumber(app.getCurrentVersion());
                snapshot.setRepoBranch(app.getCurrentBranch());
                snapshot.setRepoCommitId(app.getCurrentCommitId());
            }
        });
    }

    public Map<String, SnapshotCommitMapping> findLatestCommitMappingMap(List<String> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SnapshotCommitMapping> result = new LinkedHashMap<>();
        snapshotCommitMappingRepository.findBySnapshotIds(snapshotIds).forEach(mapping -> {
            if (StringUtils.hasText(mapping.getSnapshotId())) {
                result.putIfAbsent(mapping.getSnapshotId(), mapping);
            }
        });
        return result;
    }

    public List<DynamicItemSummary> buildDynamics(SystemSnapshot snapshot) {
        List<String> ids = new ArrayList<>();
        if (snapshot.getChangeLogs() != null) {
            ids.addAll(Arrays.stream(snapshot.getChangeLogs()).map(ChangeLog::getUserId).collect(Collectors.toList()));
        }
        if (snapshot.getComments() != null) {
            ids.addAll(Arrays.stream(snapshot.getComments()).map(Comment::getUserId).collect(Collectors.toList()));
        }
        Map<String, UserVo> userMap = userService.getUsers(ids.toArray(new String[0])).stream()
                .collect(Collectors.toMap(UserVo::getId, it -> it, (left, right) -> left));

        List<DynamicItemSummary> result = new ArrayList<>();
        if (snapshot.getChangeLogs() != null) {
            for (ChangeLog log : snapshot.getChangeLogs()) {
                DynamicItemSummary item = new DynamicItemSummary();
                item.setType(log.getType());
                item.setTitle(resolveUserName(userMap.get(log.getUserId())));
                item.setDescribe(log.getContent());
                item.setTime(DateUtil.timeDifference(log.getTime()));
                item.setDate(log.getTime());
                result.add(item);
            }
        }
        if (snapshot.getComments() != null) {
            for (Comment comment : snapshot.getComments()) {
                DynamicItemSummary item = new DynamicItemSummary();
                item.setType("comment");
                item.setTitle(resolveUserName(userMap.get(comment.getUserId())));
                item.setDescribe(comment.getContent());
                item.setTime(DateUtil.timeDifference(comment.getTime()));
                item.setDate(comment.getTime());
                result.add(item);
            }
        }
        result.sort((a, b) -> {
            if (a.getDate() == null && b.getDate() == null) {
                return 0;
            }
            if (a.getDate() == null) {
                return 1;
            }
            if (b.getDate() == null) {
                return -1;
            }
            return b.getDate().compareTo(a.getDate());
        });
        return result;
    }

    public CoverageReportSummary toCoverageReportSummary(CoverageReportIndex report) {
        if (report == null) {
            return null;
        }
        CoverageReportSummary summary = new CoverageReportSummary();
        summary.setId(report.getId());
        summary.setAppId(report.getAppId());
        summary.setVersionNumber(report.getVersionNumber());
        summary.setRepoBranch(report.getRepoBranch());
        summary.setRepoCommitId(report.getRepoCommitId());
        summary.setCreateTime(report.getCreateTime());
        summary.setCreateTimeText(formatDateTime(report.getCreateTime()));
        summary.setLastProcessedTime(report.getLastProcessedTime());
        summary.setTotalClasses(report.getTotalClasses());
        summary.setCoveredClasses(report.getCoveredClasses());
        summary.setTotalMethods(report.getTotalMethods());
        summary.setCoveredMethods(report.getCoveredMethods());
        summary.setTotalBranches(report.getTotalBranches());
        summary.setCoveredBranches(report.getCoveredBranches());
        summary.setTotalBranchTargets(report.getTotalBranchTargets());
        summary.setCoveredBranchTargets(report.getCoveredBranchTargets());
        summary.setTotalLines(report.getTotalLines());
        summary.setCoveredLines(report.getCoveredLines());
        summary.setTotalComplexity(report.getTotalComplexity());
        summary.setSnapshotCount(report.getSnapshotCount());
        return summary;
    }

    public List<LabelGroup.Label> mergeSnapshotLabels(String projectId, List<SnapshotVo> snapshots) {
        List<LabelGroup.Label> configuredLabels = projectService.getLables(projectId, LableType.snapshot);
        Set<String> configuredLabelNames = configuredLabels.stream()
                .map(LabelGroup.Label::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Set<String> usedLabelNames = snapshots.stream()
                .flatMap(snapshot -> {
                    String[] labels = snapshot.getLabels();
                    return labels == null ? java.util.stream.Stream.empty() : Arrays.stream(labels);
                })
                .filter(StringUtils::hasText)
                .filter(labelName -> !configuredLabelNames.contains(labelName))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<LabelGroup.Label> result = new ArrayList<>(configuredLabels);
        for (String labelName : usedLabelNames) {
            LabelGroup.Label label = new LabelGroup.Label();
            label.setName(labelName);
            label.setColor("");
            result.add(label);
        }

        return result;
    }

    public String buildSnapshotApiCoverageSummaryText(List<SnapshotVo> snapshots) {
        String appId = snapshots.stream()
                .map(SnapshotVo::getAppId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (!StringUtils.hasText(appId)) {
            return "0 / 0";
        }
        List<String> traceIds = snapshots.stream()
                .map(SnapshotVo::getTraceId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        ApiEndpointCoverageVo coverage = apiEndpointAnalysisService.calculateCoverage(appId, traceIds);
        return coverage.getDisplayText();
    }

    public UserSummary toUserSummary(UserVo user) {
        if (user == null) {
            return null;
        }
        UserSummary summary = new UserSummary();
        summary.setId(user.getId());
        summary.setName(user.getName());
        summary.setNickname(user.getNickname());
        summary.setEmail(user.getEmail());
        summary.setHeader(user.getHeader());
        summary.setPhone(user.getPhone());
        summary.setReadme(user.getReadme());
        return summary;
    }

    public List<String> optionalArray(String[] values) {
        if (values == null || values.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(values);
    }

    public String formatDateTime(Date date) {
        return date == null ? null : new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
    }

    public String formatRelativeTime(Date date) {
        return date == null ? "" : DateUtil.timeDifference(date);
    }

    private String resolveUserName(UserVo user) {
        if (user == null) {
            return "未知用户";
        }
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getName();
    }
}
