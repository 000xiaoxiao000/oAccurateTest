package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.CaseCenterIndex;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface CaseCenterRepository extends ElasticsearchRepository<CaseCenterIndex, String> {
    /**
     * 基于项目 ID 与 创建用户ID 查找快照
     * @param projectId
     * @param userId
     * @return
     */
    List<CaseCenterIndex> findBySnapshot_ProjectIdAndSnapshot_CreateUser(String projectId, String userId, Pageable pageable);
    List<CaseCenterIndex> findByUsecase_ProjectIdAndAndUsecase_Directory(String projectId, String directory, Pageable pageable);
    List<CaseCenterIndex> findByUsecase_ProjectId(String projectId);
    List<CaseCenterIndex> findByUsecase_ProjectIdAndUsecase_SystemSnapshotsContaining(String projectId, String systemSnapshotId);
    List<CaseCenterIndex> findByDirectory_ProjectIdAndDirectory_ParentId(String projectId, String parentId);
    List<CaseCenterIndex> findByDirectory_ProjectId(String projectId);
}
