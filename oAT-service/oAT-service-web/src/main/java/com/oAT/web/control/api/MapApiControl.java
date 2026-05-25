package com.oAT.web.control.api;

import com.oAT.web.control.MapControl;
import com.oAT.web.domain.ImageElement;
import com.oAT.web.service.entity.DatabaseTable;
import com.oAT.web.exceptions.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/map")
public class MapApiControl {

    private final MapControl mapControl;

    public MapApiControl(MapControl mapControl) {
        this.mapControl = mapControl;
    }

    @GetMapping("/home")
    public List<ImageElement> home(@PathVariable String projectId) {
        return mapControl.getHomeMapData(projectId);
    }

    @GetMapping("/apps/{appId}")
    public List<ImageElement> app(@PathVariable String projectId,
                                  @PathVariable String appId,
                                  @RequestParam(required = false) String layers) throws BusinessException {
        return mapControl.getAppMapData(projectId, appId, layers);
    }

    @GetMapping("/code")
    public List<ImageElement> code(@PathVariable String projectId,
                                   @RequestParam String traceId) {
        return mapControl.getMapStackCodeNode(traceId, projectId);
    }


    @GetMapping("/layers/app-snapshots")
    public List<ImageElement> appSnapshots(@PathVariable String projectId,
                                           @RequestParam String appId) {
        return mapControl.getAppSnapshotNode(projectId, appId);
    }

    @GetMapping("/layers/snapshots/{snapshotId}/tables")
    public List<ImageElement> snapshotTables(@PathVariable String projectId,
                                             @PathVariable String snapshotId) {
        return mapControl.getSnapshotTableNode(projectId, snapshotId);
    }

    @GetMapping("/layers/snapshots/{snapshotId}/remote")
    public List<ImageElement> snapshotRemote(@PathVariable String projectId,
                                             @PathVariable String snapshotId) {
        return mapControl.getSnapshotRemoteNode(projectId, snapshotId);
    }

    @GetMapping("/layers/snapshots/{snapshotId}/code")
    public List<ImageElement> snapshotCode(@PathVariable String projectId,
                                           @PathVariable String snapshotId) {
        return mapControl.getStackCodeNode(projectId, snapshotId);
    }

    @GetMapping("/layers/tables/snapshots")
    public List<ImageElement> tableSnapshots(@PathVariable String projectId,
                                             @RequestParam String database,
                                             @RequestParam String table) {
        DatabaseTable databaseTable = new DatabaseTable();
        databaseTable.setDatabase(database);
        databaseTable.setTable(table);
        return mapControl.getTableSnapshotNode(projectId, databaseTable);
    }

    @GetMapping("/layers/dubbo/snapshots")
    public List<ImageElement> dubboSnapshots(@PathVariable String projectId,
                                             @RequestParam String interfaceName,
                                             @RequestParam String methodName) {
        return mapControl.getDubboSnapshotNode(projectId, interfaceName, methodName);
    }
}
