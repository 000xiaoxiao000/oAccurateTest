package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.web.api.map.MapAppPayloadService;
import com.oAT.web.api.map.MapHomePayloadService;
import com.oAT.web.domain.*;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.*;
import com.oAT.web.service.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/p/{projectId}/map")
public class MapControl {

    @Autowired
    FrontendProperties frontendProperties;


    @Autowired
    SystemSnapshotService systemSnapshotService;
    @Autowired
    AppService appService;

    @Autowired
    SnapshotSearchService snapshotSearchService;

    @Autowired
    SnapshotService snapshotService;

    @Autowired
    MapHomePayloadService mapHomePayloadService;

    @Autowired
    MapAppPayloadService mapAppPayloadService;

    @RequestMapping("/app")
    public String openAppMapView(@PathVariable String projectId, String appId, String layers, Model model) {
        String suffix = StringUtils.hasText(layers) ? "?layers=" + layers : "";
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/map/app/" + appId + suffix);
    }

    @RequestMapping("/app/data")
    @ResponseBody
    public List<ImageElement> getAppMapData(@PathVariable String projectId, String appId, String layers) throws BusinessException {
        return mapAppPayloadService.buildAppMapData(projectId, appId, layers);
    }

    @RequestMapping("/home")
    public String openHomeMapView(@PathVariable String projectId, Model model, @SessionAttribute UserVo user) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/map/home");
    }

    @RequestMapping("/home/data")
    @ResponseBody
    public List<ImageElement> getHomeMapData(@PathVariable String projectId) {
        return mapHomePayloadService.buildHomeMapData(projectId);
    }

    @RequestMapping("/code")
    public String openCodeMap(String traceId, Model model, @PathVariable String projectId) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/map/code?traceId=" + traceId);
    }

    /**
     * 获取指定用例堆栈代码 精确到类
     *
     * @param traceId
     * @param projectId return List<ImageElement>
     */
    @RequestMapping("/code/data")
    @ResponseBody
    public List<ImageElement> getMapStackCodeNode(String traceId, @PathVariable String projectId) {
        return mapAppPayloadService.buildTraceStackCodeData(projectId, traceId);
    }

    // 获取指定快照图层
    @RequestMapping("/layer/snapshot")
    @ResponseBody
    public List<ImageElement> getSnapshotNode(@PathVariable String projectId, String id) {
        SystemSnapshot snapshot = systemSnapshotService.getById(id);
        return new SnapshotLayer(Arrays.asList(snapshot)).elements();
    }

    //获取应用快照图层 节点
    @RequestMapping("/layer/appSnapshot")
    @ResponseBody
    public List<ImageElement> getAppSnapshotNode(@PathVariable String projectId, String appId) {
        AppVo app = appService.getApp(appId);
        List<SystemSnapshot> snapshots = systemSnapshotService.findAll(projectId, appId);
        return new AppSnapshotLayer(snapshots, app).elements();
    }

    // 获取快照表结构
    @RequestMapping("/layer/snapshotTable")
    @ResponseBody
    public List<ImageElement> getSnapshotTableNode(@PathVariable String projectId, String snapshotId) {
        SystemSnapshot snapshots = systemSnapshotService.getById(snapshotId);
        return new SnapshotTableLayer(Arrays.asList(snapshots)).elements();
    }

    // 获取快照远程服务
    @RequestMapping("/layer/snapshotRemote")
    @ResponseBody
    public List<ImageElement> getSnapshotRemoteNode(@PathVariable String projectId, String snapshotId) {
        SystemSnapshot snapshots = systemSnapshotService.getById(snapshotId);
        return new SnapshotRemoteLayer(Arrays.asList(snapshots)).elements();
    }

    @RequestMapping("/layer/tableSnapshot")
    @ResponseBody
    public List<ImageElement> getTableSnapshotNode(@PathVariable String projectId, DatabaseTable db) {
        Assert.notNull(db, "参数 db 不能为空");
        Assert.notNull(db.getDatabase(), "参数 DataBase 不能为空");
        Assert.notNull(db.getTable(), "参数 table 不能为空");

        List<SystemSnapshot> snapshots = snapshotSearchService.searchByTable(projectId, db.getDatabase(), db.getTable());
        return new TableSnapshotLayer(snapshots, db).elements();
    }

    // 基于远程服务获取 节点层
    @RequestMapping("/layer/dubboSnapshot")
    @ResponseBody
    public List<ImageElement> getDubboSnapshotNode(@PathVariable String projectId, String interfaceName, String methodName) {
//        searchByDubbo
        List<SystemSnapshot> snapshots = snapshotSearchService.searchByDubbo(projectId, interfaceName, methodName);
        return new DubboSnapshotLayer(snapshots, interfaceName, methodName).elements();
    }

    @RequestMapping("/layer/stack/code")
    @ResponseBody
    public List<ImageElement> getStackCodeNode(@PathVariable String projectId, String snapshotId) {
        return mapAppPayloadService.buildSnapshotStackCodeData(projectId, snapshotId);
    }

    @RequestMapping("/search")
    @ResponseBody
    public SearchResult doSearch(@PathVariable String projectId, String q) {
        if (!StringUtils.hasText(q)) {
            return new SearchResult();
        }
        Assert.hasText(q, "param 'q' must be not null");
        SearchPage<SnapshotSearchResult> page = snapshotSearchService.doSearch(projectId, q);
        SearchResult searchResult = new SearchResult();
        page.getContents().stream().forEach(a -> {
            String title = Optional.ofNullable(a.getTitleFragment()).orElse(a.getTitle());
            String describe;
            if (a.getDescribeFragments() != null) {
                describe = String.join("</br>", a.getDescribeFragments());
            } else if (a.getSqlContentFragments() != null) {
                describe = String.join("</br>", a.getSqlContentFragments());
            } else if (a.getRemoteContentFragments() != null) {
                describe = String.join("</br>", a.getRemoteContentFragments());
            } else {
                describe = a.getSubTitle();
            }

            SearchResult.Result result = new SearchResult.Result(a.getId(), title);
            result.description = describe;
            searchResult.addResult(result);
        });
        return searchResult;
    }

}
