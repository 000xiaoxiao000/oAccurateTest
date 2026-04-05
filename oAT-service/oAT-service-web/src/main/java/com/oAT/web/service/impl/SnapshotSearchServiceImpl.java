package com.oAT.web.service.impl;

import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.SnapshotSearchResult;
import com.oAT.web.service.entity.TableToUsecase;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SnapshotSearchServiceImpl implements SnapshotSearchService {

    @Autowired
    ElasticsearchRestTemplate elasticsearchTemplate;

    @Autowired
    SystemSnapshotRepository systemSnapshotRepository;
    private static String[] basicFields;

    static {
        basicFields = new String[]{
                "projectId",
                "appId",
                "title",
                "subTitle",
                "topicImage",
                "describe",
                "directory",
                "versionLastUpdate",
                "labels"
        };

    }

    @Override
    public SearchPage<SnapshotSearchResult> doSearch(String projectId, String keyWords) {
        NativeSearchQueryBuilder searchBuilder = new NativeSearchQueryBuilder();
        // 基于项目id进行过滤
        searchBuilder.withFilter(QueryBuilders.termQuery("projectId", projectId));
        // 查找字段 标题 内容 sql 远程调用
        String[] searchFields = new String[]{
                "title",
                "subTitle",
                "describe",
                "codes",
        };
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.should(QueryBuilders.multiMatchQuery(keyWords, searchFields));
        NestedQueryBuilder sqlQuery = QueryBuilders.nestedQuery("sqls", QueryBuilders.matchQuery("sqls.content", keyWords), ScoreMode.Avg);
        boolQuery.should(sqlQuery);
        NestedQueryBuilder remotesQuery = QueryBuilders.nestedQuery("remotes", QueryBuilders.matchQuery("remotes.invokerInterface", keyWords),
                ScoreMode.Avg);
        boolQuery.should(remotesQuery);
        searchBuilder.withQuery(boolQuery);

        String[] returnFields = new String[]{
                "id",
                "projectId",
                "appId",
                "title",
                "subTitle",
                "directory",
                "topicImage",
                "versionLastUpdate",
                "disable"
        };
        // 返回的字段
        searchBuilder.withFields(returnFields);
        //高亮显示的字段
        HighlightBuilder builder = new HighlightBuilder();
        builder.field("title", 100);
        builder.field("subTitle", 100);
        builder.field("describe", 100);
        builder.field("sqls.content", 100);
//        builder.field("codes", 100);
        builder.field("remotes.invokerInterface", 100);
        searchBuilder.withHighlightFields(builder.fields().toArray(new HighlightBuilder.Field[0]));

        // Spring Data Elasticsearch 4.4 新 API
        SearchHits<SystemSnapshot> searchHits = elasticsearchTemplate.search(searchBuilder.build(), SystemSnapshot.class);
        List<SnapshotSearchResult> results = new ArrayList<>();

        for (SearchHit<SystemSnapshot> hit : searchHits) {
            SnapshotSearchResult r = new SnapshotSearchResult();
            r.setId(hit.getId());

            Map<String, Object> source = hit.getContent() != null ? convertToMap(hit.getContent()) : null;
            if (source == null) continue;

            r.setProjectId(source.get("projectId") != null ? source.get("projectId").toString() : null);
            r.setTitle(source.get("title") != null ? source.get("title").toString() : null);
            r.setDirectoryId(source.get("directory") != null ? source.get("directory").toString() : null);
            r.setAppId(source.get("appId") != null ? source.get("appId").toString() : null);

            if (source.containsKey("topicImage")) {
                r.setHeadImage(source.get("topicImage").toString());
            }
            if (source.containsKey("subTitle")) {
                r.setSubTitle(source.get("subTitle").toString());
            }
            // versionLastUpdate
            if (source.containsKey("versionLastUpdate")) {
                r.setUpdateTime(r.parse(source.get("versionLastUpdate").toString()));
            }

            // 处理高亮
            Map<String, List<String>> highlightFields = hit.getHighlightFields();
            for (Map.Entry<String, List<String>> entry : highlightFields.entrySet()) {
                String fieldName = entry.getKey();
                List<String> fragments = entry.getValue();
                if (fragments != null && !fragments.isEmpty()) {
                    if ("title".equals(fieldName)) {
                        r.setTitleFragment(fragments.get(0));
                    } else if ("describe".equals(fieldName)) {
                        r.setDescribeFragments(fragments.toArray(new String[0]));
                    } else if ("sqls.content".equals(fieldName)) {
                        r.setSqlContentFragments(fragments.toArray(new String[0]));
                    } else if ("remotes.invokerInterface".equals(fieldName)) {
                        r.setRemoteContentFragments(fragments.toArray(new String[0]));
                    }
                }
            }
            results.add(r);
        }

        SearchPage searchPage = new SearchPage();
        searchPage.setContents(results);
        searchPage.setTotal(searchHits.getTotalHits());
        return searchPage;
    }

    private Map<String, Object> convertToMap(SystemSnapshot snapshot) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("projectId", snapshot.getProjectId());
        map.put("appId", snapshot.getAppId());
        map.put("title", snapshot.getTitle());
        map.put("subTitle", snapshot.getSubTitle());
        map.put("topicImage", snapshot.getTopicImage());
        map.put("describe", snapshot.getDescribe());
        map.put("directory", snapshot.getDirectory());
        map.put("versionLastUpdate", snapshot.getVersionLastUpdate());
        map.put("labels", snapshot.getLabels());
        return map;
    }

    @Override
    public List<SystemSnapshot> searchByTable(String projectId, String databaseName, String tableName) {
        Assert.hasText(databaseName, "参数databaseName 不能为空");
        Assert.hasText(tableName, "参数tableName 不能为空");
        // 构建查找条件
        BoolQueryBuilder masterQuery = QueryBuilders.boolQuery();
        masterQuery.must(QueryBuilders.termQuery("projectId", projectId));
        masterQuery.must(QueryBuilders.termQuery("disable", false));

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("sqls.DataBase", databaseName));
        boolQuery.must(QueryBuilders.termQuery("sqls.actions.table", tableName));

        masterQuery.must(QueryBuilders.nestedQuery("sqls", boolQuery, ScoreMode.Avg));


        List<TableToUsecase> result = new ArrayList<>();
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFields(ArrayUtils.add(basicFields, "sqls.*"));

        builder.withFilter(masterQuery);
        SearchHits<SystemSnapshot> searchHits = elasticsearchTemplate.search(builder.build(), SystemSnapshot.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }


    @Override
    public List<SystemSnapshot> searchByCode(String projectId, String className, String... methodName) {
        Assert.hasText(projectId, "参数'projectId'不能为空");
        Assert.hasText(className, "参数'className'不能为空");

        BoolQueryBuilder masterQuery = QueryBuilders.boolQuery();
        masterQuery.must(QueryBuilders.termQuery("projectId", projectId));
        masterQuery.must(QueryBuilders.termQuery("disable", false));
        if (ArrayUtils.isNotEmpty(methodName)) {
            List<String> methods = Arrays.stream(methodName)
                    .map(m -> className + " " + m)
                    .collect(Collectors.toList());
            masterQuery.must(QueryBuilders.termsQuery("codes.keyword", methods));
        } else {
            masterQuery.must(QueryBuilders.prefixQuery("codes.keyword", className));
        }
        List<TableToUsecase> result = new ArrayList<>();
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFields(
                basicFields
        );
        builder.withFilter(masterQuery);
        SearchHits<SystemSnapshot> searchHits = elasticsearchTemplate.search(builder.build(), SystemSnapshot.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    @Override
    public List<SystemSnapshot> searchByDubbo(String projectId, String interfaceName, String... methodName) {
        Assert.hasText(projectId, "projectId 不能为空");
        Assert.hasText(interfaceName, "interfaceName 不能为空");

        BoolQueryBuilder masterQuery = QueryBuilders.boolQuery();
        masterQuery.must(QueryBuilders.termQuery("projectId", projectId));
        masterQuery.must(QueryBuilders.termQuery("disable", false));

        QueryBuilder termsQueryBuilder;
        if (ArrayUtils.isNotEmpty(methodName)) {
            List<String> methods = Arrays.stream(methodName)
                    .map(m -> interfaceName + "#" + m)
                    .collect(Collectors.toList());
            termsQueryBuilder = QueryBuilders.termsQuery("remotes.invokerInterface", methods);
        } else {
            termsQueryBuilder = QueryBuilders.prefixQuery("remotes.invokerInterface", interfaceName);
        }
        masterQuery.must(QueryBuilders.nestedQuery("remotes", termsQueryBuilder, ScoreMode.Avg));

        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFields(ArrayUtils.add(basicFields, "sqls.*"));
        builder.withFilter(masterQuery);
        SearchHits<SystemSnapshot> searchHits = elasticsearchTemplate.search(builder.build(), SystemSnapshot.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

}
