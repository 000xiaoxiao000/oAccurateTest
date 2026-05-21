package com.oAT.web.control;

import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
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
    @Autowired
    CaseCenterRepository centerRepository;

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
        return "forward:/index.html";
    }

    @RequestMapping("/get")
    public String getShareResource(String url, Model model) {
        return "forward:/index.html";
    }


    // 打开用例共享页
    @RequestMapping("/usecase/{id}")
    public String openUseCase(@PathVariable String id, Model model) {
        CaseCenterIndex index = centerRepository.findById(id).orElse(null);
        if (index == null || index.getUsecase() == null) {
            model.addAttribute("errorMessage", "找不到指定用例");
            return "forward:/error/404";
        }
        if (!BooleanUtils.isTrue(index.getUsecase().getShare())) {
            model.addAttribute("errorMessage", "当前用例未开放共享");
            return "forward:/error/404";
        }
        String projectId = index.getUsecase().getProjectId();
        ProjectVo project = projectService.getProject(projectId);
        if (project == null) {
            model.addAttribute("errorMessage", "找不到指定项目");
            return "forward:/error/404";
        }
        return "forward:/index.html";
    }

}
