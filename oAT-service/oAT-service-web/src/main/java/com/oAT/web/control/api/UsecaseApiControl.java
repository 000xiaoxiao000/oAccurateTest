package com.oAT.web.control.api;

import com.oAT.web.control.entity.DirectoryDeletePreview;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.AppService;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.SystemSnapshotService;
import com.oAT.web.service.UsecaseFileService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.DirectoryDeleteResult;
import com.oAT.web.service.entity.LableType;
import com.oAT.web.service.entity.ProjectMemberVo;
import com.oAT.web.service.entity.ProjectVo;
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
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

@RestController
@RequestMapping("/api/projects/{projectId}/usecases")
public class UsecaseApiControl {

    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().build();

    private final SnapshotService snapshotService;
    private final UsecaseService usecaseService;
    private final UsecaseFileService usecaseFileService;
    private final SystemSnapshotService systemSnapshotService;
    private final ProjectService projectService;
    private final UserService userService;
    private final AppService appService;

    @Value("${oat.usecase.defect-link-template:}")
    private String defectLinkTemplate;

    @Value("${oat.usecase.prd-link-template:}")
    private String prdLinkTemplate;

    public UsecaseApiControl(SnapshotService snapshotService,
                             UsecaseService usecaseService,
                             UsecaseFileService usecaseFileService,
                             SystemSnapshotService systemSnapshotService,
                             ProjectService projectService,
                             UserService userService,
                             AppService appService) {
        this.snapshotService = snapshotService;
        this.usecaseService = usecaseService;
        this.usecaseFileService = usecaseFileService;
        this.systemSnapshotService = systemSnapshotService;
        this.projectService = projectService;
        this.userService = userService;
        this.appService = appService;
    }

    @GetMapping
    public ResultNotified<UsecaseListPayload> list(@PathVariable String projectId,
                                                   @SessionAttribute UserVo user,
                                                   @RequestParam(required = false) String directory,
                                                   @RequestParam(required = false) String sort,
                                                   @RequestParam(required = false) String keyword) {
        ensureProjectAccess(projectId, user);
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
        return new ResultNotified<>(true, "获取用例列表成功", payload);
    }

    @GetMapping("/bootstrap")
    public ResultNotified<UsecaseBootstrapPayload> bootstrap(@PathVariable String projectId,
                                                             @SessionAttribute UserVo user,
                                                             @RequestParam(required = false) String directory,
                                                             @RequestParam(required = false) String id) {
        ensureProjectAccess(projectId, user);
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
        return new ResultNotified<>(true, "获取用例编辑上下文成功", payload);
    }

    @GetMapping("/{id}")
    public ResultNotified<UsecaseDetailPayload> detail(@PathVariable String projectId,
                                                       @PathVariable String id,
                                                       @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
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
        if (!ObjectUtils.isEmpty(usecase.getLabels())) {
            payload.setLabels(projectService.getLables(projectId, LableType.usecase, usecase.getLabels()));
        }
        if (!ObjectUtils.isEmpty(usecase.getDefects())) {
            payload.setDefects(buildTextLinks(usecase.getDefects(), defectLinkTemplate));
        }
        if (!ObjectUtils.isEmpty(usecase.getPrdRequirements())) {
            payload.setPrdRequirements(buildTextLinks(usecase.getPrdRequirements(), prdLinkTemplate));
        }
        return new ResultNotified<>(true, "获取用例详情成功", payload);
    }

    @PostMapping("/save")
    public ResultNotified<String> save(@PathVariable String projectId,
                                       @SessionAttribute UserVo user,
                                       @RequestBody SaveUsecaseRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(request.getTitle(), "用例标题不能为空");
        Assert.hasText(request.getDirectory(), "目录不能为空");

        UsecaseVo usecase = new UsecaseVo();
        usecase.setId(StringUtils.hasText(request.getId()) ? request.getId() : null);
        usecase.setProjectId(projectId);
        usecase.setTitle(request.getTitle());
        usecase.setHeadImage(request.getHeadImage());
        usecase.setContent(request.getContent());
        usecase.setDirectory(request.getDirectory());
        usecase.setSnapshots(normalizeArray(request.getSnapshots()));
        usecase.setSystemSnapshots(normalizeArray(request.getSystemSnapshots()));
        usecase.setLabels(normalizeArray(request.getLabels()));
        usecase.setDefects(parseMultiLine(request.getDefectsText()));
        usecase.setPrdRequirements(parseMultiLine(request.getPrdRequirementsText()));
        usecase.setDefectsText(request.getDefectsText());
        usecase.setPrdRequirementsText(request.getPrdRequirementsText());

        if (!StringUtils.hasText(request.getId())) {
            UsecaseVo created = usecaseService.doAdd(user.getId(), usecase);
            return new ResultNotified<>(true, "用例新增成功", created.getId());
        }
        usecaseService.doUpdate(user.getId(), usecase);
        return new ResultNotified<>(true, "用例保存成功", request.getId());
    }

    @PostMapping("/{id}/delete")
    public ResultNotified<String> delete(@PathVariable String projectId,
                                         @PathVariable String id,
                                         @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        usecaseService.doDeleteUsecase(projectId, id);
        return new ResultNotified<>(true, "用例删除成功", id);
    }

    @PostMapping("/{id}/share")
    public ResultNotified<String> updateShare(@PathVariable String projectId,
                                              @PathVariable String id,
                                              @SessionAttribute UserVo user,
                                              @RequestBody ShareUsecaseRequest request) {
        ensureProjectAccess(projectId, user);
        boolean enabled = Boolean.TRUE.equals(request.getShare());
        usecaseService.setShareState(projectId, user.getId(), id, enabled);
        return new ResultNotified<>(true, enabled ? "用例共享已开启" : "用例共享已关闭", id);
    }

    @GetMapping("/template/download")
    public void downloadTemplate(@PathVariable String projectId,
                                 @SessionAttribute UserVo user,
                                 HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        usecaseFileService.downloadTemplate(response);
    }

    @PostMapping("/upload")
    public ResultNotified<?> uploadUsecases(@PathVariable String projectId,
                                            @SessionAttribute UserVo user,
                                            @RequestParam(required = false) String directory,
                                            @RequestParam("file") MultipartFile file) throws IOException {
        ensureProjectAccess(projectId, user);
        com.oAT.web.service.entity.UsecaseImportResult importResult = usecaseFileService.importUsecases(
                projectId,
                user.getId(),
                StringUtils.hasText(directory) ? directory : "root",
                file
        );
        if (importResult.hasErrors()) {
            ResultNotified<com.oAT.web.service.entity.UsecaseImportResult> result = new ResultNotified<>(false, "用例上传失败");
            result.setErrorMessage(buildImportErrorMessage(importResult));
            result.setData(importResult);
            return result;
        }
        ResultNotified<com.oAT.web.service.entity.UsecaseImportResult> result = new ResultNotified<>(true, "用例上传成功，导入 " + importResult.getSuccessCount() + " 条");
        result.setData(importResult);
        return result;
    }

    @GetMapping("/export")
    public void exportUsecases(@PathVariable String projectId,
                               @SessionAttribute UserVo user,
                               @RequestParam(required = false) String directory,
                               @RequestParam(required = false) String sort,
                               @RequestParam(required = false) String keyword,
                               HttpServletResponse response) throws IOException {
        ensureProjectAccess(projectId, user);
        usecaseFileService.exportUsecases(projectId, directory, sort, keyword, response);
    }

    @PostMapping("/rebuild-search-data")
    public ResultNotified<Integer> rebuildSearchData(@PathVariable String projectId,
                                                     @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        int updated = usecaseService.rebuildUsecaseSearchData(projectId, user.getId());
        ResultNotified<Integer> result = new ResultNotified<>(true, "已回填用例检索数据，更新数量：" + updated);
        result.setData(updated);
        return result;
    }

    @PostMapping("/directories/create")
    public ResultNotified<String> createDirectory(@PathVariable String projectId,
                                                  @SessionAttribute UserVo user,
                                                  @RequestBody CreateDirectoryRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(request.getName(), "目录名称不能为空");
        String parentId = StringUtils.hasText(request.getParentId()) ? request.getParentId() : "root";
        UsecaseDirectoryVo created = usecaseService.createFolder(projectId, parentId, request.getName().trim());
        return new ResultNotified<>(true, "目录创建成功", created.getId());
    }

    @PostMapping("/directories/{directoryId}/rename")
    public ResultNotified<String> renameDirectory(@PathVariable String projectId,
                                                  @PathVariable String directoryId,
                                                  @SessionAttribute UserVo user,
                                                  @RequestBody RenameDirectoryRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(request.getParentId(), "上层路径不能为空");
        Assert.hasText(request.getName(), "目录名称不能为空");
        usecaseService.updateFolder(directoryId, request.getParentId(), request.getName().trim());
        return new ResultNotified<>(true, "目录保存成功", directoryId);
    }

    @GetMapping("/directories/{directoryId}/delete-preview")
    public ResultNotified<DirectoryDeletePreview> deleteDirectoryPreview(@PathVariable String projectId,
                                                                         @PathVariable String directoryId,
                                                                         @SessionAttribute UserVo user) {
        ensureProjectAccess(projectId, user);
        return buildDirectoryDeletePreviewResult(usecaseService.previewDeleteDirectory(projectId, directoryId));
    }

    @PostMapping("/directories/{directoryId}/delete")
    public ResultNotified<DirectoryDeletePreview> deleteDirectory(@PathVariable String projectId,
                                                                  @PathVariable String directoryId,
                                                                  @SessionAttribute UserVo user,
                                                                  @RequestBody DeleteDirectoryRequest request) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(request.getParentId(), "上层路径不能为空");
        Assert.hasText(request.getName(), "目录名称不能为空");
        return buildDirectoryDeletePreviewResult(usecaseService.deleteDirectory(
                projectId,
                directoryId,
                request.getParentId(),
                request.getName(),
                Boolean.TRUE.equals(request.getDeleteUsecases())
        ));
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

    private ResultNotified<DirectoryDeletePreview> buildDirectoryDeletePreviewResult(DirectoryDeleteResult serviceResult) {
        DirectoryDeletePreview preview = new DirectoryDeletePreview();
        preview.setDirectoryCount(serviceResult.getDirectoryCount());
        preview.setUsecaseCount(serviceResult.getUsecaseCount());
        preview.setRequiresCascade(serviceResult.isRequiresCascade());
        return new ResultNotified<>(serviceResult.isDeleted() || !serviceResult.isRequiresCascade(), serviceResult.getMessage(), preview);
    }

    private String buildImportErrorMessage(com.oAT.web.service.entity.UsecaseImportResult importResult) {
        if (importResult == null || importResult.getErrors() == null || importResult.getErrors().isEmpty()) {
            return "用例上传失败";
        }
        return importResult.getErrors().stream()
                .limit(10)
                .map(error -> "第" + error.getRowNumber() + "行：" + error.getMessage())
                .collect(Collectors.joining("；"));
    }

    private String resolveUserRole(String projectId, UserVo user) {
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

    private List<FrontendContextApiControl.AppSummary> toAppSummaries(String projectId) {
        List<FrontendContextApiControl.AppSummary> result = new ArrayList<>();
        for (AppVo app : appService.getAppList(projectId)) {
            FrontendContextApiControl.AppSummary summary = new FrontendContextApiControl.AppSummary();
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

    private String[] parseMultiLine(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return Arrays.stream(text.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toArray(String[]::new);
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

    private String[] normalizeArray(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        return normalized.isEmpty() ? null : normalized.toArray(new String[0]);
    }

    private FrontendContextApiControl.UserSummary toUserSummary(UserVo user) {
        FrontendContextApiControl.UserSummary summary = new FrontendContextApiControl.UserSummary();
        summary.setId(user.getId());
        summary.setName(user.getName());
        summary.setNickname(user.getNickname());
        summary.setEmail(user.getEmail());
        summary.setHeader(user.getHeader());
        summary.setPhone(user.getPhone());
        summary.setReadme(user.getReadme());
        return summary;
    }

    public static class UsecaseListPayload {
        private String currentDirectory;
        private String currentDirectoryName;
        private String sort;
        private String keyword;
        private List<UsecaseVo> usecases;
        private List<UsecaseDirectoryVo> directories;
        private List<UsecaseDirectoryVo> directoryTiers;
        private Map<String, String> maintainerNameMap;
        private List<FrontendContextApiControl.AppSummary> apps;
        private String currentUserRole;

        public String getCurrentDirectory() { return currentDirectory; }
        public void setCurrentDirectory(String currentDirectory) { this.currentDirectory = currentDirectory; }
        public String getCurrentDirectoryName() { return currentDirectoryName; }
        public void setCurrentDirectoryName(String currentDirectoryName) { this.currentDirectoryName = currentDirectoryName; }
        public String getSort() { return sort; }
        public void setSort(String sort) { this.sort = sort; }
        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public List<UsecaseVo> getUsecases() { return usecases; }
        public void setUsecases(List<UsecaseVo> usecases) { this.usecases = usecases; }
        public List<UsecaseDirectoryVo> getDirectories() { return directories; }
        public void setDirectories(List<UsecaseDirectoryVo> directories) { this.directories = directories; }
        public List<UsecaseDirectoryVo> getDirectoryTiers() { return directoryTiers; }
        public void setDirectoryTiers(List<UsecaseDirectoryVo> directoryTiers) { this.directoryTiers = directoryTiers; }
        public Map<String, String> getMaintainerNameMap() { return maintainerNameMap; }
        public void setMaintainerNameMap(Map<String, String> maintainerNameMap) { this.maintainerNameMap = maintainerNameMap; }
        public List<FrontendContextApiControl.AppSummary> getApps() { return apps; }
        public void setApps(List<FrontendContextApiControl.AppSummary> apps) { this.apps = apps; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class UsecaseBootstrapPayload {
        private String currentDirectory;
        private String currentDirectoryName;
        private List<LabelGroup.Label> labels;
        private List<SnapshotVo> snapshots;
        private List<SimpleRelationOption> systemSnapshots;
        private UsecaseVo usecase;
        private List<String> selectedSnapshotIds;
        private List<String> selectedSystemSnapshotIds;
        private List<String> selectedLabelNames;
        private String defectsText;
        private String prdRequirementsText;
        private String currentUserRole;

        public String getCurrentDirectory() { return currentDirectory; }
        public void setCurrentDirectory(String currentDirectory) { this.currentDirectory = currentDirectory; }
        public String getCurrentDirectoryName() { return currentDirectoryName; }
        public void setCurrentDirectoryName(String currentDirectoryName) { this.currentDirectoryName = currentDirectoryName; }
        public List<LabelGroup.Label> getLabels() { return labels; }
        public void setLabels(List<LabelGroup.Label> labels) { this.labels = labels; }
        public List<SnapshotVo> getSnapshots() { return snapshots; }
        public void setSnapshots(List<SnapshotVo> snapshots) { this.snapshots = snapshots; }
        public List<SimpleRelationOption> getSystemSnapshots() { return systemSnapshots; }
        public void setSystemSnapshots(List<SimpleRelationOption> systemSnapshots) { this.systemSnapshots = systemSnapshots; }
        public UsecaseVo getUsecase() { return usecase; }
        public void setUsecase(UsecaseVo usecase) { this.usecase = usecase; }
        public List<String> getSelectedSnapshotIds() { return selectedSnapshotIds; }
        public void setSelectedSnapshotIds(List<String> selectedSnapshotIds) { this.selectedSnapshotIds = selectedSnapshotIds; }
        public List<String> getSelectedSystemSnapshotIds() { return selectedSystemSnapshotIds; }
        public void setSelectedSystemSnapshotIds(List<String> selectedSystemSnapshotIds) { this.selectedSystemSnapshotIds = selectedSystemSnapshotIds; }
        public List<String> getSelectedLabelNames() { return selectedLabelNames; }
        public void setSelectedLabelNames(List<String> selectedLabelNames) { this.selectedLabelNames = selectedLabelNames; }
        public String getDefectsText() { return defectsText; }
        public void setDefectsText(String defectsText) { this.defectsText = defectsText; }
        public String getPrdRequirementsText() { return prdRequirementsText; }
        public void setPrdRequirementsText(String prdRequirementsText) { this.prdRequirementsText = prdRequirementsText; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class UsecaseDetailPayload {
        private UsecaseDetailVo usecase;
        private FrontendContextApiControl.UserSummary lastUpdateAuthor;
        private List<SnapshotVo> snapshots;
        private List<SimpleRelationOption> systemSnapshots;
        private List<LabelGroup.Label> labels;
        private List<SimpleRelationOption> defects;
        private List<SimpleRelationOption> prdRequirements;
        private String contentHtml;
        private String currentUserRole;

        public UsecaseDetailVo getUsecase() { return usecase; }
        public void setUsecase(UsecaseDetailVo usecase) { this.usecase = usecase; }
        public FrontendContextApiControl.UserSummary getLastUpdateAuthor() { return lastUpdateAuthor; }
        public void setLastUpdateAuthor(FrontendContextApiControl.UserSummary lastUpdateAuthor) { this.lastUpdateAuthor = lastUpdateAuthor; }
        public List<SnapshotVo> getSnapshots() { return snapshots; }
        public void setSnapshots(List<SnapshotVo> snapshots) { this.snapshots = snapshots; }
        public List<SimpleRelationOption> getSystemSnapshots() { return systemSnapshots; }
        public void setSystemSnapshots(List<SimpleRelationOption> systemSnapshots) { this.systemSnapshots = systemSnapshots; }
        public List<LabelGroup.Label> getLabels() { return labels; }
        public void setLabels(List<LabelGroup.Label> labels) { this.labels = labels; }
        public List<SimpleRelationOption> getDefects() { return defects; }
        public void setDefects(List<SimpleRelationOption> defects) { this.defects = defects; }
        public List<SimpleRelationOption> getPrdRequirements() { return prdRequirements; }
        public void setPrdRequirements(List<SimpleRelationOption> prdRequirements) { this.prdRequirements = prdRequirements; }
        public String getContentHtml() { return contentHtml; }
        public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
        public String getCurrentUserRole() { return currentUserRole; }
        public void setCurrentUserRole(String currentUserRole) { this.currentUserRole = currentUserRole; }
    }

    public static class SaveUsecaseRequest {
        private String id;
        private String title;
        private String headImage;
        private String content;
        private String directory;
        private List<String> snapshots;
        private List<String> systemSnapshots;
        private List<String> labels;
        private String defectsText;
        private String prdRequirementsText;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getHeadImage() { return headImage; }
        public void setHeadImage(String headImage) { this.headImage = headImage; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public List<String> getSnapshots() { return snapshots; }
        public void setSnapshots(List<String> snapshots) { this.snapshots = snapshots; }
        public List<String> getSystemSnapshots() { return systemSnapshots; }
        public void setSystemSnapshots(List<String> systemSnapshots) { this.systemSnapshots = systemSnapshots; }
        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }
        public String getDefectsText() { return defectsText; }
        public void setDefectsText(String defectsText) { this.defectsText = defectsText; }
        public String getPrdRequirementsText() { return prdRequirementsText; }
        public void setPrdRequirementsText(String prdRequirementsText) { this.prdRequirementsText = prdRequirementsText; }
    }

    public static class ShareUsecaseRequest {
        private Boolean share;

        public Boolean getShare() { return share; }
        public void setShare(Boolean share) { this.share = share; }
    }

    public static class CreateDirectoryRequest {
        private String parentId;
        private String name;

        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class RenameDirectoryRequest {
        private String parentId;
        private String name;

        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class DeleteDirectoryRequest {
        private String parentId;
        private String name;
        private Boolean deleteUsecases;

        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getDeleteUsecases() { return deleteUsecases; }
        public void setDeleteUsecases(Boolean deleteUsecases) { this.deleteUsecases = deleteUsecases; }
    }
}
