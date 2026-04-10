package com.oAT.agent.jacoco.data;

import com.oAT.agent.Agent;
import com.oAT.agent.common.HttpClient;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceContext;
import com.oAT.server.model.ClientSessionVo;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在原有紧凑写结构基础上，首次写入前上报全量静态代码结构（类/方法/行号/分支等）逻辑：
 *  1. 通过 {@link CompactDataInput#getAllClassStaticInfo()} + exportAsJson() 获取 JSON
 *  2. 复用已有 /client/upload 接口（与 TraceNode 上报一致），参数: type=StaticCodeInfo, sessionId, data
 *  3. 仅上报一次（AtomicBoolean 幂等控制），失败不影响原写流程
 */
public class CompactDataOutput {

    // ================= 静态上报控制 =================
    private static final AtomicBoolean STATIC_UPLOAD_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean STATIC_UPLOAD_COMPLETED = new AtomicBoolean(false);
    private static final Log logger = LogFactory.getLog(CompactDataOutput.class);

    /**
     * 首次写入前尝试上报静态代码信息。
     * 仅在有静态数据时启动上报；仅在上报成功后标记完成，失败允许后续重试。
     */
    public static void trySendStaticInfo() {
        if (STATIC_UPLOAD_COMPLETED.get()) {
            return;
        }
        if (CompactDataInput.getAllClassStaticInfo().isEmpty()) {
            return;
        }
        if (!STATIC_UPLOAD_STARTED.compareAndSet(false, true)) {
            return;
        }
        try {
            String json = CompactDataInput.exportAsJson();
            if (StringUtils.isEmpty(json) || "{}".equals(json)) {
                STATIC_UPLOAD_STARTED.set(false);
                return;
            }
            int byteSize = json.getBytes(StandardCharsets.UTF_8).length;
            final double mbSize = byteSize / (1024.0 * 1024.0);

            TraceContext ctx = Agent.traceContext;
            if (ctx == null) {
                STATIC_UPLOAD_STARTED.set(false);
                logger.warn("[Agent-warn]静态代码信息上报跳过: TraceContext 为 null");
                return;
            }
            String remote = ctx.getRemoteServer();
            if (StringUtils.isEmpty(remote)) {
                STATIC_UPLOAD_STARTED.set(false);
                logger.warn("[Agent-warn]静态代码信息上报跳过: remoteServer 为空");
                return;
            }
            ClientSessionVo session = ctx.getClientSession();
            String appId = (session == null || session.getApplication() == null || StringUtils.isEmpty(session.getApplication().getAppId()))
                    ? "" : session.getApplication().getAppId();
            if (StringUtils.isEmpty(appId)) {
                logger.warn("[Agent-warn]静态代码信息上报: appId 为空，仍尝试发送");
            }

            String uploadUrl = remote + "/client/uploadStaticData?appId=" + URLEncoder.encode(appId, "UTF-8");
            byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);

            logger.info(String.format("[Agent-info]开始上报静态代码信息: dataSize=%.4f MB, classCount=%d, url=%s",
                    mbSize, CompactDataInput.getAllClassStaticInfo().size(), uploadUrl));

            String resp = HttpClient.execHttpRawBody(uploadUrl, "application/json; charset=UTF-8", bodyBytes)
                    .get(30, TimeUnit.SECONDS);
            STATIC_UPLOAD_COMPLETED.set(true);
            logger.info(String.format("[Agent-succeed]静态代码信息上报成功: resp=%s, size=%.4fMB", resp, mbSize));
        } catch (Throwable t) {
            STATIC_UPLOAD_STARTED.set(false);
            try {
                if (t instanceof java.util.concurrent.TimeoutException) {
                    logger.error("[Agent-EXCError]静态代码信息上报超时: 等待超过 30 秒, " + t.getMessage());
                    return;
                }
                logger.error("[Agent-EXCError]静态代码信息上报过程异常: " + t.getMessage(), t);
            } catch (Throwable ignore) {
            }
        }
    }
}
