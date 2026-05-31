package com.oAT.web.control.api;

import com.oAT.web.control.MonitorControl;
import com.oAT.web.service.entity.TraceItemSearchParam;
import com.oAT.web.control.entity.GraphView;
import com.oAT.web.control.entity.ResultNotified;
import com.oAT.web.control.api.SnapshotApiControl.GraphNodeDetailPayload;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.TraceItemVo;
import com.oAT.web.service.entity.UserVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/monitor")
public class MonitorApiControl {

    private final MonitorControl monitorControl;

    public MonitorApiControl(MonitorControl monitorControl) {
        this.monitorControl = monitorControl;
    }

    @GetMapping("/probeStatus")
    public List<Map<String, Object>> probeStatus(@PathVariable String projectId) {
        return monitorControl.getProbeStatus(projectId);
    }

    @GetMapping("/getNodeByIndex")
    public List<TraceItemVo> getNodeByIndex(@PathVariable String projectId,
                                            @RequestParam(required = false) Integer lastIndex,
                                            @RequestParam(required = false) Integer maxSize) {
        return monitorControl.getNodeByIndex(projectId, lastIndex, maxSize);
    }

    @GetMapping("/getNodeByTime")
    public TraceItemVo[] getNodeByTime(@PathVariable String projectId,
                                       Integer upToTime,
                                       TraceItemSearchParam filter) {
        return monitorControl.getNodeByTime(projectId, upToTime, filter);
    }

    @GetMapping("/getTraceGraph")
    public GraphView getTraceGraph(@PathVariable String projectId,
                                   @RequestParam String traceId) {
        return monitorControl.getTraceGraph(projectId, traceId);
    }

    @GetMapping("/getTraceGraphNode")
    public ResultNotified<GraphNodeDetailPayload> getTraceGraphNode(@PathVariable String projectId,
                                                                    @RequestParam String traceId,
                                                                    @RequestParam String nodeId) {
        return monitorControl.getTraceGraphNode(projectId, traceId, nodeId);
    }

    @GetMapping("/system-snapshot-context")
    public ResultNotified<MonitorControl.SystemSnapshotContextPayload> systemSnapshotContext(@PathVariable String projectId,
                                                                                            @SessionAttribute UserVo user,
                                                                                            @RequestParam String traceId) {
        return monitorControl.getSystemSnapshotContext(projectId, user, traceId);
    }

    @PostMapping("/system-snapshots")
    public ResultNotified<String> saveSystemSnapshot(SystemSnapshot snapshot,
                                                     @PathVariable String projectId,
                                                     @SessionAttribute UserVo user,
                                                     String traceId) {
        return monitorControl.doSaveSystemSnapshot(snapshot, projectId, user, traceId);
    }

    @PostMapping("/autoSaveSystemSnapshot")
    public ResultNotified<String> autoSaveSystemSnapshot(@PathVariable String projectId,
                                                         @SessionAttribute UserVo user,
                                                         @RequestParam String traceId,
                                                         @RequestParam(required = false) String title) {
        return monitorControl.autoSaveSystemSnapshot(projectId, user, traceId, title);
    }
}
