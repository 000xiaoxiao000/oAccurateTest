package com.oAT.web.service;

import com.oAT.web.esDao.entity.App;
import com.oAT.web.esDao.entity.SnapshotDirectory;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.Directory;

import java.util.List;


public interface AppService {
    AppVo createApp(App app);
    AppVo updateApp(String projectId, AppVo app);

    // 创建快照目录
    SnapshotDirectory saveSnapshotDirectory(String projectId, String appId, SnapshotDirectory dir);

    // 删除快照目录
    void deleteSnapshotDirectory(String projectId, String appId, Integer directoryId) throws BusinessException;

    List<SnapshotDirectory> getDirectoryTiers(String appId, String directoryId);
    Directory getDirectory(String appId, String directoryId);
    List<AppVo> getAppList(String projectId);

    AppVo getApp(String appId);

    List<Directory> getAppSnapshotDirs(String appId);

    AppVo deleteApp(String projectId, String appId);

    /**
     * 系统快照中添加评论
     */
    void addDescribe(String id, String userId, String content);

    /**
     * 系统快照中删除评论
     */
    void delDescribe(String id, String userId, String content,String dateTime);

    /**
     * 删除系统快照
     */
    void deleteSnapshot(String id);
}
