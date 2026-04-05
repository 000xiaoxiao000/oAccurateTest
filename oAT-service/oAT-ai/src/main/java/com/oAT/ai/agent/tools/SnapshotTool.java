package com.oAT.ai.agent.tools;

import com.oAT.ai.agent.AgentContext;
import com.oAT.ai.agent.AgentDataProvider;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 快照数据查询工具
 * 提供快照列表、快照详情等查询能力
 */
public class SnapshotTool {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotTool.class);

    private final AgentDataProvider dataProvider;

    public SnapshotTool(AgentDataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Tool("获取项目下的快照列表")
    public String getSnapshots() {
        String projectId = AgentContext.getCurrentProjectId();
        if (projectId == null) {
            return "错误：未找到项目上下文，请先选择一个项目";
        }
        return getSnapshotsInternal(projectId, null);
    }

    @Tool("获取当前用户的快照列表")
    public String getMySnapshots() {
        String projectId = AgentContext.getCurrentProjectId();
        String userId = AgentContext.getCurrentUserId();
        if (projectId == null) {
            return "错误：未找到项目上下文，请先选择一个项目";
        }
        if (userId == null) {
            return "错误：未找到用户上下文";
        }
        return getSnapshotsInternal(projectId, userId);
    }

    private String getSnapshotsInternal(String projectId, String userId) {
        try {
            List<Map<String, Object>> snapshots = dataProvider.getSnapshots(projectId, userId);
            if (snapshots == null || snapshots.isEmpty()) {
                if (userId != null) {
                    return "您还没有创建快照";
                }
                return "当前项目下没有快照";
            }
            StringBuilder sb = new StringBuilder();
            if (userId != null) {
                sb.append("您的快照共 ").append(snapshots.size()).append(" 个：\n\n");
            } else {
                sb.append("项目快照共 ").append(snapshots.size()).append(" 个：\n\n");
            }
            int count = 0;
            for (Map<String, Object> snapshot : snapshots) {
                count++;
                sb.append(count).append(". ").append(snapshot.getOrDefault("name", "未命名快照"));
                sb.append("\n   - ID: ").append(snapshot.getOrDefault("id", ""));
                sb.append("\n   - 创建人: ").append(snapshot.getOrDefault("creator", ""));
                sb.append("\n   - 创建时间: ").append(snapshot.getOrDefault("createTime", ""));
                if (Boolean.TRUE.equals(snapshot.get("shared"))) {
                    sb.append(" [已共享]");
                }
                sb.append("\n");
                if (count >= 15) {
                    sb.append("... 仅显示最近15个快照\n");
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取快照列表失败", e);
            return "获取快照列表失败：" + e.getMessage();
        }
    }

    @Tool("获取快照的详细信息")
    public String getSnapshotDetail(@P("快照ID") String snapshotId) {
        if (snapshotId == null || snapshotId.trim().isEmpty()) {
            return "错误：请提供快照ID";
        }
        try {
            Map<String, Object> snapshot = dataProvider.getSnapshotDetail(snapshotId);
            if (snapshot == null || snapshot.isEmpty()) {
                return "未找到快照信息，ID: " + snapshotId;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("## 快照详情\n\n");
            sb.append("- 快照名称: ").append(snapshot.getOrDefault("name", "未命名")).append("\n");
            sb.append("- 快照ID: ").append(snapshot.getOrDefault("id", "")).append("\n");
            sb.append("- 创建人: ").append(snapshot.getOrDefault("creator", "")).append("\n");
            sb.append("- 创建时间: ").append(snapshot.getOrDefault("createTime", "")).append("\n");
            sb.append("- 共享状态: ").append(Boolean.TRUE.equals(snapshot.get("shared")) ? "已共享" : "私有").append("\n");
            if (snapshot.containsKey("description")) {
                sb.append("- 描述: ").append(snapshot.get("description")).append("\n");
            }
            
            // 关联的调用链信息
            String traceId = (String) snapshot.get("traceId");
            if (traceId != null && !traceId.isEmpty()) {
                sb.append("\n### 关联调用链\n");
                sb.append("- TraceID: ").append(traceId).append("\n");
                sb.append("- 入口URL: ").append(snapshot.getOrDefault("traceUrl", "")).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("获取快照详情失败", e);
            return "获取快照详情失败：" + e.getMessage();
        }
    }
}
