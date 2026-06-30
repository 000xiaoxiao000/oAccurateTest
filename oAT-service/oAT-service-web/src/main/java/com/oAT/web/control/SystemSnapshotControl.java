package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.web.api.snapshot.TraceGraphViewService;
import com.oAT.web.control.entity.*;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.UserVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.*;

@Controller
@RequestMapping("/p/{projectId}/{appId}/snapshot/")
public class SystemSnapshotControl {

    @Autowired
    FrontendProperties frontendProperties;

    final Logger logger = LoggerFactory.getLogger(SystemSnapshotControl.class);
    @Autowired
    SystemSnapshotService systemSnapshotService;

    @Autowired
    AppService appService;

    @Autowired
    UsecaseService usecaseService;

    @Autowired
    private TraceGraphViewService traceGraphViewService;

    // 打开系统快照列表
    @RequestMapping("/list")
    public String openList(@PathVariable String projectId, @PathVariable String appId, String directoryId, String sort,
                           String keyword, String missingSnapshotId, @SessionAttribute UserVo user, Model model) {
        StringBuilder target = new StringBuilder("/p/")
                .append(projectId)
                .append("/apps/")
                .append(appId)
                .append("/snapshots");
        List<String> query = new ArrayList<>();
        if (StringUtils.hasText(directoryId) && !"root".equals(directoryId)) {
            query.add("directoryId=" + directoryId);
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

    /**
     * 保存路径
     *
     * @param projectId
     * @param appId
     * @param dir
     * @return
     */
    @RequestMapping(value = "/directory", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<Serializable> saveDirectory(@PathVariable String projectId, @PathVariable String appId, SnapshotDirectory dir) {
        //参数directory不能为空
        Assert.notNull(dir, "路径不能为空");
        //参数dir.name不能为空
        Assert.hasText(dir.getName(), "快照名称不能为空");
        appService.saveSnapshotDirectory(projectId, appId, dir);
        return new ResultNotified<>(true, "目录保存成功");
    }

    /**
     * 删除路径
     *
     * @param projectId
     * @param appId
     * @param directoryId
     * @return
     */
    @RequestMapping(value = "/directory", method = RequestMethod.DELETE)
    @ResponseBody
    public ResultNotified<Serializable> deleteDirectory(@PathVariable String projectId, @PathVariable String appId, Integer directoryId) {
        //参数directoryId不能为空
        Assert.isTrue(directoryId != null, "路径不能为空");
        try {
            appService.deleteSnapshotDirectory(projectId, appId, directoryId);
        } catch (BusinessException e) {
            logger.info("删除目录失败", e);
            return new ResultNotified<>(false, e.getMessage());
        }
        return new ResultNotified<>(true, "目录删除成功");
    }

    /**
     * 展示快照详情节点
     *
     * @param projectId
     * @param id
     * @param model
     * @return
     */
    @RequestMapping("/detail/{id}")
    public String open(@PathVariable String projectId, @PathVariable String appId, @PathVariable String id,
                       @RequestParam(value = "tab", required = false) String tab,
                       Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots/" + id);
    }

    @RequestMapping("/{id}/usecase/bind")
    @ResponseBody
    public ResultNotified<Integer> bindUsecases(@PathVariable String projectId,
                                                @PathVariable String appId,
                                                @PathVariable String id,
                                                @SessionAttribute UserVo user,
                                                String[] usecaseIds) {
        try {
            SystemSnapshot snapshot = systemSnapshotService.getById(id);
            Assert.notNull(snapshot, "找不到系统快照 id=" + id);
            Assert.isTrue(projectId.equals(snapshot.getProjectId()), "系统快照不属于当前项目");
            usecaseService.bindSystemSnapshotToUsecases(projectId, user.getId(), id, usecaseIds);
            ResultNotified<Integer> result = new ResultNotified<>(true, "测试用例关联已更新");
            result.setData(usecaseIds == null ? 0 : usecaseIds.length);
            return result;
        } catch (Exception e) {
            logger.warn("更新系统快照关联测试用例失败, projectId={}, appId={}, snapshotId={}", projectId, appId, id, e);
            ResultNotified<Integer> result = new ResultNotified<>(false, "测试用例关联更新失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @RequestMapping("/usecase/batchBind")
    @ResponseBody
    public ResultNotified<Integer> batchBindUsecases(@PathVariable String projectId,
                                                     @PathVariable String appId,
                                                     @SessionAttribute UserVo user,
                                                     String[] snapshotIds,
                                                     String[] usecaseIds) {
        try {
            Assert.isTrue(!org.springframework.util.ObjectUtils.isEmpty(snapshotIds), "snapshotIds不能为空");
            for (String snapshotId : snapshotIds) {
                if (!StringUtils.hasText(snapshotId)) {
                    continue;
                }
                SystemSnapshot snapshot = systemSnapshotService.getById(snapshotId.trim());
                Assert.notNull(snapshot, "找不到系统快照 id=" + snapshotId);
                Assert.isTrue(projectId.equals(snapshot.getProjectId()), "系统快照不属于当前项目");
                Assert.isTrue(appId.equals(snapshot.getAppId()), "系统快照不属于当前应用");
            }
            for (String snapshotId : snapshotIds) {
                if (!StringUtils.hasText(snapshotId)) {
                    continue;
                }
                usecaseService.bindSystemSnapshotToUsecases(projectId, user.getId(), snapshotId.trim(), usecaseIds);
            }
            ResultNotified<Integer> result = new ResultNotified<>(true, "批量关联成功");
            result.setData(snapshotIds.length);
            return result;
        } catch (Exception e) {
            logger.warn("批量关联系统快照测试用例失败, projectId={}, appId={}", projectId, appId, e);
            ResultNotified<Integer> result = new ResultNotified<>(false, "批量关联失败");
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 将 TraceNode 转换成前台堆栈列表树节点
     */
    @RequestMapping("/detail/graph/{id}")
    @ResponseBody
    public GraphView getGraphView(@PathVariable String projectId, @PathVariable String id) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        Assert.notNull(snapshot, "找不到系统快照id=" + id);
        return traceGraphViewService.buildGraphView(projectId, snapshot.getTraceId());
    }

    @RequestMapping("/node/{snapshotId}")
    public String openNodeDetail(@PathVariable String projectId,
                                 @PathVariable String appId,
                                 @PathVariable String snapshotId,
                                 String traceId,
                                 String nodeId) {
        StringBuilder target = new StringBuilder("/p/")
                .append(projectId)
                .append("/apps/")
                .append(appId)
                .append("/snapshots/")
                .append(snapshotId)
                .append("/graph");
        List<String> query = new ArrayList<>();
        if (StringUtils.hasText(traceId)) query.add("traceId=" + traceId);
        if (StringUtils.hasText(nodeId)) query.add("nodeId=" + nodeId);
        if (!query.isEmpty()) target.append("?").append(String.join("&", query));
        return "redirect:" + frontendProperties.url(target.toString());
    }

    /**
     * 更新快照标题
     */
    @RequestMapping("/update")
    @ResponseBody
    public ResultNotified<Serializable> update(@PathVariable String projectId, @SessionAttribute UserVo user, SystemSnapshot snapshot) {
        systemSnapshotService.saveBasic(projectId, user.getId(), snapshot);
        return new ResultNotified<>(true, "保存成功");
    }

    /**
     * 添加评论
     */
    @RequestMapping(value = "/addDescribe", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<Serializable> addDescribe(@SessionAttribute UserVo user, String id, String content) {
        appService.addDescribe(id, user.getId(), content);
        return new ResultNotified<>(true, "添加成功");
    }

    /**
     * 删除评论
     */
    @RequestMapping(value = "/delDescribe")
    @ResponseBody
    public ResultNotified<Serializable> delDescribe(@SessionAttribute UserVo user, String id, String content, String dateTime) {
        appService.delDescribe(id, user.getId(), content, dateTime);
        return new ResultNotified<>(true, "评论删除成功");
    }

    /**
     * 删除快照
     */
    @RequestMapping("/doDelete")
    @ResponseBody
    public ResultNotified<Serializable> deleteSystemSnapshot(String id) {
        //当前快照id
        appService.deleteSnapshot(id);
        return new ResultNotified<>(true, "删除快照成功");
    }

    /**
     * 系统快照覆盖率报告
     */
    @RequestMapping("/report/{id}")
    public String systemSnapshotCodeReport(@PathVariable String projectId, @PathVariable String appId, @PathVariable String id, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots/" + id + "/report");
    }

    @RequestMapping("/report/calculate/{id}")
    @ResponseBody
    public ResultNotified<Serializable> calculateReport(@PathVariable String id) {
        systemSnapshotService.asyncCalculateCoverage(id);
        return new ResultNotified<>(true, "覆盖率计算任务已启动");
    }

    /**
     * 手工补录快照-Commit 关联关系。
     * 为当前 appId 下指定版本中无关联的快照，按版本中心当前 Commit 补录。
     * versionNumber 为空时处理该应用所有快照。
     */
    @RequestMapping(value = "/commit-mapping/backfill", method = RequestMethod.POST)
    @ResponseBody
    public ResultNotified<Integer> backfillCommitMapping(@PathVariable String appId,
                                                         @RequestParam(required = false) String versionNumber) {
        try {
            int count = systemSnapshotService.backfillCommitMapping(appId, versionNumber);
            return new ResultNotified<>(true, "补录完成，共补录 " + count + " 条关联记录", count);
        } catch (Exception e) {
            logger.warn("手工补录快照 Commit 关联失败, appId={}, versionNumber={}", appId, versionNumber, e);
            ResultNotified<Integer> result = new ResultNotified<>(false, "补录失败: " + e.getMessage());
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    @RequestMapping("/report/status/{id}")
    @ResponseBody
    public ResultNotified<SystemSnapshot> getReportStatus(@PathVariable String id) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        return new ResultNotified<>(true, "获取成功", snapshot);
    }

    @RequestMapping("/report/code")
    public String systemSnapshotCodeView(@PathVariable String projectId, @PathVariable String appId, String snapshotId, String className, Model model) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/apps/" + appId + "/snapshots/" + snapshotId + "/report/code?className=" + className);
    }

}
