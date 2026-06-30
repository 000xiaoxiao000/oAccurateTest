package com.oAT.web.control.api;

import com.oAT.web.api.search.SearchApiPayloads.*;
import com.oAT.web.control.entity.NetworkGraphData;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.SnapshotSearchResult;
import com.oAT.web.service.entity.UserVo;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/projects/{projectId}/search")
public class SearchApiControl {

    private final SnapshotSearchService snapshotSearchService;
    private final ProjectService projectService;

    public SearchApiControl(SnapshotSearchService snapshotSearchService,
                            ProjectService projectService) {
        this.snapshotSearchService = snapshotSearchService;
        this.projectService = projectService;
    }

    @GetMapping("/keyword")
    public ResultNotified<SearchKeywordPayload> keyword(@PathVariable String projectId,
                                                        @SessionAttribute UserVo user,
                                                        @RequestParam String keyword) {
        ensureProjectAccess(projectId, user);
        Assert.hasText(keyword, "keyword不能为空");

        SearchPage<SnapshotSearchResult> page = snapshotSearchService.doSearch(projectId, keyword);
        SearchKeywordPayload payload = new SearchKeywordPayload();
        payload.setKeyword(keyword);
        payload.setTotal(page.getTotal());
        payload.setResults((page.getContents() == null ? java.util.Collections.<SnapshotSearchResult>emptyList() : page.getContents())
                .stream().map(this::toResult).collect(Collectors.toList()));
        return new ResultNotified<>(true, "搜索成功", payload);
    }

    @GetMapping("/tables")
    public ResultNotified<NetworkGraphData> table(@PathVariable String projectId,
                                                  @SessionAttribute UserVo user,
                                                  @RequestParam(name = "database", required = false) String database,
                                                  @RequestParam(name = "DataBase", required = false) String legacyDatabase,
                                                  @RequestParam String table) {
        ensureProjectAccess(projectId, user);
        final String queryDatabase = StringUtils.hasText(database) ? database : legacyDatabase;
        Assert.hasText(queryDatabase, "database不能为空");
        Assert.hasText(table, "table不能为空");

        List<SystemSnapshot> list = snapshotSearchService.searchByTable(projectId, queryDatabase, table);
        List<NetworkGraphData.Node> nodeLists = list.stream()
                .map(snapshot -> {
                    NetworkGraphData.Node node = new NetworkGraphData.Node(snapshot.getId(), "snapshot", snapshot.getTitle());
                    node.setBackgroundImage("/r/" + snapshot.getTopicImage());
                    node.setAppId(snapshot.getAppId());
                    return node;
                })
                .collect(Collectors.toList());
        nodeLists.add(new NetworkGraphData.Node(queryDatabase + "_" + table, "table", queryDatabase + "." + table));
        NetworkGraphData.Node[] nodes = nodeLists.toArray(new NetworkGraphData.Node[0]);
        NetworkGraphData.Edge[] edges = list.stream()
                .map(snapshot -> buildEdge(snapshot, queryDatabase, table))
                .toArray(NetworkGraphData.Edge[]::new);
        return new ResultNotified<>(true, "搜索成功", new NetworkGraphData(nodes, edges));
    }

    private SearchKeywordResult toResult(SnapshotSearchResult item) {
        SearchKeywordResult result = new SearchKeywordResult();
        result.setId(item.getId());
        result.setAppId(item.getAppId());
        result.setTitle(StringUtils.hasText(item.getTitleFragment()) ? item.getTitleFragment() : item.getTitle());
        result.setPlainTitle(item.getTitle());
        result.setTitleFragment(item.getTitleFragment());
        result.setSubTitle(item.getSubTitle());
        result.setHeadImage(item.getHeadImage());
        result.setImagePath(StringUtils.hasText(item.getHeadImage()) ? "/r/" + item.getHeadImage() : "/images/image.png");
        result.setDirectoryPath(item.getDirectoryPath());
        result.setUpdateTimeText(item.getUpdateTime() == null ? null : item.getUpdateTime().toString());
        result.setDescribeFragments(item.getDescribeFragments());
        result.setSqlContentFragments(item.getSqlContentFragments());
        result.setRemoteContentFragments(item.getRemoteContentFragments());
        if (item.getDescribeFragments() != null) {
            result.setDescription(String.join("</br>", item.getDescribeFragments()));
        } else if (item.getSqlContentFragments() != null) {
            result.setDescription(String.join("</br>", item.getSqlContentFragments()));
        } else if (item.getRemoteContentFragments() != null) {
            result.setDescription(String.join("</br>", item.getRemoteContentFragments()));
        } else {
            result.setDescription(item.getSubTitle());
        }
        result.setTargetPath("/p/" + item.getProjectId() + "/apps/" + item.getAppId() + "/snapshots/" + item.getId());
        return result;
    }

    private NetworkGraphData.Edge buildEdge(SystemSnapshot snapshot, String database, String table) {
        NetworkGraphData.Edge edge = new NetworkGraphData.Edge();
        edge.setId(snapshot.getId() + "-" + database + "_" + table);
        edge.setSource(snapshot.getId());
        edge.setTarget(database + "_" + table);
        List<String> actions = snapshot.getSqls() == null ? java.util.Collections.emptyList() : Arrays.stream(snapshot.getSqls())
                .filter(sql -> sql != null && sql.getDatabase() != null && sql.getDatabase().equalsIgnoreCase(database))
                .flatMap(sql -> sql.getActions() == null ? Stream.empty() : Stream.of(sql.getActions()))
                .filter(action -> action != null && action.getTable() != null && action.getTable().equalsIgnoreCase(table))
                .map(action -> action.getType())
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        edge.setType("snapshotToTable");
        edge.setAction(String.join(",", actions));
        edge.setLabel(actions.stream().map(this::jdbcActionToLabel).collect(Collectors.joining(",")));
        return edge;
    }

    private String jdbcActionToLabel(String action) {
        return "insert".equals(action) ? "增"
                : "delete".equals(action) ? "删"
                : "update".equals(action) ? "改"
                : "select".equals(action) ? "查"
                : action;
    }

    private ProjectVo ensureProjectAccess(String projectId, UserVo user) {
        ProjectVo project = projectService.getProjectByProjectIdAndMemberId(projectId, user.getId());
        Assert.notNull(project, "找不到指定项目,或者您没有该项目的访问权限");
        return project;
    }

}
