package com.oAT.web.service;

import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.CaseSearchResult;
import com.oAT.web.service.entity.SearchPage;
import com.oAT.web.service.entity.SnapshotSearchResult;

import java.util.List;

public interface SnapshotSearchService {

    SearchPage<SnapshotSearchResult> doSearch(String projectId, String keyWords);

    List<SystemSnapshot> searchByTable(String projectId, String databaseName, String tableName);

    List<SystemSnapshot> searchByCode(String projectId, String className, String... methodName);

    List<SystemSnapshot> searchByDubbo(String projectId, String interfaceName, String... methodName);

}
