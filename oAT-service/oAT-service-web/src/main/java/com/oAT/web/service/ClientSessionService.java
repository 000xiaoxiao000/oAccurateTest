package com.oAT.web.service;

import com.oAT.agent.model.TraceNode;
import com.oAT.server.model.ClientInfoVo;
import com.oAT.server.model.ClientSessionVo;
import com.oAT.web.service.entity.TraceItemSearchParam;
import com.oAT.web.service.entity.TraceItemVo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface ClientSessionService {

    void putTraceNode(TraceNode node);

    Map<String, TraceNode> getTraceNodes(String traceId);

    /**
     * 基于最后更新索引 获取指定数量节点，
     *
     * @param lastUpdateIndex 最后更新索引
     * @param after           true时间刻度之前的节点 即旧节点,false时间刻度之后的节点 即新节点
     * @param size
     * @param appIds
     * @return
     */
    List<TraceItemVo> getTraceItemByIndex(Integer lastUpdateIndex, boolean after, Integer size, List<String> appIds);

    List<TraceItemVo> getTraceItemByTime(Integer upToTime, TraceItemSearchParam param);

    ClientSessionVo getClientSession(String sessionId);

    ClientSessionVo doLogin(ClientInfoVo clientInfo);
    // 基于项目ID查找指定应用
    List<ClientSessionVo> getOnlineSessions();

    List<ClientSessionVo> getOnlineSessionsByAppId(String appId);

    void heartbeat(String sessionId, String appId, Long timesTamp);

    void saveStaticData(String appId, String data);

    // agent插桩日志根据sessionId存储
    void putAgentLogs(String sessionId, String readAgentLogs);

    // 将包验证信息存储到ES
    void putPackageVerify(String sessionId, String packagePath, String gitCommitIdFromPackage);

    String getPackageVerifyData(String sessionId);

    String getLatestPackageVerifyDataByAppId(String appId);
}
