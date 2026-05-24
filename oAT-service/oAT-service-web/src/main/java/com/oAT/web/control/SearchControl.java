package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.web.control.entity.NetworkGraphData;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/p/{projectId}/")
public class SearchControl {

    @Autowired
    FrontendProperties frontendProperties;
    @Autowired
    SnapshotSearchService snapshotSearchService;

    @RequestMapping("/search")
    public String openSearchView(@PathVariable String projectId) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/search");
    }

    @RequestMapping("/doSearch")
    public String doSearch(@PathVariable String projectId, String keyword) {
        Assert.hasText(keyword, "param 'keyword' must be not null");
//        SearchPage<CaseSearchResult> page = searchService.doSearch(projectId, keyword);
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/search?keyword=" + org.springframework.web.util.UriUtils.encode(keyword, java.nio.charset.StandardCharsets.UTF_8.name()));
    }

    @RequestMapping("/searchTable")
    public String openSearchTableView(@PathVariable String projectId) {
        return "redirect:" + frontendProperties.url("/p/" + projectId + "/search?tab=table");
    }

    @RequestMapping("/doSearchTable")
    @ResponseBody
    public NetworkGraphData doSearchTable(@PathVariable String projectId,
                                          @RequestParam(name = "database", required = false) String database,
                                          @RequestParam(name = "DataBase", required = false) String legacyDatabase,
                                          String table) {
        final String queryDatabase = database != null ? database : legacyDatabase;
        final String queryTable = table;
        Assert.hasText(queryDatabase, "param 'database' must be not null");
        Assert.hasText(queryTable, "param 'table' must be not null");

        List<SystemSnapshot> list = snapshotSearchService.searchByTable(projectId, queryDatabase, queryTable);
        List<NetworkGraphData.Node> nodeLists = list.stream()
                .map(a -> {
                    NetworkGraphData.Node node = new NetworkGraphData.Node(a.getId(), "snapshot", a.getTitle());
                    node.setBackgroundImage("/r/" + a.getTopicImage());
                    return node;
                })
                .collect(Collectors.toList());
        nodeLists.add(new NetworkGraphData.Node(queryDatabase + "_" + queryTable, "table", queryDatabase + "." + queryTable));
        NetworkGraphData.Node[] nodes = nodeLists.toArray(new NetworkGraphData.Node[0]);
        NetworkGraphData.Edge[] edges = list.stream()
                .map(a -> buildEdge(a, queryDatabase, queryTable))
                .toList()
                .toArray(new NetworkGraphData.Edge[0]);
        return new NetworkGraphData(nodes, edges);
    }

    private NetworkGraphData.Edge buildEdge(SystemSnapshot snapshot, String database, String table) {
        NetworkGraphData.Edge edge = new NetworkGraphData.Edge();
        edge.setId(snapshot.getId() + "-" + database + "_" + table);
        edge.setSource(snapshot.getId());
        edge.setTarget(database + "_" + table);
        List<String> actions = Arrays.stream(snapshot.getSqls())
                .filter(a -> a.getDatabase().equalsIgnoreCase(database))
                .flatMap(a -> Stream.of(a.getActions()))
                .filter(a -> a.getTable().equalsIgnoreCase(table))
                .map(a -> a.getType())
                .distinct()
                .collect(Collectors.toList());
        edge.setType("snapshotToTable");
        edge.setAction(actions.stream().collect(Collectors.joining(",")));
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

}
