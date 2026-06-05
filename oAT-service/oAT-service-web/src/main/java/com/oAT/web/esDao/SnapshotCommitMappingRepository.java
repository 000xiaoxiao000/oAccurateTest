package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.SnapshotCommitMapping;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface SnapshotCommitMappingRepository extends ElasticsearchRepository<SnapshotCommitMapping, String> {

    List<SnapshotCommitMapping> findByAppIdAndRepoCommitId(String appId, String repoCommitId);

    List<SnapshotCommitMapping> findBySnapshotId(String snapshotId);

    List<SnapshotCommitMapping> findByAppIdAndVersionNumber(String appId, String versionNumber);

    void deleteBySnapshotId(String snapshotId);

    boolean existsBySnapshotId(String snapshotId);
}
