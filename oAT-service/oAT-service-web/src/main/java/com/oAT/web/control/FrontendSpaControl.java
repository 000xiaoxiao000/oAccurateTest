package com.oAT.web.control;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendSpaControl {

    @GetMapping({
            "/projects",
            "/account",
            "/p/{projectId}/ai",
            "/p/{projectId}/apps",
            "/p/{projectId}/apps/online",
            "/p/{projectId}/apps/{appId}/settings",
            "/p/{projectId}/apps/{appId}/probe-alerts",
            "/p/{projectId}/apps/{appId}/repository",
            "/p/{projectId}/apps/{appId}/api-endpoints",
            "/p/{projectId}/version/apps",
            "/p/{projectId}/apps/{appId}/versions",
            "/p/{projectId}/apps/{appId}/versions/new",
            "/p/{projectId}/apps/{appId}/compare",
            "/p/{projectId}/version/reports/{reportId}",
            "/p/{projectId}/coverage",
            "/p/{projectId}/apps/{appId}/coverage",
            "/p/{projectId}/apps/{appId}/coverage/details",
            "/p/{projectId}/apps/{appId}/coverage/code",
            "/p/{projectId}/map/home",
            "/p/{projectId}/map/app/{appId}",
            "/p/{projectId}/map/code",
            "/p/{projectId}/search",
            "/p/{projectId}/apps/{appId}/snapshots",
            "/p/{projectId}/apps/{appId}/snapshots/{snapshotId}",
            "/p/{projectId}/apps/{appId}/snapshots/{snapshotId}/report",
            "/p/{projectId}/apps/{appId}/snapshots/{snapshotId}/report/code",
            "/p/{projectId}/apps/{appId}/snapshots/{snapshotId}/graph",
            "/p/{projectId}/my-snapshots",
            "/p/{projectId}/my-snapshots/code-report",
            "/p/{projectId}/my-snapshots/code",
            "/p/{projectId}/my-snapshots/{snapshotId}",
            "/p/{projectId}/my-snapshots/{snapshotId}/report",
            "/p/{projectId}/my-snapshots/{snapshotId}/report/code",
            "/p/{projectId}/my-snapshots/{snapshotId}/graph",
            "/p/{projectId}/usecases",
            "/p/{projectId}/usecases/new",
            "/p/{projectId}/usecases/{usecaseId}",
            "/p/{projectId}/usecases/{usecaseId}/edit",
            "/p/{projectId}/members",
            "/p/{projectId}/labels"
    })
    public String index() {
        return "forward:/index.html";
    }
}
