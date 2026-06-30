package com.oAT.web.coveragecore.report;

import com.oAT.web.esDao.SnapshotCommitMappingRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.SnapshotCommitMapping;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.service.entity.AppVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CoverageSnapshotSelectionService {
    private static final Logger logger = LoggerFactory.getLogger(CoverageSnapshotSelectionService.class);

    private final SystemSnapshotRepository systemSnapshotRepository;
    private final SnapshotCommitMappingRepository snapshotCommitMappingRepository;
    private final CoverageSnapshotContextSupport coverageSnapshotContextSupport;

    public CoverageSnapshotSelectionService(SystemSnapshotRepository systemSnapshotRepository,
                                            SnapshotCommitMappingRepository snapshotCommitMappingRepository,
                                            CoverageSnapshotContextSupport coverageSnapshotContextSupport) {
        this.systemSnapshotRepository = systemSnapshotRepository;
        this.snapshotCommitMappingRepository = snapshotCommitMappingRepository;
        this.coverageSnapshotContextSupport = coverageSnapshotContextSupport;
    }

    public CoverageSnapshotContext buildSnapshotCoverageContext(AppVo app, String versionNumber) {
        return buildSnapshotCoverageContext(app, versionNumber, null, null);
    }

    public CoverageSnapshotContext buildSnapshotCoverageContext(AppVo app,
                                                                String versionNumber,
                                                                String commitId,
                                                                Integer reportType) {
        CoverageSnapshotContext context = new CoverageSnapshotContext();
        if (app == null || !StringUtils.hasText(app.getId()) || !StringUtils.hasText(app.getCreateProjectId())) {
            return context;
        }

        List<SystemSnapshot> allSnapshots = systemSnapshotRepository.findByProjectIdAndAppId(app.getCreateProjectId(), app.getId());
        if (allSnapshots == null || allSnapshots.isEmpty()) {
            return context;
        }

        List<SystemSnapshot> selectedSnapshots = new ArrayList<>(allSnapshots);
        if (StringUtils.hasText(versionNumber)) {
            List<SystemSnapshot> versionMatched = new ArrayList<>();
            for (SystemSnapshot snapshot : allSnapshots) {
                String snapshotVersion = snapshot == null ? null : snapshot.getVersion();
                if (snapshot != null && versionNumber.equals(snapshotVersion == null ? null : snapshotVersion.trim())) {
                    versionMatched.add(snapshot);
                }
            }
            if (!versionMatched.isEmpty()) {
                selectedSnapshots = versionMatched;
            }
        }

        if (CoverageReportTypes.normalize(reportType) == CoverageReportTypes.CURRENT_COMMIT && StringUtils.hasText(commitId)) {
            List<SnapshotCommitMapping> mappings = snapshotCommitMappingRepository
                    .findByAppIdAndRepoCommitId(app.getId(), commitId);

            if (mappings != null && !mappings.isEmpty()) {
                Set<String> commitSnapshotIds = mappings.stream()
                        .map(SnapshotCommitMapping::getSnapshotId)
                        .collect(Collectors.toSet());

                selectedSnapshots = selectedSnapshots.stream()
                        .filter(s -> commitSnapshotIds.contains(s.getId()))
                        .collect(Collectors.toList());

                logger.info("本次 Commit 报告：通过关联表过滤快照，commitId={}, 匹配快照数={}",
                        commitId, selectedSnapshots.size());
            } else {
                logger.warn("本次 Commit 报告：commitId={} 未找到关联快照，降级使用版本匹配", commitId);
            }
        }

        return coverageSnapshotContextSupport.buildContext(selectedSnapshots);
    }
}
