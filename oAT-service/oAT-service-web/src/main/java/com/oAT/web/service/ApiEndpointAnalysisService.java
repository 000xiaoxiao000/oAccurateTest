package com.oAT.web.service;

import com.oAT.web.service.entity.ApiEndpointViewVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface ApiEndpointAnalysisService {
    void analyzeUploadedArtifact(String appId, MultipartFile file) throws IOException;

    void analyzeArtifactFile(String appId, File file) throws IOException;

    List<ApiEndpointViewVo> listByAppId(String appId);
}
