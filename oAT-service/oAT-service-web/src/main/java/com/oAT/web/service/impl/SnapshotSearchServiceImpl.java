package com.oAT.web.service.impl;

import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.SnapshotSearchResult;
import com.oAT.web.service.entity.TableToUsecase;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchResultMapper;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.aggregation.impl.AggregatedPageImpl;
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
        AggregatedPage<SystemSnapshot> page = elasticsearchTemplate
                .queryForPage(searchBuilder.build(), SystemSnapshot.class, new ResultMapper());
        SearchPage searchPage = new SearchPage();
        searchPage.setContents(page.getContent());
        searchPage.setTotal(page.getTotalElements());
        return searchPage;
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
        Page<SystemSnapshot> p = systemSnapshotRepository.search(builder.build());
        return p.getContent();
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
        Page<SystemSnapshot> p = systemSnapshotRepository.search(builder.build());
        return p.getContent();
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
        Page<SystemSnapshot> p = systemSnapshotRepository.search(builder.build());
        return p.getContent();
    }

    private class ResultMapper implements SearchResultMapper {
        @Override
        public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {
            List<SnapshotSearchResult> list = new ArrayList();
            for (SearchHit hit : response.getHits()) {
                if (response.getHits().getHits().length <= 0) {
                    return null;
                }
                if (hit.getSourceAsMap().get("disable").equals(true)) {
                    continue;
                }
                SnapshotSearchResult r = new SnapshotSearchResult();
                r.setId(hit.getId());
                r.setProjectId(hit.getSourceAsMap().get("projectId").toString());
                r.setTitle(hit.getSourceAsMap().get("title").toString());
                r.setDirectoryId(hit.getSourceAsMap().get("directory").toString());
                r.setAppId(hit.getSourceAsMap().get("appId").toString());

                if (hit.getSourceAsMap().containsKey("topicImage")) {
                    r.setHeadImage(hit.getSourceAsMap().get("topicImage").toString());
                }
                if (hit.getSourceAsMap().containsKey("subTitle")) {
                    r.setSubTitle(hit.getSourceAsMap().get("subTitle").toString());
                }
                // versionLastUpdate
                r.setUpdateTime(r.parse(hit.getSourceAsMap().get("versionLastUpdate").toString()));
                for (Map.Entry<String, HighlightField> hf : hit.getHighlightFields().entrySet()) {
                    String[] values = toString(hf.getValue().getFragments());
                    if ("title".equals(hf.getKey())) {
                        r.setTitleFragment(values[0]);
                    } else if ("describe".equals(hf.getKey())) {
                        r.setDescribeFragments(values);
                    } else if ("sqls.content".equals(hf.getKey())) {
                        r.setSqlContentFragments(values);
                    } else if ("remotes.invokerInterface".equals(hf.getKey())) {
                        r.setRemoteContentFragments(values);
                    }
                }
                list.add(r);
            }
            return new AggregatedPageImpl<T>((List<T>) list);
        }

        @Override
        public <T> T mapSearchHit(SearchHit searchHit, Class<T> aClass) {
            return null;
        }


        private String[] toString(Text[] texts) {
            String[] result = new String[texts.length];
            for (int i = 0; i < texts.length; i++) {
                result[i] = texts[i].string();
            }
            return result;
        }
    }

}
