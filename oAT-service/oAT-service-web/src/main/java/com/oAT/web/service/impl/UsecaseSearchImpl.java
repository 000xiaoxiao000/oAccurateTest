package com.oAT.web.service.impl;

import com.oAT.web.esDao.entity.UsecaseSql;
import com.oAT.web.service.UsecaseSearchService;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.service.entity.CaseSearchResult;
import com.oAT.web.service.entity.TableToUsecase;
import com.oAT.web.service.entity.UsecaseVo;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.beans.BeanUtils;
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
        AggregatedPage<CaseCenterIndex> page = elasticsearchTemplate
                .queryForPage(searchBuilder.build(), CaseCenterIndex.class, new CaseSearchResultMapper());
        SearchPage searchPage = new SearchPage();
        searchPage.setContents(page.getContent());
        searchPage.setTotal(page.getTotalElements());
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
        Page<CaseCenterIndex> items = caseRepository.search(builder.build());
        List<CaseCenterIndex> list = items.getContent();
        for (CaseCenterIndex caseCenterIndex : list) {
            UsecaseVo usecaseVo = new UsecaseVo();
            BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
            usecaseVo.setId(caseCenterIndex.getId());
            usecaseVo.setUpdateTime(caseCenterIndex.parse(caseCenterIndex.getUpdateTime()));
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
        Page<CaseCenterIndex> items = caseRepository.search(builder.build());
        List<CaseCenterIndex> list = items.getContent();
        for (CaseCenterIndex caseCenterIndex : list) {
            UsecaseVo usecaseVo = new UsecaseVo();
            BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
            usecaseVo.setId(caseCenterIndex.getId());
            usecaseVo.setUpdateTime(caseCenterIndex.parse(caseCenterIndex.getUpdateTime()));
            result.add(usecaseVo);
        }
        return result;
    }


    private class CaseSearchResultMapper implements SearchResultMapper {
        @Override
        public <T> AggregatedPage<T> mapResults(SearchResponse response, Class<T> clazz, Pageable pageable) {

            List<CaseSearchResult> list = new ArrayList();
            for (SearchHit hit : response.getHits()) {
                if (response.getHits().getHits().length <= 0) {
                    return null;
                }
                CaseSearchResult r = new CaseSearchResult();
                r.setId(hit.getId());
                r.setProjectId(((Map) hit.getSourceAsMap().get("usecase")).get("projectId").toString());
                r.setTitle(((Map) hit.getSourceAsMap().get("usecase")).get("title").toString());
                if (((Map) hit.getSourceAsMap().get("usecase")).containsKey("headImage")) {
                    r.setHeadImage(((Map) hit.getSourceAsMap().get("usecase")).get("headImage").toString());
                }
                for (Map.Entry<String, HighlightField> hf : hit.getHighlightFields().entrySet()) {
                    String[] values = toString(hf.getValue().getFragments());
                    if ("usecase.title".equals(hf.getKey())) {
                        r.setTitleFragment(values[0]);
                    } else if ("usecase.content".equals(hf.getKey())) {
                        r.setContentFragments(values);
                    } else if ("usecase.sql.contents".equals(hf.getKey())) {
                        r.setSqlContentFragments(values);
                    } else if ("usecase.remote.content".equals(hf.getKey())) {
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
