package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.VersionCenterIndex;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface VersionCenterRepository extends ElasticsearchRepository<VersionCenterIndex, String>{

    /**
     * 基于 项目id 与应用id 进行查找
     *
     * @param projectId
     * @param appId
     * @return
     */
    List<VersionCenterIndex> findByVersionItem_ProjectIdAndVersionItem_AppId(String projectId, String appId);

    Page<VersionCenterIndex> findByVersionItem_ProjectIdAndVersionItem_AppId(String projectId, String appId, Pageable pageable);

    /**
     * 基于项目ID与应用ID 查找相对应的版本记录
     *
     * @param projectId
     * @param appId
     * @param pageable
     * @return
     */
    List<VersionCenterIndex> findByCompareReport_ProjectIdAndCompareReport_AppId(String projectId, String appId, Pageable pageable);

    /**
     * 基于版本号查找 版本项，并忽略大小写
     *
     * @param appId         应用ID
     * @param versionNumber 版本号
     * @return
     */
    List<VersionCenterIndex> findTop1ByVersionItem_AppIdAndVersionItem_VersionNumber(String appId, String versionNumber);

    /**
     * 查找最新版本，基于创建时间确认
     *
     * @param projectId
     * @param appId
     * @return
     */
    List<VersionCenterIndex> findTop1ByVersionItem_ProjectIdAndVersionItem_AppIdOrderByCreateTimeDesc(String projectId, String appId);

    /**
     * 根据应用ID、分支和CommitID查找版本记录
     *
     * @param appId
     * @param repoBranch
     * @param repoCommitId
     * @return
     */
    List<VersionCenterIndex> findTop1ByVersionItem_AppIdAndVersionItem_RepoBranchAndVersionItem_RepoCommitId(String appId, String repoBranch, String repoCommitId);

    /**
     * 根据应用ID和CommitID查找版本记录
     *
     * @param appId
     * @param repoCommitId
     * @return
     */
    List<VersionCenterIndex> findTop1ByVersionItem_AppIdAndVersionItem_RepoCommitId(String appId, String repoCommitId);

    /**
     * 根据应用ID和分支查找版本记录列表
     *
     * @param appId      应用ID
     * @param repoBranch 分支名
     * @return 版本记录列表
     */
    List<VersionCenterIndex> findByVersionItem_AppIdAndVersionItem_RepoBranch(String appId, String repoBranch);

    /**
     * 获取应用下最新的版本记录
     *
     * @param appId
     * @return
     */
    List<VersionCenterIndex> findTop1ByVersionItem_AppIdOrderByCreateTimeDesc(String appId);

    List<VersionCenterIndex> findTop1ByVersionItem_AppIdAndVersionItem_VersionNumberAndVersionItem_RepoBranchAndVersionItem_RepoCommitId(String appId, String versionNumber, String repoBranch, String repoCommitId);

}
