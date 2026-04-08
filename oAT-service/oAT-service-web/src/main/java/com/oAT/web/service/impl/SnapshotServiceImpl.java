package com.oAT.web.service.impl;

import com.oAT.agent.model.TraceNode;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.Snapshot;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.entity.SnapshotVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class SnapshotServiceImpl implements SnapshotService{

    @Autowired
    CaseCenterRepository centerRepository;
    @Autowired
    TraceNodeRepository traceNodeRepository;

    @Override
    public SnapshotVo addSnapshot(Snapshot snapshot, Collection<TraceNode> nodes) {

        List<TraceNodeIndex> list = new ArrayList<>();
        for (TraceNode node : nodes) {
            list.add(new TraceNodeIndex(node));
        }

        // 批量保存 TraceNode。对同一个 traceId_nodeId 执行覆盖保存，避免旧节点缺失请求参数等字段。
        if (!list.isEmpty()) {
            traceNodeRepository.saveAll(list);
        }

        // 如果快照对象本身没有记录 appId，尝试从上传的节点中提取第一个有效的 appId 进行关联
        if (!StringUtils.hasText(snapshot.getAppId()) && nodes != null && !nodes.isEmpty()) {
            for (TraceNode node : nodes) {
                if (node.getApp() != null && StringUtils.hasText(node.getApp().getAppId())) {
                    snapshot.setAppId(node.getApp().getAppId());
                    break;
                }
            }
        }

        // 保存快照
        CaseCenterIndex index = new CaseCenterIndex(snapshot);
        index = centerRepository.save(index);
        return convert(index);
    }

    @Override
    public List<SnapshotVo> findSnapshot(String projectId, String userId, String sort) {
        if ("name".equals(sort)) {
            sort = "snapshot.name.keyword";
        } else if (sort == null) {
            sort = "updateTime";
        }
        List<CaseCenterIndex> list = centerRepository.findBySnapshot_ProjectIdAndSnapshot_CreateUser(projectId, userId
                , PageRequest.of(0, 500, Sort.Direction.DESC, sort));
        List<SnapshotVo> result = new ArrayList<>(list.size());
        for (CaseCenterIndex index : list) {
            result.add(convert(index));
        }
        return result;
    }

    @Override
    public List<SnapshotVo> findSnapshot(String projectId, String userId) {
        return findSnapshot(projectId, userId, null);
    }

    @Override
    public void deleteById(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        centerRepository.deleteById(id);
    }

    @Override
    public SnapshotVo get(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        Optional<CaseCenterIndex> indexItem = centerRepository.findById(id);
        return indexItem.isPresent() ? convert(indexItem.get()) : null;
    }

    @Override
    public List<SnapshotVo> getByIds(String[] ids) {
        Iterable<CaseCenterIndex> list = centerRepository.findAllById(Arrays.asList(ids));
        List<SnapshotVo> result = new ArrayList<>();
        for (CaseCenterIndex caseCenterIndex : list) {
            result.add(convert(caseCenterIndex));
        }
        return result;
    }

    @Override
    public void doUpdate(String id, Snapshot snapshot) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        Optional<CaseCenterIndex> indexItem = centerRepository.findById(id);
        if (indexItem.isPresent()) {
            CaseCenterIndex old = indexItem.get();
            //将快照中新值 替换旧属性
            BeanUtils.copyProperties(snapshot, old.getSnapshot(), "traceId", "createUser");
            old.setUpdateTime(new java.util.Date());
            centerRepository.save(old);
        }
    }

    @Override
    public Collection<TraceNode> getTraceNodes(String traceId) {
        List<TraceNodeIndex> list = traceNodeRepository.findByTraceId(traceId, PageRequest.of(0, 200));
        List<TraceNode> result = new ArrayList<>(list.size());
        for (TraceNodeIndex traceNodeIndex : list) {
            result.add(traceNodeIndex.toTraceNode());
        }
        return result;
    }

    @Override
    public TraceNode getTraceNode(String traceId, String nodeId) {
        Assert.hasText(traceId, "参数'traceId'不能为空");
        Assert.hasText(traceId, "参数'nodeId'不能为空");
        Optional<TraceNodeIndex> optional = traceNodeRepository.findById(traceId + "_" + nodeId);
        TraceNodeIndex index = optional.orElseThrow(() ->
                new IllegalArgumentException(String.format("找不到指定数据 traceId=%s nodeId=%s", traceId, nodeId)));
        return index.toTraceNode();
    }


    @Override
    public void setShareState(String userId, String snapshotId, Boolean share) {
        if (!StringUtils.hasText(snapshotId)) {
            return;
        }
        Optional<CaseCenterIndex> op = centerRepository.findById(snapshotId);
        Assert.isTrue(op.isPresent(), "找不到指定快照 id=" + snapshotId);
        Assert.notNull(op.get().getSnapshot(), "找不到指定快照 id=" + snapshotId);
        Assert.isTrue(op.get().getSnapshot().getCreateUser().equals(userId), "只有快照的创建人才有权限发起共享");
        if (!share.equals(op.get().getSnapshot().getShare())) {
            op.get().getSnapshot().setShare(share);
            centerRepository.save(op.get());
        }
    }


    private SnapshotVo convert(CaseCenterIndex index) {
        SnapshotVo vo = new SnapshotVo(index.getId());
        BeanUtils.copyProperties(index.getSnapshot(), vo);
        vo.setId(index.getId());
        vo.setCreateTime(index.getCreateTime());
        if (index.getUpdateTime() != null) {
            vo.setUpdateTime(index.getUpdateTime());
        } else {
            vo.setUpdateTime(index.getCreateTime());
        }
        return vo;
    }

}
