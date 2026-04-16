package com.oAT.web.service.impl;

import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.SnapshotSearchResult;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SnapshotSearchServiceImpl implements SnapshotSearchService {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public SearchPage<SnapshotSearchResult> doSearch(String projectId, String keyWords) {
        NativeSearchQueryBuilder searchBuilder = new NativeSearchQueryBuilder();
        searchBuilder.withFilter(QueryBuilders.termQuery("projectId", projectId));

        String[] searchFields = new String[]{
                "title",
                "subTitle",
                "describe",
                "codes",
        };
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.should(QueryBuilders.multiMatchQuery(keyWords, searchFields));
        NestedQueryBuilder sqlQuery = QueryBuilders.nestedQuery(
                "sqls",
                QueryBuilders.matchQuery("sqls.content", keyWords),
                ScoreMode.Avg
        );
        boolQuery.should(sqlQuery);
        NestedQueryBuilder remotesQuery = QueryBuilders.nestedQuery(
                "remotes",
                QueryBuilders.matchQuery("remotes.invokerInterface", keyWords),
                ScoreMode.Avg
        );
        boolQuery.should(remotesQuery);
        searchBuilder.withQuery(boolQuery);

        SearchHits<SystemSnapshot> searchHits = elasticsearchOperations.search(searchBuilder.build(), SystemSnapshot.class);
        List<SnapshotSearchResult> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        SearchPage<SnapshotSearchResult> searchPage = new SearchPage<>();
        searchPage.setContents(results);
        searchPage.setTotal(searchHits.getTotalHits());
        return searchPage;
    }

    private SnapshotSearchResult toSearchResult(SystemSnapshot snapshot) {
        SnapshotSearchResult result = new SnapshotSearchResult();
        result.setId(snapshot.getId());
        result.setProjectId(snapshot.getProjectId());
        result.setTitle(snapshot.getTitle());
        result.setDirectoryId(snapshot.getDirectory());
        result.setAppId(snapshot.getAppId());
        result.setHeadImage(snapshot.getTopicImage());
        result.setSubTitle(snapshot.getSubTitle());
        if (snapshot.getVersionLastUpdate() != null) {
            result.setUpdateTime(snapshot.getVersionLastUpdate());
        }
        return result;
    }

    @Override
    public List<SystemSnapshot> searchByTable(String projectId, String databaseName, String tableName) {
        Assert.hasText(databaseName, "参数databaseName 不能为空");
        Assert.hasText(tableName, "参数tableName 不能为空");

        BoolQueryBuilder masterQuery = QueryBuilders.boolQuery();
        masterQuery.must(QueryBuilders.termQuery("projectId", projectId));
        masterQuery.must(QueryBuilders.termQuery("disable", false));

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.termQuery("sqls.DataBase", databaseName));
        boolQuery.must(QueryBuilders.termQuery("sqls.actions.table", tableName));

        masterQuery.must(QueryBuilders.nestedQuery("sqls", boolQuery, ScoreMode.Avg));

        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFilter(masterQuery);

        SearchHits<SystemSnapshot> searchHits = elasticsearchOperations.search(builder.build(), SystemSnapshot.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    @Override
    public List<SystemSnapshot> searchByCode(String projectId, String className, String... methodName) {
        return searchByCode(projectId, null, className, methodName);
    }

    @Override
    public List<SystemSnapshot> searchByCode(String projectId, String appId, String className, String... methodName) {
        Assert.hasText(projectId, "参数'projectId'不能为空");
        Assert.hasText(className, "参数'className'不能为空");

        List<String> normalizedMethods = normalizeMethodNames(methodName);
        boolean probeOnly = normalizedMethods.size() == 1 && "__oat_probe__".equals(normalizedMethods.get(0));
        List<String> queryCandidates = probeOnly ? Collections.emptyList() : buildCodeQueryCandidates(className, normalizedMethods);

        BoolQueryBuilder masterQuery = QueryBuilders.boolQuery();
        masterQuery.must(QueryBuilders.termQuery("projectId", projectId));
        masterQuery.must(QueryBuilders.termQuery("disable", false));
        if (StringUtils.hasText(appId)) {
            masterQuery.must(QueryBuilders.termQuery("appId", appId));
        }
        if (probeOnly) {
            // 仅用于统计当前应用下快照数量，不附加 codes 条件
        } else if (!queryCandidates.isEmpty()) {
            BoolQueryBuilder codeQuery = QueryBuilders.boolQuery();
            for (String candidate : queryCandidates) {
                codeQuery.should(QueryBuilders.termQuery("codes.keyword", candidate));
                codeQuery.should(QueryBuilders.prefixQuery("codes.keyword", candidate + " "));
                codeQuery.should(QueryBuilders.prefixQuery("codes.keyword", candidate + "("));
                String simpleMethod = extractSimpleMethodName(candidate.substring(candidate.lastIndexOf(' ') + 1));
                if (StringUtils.hasText(simpleMethod)) {
                    codeQuery.should(QueryBuilders.wildcardQuery("codes.keyword", className + " *" + simpleMethod + "*"));
                }
            }
            codeQuery.minimumShouldMatch(1);
            masterQuery.must(codeQuery);
        } else {
            BoolQueryBuilder classQuery = QueryBuilders.boolQuery();
            classQuery.should(QueryBuilders.prefixQuery("codes.keyword", className));
            classQuery.should(QueryBuilders.wildcardQuery("codes.keyword", className + " *"));
            classQuery.minimumShouldMatch(1);
            masterQuery.must(classQuery);
        }

        NativeSearchQueryBuilder builder = new NativeSearchQueryBuilder();
        builder.withFilter(masterQuery);

        SearchHits<SystemSnapshot> searchHits = elasticsearchOperations.search(builder.build(), SystemSnapshot.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private List<String> normalizeMethodNames(String... methodName) {
        if (ArrayUtils.isEmpty(methodName)) {
            return Collections.emptyList();
        }
        return Arrays.stream(methodName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> buildCodeQueryCandidates(String className, List<String> methodNames) {
        if (methodNames == null || methodNames.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String method : methodNames) {
            candidates.add(className + " " + method);
            String simpleMethod = extractSimpleMethodName(method);
            if (StringUtils.hasText(simpleMethod) && !simpleMethod.equals(method)) {
                candidates.add(className + " " + simpleMethod);
            }
        }
        return new ArrayList<>(candidates);
    }

    private String extractSimpleMethodName(String methodName) {
        if (!StringUtils.hasText(methodName)) {
            return methodName;
        }
        int dotIndex = methodName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < methodName.length() - 1) {
            return methodName.substring(dotIndex + 1);
        }
        return methodName;
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
        builder.withFilter(masterQuery);

        SearchHits<SystemSnapshot> searchHits = elasticsearchOperations.search(builder.build(), SystemSnapshot.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }
}
