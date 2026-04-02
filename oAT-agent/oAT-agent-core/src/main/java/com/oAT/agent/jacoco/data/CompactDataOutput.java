/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package com.oAT.agent.jacoco.data;

import com.oAT.agent.Agent;
import com.oAT.agent.common.HttpClient;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.trace.TraceContext;
import com.oAT.server.model.ClientSessionVo;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
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
    private static final AtomicBoolean STATIC_SENT = new AtomicBoolean(false);
    private static final Log logger = LogFactory.getLog(CompactDataOutput.class);

    /**
     * 首次写入前尝试上报静态代码信息。
     * 幂等：仅成功进入 compareAndSet 的线程构造和发送，上报失败不会重试（避免频繁重负载），如需可后续扩展重试机制。
     */
    public void trySendStaticInfo() {
        if (!STATIC_SENT.compareAndSet(false, true)) {
            return; // 已发送
        }
        try {
            // 组装 JSON 数据
            String json = CompactDataInput.exportAsJson();
            int byteSize = json.getBytes(StandardCharsets.UTF_8).length;
            final double mbSize = byteSize / (1024.0 * 1024.0);
            // 全局上下文
            TraceContext ctx = Agent.traceContext;
            if (ctx == null) {
                logger.warn("[Agent-warn]静态代码信息上报跳过: TraceContext 为 null");
                return;
            }
            String remote = ctx.getRemoteServer();
            if (StringUtils.isEmpty(remote)) {
                logger.warn("[Agent-warn]静态代码信息上报跳过: remoteServer 为空");
                return;
            }
            ClientSessionVo session = ctx.getClientSession();
            String appId = (session == null || StringUtils.isEmpty(session.getApplication().getAppId())) ? "" :
                    session.getApplication().getAppId();
            if (StringUtils.isEmpty(appId)) {
                logger.warn("[Agent-warn]静态代码信息上报: sessionId 为空，仍尝试发送");
            }

            // 构造，上传路径/参数
            final String uploadUrl = remote + "/client/uploadStaticData"; // 复用现有服务端处理逻辑
            Map<String, String> params = new HashMap<String, String>(4);
            params.put("appId", appId);
            params.put("data", json);

            logger.info(String.format("[Agent-info]开始上报静态代码信息: dataSize=%.4f MB, classCount=%d, url=%s", mbSize, CompactDataInput.getAllClassStaticInfo().size(), uploadUrl));

            String resp  = HttpClient.execHttp(uploadUrl, params).get(30, TimeUnit.SECONDS);
            logger.info(String.format("[Agent-succeed]静态代码信息上报成功: resp=%s, size=%.4fMB", resp, mbSize));
        } catch (Throwable t) {
            // 捕获所有异常，防止影响原有写流程
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
