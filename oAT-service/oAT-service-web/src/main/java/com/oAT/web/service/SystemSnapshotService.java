package com.oAT.web.service;

import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.entity.SystemSnapshot;

import java.util.Collection;
import java.util.List;

public interface SystemSnapshotService {

    SystemSnapshot getById(String id);

    SystemSnapshot create(String projectId, String userId, SystemSnapshot snapshot, Collection<TraceNode> nodes);

    SystemSnapshot saveBasic(String projectId, String userId, SystemSnapshot source);

    /**
     *
     * @param projectId 项目ID
     * @param appId     应用ID
     * @param directory 对应目录ID
     * @return
     */
    List<SystemSnapshot> findBy(String projectId, String appId, String directory, String keyword);


    List<SystemSnapshot> findAll(String projectId, String appId);

    List<SystemSnapshot> findAll(String projectId);

    void asyncCalculateCoverage(String snapshotId);

    /**
     * 手工补录快照-Commit 关联关系。
     * 为当前 appId + versionNumber 下所有无关联的快照，按版本中心当前 Commit 补录。
     *
     * @param appId         应用ID
     * @param versionNumber 版本号，为空则处理该应用所有快照
     * @return 成功补录的数量
     */
    int backfillCommitMapping(String appId, String versionNumber);

}
