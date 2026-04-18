package com.oAT.web.service.impl;

import com.oAT.web.esDao.SystemRepository;
import com.oAT.web.esDao.SystemSnapshotRepository;
import com.oAT.web.esDao.entity.*;
import com.oAT.web.esDao.entity.StandardDate;
import com.oAT.web.exceptions.BusinessException;
import com.oAT.web.exceptions.DirtyDataException;
import com.oAT.web.service.AppService;
import com.oAT.web.service.UsecaseService;
import com.oAT.web.service.entity.AppVo;
import com.oAT.web.service.entity.Directory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppServiceImpl implements AppService, StandardDate {

    @Autowired
    private SystemRepository systemRepository;
    @Autowired
    private SystemSnapshotRepository systemSnapshotRepository;
    @Autowired
    private UsecaseService usecaseService;

    /**
     * 创建新的应用
     */
    @Override
    public AppVo createApp(App app) {
        Assert.notNull(app, "param 'app' must be not null");
        Assert.hasText(app.getName(), "param 'app.name' must be not null");
        Assert.hasText(app.getCreateProjectId(), "param 'app.projectId' must be not null");
        Assert.hasText(app.getCreateUserId(), "param 'app.createUserId' must be not null");
        SystemIndex systemIndex = systemRepository.save(new SystemIndex(app));
        return convertApp(systemIndex);
    }

    /**
     * 更新应用
     */
    @Override
    public AppVo updateApp(String projectId, AppVo appVo) {
        Project project = systemRepository.findById(projectId).get().getProject();
        SystemIndex appIndex = systemRepository.findById(appVo.getId()).get();
        App app = appIndex.getApp();
        Assert.isTrue(app.getCreateProjectId().equalsIgnoreCase(projectId), String.format("当前项目(projectId=%s)没有权限修改该应用(appId=%s)", project,
                appVo.getId()));
        app.setDescribe(appVo.getDescribe());
        app.setName(appVo.getName());
        app.setSrcName(appVo.getSrcName());
        app.setRange(appVo.getRange());
        app.setProperties(appVo.getProperties());
        app.setCurrentVersion(appVo.getCurrentVersion());
        app.setCurrentBranch(appVo.getCurrentBranch());
        app.setCurrentCommitId(appVo.getCurrentCommitId());
        app.setRepoAddress(appVo.getRepoAddress());
        app.setRepoUserName(appVo.getRepoUserName());
        app.setRepoPassword(appVo.getRepoPassword());
        appIndex.setApp(app);
        appIndex.setUpdateTime(new java.util.Date());
        systemRepository.save(appIndex);
        return convertApp(appIndex);
    }

    /**
     * 创建快照目录
     */
    @Override
    public SnapshotDirectory saveSnapshotDirectory(String projectId, String appId, SnapshotDirectory dir) {
        SystemIndex appIndex = systemRepository.findById(appId).orElseThrow(() -> new IllegalArgumentException("找不到应用 id=" + appId));
        App app = appIndex.getApp();
        //TODO 判断当前项目是否有权限 对该目录进行修改
       /* Project project = systemRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("找不到项目 id=" + projectId))
                .getProject();*/
//        Assert.isTrue(app.getCreateProjectId().equalsIgnoreCase(projectId), String.format("当前项目(projectId=%s)没有权限修改该应用(appId=%s)", project, appId));

        SnapshotDirectory[] dirs = Optional.ofNullable(app.getSnapshotDirs()).orElse(new SnapshotDirectory[0]);
        if (dir.getId() == null) {
            // 查找出ID最大值
            Integer maxId = Arrays.stream(dirs).map(SnapshotDirectory::getId).max(Integer::compareTo).orElse(0);
            dir.setId(maxId + 1);
            dirs = Arrays.copyOf(dirs, dirs.length + 1);
            dirs[dirs.length - 1] = dir;
        } else {
            SnapshotDirectory oldDir =
                    Arrays.stream(dirs).filter(a -> a.getId().equals(dir.getId())).findFirst().orElseThrow(() -> new IllegalArgumentException(String.format("找不到快照目录appId=%s directory=%s", appId, dir.getId())));
            BeanUtils.copyProperties(dir, oldDir);
        }
        app.setSnapshotDirs(dirs);
        appIndex.setUpdateTime(new java.util.Date());
        systemRepository.save(appIndex);
        return dir;
    }

    /**
     * 删除快照目录
     */
    @Override
    public void deleteSnapshotDirectory(String projectId, String appId, Integer directoryId) throws BusinessException {
        SystemIndex appIndex = systemRepository.findById(appId).orElseThrow(() -> new IllegalArgumentException("找不到应用 id=" + appId));
        App app = appIndex.getApp();
        SnapshotDirectory[] dirs = Optional.ofNullable(app.getSnapshotDirs()).orElse(new SnapshotDirectory[0]);
        Assert.isTrue(Arrays.stream(dirs).anyMatch(a -> Objects.equals(a.getId(), directoryId)), String.format("指定目录不存在 app=%s,directoryId=%s",
                appId, directoryId));
        if (!Arrays.stream(dirs).noneMatch(a -> a.getParentId().equals(directoryId.toString()))) {
            throw new BusinessException("删除目录失败，当前目录非空");
        }
        if (!systemSnapshotRepository.findByProjectIdAndAppIdAndDirectory(projectId, appId, directoryId.toString()).isEmpty()) {
            throw new BusinessException("删除目录失败，当前目录非空");
        }
        dirs = Arrays.stream(dirs).filter(a -> !Objects.equals(a.getId(), directoryId)).collect(Collectors.toList()).toArray(new SnapshotDirectory[0]);
        app.setSnapshotDirs(dirs);
        systemRepository.save(appIndex);
    }

    @Override
    public List<SnapshotDirectory> getDirectoryTiers(String appId, String directoryId) {
        SystemIndex appIndex = systemRepository.findById(appId).orElseThrow(() -> new IllegalArgumentException("找不到应用 id=" + appId));
        if ("root".equals(directoryId)) {
            return new ArrayList<>();
        }
        SnapshotDirectory[] dirs = appIndex.getApp().getSnapshotDirs();
        dirs = Optional.ofNullable(dirs).orElse(new SnapshotDirectory[0]);
        Map<String, SnapshotDirectory> map = Arrays.stream(dirs).collect(Collectors.toMap(k -> k.getId().toString(), v -> v));
        List<SnapshotDirectory> result = getDirectoryParents(map, directoryId);
        // 到序排列
        return result;
    }

    @Override
    public Directory getDirectory(String appId, String directoryId) {
        if ("root".equalsIgnoreCase(directoryId)) {
            return new Directory("/root", "root", "root");
        }
        return getAppSnapshotDirs(appId).stream().filter(a -> a.getId().equalsIgnoreCase(directoryId)).findFirst().orElseThrow(() -> new IllegalArgumentException("找不到快照目录 directoryId=" + directoryId));
    }

    @Override
    public List<AppVo> getAppList(String projectId) {
        List<AppVo> result = new ArrayList<>();
        List<SystemIndex> list = systemRepository.findByAppCreateProjectIdOrAppRange(projectId, App.Range.all.toString());
        for (SystemIndex systemIndex : list) {
            result.add(convertApp(systemIndex));
        }
        return result;
    }

    @Override
    public AppVo getApp(String appId) {
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        Optional<SystemIndex> systemIndex = systemRepository.findById(appId);
        if (systemIndex.isPresent()) {
            return convertApp(systemIndex.get());
        } else {
            // 处理找不到应用的情况，例如返回 null 或者抛出异常
            throw new IllegalArgumentException("找不到应用 id=" + appId);
        }
    }


    @Override
    public List<Directory> getAppSnapshotDirs(String appId) {
        SystemIndex appIndex = systemRepository.findById(appId).orElseThrow(() -> new IllegalArgumentException("找不到应用 id=" + appId));
        SnapshotDirectory[] dirs = appIndex.getApp().getSnapshotDirs();
        dirs = Optional.ofNullable(dirs).orElse(new SnapshotDirectory[0]);
        Map<String, SnapshotDirectory> map = Arrays.stream(dirs).collect(Collectors.toMap(k -> k.getId().toString(), v -> v));

        List<Directory> result = Arrays.stream(dirs).map(a -> {
            String path = getDirectoryParents(map, a.getId().toString()).stream().map(b -> b.getName()).collect(Collectors.joining("/", "/root/",
                    ""));
            return new Directory(path, a.getName(), a.getId().toString());
        }).sorted((a, b) -> a.getPath().compareToIgnoreCase(b.getPath())).collect(Collectors.toList());
        return result;
    }

    private List<SnapshotDirectory> getDirectoryParents(Map<String, SnapshotDirectory> map, String id) {
        SnapshotDirectory current = map.get(id);
        List<SnapshotDirectory> list = new ArrayList<>();
        list.add(current);
        while (!current.getParentId().equals("root")) {
            if (!map.containsKey(current.getParentId())) {
                throw new DirtyDataException(String.format("SnapshotDirectory id=%s name=%s parent  not found", current.getId(), current.getName()));
            }
            current = map.get(current.getParentId());
            list.add(current);
        }
        Collections.reverse(list);
        return list;
    }

    @Override
    public AppVo deleteApp(String projectId, String appId) {
        Project project = systemRepository.findById(projectId).get().getProject();
        SystemIndex appIndex = systemRepository.findById(appId).get();
        App app = appIndex.getApp();
        Assert.isTrue(app.getCreateProjectId().equalsIgnoreCase(projectId), String.format("当前项目 %s 没有权限修改该应用(%s)", project.getName(), app.getName()));
        systemRepository.deleteById(appId);
        return convertApp(appIndex);
    }

    private AppVo convertApp(SystemIndex systemIndex) {
        AppVo appVo = new AppVo();
        BeanUtils.copyProperties(systemIndex, appVo);
        BeanUtils.copyProperties(systemIndex.getApp(), appVo);
        return appVo;
    }

    /**
     * 系统快照中添加评论
     */
    @Override
    public void addDescribe(String id, String userId, String content) {
        SystemSnapshot systemSnapshotIndex =
                systemSnapshotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("找不到应用 id=" + id));

        Comment[] comments = systemSnapshotIndex.getComments();
        int length = comments.length;
        Comment[] newComments = new Comment[length + 1];
        for (int i = 0; i < length; i++) {
            newComments[i] = comments[i];
        }
        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setTime(new Date());
        comment.setContent(content);
        newComments[length] = comment;

        systemSnapshotIndex.setComments(newComments);
        systemSnapshotRepository.save(systemSnapshotIndex);
    }

    /**
     * 系统快照中删除评论
     */
    @Override
    public void delDescribe(String id, String userId, String content, String dateTime) {
        SystemSnapshot systemSnapshotIndex =
                systemSnapshotRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("找不到应用 id=" + id));
        Comment[] comments = systemSnapshotIndex.getComments();
        List<Comment> comments1 = new ArrayList<>(Arrays.asList(comments));
        for (Comment c : comments1) {
            int i = 0;
            Timestamp ts1 = Timestamp.valueOf(dateTime);
            // Convert Date to String before splitting
            String timeStr = new SimpleDateFormat(StandardDate.dateFormat).format(c.getTime());
            Timestamp ts2 = Timestamp.valueOf(timeStr.split(",")[0]);
            if (c.getUserId().equals(userId) && c.getContent().equals(content) && ts1.equals(ts2)) {
                comments1.remove(i);
                break;
            }
            i++;
        }

        Comment[] comments2 = new Comment[comments1.size()];
        Comment[] comments3 = comments1.toArray(comments2);
        systemSnapshotIndex.setComments(comments3);
        systemSnapshotRepository.save(systemSnapshotIndex);
    }

    /**
     * 删除系统快照
     */
    @Override
    public void deleteSnapshot(String id) {
        usecaseService.removeSystemSnapshotRelation(id);
        systemSnapshotRepository.deleteById(id);
    }

}
