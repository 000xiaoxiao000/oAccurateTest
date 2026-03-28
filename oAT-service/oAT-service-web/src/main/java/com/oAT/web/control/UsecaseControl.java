package com.oAT.web.control;

import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.LabelGroup;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.UserService;
import com.oAT.web.service.entity.*;
import org.apache.commons.lang3.ArrayUtils;
import org.pegdown.PegDownProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Controller
@RequestMapping("/p/{projectId}/usecase/")
public class UsecaseControl {

    @Autowired
    private SnapshotService snapshotService;
    @Autowired
    private UsecaseService usecaseService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private UserService userService;

    /**
     * 打开用例文档编辑页面
     */
    @RequestMapping("/new")
    public String openNewView(@PathVariable String projectId, @SessionAttribute UserVo user, String directory, Model model) {
        directory = directory == null ? "root" : directory;
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId());
        List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.usecase);
        model.addAttribute("snapshots", snapshots);
        model.addAttribute("currentDir", directory);
        model.addAttribute("labels", labels);
        return "/usecase/usecaseNew";
    }

    @RequestMapping("/edit")
    public String openEditView(@PathVariable String projectId, @SessionAttribute UserVo user, String id, Model model) {
        UsecaseVo usecase = usecaseService.getUsecase(projectId, id);
        Assert.notNull(usecase, "not fount usecase by id. id=" + id);
        // 获取当前用户下所有的快照
        List<SnapshotVo> snapshots = snapshotService.findSnapshot(projectId, user.getId());
        if (ArrayUtils.isNotEmpty(usecase.getSnapshots())) {
            // 找出被删除的快照ID
            String[] deleteByIds =
                    Stream.of(usecase.getSnapshots()).filter(s -> snapshots.stream().noneMatch((t -> t.getId().equals(s)))).collect(Collectors.toList()).toArray(new String[0]);
            // 已被删除的快照同样需要加入到 选择项当中
            if (ArrayUtils.isNotEmpty(deleteByIds)) {
                snapshots.addAll(snapshotService.getByIds(deleteByIds));
            }
        }

        // 获取当前项目下所有关于用例的所有标签
        List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.usecase);
        model.addAttribute("snapshots", snapshots);
        model.addAttribute("usecase", usecase);
        model.addAttribute("labels", labels);
        model.addAttribute("selectLabels", arrayToString(usecase.getLabels()));// 已选中的节点
        model.addAttribute("selectSnapshots", arrayToString(usecase.getSnapshots()));//已选中的快照

        return "/usecase/usecaseEdit";
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

    @RequestMapping("/detail")
    public String openDetails(@PathVariable String projectId, String id, Model model) {
        UsecaseDetailVo usecase = usecaseService.getUsecaseDetail(projectId, id);

        model.addAttribute("usecase", usecase);
        UserVo lastUpdateAuthor = userService.getUser(usecase.getLastUpdateAuthor());
        model.addAttribute("lastUpdateAuthor", lastUpdateAuthor);

        // 获取快照
        if (!ObjectUtils.isEmpty(usecase.getSnapshots())) {
            List<SnapshotVo> snapshots = snapshotService.getByIds(usecase.getSnapshots());
            model.addAttribute("snapshots", snapshots);
        }

        // 获取当前项目下的标签
        if (!ObjectUtils.isEmpty(usecase.getLabels())) {
            List<LabelGroup.Label> labels = projectService.getLables(projectId, LableType.usecase, usecase.getLabels());
            model.addAttribute("labels", labels);
        }
        if (StringUtils.hasText(usecase.getContent())) {
            // markdown 转html
            String html = new PegDownProcessor().markdownToHtml(usecase.getContent());
            model.addAttribute("usecaseContent", html);
        }

        return "/usecase/usecaseDetail";
    }

    /**
     * 新增用例
     *
     * @return
     */
    @RequestMapping("/doSave")
    @ResponseBody
    public ResultNotified doSave(@PathVariable String projectId, @SessionAttribute UserVo user, UsecaseVo usecase) {
        Assert.hasText(usecase.getTitle(), "用户名称不能为空");
        Assert.hasText(usecase.getDirectory(), "目录不能为空");
        ResultNotified result;

        if (usecase.getId() == null) {
            usecase.setProjectId(projectId);
            UsecaseVo vo = usecaseService.doAdd(user.getId(), usecase);
            result = new ResultNotified(true, "用例新增成功");
            result.setData(vo.getId());
            return result;
        } else {
            usecaseService.doUpdate(user.getId(), usecase);
            result = new ResultNotified(true, "用例保存成功");
            return result;
        }
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
    public String openListView(@PathVariable String projectId, String directory, String sort, Model model) {
        // 默认root
        directory = directory == null ? "root" : directory;
        sort = sort == null ? "updateTime" : sort;
        model.addAttribute("directory", directory);
        model.addAttribute("sort", sort);
        List<UsecaseVo> list = usecaseService.getUsecases(projectId, directory, sort);
        List<UsecaseDirectoryVo> directorys = usecaseService.getDirectory(projectId, directory);
        if (!"root".equals(directory)) {
            // 查找目录层级
            List<UsecaseDirectoryVo> dirTier = usecaseService.getDirectoryTier(projectId, directory);
            // 到序排列
            Collections.reverse(dirTier);
            model.addAttribute("dirTiers", dirTier);
        }
        model.addAttribute("cases", list);
        model.addAttribute("dirs", directorys);
        model.addAttribute("currentDir", directory);
        return "/usecase/usecaseList";
    }

    @RequestMapping("/doDelete")
    @ResponseBody
    public ResultNotified doDeleteUsecase(@PathVariable String projectId, String id) {
        usecaseService.doDeleteUsecase(projectId, id);
        return new ResultNotified(true, "用例删除成功");
    }

    /**
     * 新增用例目录
     */
    @RequestMapping("/directory/new")
    @ResponseBody
    public ResultNotified doCreateDirectory(@PathVariable String projectId, String parentId, String name) {
        //参数parentId不能为空
        Assert.isTrue(name != null, "父级用例路径不能为空");
        //参数dir.name不能为空
        Assert.hasText(name, "用例路径名称不能为空");
        usecaseService.createFolder(projectId, parentId, name);
        return new ResultNotified(true, "目录保存成功");
    }

    @RequestMapping("/directory/save")
    @ResponseBody
    public ResultNotified doEditDirectory(String id, String parentId, String name) {
        //id不能为空
        Assert.hasText(id, "用例路径不能为空");
        //parentId不能为空
        Assert.hasText(parentId, "上层路径不能为空");
        //name不能为空
        Assert.hasText(name, "路径名称不能为空");
        usecaseService.updateFolder(id, parentId, name);
        return new ResultNotified(true, "目录保存成功");
    }

    @RequestMapping("/directory/del")
    @ResponseBody
    public ResultNotified doDelDirectory(@PathVariable String projectId, String id, String parentId, String name) {
        //id不能为空
        Assert.hasText(id, "用例路径不能为空");
        //parentId不能为空
        Assert.hasText(parentId, "上层路径不能为空");
        //name不能为空
        Assert.hasText(name, "路径名称不能为空");
        boolean b = usecaseService.delFolder(projectId, id, parentId, name);
        if (b) {
            return new ResultNotified(true, "用例目录删除成功");
        }
        return new ResultNotified(false, "用例目录不为空，删除失败");
    }

}
