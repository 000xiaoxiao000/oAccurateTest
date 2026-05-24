package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
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
import com.oAT.web.service.entity.SimpleRelationOption;
import com.oAT.web.service.entity.SnapshotVo;
import com.oAT.web.service.entity.UsecaseDetailVo;
import com.oAT.web.service.entity.UsecaseDirectoryVo;
import com.oAT.web.service.entity.UsecaseVo;
import com.oAT.web.service.entity.UserVo;
import org.apache.commons.lang3.ArrayUtils;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
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

@Controller
@RequestMapping({"/p/{projectId}/usecase", "/p/{projectId}/usecase/"})
public class UsecaseControl {

    @Autowired
    FrontendProperties frontendProperties;


    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer MARKDOWN_RENDERER = HtmlRenderer.builder().build();

    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    private UsecaseService usecaseService;
    @Autowired
    private UsecaseFileService usecaseFileService;
    @Autowired
    private SystemSnapshotService systemSnapshotService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    @Autowired
    private AppService appService;

    @Value("${oat.usecase.defect-link-template:}")
    private String defectLinkTemplate;

    @Value("${oat.usecase.prd-link-template:}")
    private String prdLinkTemplate;

    /**
     * 打开用例文档编辑页面
     */
    @RequestMapping("/new")
    public String openNewView(@PathVariable String projectId, @SessionAttribute UserVo user, String directory, Model model) {
        String suffix = StringUtils.hasText(directory) ? "?directory=" + directory : "";
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/usecases/new" + suffix);
    }

    @RequestMapping("/edit")
    public String openEditView(@PathVariable String projectId, @SessionAttribute UserVo user, String id, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/usecases/" + id + "/edit");
    }

    private String arrayToString(String[] labels) {
        StringBuffer sb = new StringBuffer();
        if (labels == null) {
            return "";
        }
        for (int i = 0; i < labels.length; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(labels[i]);
        }
        return sb.toString();
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

    private String arrayToMultiLine(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return String.join("\n", values);
    }

    @RequestMapping("/detail")
    public String openDetails(@PathVariable String projectId, String id, Model model, HttpServletRequest request) {
        if (System.currentTimeMillis() >= 0) {
            Boolean share = (Boolean) request.getAttribute("_share");
            if (Boolean.TRUE.equals(share)) {
                return "redirect:" + frontendProperties.url("/share/usecase/" + id);
            }
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/usecases/" + id);
        }
        Boolean share = (Boolean) request.getAttribute("_share");
        if (!Boolean.TRUE.equals(share)) {
            return "redirect:" + frontendProperties.url("/p/" + projectId + "/usecases/" + id);
        }

        UsecaseDetailVo usecase;
        try {
            usecase = usecaseService.getUsecaseDetail(projectId, id);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("missingUsecaseMessage", "要查看的用例不存在或已被删除");
            return openListView(projectId, "root", "updateTime", null, null, model);
        }

        model.addAttribute("usecase", usecase);
        UserVo lastUpdateAuthor = userService.getUser(usecase.getLastUpdateAuthor());
        model.addAttribute("lastUpdateAuthor", lastUpdateAuthor);

        if (!ObjectUtils.isEmpty(usecase.getSnapshots())) {
            List<SnapshotVo> snapshots = snapshotService.getByIds(usecase.getSnapshots());
            model.addAttribute("snapshots", snapshots);
        }

        if (!ObjectUtils.isEmpty(usecase.getSystemSnapshots())) {
            List<SimpleRelationOption> systemSnapshots = Arrays.stream(usecase.getSystemSnapshots())
                    .map(this::getSystemSnapshotOption)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            model.addAttribute("systemSnapshots", systemSnapshots);
        }

        if (!ObjectUtils.isEmpty(usecase.getDefects())) {
            model.addAttribute("defects", buildTextLinks(usecase.getDefects(), defectLinkTemplate));
        }

        if (!ObjectUtils.isEmpty(usecase.getPrdRequirements())) {
            model.addAttribute("prdRequirements", buildTextLinks(usecase.getPrdRequirements(), prdLinkTemplate));
        }

        if (!ObjectUtils.isEmpty(usecase.getLabels())) {
            List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.usecase, usecase.getLabels());
            model.addAttribute("labels", labels);
        }
        if (StringUtils.hasText(usecase.getContent())) {
            model.addAttribute("usecaseContent", renderMarkdown(usecase.getContent()));
        }

        return "redirect:" + frontendProperties.url("/p/" + projectId + "/usecases/" + id);
    }

    private String renderMarkdown(String markdown) {
        return MARKDOWN_RENDERER.render(MARKDOWN_PARSER.parse(markdown));
    }

    /**
     * 新增用例
     *
     * @return
     */
    @RequestMapping("/doSave")
    @ResponseBody
    public ResultNotified<String> doSave(@PathVariable String projectId, @SessionAttribute UserVo user, UsecaseVo usecase) {
        Assert.hasText(usecase.getTitle(), "用户名称不能为空");
        Assert.hasText(usecase.getDirectory(), "目录不能为空");
        usecase.setDefects(parseMultiLine(usecase.getDefectsText()));
        usecase.setPrdRequirements(parseMultiLine(usecase.getPrdRequirementsText()));
        ResultNotified<String> result;

        if (usecase.getId() == null) {
            usecase.setProjectId(projectId);
            UsecaseVo vo = usecaseService.doAdd(user.getId(), usecase);
            result = new ResultNotified<>(true, "用例新增成功");
            result.setData(vo.getId());
            return result;
        } else {
            usecaseService.doUpdate(user.getId(), usecase);
            result = new ResultNotified<>(true, "用例保存成功");
            return result;
        }
    }

    private String[] intersectExistingIds(String[] selectedIds, String[] currentIds) {
        if (selectedIds == null) {
            return null;
        }
        if (currentIds == null || currentIds.length == 0) {
            return selectedIds;
        }
        Set<String> currentSet = new LinkedHashSet<>(Arrays.asList(currentIds));
        return Arrays.stream(selectedIds)
                .filter(currentSet::contains)
                .toArray(String[]::new);
    }

    /**
     * 打开用例列表
     *
     * @param projectId
     * @param directory
     * @param model
     * @return
     */
    @RequestMapping("/list")
    public String openListView(@PathVariable String projectId, String directory, String sort, String keyword, String usecaseId, Model model) {
        StringBuilder target = new StringBuilder("/p/").append(projectId).append("/usecases");
        List<String> query = new ArrayList<>();
        if (StringUtils.hasText(directory) && !"root".equals(directory)) {
            query.add("directory=" + directory);
        }
        if (StringUtils.hasText(sort)) {
            query.add("sort=" + sort);
        }
        if (StringUtils.hasText(keyword)) {
            query.add("keyword=" + keyword);
        }
        if (!query.isEmpty()) {
            target.append("?").append(String.join("&", query));
        }
        return "redirect:" + frontendProperties.url(target.toString());
    }

    @RequestMapping("/doDelete")
    @ResponseBody
    public ResultNotified<String> doDeleteUsecase(@PathVariable String projectId, String id) {
        usecaseService.doDeleteUsecase(projectId, id);
        return new ResultNotified<>(true, "用例删除成功");
    }

    @RequestMapping("/openShare/{id}")
    @ResponseBody
    public ResultNotified<String> openShareUsecase(@PathVariable String projectId, @SessionAttribute UserVo user, @PathVariable String id) {
        usecaseService.setShareState(projectId, user.getId(), id, true);
        return new ResultNotified<>(true, "用例共享已开启");
    }

    @RequestMapping("/closeShare/{id}")
    @ResponseBody
    public ResultNotified<String> closeShareUsecase(@PathVariable String projectId, @SessionAttribute UserVo user, @PathVariable String id) {
        usecaseService.setShareState(projectId, user.getId(), id, false);
        return new ResultNotified<>(true, "用例共享已关闭");
    }

    @RequestMapping("/template/download")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        usecaseFileService.downloadTemplate(response);
    }

    @RequestMapping("/upload")
    @ResponseBody
    public ResultNotified<?> uploadUsecases(@PathVariable String projectId, @SessionAttribute UserVo user, String directory, MultipartFile file) throws IOException {
        com.oAT.web.service.entity.UsecaseImportResult importResult = usecaseFileService.importUsecases(projectId, user.getId(), directory, file);
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

    @RequestMapping("/export")
    public void exportUsecases(@PathVariable String projectId, String directory, String sort, String keyword, HttpServletResponse response) throws IOException {
        usecaseFileService.exportUsecases(projectId, directory, sort, keyword, response);
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

    @RequestMapping("/rebuildSearchData")
    @ResponseBody
    public ResultNotified<Integer> rebuildSearchData(@PathVariable String projectId, @SessionAttribute UserVo user) {
        int updated = usecaseService.rebuildUsecaseSearchData(projectId, user.getId());
        ResultNotified<Integer> result = new ResultNotified<>(true, "已回填用例检索数据，更新数量：" + updated);
        result.setData(updated);
        return result;
    }

    /**
     * 新增用例目录
     */
    @RequestMapping("/directory/new")
    @ResponseBody
    public ResultNotified<String> doCreateDirectory(@PathVariable String projectId, String parentId, String name) {
        //参数parentId不能为空
        Assert.isTrue(name != null, "父级用例路径不能为空");
        //参数dir.name不能为空
        Assert.hasText(name, "用例路径名称不能为空");
        usecaseService.createFolder(projectId, parentId, name);
        return new ResultNotified<>(true, "目录保存成功");
    }

    @RequestMapping("/directory/save")
    @ResponseBody
    public ResultNotified<String> doEditDirectory(String id, String parentId, String name) {
        //id不能为空
        Assert.hasText(id, "用例路径不能为空");
        //parentId不能为空
        Assert.hasText(parentId, "上层路径不能为空");
        //name不能为空
        Assert.hasText(name, "路径名称不能为空");
        usecaseService.updateFolder(id, parentId, name);
        return new ResultNotified<>(true, "目录保存成功");
    }

    private ResultNotified<DirectoryDeletePreview> buildDirectoryDeletePreviewResult(DirectoryDeleteResult serviceResult) {
        DirectoryDeletePreview preview = new DirectoryDeletePreview();
        preview.setDirectoryCount(serviceResult.getDirectoryCount());
        preview.setUsecaseCount(serviceResult.getUsecaseCount());
        preview.setRequiresCascade(serviceResult.isRequiresCascade());
        return new ResultNotified<>(serviceResult.isDeleted() || !serviceResult.isRequiresCascade(), serviceResult.getMessage(), preview);
    }

    @RequestMapping("/directory/deletePreview")
    @ResponseBody
    public ResultNotified<DirectoryDeletePreview> deleteDirectoryPreview(@PathVariable String projectId, String id, String parentId, String name) {
        Assert.hasText(id, "用例路径不能为空");
        Assert.hasText(parentId, "上层路径不能为空");
        Assert.hasText(name, "路径名称不能为空");
        return buildDirectoryDeletePreviewResult(usecaseService.previewDeleteDirectory(projectId, id));
    }


    @RequestMapping("/directory/del")
    @ResponseBody
    public ResultNotified<DirectoryDeletePreview> doDelDirectory(@PathVariable String projectId, String id, String parentId, String name, Boolean deleteUsecases) {
        Assert.hasText(id, "用例路径不能为空");
        Assert.hasText(parentId, "上层路径不能为空");
        Assert.hasText(name, "路径名称不能为空");
        return buildDirectoryDeletePreviewResult(usecaseService.deleteDirectory(projectId, id, parentId, name, Boolean.TRUE.equals(deleteUsecases)));
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
            UserVo user = userService.getUser(userId);
            if (user == null) {
                continue;
            }
            String displayName = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getName();
            if (StringUtils.hasText(displayName)) {
                result.put(userId, displayName);
            }
        }
        return result;
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
            String url = linkTemplate.replace("{id}", text);
            return new SimpleRelationOption(text, text, url, true);
        }
        return new SimpleRelationOption(text, text, null, false);
    }
}
