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

    void asyncCalculateCoverage(String snapshotId);

}
