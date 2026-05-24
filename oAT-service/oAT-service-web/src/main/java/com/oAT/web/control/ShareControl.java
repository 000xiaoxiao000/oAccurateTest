package com.oAT.web.control;

import com.oAT.web.config.FrontendProperties;
import com.oAT.web.esDao.CaseCenterRepository;
import com.oAT.web.esDao.entity.CaseCenterIndex;
import com.oAT.web.service.ProjectService;
import com.oAT.web.service.SnapshotService;
import com.oAT.web.service.entity.ProjectVo;
import com.oAT.web.service.entity.SnapshotVo;
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
    @Autowired
    FrontendProperties frontendProperties;

    @RequestMapping("/snapshot/{id}")
    public String openSnapshot(@PathVariable String id, Model model) {
        SnapshotVo snapshot = snapshotService.get(id);
        if (snapshot == null || Boolean.TRUE.equals(snapshot.getDisable()) || !Boolean.TRUE.equals(snapshot.getShare())) {
            return "redirect:" + frontendProperties.url("/share/snapshot/" + id);
        }
        return "redirect:" + frontendProperties.url("/share/snapshot/" + id);
    }

    @RequestMapping("/get")
    public String getShareResource(String url, Model model) {
        String target = normalizeShareUrl(url);
        return "redirect:" + frontendProperties.url(target);
    }

    @RequestMapping("/usecase/{id}")
    public String openUseCase(@PathVariable String id, Model model) {
        CaseCenterIndex index = centerRepository.findById(id).orElse(null);
        if (index == null || index.getUsecase() == null) {
            return "redirect:" + frontendProperties.url("/share/usecase/" + id);
        }
        String projectId = index.getUsecase().getProjectId();
        ProjectVo project = projectService.getProject(projectId);
        if (project == null) {
            return "redirect:" + frontendProperties.url("/share/usecase/" + id);
        }
        return "redirect:" + frontendProperties.url("/share/usecase/" + id);
    }

    private String normalizeShareUrl(String url) {
        if (url == null || url.isBlank()) {
            return "/projects";
        }
        String normalized = url.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("//")) {
            return "/projects";
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
