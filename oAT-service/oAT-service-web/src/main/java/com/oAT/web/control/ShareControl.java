package com.oAT.web.control;

import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SnapshotVo;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/share")
public class ShareControl {

    @Autowired
    SnapshotService snapshotService;
    @Autowired
    ProjectService projectService;

    // 打开快照共享页
    @RequestMapping("/snapshot/{id}")
    public String openSnapshot(@PathVariable String id, Model model) {
        SnapshotVo snapshot = snapshotService.get(id);
        if (snapshot == null) {
            model.addAttribute("errorMessage", "找不到指定快照");
            return "forward:/error/404";
        }
        if (BooleanUtils.isTrue(snapshot.getDisable())) {
            model.addAttribute("errorMessage", "快照已被删除");
            return "forward:/error/404";
        }
        if (!BooleanUtils.isTrue(snapshot.getShare())) {
            model.addAttribute("errorMessage", "当前快照未开放共享");
            return "forward:/error/404";
        }
        ProjectVo project = projectService.getProject(snapshot.getProjectId());
        model.addAttribute("_share", true);
        return "forward:/p/" + project.getId() + "/snapshot/detail/" + id;
    }

    @RequestMapping("/get")
    public String getShareResource(String url, Model model) {
        model.addAttribute("_share", true);
        return "forward:" + url;
    }


    // 打开用例共享页
    private void openUseCase() {}

}
