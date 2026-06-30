package com.oAT.web.api.usecase;

import com.oAT.web.api.common.ApiSummaries.AppSummary;
import com.oAT.web.api.common.ApiSummaries.UserSummary;
import com.oAT.web.api.usecase.UsecaseApiPayloads.UsecaseBootstrapPayload;
import com.oAT.web.api.usecase.UsecaseApiPayloads.UsecaseDetailPayload;
import com.oAT.web.api.usecase.UsecaseApiPayloads.UsecaseListPayload;
import com.oAT.web.coveragecore.report.CoverageFootprintSnapshotService;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.SimpleRelationOption;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UsecaseDetailVo;
import com.oAT.web.service.entity.UsecaseDirectoryVo;
import com.oAT.web.service.entity.UsecaseVo;
import com.oAT.web.service.entity.UserVo;
import org.apache.commons.lang3.ArrayUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class UsecasePayloadService {

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().build();

    private final SnapshotService snapshotService;
    private final UsecaseService usecaseService;
    private final SystemSnapshotService systemSnapshotService;
    private final ProjectService projectService;
    private final UserService userService;
    private final AppService appService;
    private final CoverageFootprintSnapshotService coverageFootprintSnapshotService;

    @Value("${oat.usecase.defect-link-template:}")
    private String defectLinkTemplate;

    @Value("${oat.usecase.prd-link-template:}")
    private String prdLinkTemplate;

    public UsecasePayloadService(SnapshotService snapshotService,
                                 UsecaseService usecaseService,
                                 SystemSnapshotService systemSnapshotService,
                                 ProjectService projectService,
                                 UserService userService,
                                 AppService appService,
                                 CoverageFootprintSnapshotService coverageFootprintSnapshotService) {
        this.snapshotService = snapshotService;
        this.usecaseService = usecaseService;
        this.systemSnapshotService = systemSnapshotService;
        this.projectService = projectService;
        this.userService = userService;
        this.appService = appService;
        this.coverageFootprintSnapshotService = coverageFootprintSnapshotService;
    }

    public UsecaseListPayload buildListPayload(String projectId, UserVo user, String directory, String sort, String keyword) {
        String currentDirectory = StringUtils.hasText(directory) ? directory : "root";
        String effectiveSort = StringUtils.hasText(sort) ? sort : "updateTime";

        List<UsecaseVo> usecases = usecaseService.getUsecases(projectId, currentDirectory, effectiveSort, keyword);
        List<UsecaseDirectoryVo> directories = usecaseService.getDirectory(projectId, currentDirectory);

        UsecaseListPayload payload = new UsecaseListPayload();
        payload.setCurrentDirectory(currentDirectory);
        payload.setCurrentDirectoryName(resolveDirectoryName(projectId, currentDirectory));
        payload.setSort(effectiveSort);
        payload.setKeyword(keyword);
        payload.setUsecases(usecases);
        payload.setDirectories(directories);
        payload.setMaintainerNameMap(buildMaintainerNameMap(usecases));
        payload.setApps(toAppSummaries(projectId));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        if (!"root".equals(currentDirectory)) {
            List<UsecaseDirectoryVo> tiers = new ArrayList<>(usecaseService.getDirectoryTier(projectId, currentDirectory));
            Collections.reverse(tiers);
            payload.setDirectoryTiers(tiers);
        }
        return payload;
    }

    public UsecaseBootstrapPayload buildBootstrapPayload(String projectId, UserVo user, String directory, String id) {
        String currentDirectory = StringUtils.hasText(directory) ? directory : "root";
        UsecaseBootstrapPayload payload = new UsecaseBootstrapPayload();
        payload.setCurrentDirectory(currentDirectory);
        payload.setCurrentDirectoryName(resolveDirectoryName(projectId, currentDirectory));
        payload.setLabels(projectService.getLables(projectId, LableType.usecase));
        payload.setSnapshots(snapshotService.findSnapshot(projectId, user.getId()));
        payload.setSystemSnapshots(getSystemSnapshotOptions(projectId));
        payload.setCurrentUserRole(resolveUserRole(projectId, user));
        if (StringUtils.hasText(id)) {
            UsecaseVo usecase = usecaseService.getUsecase(projectId, id);
            payload.setUsecase(usecase);
            payload.setCurrentDirectory(usecase.getDirectory());
            payload.setCurrentDirectoryName(resolveDirectoryName(projectId, usecase.getDirectory()));
            payload.setSelectedSnapshotIds(optionalArray(usecase.getSnapshots()));
            payload.setSelectedSystemSnapshotIds(optionalArray(usecase.getSystemSnapshots()));
            payload.setSelectedLabelNames(optionalArray(usecase.getLabels()));
            payload.setDefectsText(arrayToMultiLine(usecase.getDefects()));
            payload.setPrdRequirementsText(arrayToMultiLine(usecase.getPrdRequirements()));
            payload.setSnapshots(mergeSnapshots(projectId, user.getId(), usecase));
            payload.setSystemSnapshots(mergeSystemSnapshots(projectId, usecase));
        }
        return payload;
    }

    public UsecaseDetailPayload buildDetailPayload(String projectId, String id, UserVo user) {
        UsecaseDetailVo usecase = usecaseService.getUsecaseDetail(projectId, id);
        UsecaseDetailPayload payload = new UsecaseDetailPayload();
        payload.setUsecase(usecase);
        payload.setContentHtml(StringUtils.hasText(usecase.getContent()) ? renderMarkdown(usecase.getContent()) : "");
        payload.setCurrentUserRole(resolveUserRole(projectId, user));

        UserVo lastUpdateAuthor = userService.getUser(usecase.getLastUpdateAuthor());
        if (lastUpdateAuthor != null) {
            payload.setLastUpdateAuthor(toUserSummary(lastUpdateAuthor));
        }
        if (!ObjectUtils.isEmpty(usecase.getSnapshots())) {
            payload.setSnapshots(snapshotService.getByIds(usecase.getSnapshots()));
        }
        if (!ObjectUtils.isEmpty(usecase.getSystemSnapshots())) {
            payload.setSystemSnapshots(Arrays.stream(usecase.getSystemSnapshots())
                    .map(this::getSystemSnapshotOption)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
        }
        if (!ObjectUtils.isEmpty(usecase.getCoverageFootprints())) {
            payload.setCoverageFootprints(coverageFootprintSnapshotService.findFootprintsByKeys(projectId, usecase.getCoverageFootprints()));
        }
        if (!ObjectUtils.isEmpty(usecase.getLabels())) {
            payload.setLabels(projectService.getLables(projectId, LableType.usecase, usecase.getLabels()));
        }
        if (!ObjectUtils.isEmpty(usecase.getDefects())) {
            payload.setDefects(buildTextLinks(usecase.getDefects(), defectLinkTemplate));
        }
        if (!ObjectUtils.isEmpty(usecase.getPrdRequirements())) {
            payload.setPrdRequirements(buildTextLinks(usecase.getPrdRequirements(), prdLinkTemplate));
        }
        return payload;
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

    private String resolveDirectoryName(String projectId, String directoryId) {
        if (!StringUtils.hasText(directoryId) || "root".equalsIgnoreCase(directoryId)) {
            return "ROOT";
        }
        try {
            return usecaseService.getDirectoryTier(projectId, directoryId).stream()
                    .findFirst()
                    .map(UsecaseDirectoryVo::getName)
                    .filter(StringUtils::hasText)
                    .orElse("未知目录");
        } catch (Exception e) {
            return "未知目录";
        }
    }

    private Map<String, String> buildMaintainerNameMap(List<UsecaseVo> usecases) {
        Map<String, String> result = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(usecases)) {
            return result;
        }
        Set<String> userIds = usecases.stream()
                .flatMap(usecase -> Stream.of(
                        usecase.getLastUpdateAuthor(),
                        ArrayUtils.isEmpty(usecase.getAuthors()) ? null : usecase.getAuthors()[0]
                ))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String userId : userIds) {
            UserVo currentUser = userService.getUser(userId);
            if (currentUser == null) {
                continue;
            }
            String displayName = StringUtils.hasText(currentUser.getNickname()) ? currentUser.getNickname() : currentUser.getName();
            if (StringUtils.hasText(displayName)) {
                result.put(userId, displayName);
            }
        }
        return result;
    }

    private List<AppSummary> toAppSummaries(String projectId) {
        List<AppSummary> result = new ArrayList<>();
        for (AppVo app : appService.getAppList(projectId)) {
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
            result.add(summary);
        }
        return result;
    }

    private List<SnapshotVo> mergeSnapshots(String projectId, String userId, UsecaseVo usecase) {
        List<SnapshotVo> selectedSnapshots = ArrayUtils.isNotEmpty(usecase.getSnapshots())
                ? snapshotService.getByIds(usecase.getSnapshots())
                : new ArrayList<>();
        if (ArrayUtils.isNotEmpty(usecase.getSnapshots())) {
            String[] deletedSnapshotIds = Stream.of(usecase.getSnapshots())
                    .filter(snapshotId -> selectedSnapshots.stream().noneMatch(snapshot -> snapshot.getId().equals(snapshotId)))
                    .toArray(String[]::new);
            if (ArrayUtils.isNotEmpty(deletedSnapshotIds)) {
                selectedSnapshots.addAll(snapshotService.getByIds(deletedSnapshotIds));
            }
        }
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, userId);
        snapshots.addAll(selectedSnapshots.stream()
                .filter(selected -> snapshots.stream().noneMatch(item -> item.getId().equals(selected.getId())))
                .collect(Collectors.toList()));
        return snapshots;
    }

    private List<SimpleRelationOption> mergeSystemSnapshots(String projectId, UsecaseVo usecase) {
        List<SimpleRelationOption> options = getSystemSnapshotOptions(projectId);
        if (ArrayUtils.isNotEmpty(usecase.getSystemSnapshots())) {
            for (String relationId : usecase.getSystemSnapshots()) {
                if (options.stream().noneMatch(item -> item.getId().equals(relationId))) {
                    SimpleRelationOption option = getSystemSnapshotOption(relationId);
                    if (option != null) {
                        options.add(option);
                    }
                }
            }
        }
        return options;
    }

    private List<SimpleRelationOption> getSystemSnapshotOptions(String projectId) {
        List<SimpleRelationOption> result = new ArrayList<>();
        for (AppVo app : appService.getAppList(projectId)) {
            for (SystemSnapshot snapshot : systemSnapshotService.findAll(projectId, app.getId())) {
                result.add(new SimpleRelationOption(
                        snapshot.getId(),
                        app.getName() + " / " + snapshot.getTitle(),
                        "/p/" + projectId + "/" + app.getId() + "/snapshot/detail/" + snapshot.getId(),
                        false
                ));
            }
        }
        return result;
    }

    private SimpleRelationOption getSystemSnapshotOption(String snapshotId) {
        if (!StringUtils.hasText(snapshotId)) {
            return null;
        }
        SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId);
        if (snapshot == null) {
            return null;
        }
        AppVo app = appService.getApp(snapshot.getAppId());
        if (app == null) {
            return null;
        }
        return new SimpleRelationOption(
                snapshot.getId(),
                app.getName() + " / " + snapshot.getTitle(),
                "/p/" + snapshot.getProjectId() + "/" + app.getId() + "/snapshot/detail/" + snapshot.getId(),
                false
        );
    }

    private List<SimpleRelationOption> buildTextLinks(String[] values, String linkTemplate) {
        return Arrays.stream(values)
                .map(value -> buildTextLink(value, linkTemplate))
                .collect(Collectors.toList());
    }

    private SimpleRelationOption buildTextLink(String value, String linkTemplate) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return new SimpleRelationOption(text, text, text, true);
        }
        if (StringUtils.hasText(linkTemplate) && linkTemplate.contains("{id}")) {
            return new SimpleRelationOption(text, text, linkTemplate.replace("{id}", text), true);
        }
        return new SimpleRelationOption(text, text, null, false);
    }

    private String renderMarkdown(String markdown) {
        return MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown));
    }

    private String arrayToMultiLine(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return String.join("\n", values);
    }

    private List<String> optionalArray(String[] values) {
        if (values == null || values.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(values);
    }

    private UserSummary toUserSummary(UserVo user) {
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
}
