package com.oAT.web.service.impl;

import com.oAT.web.common.Job;
import com.oAT.web.common.compare.CompareResult;
import com.oAT.web.common.compare.CompareUtils;
import com.oAT.web.esDao.VersionCenterRepository;
import com.oAT.web.esDao.entity.SystemSnapshot;
import com.oAT.web.esDao.entity.VersionCenterIndex;
import com.oAT.web.esDao.entity.VersionCompareReport;
import com.oAT.web.esDao.entity.VersionItem;
import com.oAT.web.service.ResourceService;
import com.oAT.web.service.SnapshotSearchService;
import com.oAT.web.service.VersionService;
import com.oAT.web.service.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.util.concurrent.Executors.*;

import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.service.entity.GitDiffVo;

@Service
public class VersionServiceImpl implements VersionService, InitializingBean {
    static Logger logger = LoggerFactory.getLogger(VersionServiceImpl.class);

    @Autowired
    VersionCenterRepository versionCenterRepository;

    @Autowired
    ResourceService resourceService;

    @Autowired
    com.oAT.web.esDao.CoverageReportRepository coverageReportRepository;

    @Autowired
    SnapshotSearchService snapshotSearchService;

    @Autowired
    com.oAT.web.service.GitService gitService;

    private ExecutorService compareJobExecutors;
    private List<Job<CompareJobVo>> jobs;

    @Override
    public void addVersionItem(VersionItemVo itemVo) {
        Assert.notNull(itemVo, "参数'itemVo'不能为空");
        Assert.notNull(itemVo.getProgramFile(), "参数'itemVo.programFile'不能为空");
        Assert.hasText(itemVo.getVersionNumber(), "参数'itemVo.versionNumber'不能为空");

        VersionItem item = new VersionItem();
        BeanUtils.copyProperties(itemVo, item);
        VersionCenterIndex versionCenterIndex = new VersionCenterIndex(item);
        versionCenterIndex = versionCenterRepository.save(versionCenterIndex);
        convertVersionItem(versionCenterIndex);
    }

    @Override
    public List<VersionItemVo> getVersionItemList(String projectId, String appId) {
        List<VersionCenterIndex> list =
                versionCenterRepository.findByVersionItem_ProjectIdAndVersionItem_AppId(projectId, appId);
        // Batch fetch coverage reports for this app to avoid per-item queries
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppId(appId);
        List<VersionItemVo> result = new ArrayList<>();
        for (VersionCenterIndex versionCenterIndex : list) {
            result.add(convertVersionItem(versionCenterIndex, reports));
        }
        return result;
    }

    @Override
    public Page<VersionItemVo> getVersionItemList(String projectId, String appId, Pageable pageable) {
        Page<VersionCenterIndex> page =
                versionCenterRepository.findByVersionItem_ProjectIdAndVersionItem_AppId(projectId, appId, pageable);
        // Batch fetch coverage reports for this app
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppId(appId);
        List<VersionItemVo> vos = page.getContent().stream()
                .map(idx -> convertVersionItem(idx, reports))
                .collect(Collectors.toList());
        return new PageImpl<>(vos, pageable, page.getTotalElements());
    }

    @Override
    public VersionItemVo getLastVersionItem(String projectId, String appId) {
        List<VersionCenterIndex> items =
                versionCenterRepository.findTop1ByVersionItem_ProjectIdAndVersionItem_AppIdOrderByCreateTimeDesc(projectId, appId);
        VersionCenterIndex item = (items != null && !items.isEmpty()) ? items.get(0) : null;
        return Optional.ofNullable(item).map(this::convertVersionItem).orElse(null);
    }

    @Override
    public void doDeleteVersionItem(String id) {
        Optional<VersionCenterIndex> indexOpt = versionCenterRepository.findById(id);
        if (indexOpt.isPresent()) {
            VersionItem item = indexOpt.get().getVersionItem();
            if (item != null && StringUtils.hasText(item.getProgramFile())) {
                try {
                    String cacheRootStr = resourceService.getCacheRoot();
                    File cacheRoot = new File(cacheRootStr);
                    File file = new File(cacheRoot, item.getProgramFile());
                    if (file.exists()) {
                        File parent = file.getParentFile();
                        // 判定逻辑：如果是 cacheRoot 下的子目录（即 md5 目录），则递归删除
                        if (parent != null && !parent.equals(cacheRoot) && parent.getParentFile().equals(cacheRoot)) {
                            // 删除包含文件的整个目录（MD5层级）
                            FileSystemUtils.deleteRecursively(parent);
                            logger.info("Deleted version directory: {}", parent.getAbsolutePath());
                        } else {
                            // 否则只删文件
                            if (file.delete()) {
                                logger.info("Deleted version file: {}", file.getAbsolutePath());
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error deleting version file", e);
                }
            }
        }
        versionCenterRepository.deleteById(id);
    }

    @Override
    public VersionItemVo getVersionByGitInfo(String appId, String versionNumber, String branch, String commitId) {
        List<VersionCenterIndex> results =
                versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_VersionNumberAndVersionItem_RepoBranchAndVersionItem_RepoCommitId(appId, versionNumber, branch, commitId);
        VersionCenterIndex c = (results != null && !results.isEmpty()) ? results.get(0) : null;
        return c == null ? null : convertVersionItem(c);
    }

    @Override
    public void deleteCacheFile(String path) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        try {
            String cacheRootStr = resourceService.getCacheRoot();
            File cacheRoot = new File(cacheRootStr);
            File file = new File(cacheRoot, path);
            if (file.exists()) {
                File parent = file.getParentFile();
                // 判定逻辑：如果是 cacheRoot 下的子目录（即 md5 目录），则尝试递归删除整个目录
                if (parent != null && !parent.equals(cacheRoot) && parent.getParentFile().equals(cacheRoot)) {
                    FileSystemUtils.deleteRecursively(parent);
                    logger.info("Deleted cache directory: {}", parent.getAbsolutePath());
                } else {
                    if (file.delete()) {
                        logger.info("Deleted cache file: {}", file.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error deleting cache file: {}", path, e);
        }
    }

    private VersionItemVo convertVersionItem(VersionCenterIndex index) {
        // Fallback converter that fetches reports for the app and delegates to the batch-aware converter
        VersionItemVo vo = new VersionItemVo();
        BeanUtils.copyProperties(index.getVersionItem(), vo);
        vo.setId(index.getId());
        if (StringUtils.hasText(vo.getProgramFile())) {
            vo.setProgramName(new File(vo.getProgramFile()).getName());

            // 检查物理文件是否存在
            File file = new File(vo.getProgramFile());
            if (!file.exists()) {
                file = new File(resourceService.getCacheRoot(), vo.getProgramFile());
            }
            vo.setFileExist(file.exists());
        }
        vo.setCreateTime(index.parse(index.getCreateTime()));
        // Use a safe, slightly broader check when called without pre-fetched reports
        List<CoverageReportIndex> reports = coverageReportRepository.findByAppId(vo.getAppId());
        return convertVersionItem(index, reports);
    }

    /**
     * Batch-aware converter: use pre-fetched coverage reports to determine whether this version has any related report.
     */
    private VersionItemVo convertVersionItem(VersionCenterIndex index, List<CoverageReportIndex> reports) {
        VersionItemVo vo = new VersionItemVo();
        BeanUtils.copyProperties(index.getVersionItem(), vo);
        vo.setId(index.getId());
        if (StringUtils.hasText(vo.getProgramFile())) {
            vo.setProgramName(new File(vo.getProgramFile()).getName());

            // 检查物理文件是否存在
            File file = new File(vo.getProgramFile());
            if (!file.exists()) {
                file = new File(resourceService.getCacheRoot(), vo.getProgramFile());
            }
            vo.setFileExist(file.exists());
        }
        vo.setCreateTime(index.parse(index.getCreateTime()));

        // Normalize version and commit for safe comparison
        String version = vo.getVersionNumber() == null ? null : vo.getVersionNumber().trim();
        String commit = vo.getRepoCommitId() == null ? null : vo.getRepoCommitId().trim();

        boolean hasReport = false;
        if (StringUtils.hasText(version)) {
            // 更稳健的匹配逻辑：逐条检查 coverage report，支持短 SHA 与全 SHA 前缀匹配
            if (reports != null) {
                for (CoverageReportIndex r : reports) {
                    if (r == null) continue;
                    String rVer = r.getVersionNumber() == null ? null : r.getVersionNumber().trim();
                    String rCommit = r.getRepoCommitId() == null ? null : r.getRepoCommitId().trim();
                    String rBase = r.getBaseVersionNumber() == null ? null : r.getBaseVersionNumber().trim();

                    // 如果版本号匹配且提交 ID 可比对，则尝试更宽松的匹配（相等或前缀匹配）
                    if (StringUtils.hasText(rVer) && rVer.equals(version)) {
                        if (StringUtils.hasText(commit) && StringUtils.hasText(rCommit)) {
                            if (rCommit.equals(commit) || rCommit.startsWith(commit) || commit.startsWith(rCommit)) {
                                hasReport = true;
                                break;
                            }
                        } else {
                            // 版本号匹配且 report 没有提交信息，则也视为存在覆盖率报告
                            hasReport = true;
                            break;
                        }
                    }
                    // 检查是否为某些增量报告引用了该版本作为 baseVersion
                    if (StringUtils.hasText(rBase) && rBase.equals(version)) {
                        hasReport = true;
                        break;
                    }
                }
            }
        }
        vo.setHasReport(hasReport);
        return vo;
    }

    @Override
    public String startCompareJob(String projectId, AppVo appinfo, String packageName, String sourceFile,
                                  String targetFile) {
        if (sourceFile == null || sourceFile.isEmpty() || targetFile == null || targetFile.isEmpty()) {
            return "";
        }
        CompareJobVo jobInfo = new CompareJobVo(sourceFile, targetFile);
        jobInfo.setProjectId(projectId);
        jobInfo.setAppId(appinfo.getId());
        jobInfo.setProgressName("等待启动");
        jobInfo.setName(String.format("%s 比对 %s",
                new File(sourceFile).getName(),
                new File(targetFile).getName()));
        final Job<CompareJobVo> job = new Job<>(jobInfo);

        Future<?> f = compareJobExecutors.submit(() -> {
            jobInfo.setFinish(false);
            try {
                startCompareJobInternal(packageName, job);
            } catch (Exception e) {
                job.getLogger().error(e);
                job.state = Job.JobState.error;
                logger.error("版本文件比对失败:{}", job.getData(), e);
            } finally {
                job.state = Job.JobState.finish;
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        jobs.remove(job);
                    } catch (InterruptedException e) {
                        logger.error("比对任务移除失败", e);
                    }
                }).start();
            }
        });
        jobInfo.setId(job.getId());
        job.setFuture(f);
        jobs.add(job);
        return job.getId();
    }

    @Override
    public String startCompareFromGit(String projectId, AppVo appinfo, String packageName, String branch,
                                      String oldCommit, String newCommit) {
        // Create a lightweight compare job that uses gitService.getDiff to obtain changed classes
        CompareJobVo jobInfo = new CompareJobVo(oldCommit == null ? ("git:" + newCommit) :
                (oldCommit + ".." + newCommit), "git-diff");
        jobInfo.setProjectId(projectId);
        jobInfo.setAppId(appinfo.getId());
        jobInfo.setProgressName("准备中");
        jobInfo.setName(String.format("Git Diff %s..%s", oldCommit, newCommit));
        // 保存 git 元信息到 job，用于后续保存到报告
        jobInfo.setGitBranch(branch);
        jobInfo.setGitOldCommit(oldCommit);
        jobInfo.setGitNewCommit(newCommit);
        // 将 commit 作为 source/target 字段，便于在报告中展示
        jobInfo.setSourceFile(oldCommit == null ? "" : oldCommit);
        jobInfo.setTargetFile(newCommit == null ? "" : newCommit);
        final Job<CompareJobVo> job = new Job<>(jobInfo);

        Future<?> f = compareJobExecutors.submit(() -> {
            job.state = Job.JobState.active;
            jobInfo.setFinish(false);
            try {
                job.getLogger().info(String.format("开始基于Git的差异比对: %s -> %s", oldCommit, newCommit));
                job.setProgress(new Job.JobProgress());
                job.getProgress().next("获取Git差异", 50);

                List<GitDiffVo> diffs;
                try {
                    diffs = gitService.getDiffDetail(appinfo.getRepoAddress(), appinfo.getRepoUserName(),
                            appinfo.getRepoPassword(), oldCommit, newCommit);
                } catch (Exception e) {
                    job.getLogger().error(e);
                    job.state = Job.JobState.error;
                    return;
                }

                job.getProgress().next("生成差异结果", 40);
                List<CompareResult> differences = new ArrayList<>();
                int totalClasses = 0, addedClasses = 0, deletedClasses = 0, modifiedClasses = 0;
                int addedMethods = 0, deletedMethods = 0, modifiedMethods = 0;

                if (diffs != null) {
                    for (GitDiffVo diffVo : diffs) {
                        String changeType = diffVo.getChangeType();
                        String dottedName = diffVo.getClassName();

                        // remove any leading dots or slashes
                        dottedName = dottedName.replaceFirst("^[./]+", "");

                        // prepare possible file path for fetching source
                        String filePath = dottedName.replace('.', '/') + ".java";

                        CompareResult.Model classModel;
                        if ("ADD".equals(changeType)) classModel = CompareResult.Model.add;
                        else if ("DELETE".equals(changeType)) classModel = CompareResult.Model.delete;
                        else classModel = CompareResult.Model.update;

                        CompareResult r = new CompareResult(dottedName, classModel);
                        totalClasses++;
                        if (classModel == CompareResult.Model.add) addedClasses++;
                        else if (classModel == CompareResult.Model.delete) deletedClasses++;
                        else modifiedClasses++;

                        try {
                            String repo = appinfo.getRepoAddress();
                            String user = appinfo.getRepoUserName();
                            String pass = appinfo.getRepoPassword();
                            String oldContent = null, newContent = null;

                            // 优先使用 GitDiffVo 提供的原始路径
                            List<String> candidates = new ArrayList<>();
                            if (StringUtils.hasText(diffVo.getOriginalPath())) {
                                candidates.add(diffVo.getOriginalPath());
                            }
                            candidates.add("src/main/java/" + filePath);
                            candidates.add("src/test/java/" + filePath);
                            candidates.add(filePath);

                            for (String cand : candidates) {
                                String o = null;
                                if (classModel != CompareResult.Model.add && oldCommit != null && !oldCommit.isEmpty()) {
                                    o = gitService.getFileContent(repo, user, pass, oldCommit, cand);
                                }
                                String n = null;
                                if (classModel != CompareResult.Model.delete) {
                                    n = gitService.getFileContent(repo, user, pass, newCommit, cand);
                                }

                                if ((classModel == CompareResult.Model.add && n != null) ||
                                    (classModel == CompareResult.Model.delete && o != null) ||
                                    (classModel == CompareResult.Model.update && (o != null || n != null))) {
                                    oldContent = o;
                                    newContent = n;
                                    break;
                                }
                            }

                            List<Integer> changedLines = diffVo.getChangedLines();
                            Map<String, MethodInfo> oldMethods = (oldContent == null) ? Collections.emptyMap() : extractMethodsWithLines(oldContent);
                            Map<String, MethodInfo> newMethods = (newContent == null) ? Collections.emptyMap() : extractMethodsWithLines(newContent);

                            if (classModel == CompareResult.Model.delete) {
                                job.getLogger().info(String.format("发现【删除】类: %s", dottedName));
                                for (Map.Entry<String, MethodInfo> eMethod : oldMethods.entrySet()) {
                                    MethodInfo om = eMethod.getValue();
                                    r.add(eMethod.getKey(), String.format("行: %d-%d", om.startLine, om.endLine), CompareResult.Model.delete);
                                    deletedMethods++;
                                }
                            } else if (classModel == CompareResult.Model.add) {
                                job.getLogger().info(String.format("发现【新增】类: %s", dottedName));
                                for (Map.Entry<String, MethodInfo> eMethod : newMethods.entrySet()) {
                                    MethodInfo nm = eMethod.getValue();
                                    r.add(eMethod.getKey(), String.format("行: %d-%d", nm.startLine, nm.endLine), CompareResult.Model.add);
                                    addedMethods++;
                                }
                            } else {
                                // Compare methods for updated classes
                                boolean nameLogged = false;
                                for (Map.Entry<String, MethodInfo> eMethod : oldMethods.entrySet()) {
                                    String mName = eMethod.getKey();
                                    MethodInfo om = eMethod.getValue();
                                    if (newMethods.containsKey(mName)) {
                                        MethodInfo nm = newMethods.get(mName);
                                        boolean bodyChanged = !Objects.equals(om.body, nm.body);
                                        boolean linesIntersect = changedLines.stream().anyMatch(l -> l >= nm.startLine && l <= nm.endLine);
                                        if (bodyChanged || linesIntersect) {
                                            if (!nameLogged) { job.getLogger().info(String.format("发现【修改】类: %s", dottedName)); nameLogged = true; }
                                            r.add(mName, String.format("行: %d-%d", nm.startLine, nm.endLine), CompareResult.Model.update);
                                            modifiedMethods++;
                                            job.getLogger().info(String.format("    - 变更方法: %s (行: %d-%d)", mName, nm.startLine, nm.endLine));
                                        }
                                        newMethods.remove(mName);
                                    } else {
                                        if (!nameLogged) { job.getLogger().info(String.format("发现【修改】类: %s", dottedName)); nameLogged = true; }
                                        r.add(mName, String.format("行: %d-%d", om.startLine, om.endLine), CompareResult.Model.delete);
                                        deletedMethods++;
                                        job.getLogger().info(String.format("    - 删除方法: %s", mName));
                                    }
                                }
                                for (Map.Entry<String, MethodInfo> eNew : newMethods.entrySet()) {
                                    if (!nameLogged) { job.getLogger().info(String.format("发现【修改】类: %s", dottedName)); nameLogged = true; }
                                    MethodInfo nm = eNew.getValue();
                                    r.add(eNew.getKey(), String.format("行: %d-%d", nm.startLine, nm.endLine), CompareResult.Model.add);
                                    addedMethods++;
                                    job.getLogger().info(String.format("    - 新增方法: %s (行: %d-%d)", eNew.getKey(), nm.startLine, nm.endLine));
                                }
                                if (!nameLogged) {
                                    // Modified but methods didn't show changes, maybe comments or imports
                                    job.getLogger().info(String.format("发现【修改】类(细节无显著变化): %s", dottedName));
                                }
                            }
                        } catch (Exception e) {
                            job.getLogger().error("处理类 " + dottedName + " 差异失败: " + e.getMessage());
                        }
                        differences.add(r);
                    }
                }
                job.getLogger().info(String.format("比对完成: 共分析 %d 个类 (新增:%d, 修改:%d, 删除:%d)", totalClasses, addedClasses, modifiedClasses, deletedClasses));
                job.getLogger().info(String.format("方法变更统计: 新增:%d, 修改:%d, 删除:%d", addedMethods, modifiedMethods, deletedMethods));

                // 初始化并设置差异和影响快照集合，防止后续保存时报空指针
                job.getData().setDifferences(differences);
                if (job.getData().getImpactSnapshot() == null) {
                    job.getData().setImpactSnapshot(new HashMap<>());
                }
                countJobInfo(differences, job.getData());

                // 分析受影响的用例（影响范围）
                job.getLogger().info("开始分析用例影响");
                job.setProgress(new Job.JobProgress());
                job.getProgress().next("分析用例影响", 80);
                job.getProgress().total = differences.size();
                // 确保 impactSnapshot 已初始化
                if (job.getData().getImpactSnapshot() == null) {
                    job.getData().setImpactSnapshot(new HashMap<>());
                }
                for (CompareResult compareResult : differences) {
                    if (compareResult.getModel() != CompareResult.Model.same) {
                        try {
                            findUsecaseImpact(job, compareResult);
                        } catch (Exception e) {
                            job.getLogger().error(e);
                        }
                    }
                    job.getProgress().loaded++;
                }

                job.getProgress().next("保存比对报告", 10);
                // 在保存之前再次确保差异集合与影响集合非空，避免NPE
                if (job.getData().getDifferences() == null) {
                    job.getData().setDifferences(new ArrayList<>());
                }
                if (job.getData().getImpactSnapshot() == null) {
                    job.getData().setImpactSnapshot(new HashMap<>());
                }
                saveCompareReport(job);
                job.getProgress().finish("比对完成");
                job.state = Job.JobState.finish;
            } catch (Exception e) {
                job.getLogger().error(e);
                job.state = Job.JobState.error;
            } finally {
                jobInfo.setFinish(true);
                new Thread(() -> {
                    try {
                        Thread.sleep(5000);
                        jobs.remove(job);
                    } catch (InterruptedException e) {
                        logger.error("移除比对任务失败", e);
                    }
                }).start();
            }
        });

        jobInfo.setId(job.getId());
        job.setFuture(f);
        jobs.add(job);
        return job.getId();
    }

    @Override
    public CompareJobVo getCompareJob(String jobId) {
        if (jobId == null) return null;
        for (Job<CompareJobVo> job : jobs) {
            if (job.getId().equals(jobId)) {
                CompareJobVo jobVo = flushJobState(job);
                CompareJobVo result = new CompareJobVo();
                BeanUtils.copyProperties(jobVo, result);
                return result;
            }
        }
        return null;
    }

    private CompareJobVo flushJobState(Job<CompareJobVo> job) {
        CompareJobVo result = job.getData();
        result.setLog(job.getLog());
        result.setProgress(job.getProgress().getPercent());
        result.setProgressName(job.getProgress().getName());
        result.setFinish(job.state == Job.JobState.finish);
        return result;
    }

    private void startCompareJobInternal(String packageName, Job<CompareJobVo> job) {
        if (packageName == null || packageName.isEmpty()) {
            packageName = "*";
        }
        job.state = Job.JobState.active;
        File parent = new File(resourceService.getCacheRoot());
        File sourceFile = new File(parent, job.getData().getSourceFile());
        File targetFile = new File(parent, job.getData().getTargetFile());

        CompareUtils compareUtils = new CompareUtils(job.getLogger(), job.getProgress());
        List<CompareResult> difference;
        try {
            difference = compareUtils.compareWar(packageName, sourceFile, targetFile);
            countJobInfo(difference, job.getData());
        } catch (IOException e) {
            job.getLogger().error(e);
            job.state = Job.JobState.error;
            logger.error("版本文件比对失败:{}", job.getData(), e);
            return;
        }
        job.getData().setDifferences(difference);

        // 检测受影响的功能
        job.getLogger().info(String.format("版本文件比较完成,发现差异项: %s", difference.size()));
        job.getLogger().info("开始分析用例影响");
        // 关联用例查找，开启新的进度条
        job.setProgress(new Job.JobProgress());
        job.getProgress().next("分析用例影响", 80);
        job.getProgress().total = difference.size();
        job.getData().setImpactSnapshot(new HashMap<>());
        for (CompareResult compareResult : difference) {
            // 新增的类也可能会产生影响（例如新增的接口实现），建议分析所有非 same 的类
            if (compareResult.getModel() != CompareResult.Model.same) {
                findUsecaseImpact(job, compareResult);
            }
            job.getProgress().loaded++;
        }
        // 保存比对报告
        job.getProgress().next("生成比对报告", 20);
        job.getLogger().info("开始生成比对报告");
        job.getProgress().total = 1;
        saveCompareReport(job);
        job.getProgress().loaded++;
        job.getLogger().info("比对报告已生成");
        job.getLogger().info("版本比对完成");
        job.getProgress().finish("对比完成");
        // 保存比对报告
        job.state = Job.JobState.finish;
    }

    private void saveCompareReport(Job<CompareJobVo> job) {
        flushJobState(job);
        CompareJobVo vo = job.getData();
        VersionCompareReport report = new VersionCompareReport();
        report.setProjectId(job.getData().getProjectId());
        report.setAppId(job.getData().getAppId());
        report.setJobId(job.getId());
        report.setJobLog(vo.getLog());
        report.setJobName(vo.getName());

        String sv = vo.getGitOldCommit();
        if (!StringUtils.hasText(sv)) sv = vo.getSourceFile();
        report.setSourceVersion(sv);

        String tv = vo.getGitNewCommit();
        if (!StringUtils.hasText(tv)) tv = vo.getTargetFile();
        report.setTargetVersion(tv);

        // Git metadata
        report.setGitBranch(vo.getGitBranch());
        report.setGitOldCommit(vo.getGitOldCommit());
        report.setGitNewCommit(vo.getGitNewCommit());

        List<VersionCompareReport.Difference> listDifference = new ArrayList<>();

        List<CompareResult> diffsToIterate = vo.getDifferences() == null ? Collections.emptyList() :
                vo.getDifferences();
        for (CompareResult difference : diffsToIterate) {
            listDifference.add(new VersionCompareReport.Difference("class", difference.getModel().toString(),
                    difference.getClassName()));
            for (CompareResult.Method method : difference.getMethods()) {
                String methodValue = difference.getClassName() + "\t" + method.getName() + "\t" + (method.getDesc()==null?"":method.getDesc());
                listDifference.add(new VersionCompareReport.Difference("method", method.getModel().toString(), methodValue));
            }
        }
        report.setDifferences(listDifference.toArray(new VersionCompareReport.Difference[0]));

        List<VersionCompareReport.ImpactCase> listCase = new ArrayList<>();
        Map<String, CompareJobVo.SnapshotUnion> impact = vo.getImpactSnapshot() == null ? Collections.emptyMap() :
                vo.getImpactSnapshot();
        impact.values().forEach(a -> listCase.add(new VersionCompareReport.ImpactCase(a.getSnapshot().getId(),
                a.getClasses().toArray(new String[0]))));
        report.setCases(listCase.toArray(new VersionCompareReport.ImpactCase[0]));

        // set statistics counts from job
        report.setAddClassCount(vo.getAddClassCount());
        report.setUpdateClassCount(vo.getUpdateClassCount());
        report.setDeleteClassCount(vo.getDeleteClassCount());
        report.setAddMethodCount(vo.getAddMethodCount());
        report.setUpdateMethodCount(vo.getUpdateMethodCount());
        report.setDeleteMethodCount(vo.getDeleteMethodCount());
        report.setImpactCaseCount(impact.size());

        VersionCenterIndex index = new VersionCenterIndex(report);
        index.setId(job.getId());
        versionCenterRepository.save(index);
    }

    private void findUsecaseImpact(Job<CompareJobVo> job, CompareResult compareResult) {
        //        job.getLogger().info(String.format("查找用例影响:%s", compareResult.getClassName()));
        Map<String, CompareJobVo.SnapshotUnion> cases = job.getData().getImpactSnapshot();
        List<SystemSnapshot> list = null;
        // normalize class name to dotted form for snapshot search
        String originalName = compareResult.getClassName();
        if (originalName != null && originalName.startsWith("/")) {
            originalName = originalName.substring(1);
        }
        String classDot = Optional.ofNullable(originalName).orElse("");
        classDot = classDot.replace('/', '.');
        if (compareResult.getModel() == CompareResult.Model.delete) {
            list = snapshotSearchService.searchByCode(job.getData().getProjectId(), classDot);
            job.getLogger().info(String.format("查找快照影响类名：%s 影响数：%s", classDot, list.size()));
        } else if (compareResult.getModel() == CompareResult.Model.update) {
            List<String> names = Arrays.stream(compareResult.getMethods())
                    .filter(m -> m.getModel() != CompareResult.Model.add)
                    .map(CompareResult.Method::getName)
                    .collect(Collectors.toList());
            // 过滤掉占位符或空的方法名（例如我们可能在无法解析方法时加入的占位项 "(类变更)"）
            final String finalClassDot = classDot;
            final String simpleClassName = finalClassDot.contains(".") ? finalClassDot.substring(finalClassDot.lastIndexOf(".") + 1) : finalClassDot;
            List<String> filteredNames = names.stream()
                    .filter(n -> n != null && !n.trim().isEmpty() && !n.trim().startsWith("("))
                    .map(n -> {
                        // 如果方法名包含了类名（如 Web3Controller.StringBuilder），尝试剥离类名部分
                        // 因为 snapshotSearchService.searchByCode 内部会再次拼接 classDot + " " + methodName
                        if (n.startsWith(simpleClassName + ".")) {
                            return n.substring(simpleClassName.length() + 1);
                        }
                        return n;
                    })
                    .collect(Collectors.toList());

            if (filteredNames.isEmpty()) {
                // 回退到类级别检索，确保能找到受影响的用例
                list = snapshotSearchService.searchByCode(job.getData().getProjectId(), classDot);
                job.getLogger().info(String.format("查找快照影响 类名：%s (方法未解析或仅占位) 影响数：%s", classDot, list.size()));
            } else {
                list = snapshotSearchService.searchByCode(job.getData().getProjectId(), classDot, StringUtils.toStringArray(filteredNames));
                // 如果按方法名没搜到，尝试按类名回退搜索，防止漏掉影响用例
                if (list.isEmpty()) {
                    list = snapshotSearchService.searchByCode(job.getData().getProjectId(), classDot);
                    job.getLogger().info(String.format("查找快照影响 类名：%s 方法：%s() 未找到，回退到类级别检索，影响数：%s",
                            classDot, Arrays.toString(StringUtils.toStringArray(filteredNames)), list.size()));
                } else {
                    job.getLogger().info(String.format("查找快照影响 类名：%s 方法：%s() 影响数：%s",
                            classDot, Arrays.toString(StringUtils.toStringArray(filteredNames)), list.size()));
                }
            }
        }
        Objects.requireNonNull(list).stream().filter(a -> !cases.containsKey(a.getId())).forEach(a -> {
            if (!cases.containsKey(a.getId())) {
                cases.put(a.getId(), new CompareJobVo.SnapshotUnion(a));
            }
            cases.get(a.getId()).getClasses().add(compareResult.getClassName());
        });
    }

    private void countJobInfo(List<CompareResult> result, CompareJobVo jobInfo) {
        for (CompareResult compareResult : result) {
            switch (compareResult.getModel()) {
                case add:
                    jobInfo.addClassCount++;
                    break;
                case delete:
                    jobInfo.deleteClassCount++;
                    break;
                case update:
                    jobInfo.updateClassCount++;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + compareResult.getModel());
            }
            for (CompareResult.Method method : compareResult.getMethods()) {
                switch (method.getModel()) {
                    case add:
                        jobInfo.addMethodCount++;
                        break;
                    case delete:
                        jobInfo.deleteMethodCount++;
                        break;
                    case update:
                        jobInfo.updateMethodCount++;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + compareResult.getModel());
                }
            }
        }
    }

    @Override
    public void afterPropertiesSet() {
        compareJobExecutors = newFixedThreadPool(5);
        // 构建线程安全的任务对列
        jobs = Collections.synchronizedList(new LinkedList<>());
    }

    @Override
    public VersionCompareReport getCompareReport(String compareId) {
        Optional<VersionCenterIndex> optional = versionCenterRepository.findById(compareId);
        Assert.isTrue(optional.isPresent(), String.format("找不到id=%s的比对报告", compareId));
        VersionCenterIndex index = optional.get();
        VersionCompareReport report = index.getCompareReport();
        report.setCreateTime(index.parse(index.getCreateTime()));
        return report;
    }

    @Override
    public List<VersionCompareReportVo> getCompareReportList(String projectId, String appId) {
        List<VersionCompareReportVo> result = new ArrayList<>();
        Pageable page = PageRequest.of(0, 50);
        List<VersionCenterIndex> list =
                versionCenterRepository.findByCompareReport_ProjectIdAndCompareReport_AppId(projectId, appId, page);
        for (VersionCenterIndex index : list) {
            VersionCompareReportVo report = new VersionCompareReportVo(index.getId(),
                    index.getCompareReport().getJobName());
            report.setSourceVersion(index.getCompareReport().getSourceVersion());
            report.setTargetVersion(index.getCompareReport().getTargetVersion());
            report.setGitBranch(index.getCompareReport().getGitBranch());
            report.setGitOldCommit(index.getCompareReport().getGitOldCommit());
            report.setGitNewCommit(index.getCompareReport().getGitNewCommit());
            report.setCreateTime(index.parse(index.getCreateTime()));
            // populate counts if available
            VersionCompareReport r = index.getCompareReport();
            if (r != null) {
                report.setAddClassCount(r.getAddClassCount());
                report.setUpdateClassCount(r.getUpdateClassCount());
                report.setDeleteClassCount(r.getDeleteClassCount());
                report.setAddMethodCount(r.getAddMethodCount());
                report.setUpdateMethodCount(r.getUpdateMethodCount());
                report.setDeleteMethodCount(r.getDeleteMethodCount());
                report.setImpactCaseCount(r.getImpactCaseCount());
            }
            result.add(report);
        }
        return result;
    }

    @Override
    public void deleteCompareReport(String projectId, String reportId) {
        Optional<VersionCenterIndex> index = versionCenterRepository.findById(reportId);
        Assert.isTrue(index.isPresent(), "找不到比对报告，id=" + reportId);
        Assert.isTrue("compareReport".equalsIgnoreCase(index.get().getType()), "找不到比对报告，id=" + reportId);
        Assert.isTrue(index.get().getCompareReport().getProjectId().equalsIgnoreCase(projectId), "项目ID不符，非法的操作");
        versionCenterRepository.deleteById(reportId);
    }

    // Helper to hold method body and line range
    private static class MethodInfo {
        String body;
        int startLine;
        int endLine;

        MethodInfo(String body, int startLine, int endLine) {
            this.body = body;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    // Strip comments but preserve newlines
    private String stripCommentsPreserveLines(String src) {
        if (src == null) return null;
        String s = src;
        // replace block comments with same number of newlines
        java.util.regex.Pattern block = java.util.regex.Pattern.compile("(?s)/\\*.*?\\*/");
        java.util.regex.Matcher bm = block.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (bm.find()) {
            String match = bm.group();
            long newlines = match.chars().filter(ch -> ch == '\n').count();
            StringBuilder rep = new StringBuilder();
            for (int i = 0; i < newlines; i++) rep.append('\n');
            bm.appendReplacement(sb, rep.toString());
        }
        bm.appendTail(sb);
        s = sb.toString();

        // remove line comments but keep newline
        s = s.replaceAll("//[^\n]*", "");
        return s;
    }

    // Extract methods and their start/end line numbers from source text
    private Map<String, MethodInfo> extractMethodsWithLines(String source) {
        if (source == null) return new LinkedHashMap<>();
        String clean = stripCommentsPreserveLines(source);
        return scanForMethods(clean, "", 0, clean.length());
    }

    private Map<String, MethodInfo> scanForMethods(String clean, String prefix, int start, int end) {
        Map<String, MethodInfo> result = new LinkedHashMap<>();
        if (start >= end || start >= clean.length()) return result;
        String scope = clean.substring(start, end);

        // Improved Regex:
        // Group 1: class/interface/enum/record name
        // Group 2: anonymous class base type
        // Group 3: method name
        // Group 4: static or instance initializer '{'
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?ms)(?:class|interface|enum|record|@interface)\\s+([\\w$]+)[^{]*\\{|new\\s+([\\w$<>.\\[\\]]+)\\s*\\([^)]*\\)\\s*[^{]*\\{|(?:public|protected|private|static|final|synchronized|native|abstract|default|\\s|<[^>]+>)+[\\w\\[\\]<>.,\\s@$]+\\s+([\\w$]+)\\s*\\([^)]*\\)[^{]*\\{|(\\b(?:static\\s*)?\\{)");
        java.util.regex.Matcher m = p.matcher(scope);

        int anonymousCount = 0;
        int lastPos = 0;
        while (m.find(lastPos)) {
            int entryPoint = start + m.start();
            int bodyStart = start + m.end() - 1; // '{' position
            int bodyEnd = findMatchingBrace(clean, bodyStart);
            if (bodyEnd <= bodyStart) {
                lastPos = m.end();
                continue;
            }

            if (m.group(3) != null) {
                // It's a method
                String methodName = m.group(3);
                // Basic filter for keywords that look like methods
                if (!isKeyword(methodName)) {
                    String fullName = prefix.isEmpty() ? methodName : prefix + "." + methodName;
                    String body = clean.substring(bodyStart, bodyEnd);
                    int startLine = 1 + countNewlinesBefore(clean, entryPoint);
                    int endLine = 1 + countNewlinesBefore(clean, bodyEnd - 1);
                    result.put(fullName, new MethodInfo(body.trim(), startLine, endLine));
                }
                // Even if it's a method, we might have inner classes inside it
                result.putAll(scanForMethods(clean, prefix, bodyStart + 1, bodyEnd - 1));
            } else if (m.group(4) != null) {
                // Static or instance initializer
                String initName = m.group(4).contains("static") ? "<clinit>" : "<init>_block";
                String fullName = prefix.isEmpty() ? initName : prefix + "." + initName;
                int startLine = 1 + countNewlinesBefore(clean, entryPoint);
                int endLine = 1 + countNewlinesBefore(clean, bodyEnd - 1);
                result.put(fullName, new MethodInfo(clean.substring(bodyStart, bodyEnd).trim(), startLine, endLine));
                result.putAll(scanForMethods(clean, prefix, bodyStart + 1, bodyEnd - 1));
            } else {
                // It's a class or anonymous class
                String className;
                if (m.group(1) != null) {
                    className = m.group(1);
                } else if (m.group(2) != null) {
                    className = "Anon" + (++anonymousCount) + "_" + m.group(2).replaceAll("[<>\\[\\]]", "");
                } else {
                    className = "Unknown" + (++anonymousCount);
                }
                String newPrefix = prefix.isEmpty() ? className : prefix + "$" + className;
                result.putAll(scanForMethods(clean, newPrefix, bodyStart + 1, bodyEnd - 1));
            }
            lastPos = bodyEnd - start;
        }
        return result;
    }

    private boolean isKeyword(String name) {
        return "if".equals(name) || "for".equals(name) || "while".equals(name) || "switch".equals(name) || "catch".equals(name) || "synchronized".equals(name) || "try".equals(name);
    }

    private int findMatchingBrace(String clean, int openBraceIdx) {
        int idx = openBraceIdx;
        int len = clean.length();
        int depth = 0;
        while (idx < len) {
            char c = clean.charAt(idx);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return idx + 1;
            }
            idx++;
        }
        return len;
    }

    private int countNewlinesBefore(String s, int index) {
        if (index <= 0) return 0;
        int count = 0;
        for (int i = 0; i < Math.min(index, s.length()); i++) {
            if (s.charAt(i) == '\n') count++;
        }
        return count;
    }
}
