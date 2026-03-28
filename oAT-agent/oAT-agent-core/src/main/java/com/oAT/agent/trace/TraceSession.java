package com.oAT.agent.trace;

import com.oAT.agent.common.Assert;
import com.oAT.agent.common.NetUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.transfer.TransferService;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 当前会话信息管理
 *
 * @since 0.1.0
 */
public class TraceSession {
    private static final Log logger = LogFactory.getLog(TraceSession.class);

    /**
     * session 节点信息
     */
    private TraceRequest traceRequest;

    /**
     * 生产下个节点的调用Id
     */
    private String nodeId;

    private TransferService transferService;

    private TraceContext traceContext;
    private AtomicInteger serialNumber = new AtomicInteger(0);

    public TraceSession(TraceContext traceContext, TraceRequest traceRequest, TransferService transferService) {
        Assert.notNull(traceContext);
        Assert.notNull(traceRequest);
        Assert.notNull(traceRequest.getParentNodeCallId());

        this.traceContext = traceContext;
        this.traceRequest = traceRequest;
        this.transferService = transferService;
    }

    /**
     * 获取当前nodeId
     */
    public String getCurrentNodeId() {
        return nodeId == null ? "0" : nodeId;
    }

    /**
     * a->b(0.1)-c(0.1.1) 生产下个节点RPCId
     */
    public String getNextNodeId() {
        int next = serialNumber.incrementAndGet();
        nodeId = traceRequest.getParentNodeCallId() + "." + next;
        return nodeId;
    }

    public String getParentNodeId() {
        return traceRequest.getParentNodeCallId();
    }

    /**
     * 获取traceId
     */
    public String getTraceId() {
        return traceRequest.getTraceId();
    }

    public TraceRequest getTraceRequest() {
        return traceRequest;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    /**
     * 开启事件监听
     */
    public Event openEvent() {
        return null;
    }

    /**
     * 事件结束关闭监听
     */
    public void closeEvent(Event event) {
    }

    /**
     * 保存节点信息
     */
    public void saveNode(final TraceNode traceNode) {
        if (traceNode == null) {
            logger.warn("[Agent-warn]节点不上传，traceNode为空");
            return;
        }
        if (traceNode.getTraceNodeId() == null || traceNode.getTraceNodeId().isEmpty()) {
            logger.warn("[Agent-warn]节点不上传，traceNodeId 为空, traceNode: " + traceNode);
            return;
        }
        if (traceNode.getTraceId() == null || traceNode.getTraceId().isEmpty()) {
            logger.warn("[Agent-warn]节点不上传，TraceId 为空");
            return;
        }
        if (traceContext == null) {
            logger.warn("[Agent-warn]节点不上传，traceContext 为空");
            return;
        }
        if (traceContext.getClientSessionId() == null || traceContext.getClientSessionId().isEmpty()) {
            logger.warn("[Agent-warn]节点不上传，sessionId 为空");
            return;
        }
        if (traceNode.getSessionId() == null) {
            traceNode.setSessionId(traceContext.getClientSessionId());
        }
        if (traceNode.getApp() == null) {
            traceNode.setApp(traceContext.getClientSession().getApplication());
        }
        if (traceNode.getAddressIp() == null) {
            traceNode.setAddressIp(NetUtils.getLocalHost());
        }
        transferService.uploadNode(traceNode.getTraceId(), traceNode.getClass().getName(), traceNode);
    }

    public TransferService getTransferService() {
        return transferService;
    }
}
