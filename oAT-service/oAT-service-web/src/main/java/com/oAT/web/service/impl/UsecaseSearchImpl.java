package com.oAT.web.service.impl;

import com.oAT.web.esDao.entity.UsecaseSql;
import com.oAT.web.service.UsecaseSearchService;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.service.entity.CaseSearchResult;
import com.oAT.web.service.entity.TableToUsecase;
import com.oAT.web.service.entity.UsecaseVo;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.springframework.beans.BeanUtils;
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


@Service
public class UsecaseSearchImpl implements UsecaseSearchService{

    @Autowired
    CaseCenterRepository caseRepository;
    @Autowired
    ElasticsearchRestTemplate elasticsearchTemplate;

    @Override
    public SearchPage<CaseSearchResult> doSearch(String projectId, String keyWords) {
        NativeSearchQueryBuilder searchBuilder = new NativeSearchQueryBuilder();
        // 基于项目id进行过滤
        searchBuilder.withFilter(QueryBuilders.termQuery("usecase.projectId", projectId));
        // 查找字段 标题 内容 sql 远程调用
        String[] searchFields = new String[]{
                "usecase.title",
                "usecase.content",
                "usecase.sql.contents",
                "usecase.remote.content",
                "usecase.srcStack"
        };
        searchBuilder.withQuery(QueryBuilders.multiMatchQuery(keyWords, searchFields));
        String[] returnFields = new String[]{
                "id",
                "updateTime",
                "usecase.projectId",
                "usecase.title",
                "usecase.headImage"
        };
        // 返回的字段
        searchBuilder.withFields(returnFields);
        //高亮显示的字段
        HighlightBuilder builder = new HighlightBuilder();
        builder.field("usecase.title", 100);
        builder.field("usecase.content", 100);
        builder.field("usecase.sql.contents", 100);
        builder.field("usecase.remote.content", 100);
        searchBuilder.withHighlightFields(builder.fields().toArray(new HighlightBuilder.Field[0]));

        // Spring Data Elasticsearch 4.4 新 API
        SearchHits<CaseCenterIndex> searchHits = elasticsearchTemplate.search(searchBuilder.build(), CaseCenterIndex.class);
        List<CaseSearchResult> results = new ArrayList<>();

        for (SearchHit<CaseCenterIndex> hit : searchHits) {
            CaseSearchResult r = new CaseSearchResult();
            r.setId(hit.getId());

            CaseCenterIndex content = hit.getContent();
            if (content == null || content.getUsecase() == null) continue;

            r.setProjectId(content.getUsecase().getProjectId());
            r.setTitle(content.getUsecase().getTitle());
            if (content.getUsecase().getHeadImage() != null) {
                r.setHeadImage(content.getUsecase().getHeadImage());
            }

            // 处理高亮
            Map<String, List<String>> highlightFields = hit.getHighlightFields();
            for (Map.Entry<String, List<String>> entry : highlightFields.entrySet()) {
                String fieldName = entry.getKey();
                List<String> fragments = entry.getValue();
                if (fragments != null && !fragments.isEmpty()) {
                    if ("usecase.title".equals(fieldName)) {
                        r.setTitleFragment(fragments.get(0));
                    } else if ("usecase.content".equals(fieldName)) {
                        r.setContentFragments(fragments.toArray(new String[0]));
                    } else if ("usecase.sql.contents".equals(fieldName)) {
                        r.setSqlContentFragments(fragments.toArray(new String[0]));
                    } else if ("usecase.remote.content".equals(fieldName)) {
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

    //TODO  usecase.srcStack.keyword 极有可能会超出256
    @Override
    public List<UsecaseVo> getBySrcMethod(String projectId, String... srcMethods) {
        Assert.notNull(projectId, "参数projectId 不能为空");
        Assert.notNull(srcMethods, "srcMethod 不能为空");
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("usecase.projectId", projectId));
        boolQuery.must(QueryBuilders.termQuery("type", "usecase"));
        boolQuery.must(QueryBuilders.termsQuery("usecase.srcStack.keyword", srcMethods));
        return _query(boolQuery);
    }

    // 基于类名称match_phrase（短语匹配）进行查找
    @Override
    public List<UsecaseVo> getBySrcClass(String projectId, String srcClass) {
        Assert.notNull(projectId, "参数projectId 不能为空");
        Assert.notNull(srcClass, "srcClass 不能为空");
        // 移除 class Package 中第一个包，目的是为了在当该类做为参数时也能匹配。
        srcClass = srcClass.substring(srcClass.indexOf("/") + 1, srcClass.length());

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("usecase.projectId", projectId));
        boolQuery.must(QueryBuilders.termQuery("type", "usecase"));
        boolQuery.must(QueryBuilders.matchPhraseQuery("usecase.srcStack", srcClass));
        return _query(boolQuery);
    }

    @Override
    public List<TableToUsecase> getByTable(String projectId, String databaseName, String tableName) {
        Assert.hasText(databaseName, "参数databaseName 不能为空");
        Assert.hasText(tableName, "参数tableName 不能为空");
        // 构建查找条件
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("usecase.projectId", projectId));
        boolQuery.must(QueryBuilders.termQuery("type", "usecase"));

        String queryParam = databaseName + "." + tableName;
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.selects.name", queryParam));// 查
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.deletes.name", queryParam));// 删
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.inserts.name", queryParam)); // 增
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.updates.name", queryParam));// 改
        boolQuery.minimumShouldMatch(1);

        List<TableToUsecase> result = new ArrayList<>();
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFields("id", "updateTime",
                "usecase.title",
                "usecase.labels",
                "usecase.authors",
                "usecase.directory",
                "usecase.sql",
                "usecase.headImage");
        builder.withFilter(boolQuery);
        SearchHits<CaseCenterIndex> searchHits = elasticsearchTemplate.search(builder.build(), CaseCenterIndex.class);
        for (SearchHit<CaseCenterIndex> hit : searchHits) {
            CaseCenterIndex caseCenterIndex = hit.getContent();
            if (caseCenterIndex == null || caseCenterIndex.getUsecase() == null) continue;
            UsecaseVo usecaseVo = new UsecaseVo();
            BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
            usecaseVo.setId(caseCenterIndex.getId());
            usecaseVo.setUpdateTime(caseCenterIndex.getUpdateTime());
            TableToUsecase tableToUsecase = new TableToUsecase();
            tableToUsecase.setTable(tableName);
            tableToUsecase.setDatabase(databaseName);
            tableToUsecase.setUsecaseVo(usecaseVo);
            // 计算Action
            tableToUsecase.setAction(parseAction(caseCenterIndex.getUsecase().getSql(), databaseName, tableName));
            result.add(tableToUsecase);
        }
        return result;
    }

    private String[] parseAction(UsecaseSql sql, String database, String name) {
        boolean insert = Arrays.stream(sql.getInserts()).anyMatch(a -> a.getName().startsWith(database + "." + name));
        boolean update = Arrays.stream(sql.getUpdates()).anyMatch(a -> a.getName().startsWith(database + "." + name));
        boolean delete = Arrays.stream(sql.getDeletes()).anyMatch(a -> a.getName().startsWith(database + "." + name));
        boolean select = Arrays.stream(sql.getSelects()).anyMatch(a -> a.getName().startsWith(database + "." + name));
        List<String> list = new ArrayList<>();
        if (insert){
            list.add("insert");}
        if (update){
            list.add("update");}
        if (delete){
            list.add("delete");}
        if (select){
            list.add("select");}

        return list.toArray(new String[0]);
    }

    private List<UsecaseVo> _query(BoolQueryBuilder boolQuery) {
        List<UsecaseVo> result = new ArrayList<>();
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFields("id", "updateTime",
                "usecase.title",
                "usecase.labels",
                "usecase.authors",
                "usecase.directory",
                "usecase.headImage");
        builder.withFilter(boolQuery);
        SearchHits<CaseCenterIndex> searchHits = elasticsearchTemplate.search(builder.build(), CaseCenterIndex.class);
        for (SearchHit<CaseCenterIndex> hit : searchHits) {
            CaseCenterIndex caseCenterIndex = hit.getContent();
            if (caseCenterIndex == null || caseCenterIndex.getUsecase() == null) continue;
            UsecaseVo usecaseVo = new UsecaseVo();
            BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
            usecaseVo.setId(caseCenterIndex.getId());
            usecaseVo.setUpdateTime(caseCenterIndex.getUpdateTime());
            result.add(usecaseVo);
        }
        return result;
    }

}
