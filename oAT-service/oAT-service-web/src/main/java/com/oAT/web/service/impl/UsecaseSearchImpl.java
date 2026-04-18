package com.oAT.web.service.impl;

import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.service.UsecaseSearchService;
import com.oAT.web.service.entity.CaseSearchResult;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.TableToUsecase;
import com.oAT.web.service.entity.UsecaseVo;
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
import java.util.LinkedHashSet;
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
                "usecase.title", "usecase.content", "usecase.srcStack"));

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
        LinkedHashSet<String> normalizedMethods = Arrays.stream(srcMethods)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(method -> !method.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedMethods.isEmpty()) {
            return new ArrayList<>();
        }
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("usecase.projectId", projectId));
        boolQuery.must(QueryBuilders.termQuery("type", "usecase"));

        BoolQueryBuilder srcStackQuery = QueryBuilders.boolQuery();
        srcStackQuery.should(QueryBuilders.termsQuery("usecase.srcStack.keyword", normalizedMethods));
        srcStackQuery.should(QueryBuilders.termsQuery("usecase.srcStack", normalizedMethods));
        for (String method : normalizedMethods) {
            srcStackQuery.should(QueryBuilders.prefixQuery("usecase.srcStack.keyword", method + "("));
            srcStackQuery.should(QueryBuilders.wildcardQuery("usecase.srcStack.keyword", method + "(*"));
            srcStackQuery.should(QueryBuilders.wildcardQuery("usecase.srcStack.keyword", method + " *"));
        }
        srcStackQuery.minimumShouldMatch(1);
        boolQuery.must(srcStackQuery);
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

        BoolQueryBuilder srcStackQuery = QueryBuilders.boolQuery();
        srcStackQuery.should(QueryBuilders.matchPhraseQuery("usecase.srcStack", normalizedSrcClass));
        srcStackQuery.should(QueryBuilders.wildcardQuery("usecase.srcStack.keyword", normalizedSrcClass + " *"));
        srcStackQuery.should(QueryBuilders.prefixQuery("usecase.srcStack.keyword", normalizedSrcClass + " "));
        srcStackQuery.minimumShouldMatch(1);
        boolQuery.must(srcStackQuery);
        return queryUsecases(boolQuery);
    }

    @Override
    public List<TableToUsecase> getByTable(String projectId, String databaseName, String tableName) {
        return new ArrayList<>();
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
