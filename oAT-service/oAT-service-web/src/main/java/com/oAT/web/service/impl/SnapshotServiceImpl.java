package com.oAT.web.service.impl;

import com.oAT.agent.model.*;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.TraceNodeRepository;
import com.oAT.web.esDao.TraceSummaryRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.esDao.entity.Snapshot;
import com.oAT.web.esDao.entity.TraceNodeIndex;
import com.oAT.web.esDao.entity.TraceSummaryIndex;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.entity.SnapshotVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class SnapshotServiceImpl implements SnapshotService{

    private static final Logger logger = LoggerFactory.getLogger(SnapshotServiceImpl.class);
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    CaseCenterRepository centerRepository;
    @Autowired
    TraceNodeRepository traceNodeRepository;
    @Autowired
    TraceSummaryRepository traceSummaryRepository;
    @Autowired
    private UsecaseService usecaseService;
    @Autowired
    private com.oAT.web.service.ApiEndpointAnalysisService apiEndpointAnalysisService;

    @Override
    public SnapshotVo addSnapshot(Snapshot snapshot, Collection<TraceNode> nodes) {

        List<TraceNodeIndex> list = new ArrayList<>();
        for (TraceNode node : nodes) {
            list.add(new TraceNodeIndex(node));
        }

        // 批量保存 TraceNode
        if (!list.isEmpty()) {
            traceNodeRepository.saveAll(list);
        }

        // 如果快照对象本身没有记录 appId，尝试从上传的节点中提取第一个有效的 appId 进行关联
        if (!StringUtils.hasText(snapshot.getAppId()) && !nodes.isEmpty()) {
            for (TraceNode node : nodes) {
                if (node.getApp() != null && StringUtils.hasText(node.getApp().getAppId())) {
                    snapshot.setAppId(node.getApp().getAppId());
                    break;
                }
            }
        }

        // 生成并保存 TraceSummary
        if (!nodes.isEmpty()) {
            try {
                TraceSummaryIndex summary = buildTraceSummary(nodes, snapshot.getProjectId());
                traceSummaryRepository.save(summary);
            } catch (Exception e) {
                logger.error("Failed to save trace summary for traceId: {}", snapshot.getTraceId(), e);
            }
        }

        // 保存快照
        CaseCenterIndex index = new CaseCenterIndex(snapshot);
        index = centerRepository.save(index);
        return convert(index);
    }

    private TraceSummaryIndex buildTraceSummary(Collection<TraceNode> nodes, String projectId) {
        TraceSummaryIndex summary = new TraceSummaryIndex();
        
        HttpTraceNode rootHttp = null;
        int sqlCount = 0;
        int remoteCount = 0;
        int redisCount = 0;
        int mqCount = 0;
        int errorCount = 0;
        
        for (TraceNode node : nodes) {
            if (node instanceof HttpTraceNode && "0".equals(node.getTraceNodeId())) {
                rootHttp = (HttpTraceNode) node;
            }
            
            if (node instanceof SqlTraceNode || node instanceof CKSqlTraceNode) {
                sqlCount++;
            } else if (node instanceof DubboTraceNode || node instanceof FeignTraceNode || 
                       node instanceof HttpClientTraceNode || node instanceof SofaRpcTraceNode) {
                remoteCount++;
            } else if (node instanceof RedisTraceNode) {
                redisCount++;
            } else if (node instanceof RabbitMQTraceNode || node instanceof RocketMQProducerTraceNode || 
                       node instanceof KafkaMQTraceNode) {
                mqCount++;
            }
            
            if (node instanceof StatementError && ((StatementError) node).getError() != null) {
                errorCount++;
            }
        }
        
        if (rootHttp != null) {
            summary.setTraceId(rootHttp.getTraceId());
            summary.setProjectId(projectId);
            summary.setSessionId(rootHttp.getSessionId());
            summary.setStatus(rootHttp.getStatus());
            summary.setHasError(errorCount > 0);
            summary.setHttpMethod(rootHttp.getRequestMethod());
            summary.setHttpUrl(rootHttp.getRequestUrl());
            summary.setHttpResponseCode(rootHttp.getResponseCode());
            summary.setHttpClientIp(rootHttp.getClientIp());
            summary.setHttpServerIp(rootHttp.getServerIp());
            summary.setHttpServerPort(rootHttp.getServerPort());
            summary.setHttpAjax(rootHttp.getAjax());
            summary.setBeginTime(rootHttp.getBeginTime());
            summary.setEndTime(rootHttp.getEndTime());
            summary.setUseTime(rootHttp.getUseTime());
            summary.setCreateTime(new Date());
            
            if (rootHttp.getApp() != null) {
                summary.setAppId(rootHttp.getApp().getAppId());
                summary.setAppName(rootHttp.getApp().getAppName());
            }
            
            if (rootHttp.getRequestUrl() != null) {
                String url = rootHttp.getRequestUrl();
                int queryIndex = url.indexOf('?');
                summary.setHttpUrlPath(queryIndex > 0 ? url.substring(0, queryIndex) : url);
            }
        }
        
        summary.setNodeCount(nodes.size());
        summary.setSqlCount(sqlCount);
        summary.setRemoteCount(remoteCount);
        summary.setRedisCount(redisCount);
        summary.setMqCount(mqCount);
        summary.setErrorCount(errorCount);
        
        return summary;
    }

    public boolean existsByProjectUserAndTraceId(String projectId, String userId, String traceId) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(userId) || !StringUtils.hasText(traceId)) {
            return false;
        }
        List<CaseCenterIndex> exists = centerRepository.findBySnapshot_ProjectIdAndSnapshot_CreateUserAndSnapshot_TraceId(
                projectId,
                userId,
                traceId,
                PageRequest.of(0, 1)
        );
        return exists != null && !exists.isEmpty();
    }

    @Override
    public List<SnapshotVo> findSnapshot(String projectId, String userId, String sort, String keyword) {
        if ("name".equals(sort)) {
            sort = "snapshot.name.keyword";
        } else if (sort == null) {
            sort = "updateTime";
        }
        List<CaseCenterIndex> list = centerRepository.findBySnapshot_ProjectIdAndSnapshot_CreateUser(projectId, userId
                , PageRequest.of(0, 500, Sort.Direction.DESC, sort));
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim().toLowerCase();
            list = list.stream()
                    .filter(index -> index.getSnapshot() != null && StringUtils.hasText(index.getSnapshot().getName()))
                    .filter(index -> index.getSnapshot().getName().toLowerCase().contains(normalizedKeyword))
                    .toList();
        }
        List<SnapshotVo> result = new ArrayList<>(list.size());
        for (CaseCenterIndex index : list) {
            result.add(convert(index));
        }
        return result;
    }

    @Override
    public List<SnapshotVo> findSnapshot(String projectId, String userId, String sort) {
        return findSnapshot(projectId, userId, sort, null);
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
        usecaseService.removeSnapshotRelation(id);
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
            BeanUtils.copyProperties(snapshot, old.getSnapshot(), "traceId", "createUser", "projectId", "appId");
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
        Assert.hasText(nodeId, "参数'nodeId'不能为空");
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
        vo.setCreateTimeText(formatDateTime(index.getCreateTime()));
        if (index.getUpdateTime() != null) {
            vo.setUpdateTime(index.getUpdateTime());
        } else {
            vo.setUpdateTime(index.getCreateTime());
        }
        vo.setUpdateTimeText(formatDateTime(vo.getUpdateTime()));
        vo.setUpdateTimeRelativeText(formatRelativeTime(vo.getUpdateTime()));
        fillApiCoverage(vo);
        return vo;
    }

    private void fillApiCoverage(SnapshotVo vo) {
        if (!StringUtils.hasText(vo.getAppId()) || !StringUtils.hasText(vo.getTraceId())) {
            vo.setApiCoveredCount(0);
            vo.setApiTotalCount(0);
            vo.setApiCoverageText("0 / 0");
            return;
        }
        com.oAT.web.service.entity.ApiEndpointCoverageVo coverage = apiEndpointAnalysisService.calculateCoverage(vo.getAppId(), vo.getTraceId());
        vo.setApiCoveredCount(coverage.getCoveredCount());
        vo.setApiTotalCount(coverage.getTotalCount());
        vo.setApiCoverageText(coverage.getDisplayText());
    }

    private String formatDateTime(Date date) {
        if (date == null) {
            return "-";
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
    }

    private String formatRelativeTime(Date date) {
        if (date == null) {
            return "-";
        }
        return com.oAT.web.common.DateUtil.timeDifference(date) + "前";
    }

}
