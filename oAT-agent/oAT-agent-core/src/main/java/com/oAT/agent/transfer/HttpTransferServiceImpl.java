package com.oAT.agent.transfer;

import com.oAT.agent.common.HttpClient;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceContext;
import com.oAT.server.model.ClientSessionVo;

import java.util.HashMap;
import java.util.Map;

public class HttpTransferServiceImpl implements TransferService {
    private static final Log logger = LogFactory.getLog(HttpTransferServiceImpl.class);
    private static final String NULL_DATA_PLACEHOLDER = "null";
    private static final String JSON_ERROR_PLACEHOLDER = "[JsonError]";

    private final String[] uploadPaths;
    private final TraceContext traceContext;
    private final String clientSessionId;

    public HttpTransferServiceImpl(TraceContext traceContext) {
        if (traceContext == null || traceContext.getRemoteServer() == null || traceContext.getRemoteServer().isEmpty()) {
            logger.warn("[Agent-warn] TraceContext or remote server is null/empty");
            this.traceContext = null;
            this.clientSessionId = null;
            this.uploadPaths = null;
            return;
        }

        this.traceContext = traceContext;
        ClientSessionVo clientSession = traceContext.getClientSession();
        if (clientSession == null || clientSession.getSessionId() == null || clientSession.getSessionId().isEmpty()) {
            logger.warn("[Agent-warn] ClientSession or sessionId is null");
            this.clientSessionId = null;
            this.uploadPaths = new String[]{traceContext.getRemoteServer() + "/client/upload"};
            return;
        }

        this.clientSessionId = clientSession.getSessionId();
        this.uploadPaths = new String[]{traceContext.getRemoteServer() + "/client/upload"};
    }

    @Override
    public void uploadNode(final String traceId, final String type, Object data) {
        if (uploadPaths == null || uploadPaths.length == 0) {
            logger.warn("[Agent-warn]不可上传节点数据，上传路径未配置");
            return;
        }

        final String uploadPath = uploadPaths[0];
        final String sessionId = StringUtils.isEmpty(this.clientSessionId)
                ? traceContext.getClientSession().getSessionId()
                : this.clientSessionId;
        if (StringUtils.isEmpty(sessionId)) {
            logger.warn("[Agent-warn]不可上传节点数据，sessionId 为空，type: " + type);
            return;
        }

        String dataJson;
        if (data != null) {
            try {
                dataJson = JsonUtil.toJson(data);
            } catch (Throwable jsonEx) {
                dataJson = JSON_ERROR_PLACEHOLDER;
                logger.error(String.format("[Agent-EXCError]对象转JSON失败，但可上传节点数据，" +
                                "traceId=%s, sessionId=%s, type: %s, data:%s, 异常: %s",
                        traceId, sessionId, type, dataJson, jsonEx));
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("[Agent-debug]Preview 可上传，节点数据 data 为空，" +
                                "traceId=%s, sessionId=%s, type: %s", traceId, sessionId, type));
            }
            dataJson = NULL_DATA_PLACEHOLDER;
        }

        try {
            Map<String, String> params = new HashMap<String, String>(4);
            params.put("type", type);
            params.put("sessionId", sessionId);
            params.put("data", dataJson);

            int byteSize = dataJson.length();
            double mbSize = byteSize / (1024.0 * 1024.0);
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("[Agent-debug]Preview 可上传的节点信息，" +
                                "traceId=%s, sessionId: %s, traceNode: %s",
                        traceId, sessionId, dataJson));
            }
            logger.info(String.format("[Agent-info]Preview 可上传的节点信息，" +
                            "traceId=%s, sessionId: %s, dataSize: %.4f MB",
                    traceId, sessionId, mbSize));

            HttpClient.execHttp(uploadPath, params, new HttpClient.HttpCallback() {
                @Override
                public void onComplete(String resp, Throwable err) {
                    if (err != null) {
                        logger.error(String.format("[Agent-EXCError]上传节点数据失败: " +
                                        "url=%s, sessionId=%s, type=%s, 异常: %s",
                                uploadPath, sessionId, type, err.getMessage()));
                    } else {
                        logger.info(String.format("[Agent-succeed]上传节点数据成功: " +
                                        "resp=%s, traceId=%s, sessionId=%s, type=%s, url=%s",
                                resp, traceId, sessionId, type, uploadPath));
                    }
                }
            });
        } catch (Throwable e) {
            logger.error(String.format("[Agent-EXCError]上传时处理异常: traceId=%s, sessionId=%s, type=%s, url=%s, 异常: %s",
                    traceId, sessionId, type, uploadPath, e.getMessage()));
        }
    }
}
