package com.oAT.web.service;

import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.entity.Snapshot;
import com.oAT.web.service.entity.SnapshotVo;

import java.util.Collection;
import java.util.List;

public interface SnapshotService {

    SnapshotVo addSnapshot(Snapshot snapshot, Collection<TraceNode> nodes);

    List<SnapshotVo> findSnapshot(String projectId, String userId, String sort);

    List<SnapshotVo> findSnapshot(String projectId, String userId);

    void deleteById(String id);

    SnapshotVo get(String id);

    List<SnapshotVo> getByIds(String[] ids);

    void doUpdate(String id, Snapshot snapshot);

    Collection<TraceNode> getTraceNodes(String traceId);

    TraceNode getTraceNode(String traceId, String nodeId);

    void setShareState(String userId, String snapshotId, Boolean share);

}
