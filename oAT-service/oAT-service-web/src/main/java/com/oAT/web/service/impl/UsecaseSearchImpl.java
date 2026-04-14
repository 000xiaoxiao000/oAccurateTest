package com.oAT.web.service.impl;

import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.UsecaseSql;
import com.oAT.web.service.UsecaseSearchService;
import com.oAT.web.service.entity.CaseSearchResult;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.TableToUsecase;
import com.oAT.web.service.entity.UsecaseVo;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UsecaseSearchImpl implements UsecaseSearchService {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public SearchPage<CaseSearchResult> doSearch(String projectId, String keyWords) {
        NativeSearchQueryBuilder searchBuilder = new NativeSearchQueryBuilder();
        searchBuilder.withFilter(QueryBuilders.termQuery("usecase.projectId", projectId));
        searchBuilder.withQuery(QueryBuilders.multiMatchQuery(keyWords,
                "usecase.title", "usecase.content", "usecase.sql.contents", "usecase.remote.content", "usecase.srcStack"));

        SearchHits<CaseCenterIndex> searchHits = elasticsearchOperations.search(searchBuilder.build(), CaseCenterIndex.class);
        List<CaseSearchResult> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toCaseSearchResult)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        SearchPage<CaseSearchResult> searchPage = new SearchPage<>();
        searchPage.setContents(results);
        searchPage.setTotal(searchHits.getTotalHits());
        return searchPage;
    }

    private CaseSearchResult toCaseSearchResult(CaseCenterIndex content) {
        if (content == null || content.getUsecase() == null) {
            return null;
        }
        CaseSearchResult result = new CaseSearchResult();
        result.setId(content.getId());
        result.setProjectId(content.getUsecase().getProjectId());
        result.setTitle(content.getUsecase().getTitle());
        result.setUpdateTime(content.getUpdateTime());
        if (content.getUsecase().getHeadImage() != null) {
            result.setHeadImage(content.getUsecase().getHeadImage());
        }
        return result;
    }

    @Override
    public List<UsecaseVo> getBySrcMethod(String projectId, String... srcMethods) {
        Assert.notNull(projectId, "参数projectId 不能为空");
        Assert.notNull(srcMethods, "srcMethod 不能为空");
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("usecase.projectId", projectId));
        boolQuery.must(QueryBuilders.termQuery("type", "usecase"));
        boolQuery.must(QueryBuilders.termsQuery("usecase.srcStack.keyword", srcMethods));
        return queryUsecases(boolQuery);
    }

    @Override
    public List<UsecaseVo> getBySrcClass(String projectId, String srcClass) {
        Assert.notNull(projectId, "参数projectId 不能为空");
        Assert.notNull(srcClass, "srcClass 不能为空");
        String normalizedSrcClass = srcClass.substring(srcClass.indexOf("/") + 1);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("usecase.projectId", projectId));
        boolQuery.must(QueryBuilders.termQuery("type", "usecase"));
        boolQuery.must(QueryBuilders.matchPhraseQuery("usecase.srcStack", normalizedSrcClass));
        return queryUsecases(boolQuery);
    }

    @Override
    public List<TableToUsecase> getByTable(String projectId, String databaseName, String tableName) {
        Assert.hasText(databaseName, "参数databaseName 不能为空");
        Assert.hasText(tableName, "参数tableName 不能为空");

        String queryParam = databaseName + "." + tableName;
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("usecase.projectId", projectId));
        boolQuery.must(QueryBuilders.termQuery("type", "usecase"));
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.selects.name", queryParam));
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.deletes.name", queryParam));
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.inserts.name", queryParam));
        boolQuery.should(QueryBuilders.prefixQuery("usecase.sql.updates.name", queryParam));
        boolQuery.minimumShouldMatch(1);

        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFilter(boolQuery);

        SearchHits<CaseCenterIndex> searchHits = elasticsearchOperations.search(builder.build(), CaseCenterIndex.class);
        List<TableToUsecase> result = new ArrayList<>();
        for (SearchHit<CaseCenterIndex> hit : searchHits) {
            CaseCenterIndex caseCenterIndex = hit.getContent();
            if (caseCenterIndex == null || caseCenterIndex.getUsecase() == null) {
                continue;
            }
            UsecaseVo usecaseVo = new UsecaseVo();
            BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
            usecaseVo.setId(caseCenterIndex.getId());
            usecaseVo.setUpdateTime(caseCenterIndex.getUpdateTime());
            TableToUsecase tableToUsecase = new TableToUsecase();
            tableToUsecase.setTable(tableName);
            tableToUsecase.setDatabase(databaseName);
            tableToUsecase.setUsecaseVo(usecaseVo);
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
        if (insert) {
            list.add("insert");
        }
        if (update) {
            list.add("update");
        }
        if (delete) {
            list.add("delete");
        }
        if (select) {
            list.add("select");
        }

        return list.toArray(new String[0]);
    }

    private List<UsecaseVo> queryUsecases(BoolQueryBuilder boolQuery) {
        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFilter(boolQuery);
        SearchHits<CaseCenterIndex> searchHits = elasticsearchOperations.search(builder.build(), CaseCenterIndex.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .filter(index -> index != null && index.getUsecase() != null)
                .map(this::toUsecaseVo)
                .collect(Collectors.toList());
    }

    private UsecaseVo toUsecaseVo(CaseCenterIndex caseCenterIndex) {
        UsecaseVo usecaseVo = new UsecaseVo();
        BeanUtils.copyProperties(caseCenterIndex.getUsecase(), usecaseVo);
        usecaseVo.setId(caseCenterIndex.getId());
        usecaseVo.setUpdateTime(caseCenterIndex.getUpdateTime());
        return usecaseVo;
    }
}
