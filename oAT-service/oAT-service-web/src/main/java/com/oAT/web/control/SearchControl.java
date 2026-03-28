package com.oAT.web.control;

import com.oAT.web.control.entity.NetworkGraphData;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.UsecaseSearchService;
import com.oAT.web.service.entity.CaseSearchResult;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.SnapshotSearchResult;
import com.oAT.web.service.entity.TableToUsecase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/p/{projectId}/")
public class SearchControl {

    @Autowired
    UsecaseSearchService searchService;
    @Autowired
    SnapshotSearchService snapshotSearchService;

    @RequestMapping("/search")
    public String openSearchView() {
        return "/search/search";
    }

    @RequestMapping("/doSearch")
    public String doSearch(@PathVariable String projectId, String keyword, Model model) {
        Assert.hasText(keyword, "param 'keyword' must be not null");
//        SearchPage<CaseSearchResult> page = searchService.doSearch(projectId, keyword);
        SearchPage<SnapshotSearchResult> page = snapshotSearchService.doSearch(projectId, keyword);
        model.addAttribute("searchPage", page);
        return "/search/searchResult";
    }

    @RequestMapping("/searchTable")
    public String openSearchTableView() {
        return "/search/tableSearch";
    }

    @RequestMapping("/doSearchTable")
    @ResponseBody
    public NetworkGraphData doSearchTable(@PathVariable String projectId, String database, String table, Model model) {
        Assert.hasText(database, "param 'DataBase' must be not null");
        Assert.hasText(table, "param 'table' must be not null");

        List<SystemSnapshot> list = snapshotSearchService.searchByTable(projectId, database, table);
        // 将usecasee转换成节点
        List<NetworkGraphData.Node> nodeLists = list.stream()
                .map(a ->
                        {
                            NetworkGraphData.Node node = new NetworkGraphData.Node(a.getId(), "snapshot", a.getTitle());
                            node.setBackgroundImage("/r/" + a.getTopicImage());
                            return node;
                        }
                )
                .collect(Collectors.toList());
        nodeLists.add(new NetworkGraphData.Node(database + "_" + table, "table", database + "." + table));
        NetworkGraphData.Node[] nodes = nodeLists.toArray(new NetworkGraphData.Node[0]);
        // 将usecase 转换成边线
        NetworkGraphData.Edge[] edges = list.stream()
                .map(a->buildEdge(a,database,table))
                .collect(Collectors.toList())
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
                .map(a -> jdbcActionToLabel(a.getType()))
                .distinct()
                .collect(Collectors.toList());
        edge.setType("snapshotToTable");

        edge.setAction(actions.stream().collect(Collectors.joining(",")));
        edge.setLabel(actions.stream().map(a->jdbcActionToLabel(a)).collect(Collectors.joining(",")));
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
