package com.oAT.web.domain.version;

import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.VersionCenterIndex;
import com.oAT.web.service.ResourceService;
import com.oAT.web.service.entity.VersionItemVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.List;

@Service
public class VersionItemViewService {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private CoverageReportRepository coverageReportRepository;

    public VersionItemVo convertVersionItem(VersionCenterIndex index) {
        VersionItemVo vo = new VersionItemVo();
        BeanUtils.copyProperties(index.getVersionItem(), vo);
        vo.setId(index.getId());
        populateFileState(vo);
        vo.setCreateTime(index.getCreateTime());
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppId(vo.getAppId());
        return convertVersionItem(index, reports);
    }

    public VersionItemVo convertVersionItem(VersionCenterIndex index, List<CoverageReportIndex> reports) {
        VersionItemVo vo = new VersionItemVo();
        BeanUtils.copyProperties(index.getVersionItem(), vo);
        vo.setId(index.getId());
        populateFileState(vo);
        vo.setCreateTime(index.getCreateTime());
        vo.setHasReport(hasRelatedCoverageReport(vo, reports));
        return vo;
    }

    private void populateFileState(VersionItemVo vo) {
        if (!StringUtils.hasText(vo.getProgramFile())) {
            return;
        }
        vo.setProgramName(new File(vo.getProgramFile()).getName());
        File file = new File(vo.getProgramFile());
        if (!file.exists()) {
            file = new File(resourceService.getCacheRoot(), vo.getProgramFile());
        }
        vo.setFileExist(file.exists());
    }

    private boolean hasRelatedCoverageReport(VersionItemVo vo, List<CoverageReportIndex> reports) {
        String version = vo.getVersionNumber() == null ? null : vo.getVersionNumber().trim();
        String commit = vo.getRepoCommitId() == null ? null : vo.getRepoCommitId().trim();
        if (!StringUtils.hasText(version) || reports == null) {
            return false;
        }
        for (CoverageReportIndex report : reports) {
            if (report == null) {
                continue;
            }
            String reportVersion = report.getVersionNumber() == null ? null : report.getVersionNumber().trim();
            String reportCommit = report.getRepoCommitId() == null ? null : report.getRepoCommitId().trim();
            String reportBaseVersion = report.getBaseVersionNumber() == null ? null : report.getBaseVersionNumber().trim();
            if (StringUtils.hasText(reportVersion) && reportVersion.equals(version)) {
                if (StringUtils.hasText(commit) && StringUtils.hasText(reportCommit)) {
                    if (reportCommit.equals(commit) || reportCommit.startsWith(commit) || commit.startsWith(reportCommit)) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
            if (StringUtils.hasText(reportBaseVersion) && reportBaseVersion.equals(version)) {
                return true;
            }
        }
        return false;
    }
}
