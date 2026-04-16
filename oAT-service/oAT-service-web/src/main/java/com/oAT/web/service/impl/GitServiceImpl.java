package com.oAT.web.service.impl;

import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.common.Job;
import com.oAT.web.service.GitService;
import com.oAT.web.service.ResourceService;
import com.oAT.web.service.entity.GitCommitOptionVo;
import com.oAT.web.service.entity.GitDiffVo;
import com.oAT.web.service.entity.GitJobVo;
import com.oAT.web.service.entity.GitPullEstimateVo;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class GitServiceImpl implements GitService {

    private static final Logger logger = LoggerFactory.getLogger(GitServiceImpl.class);

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private RedissonClient redissonClient;

    @org.springframework.beans.factory.annotation.Value("${git.default.username:}")
    private String defaultUsername;

    @org.springframework.beans.factory.annotation.Value("${git.default.password:}")
    private String defaultPassword;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, Job<GitJobVo>> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> runningTaskMap = new ConcurrentHashMap<>();

    private UsernamePasswordCredentialsProvider getCredentials(String username, String password) {
        String finalUser = StringUtils.hasText(username) ? username : defaultUsername;
        String finalPass = StringUtils.hasText(password) ? password : defaultPassword;
        if (StringUtils.hasText(finalPass)) {
            return new UsernamePasswordCredentialsProvider(StringUtils.hasText(finalUser) ? finalUser : "git", finalPass);
        }
        return null;
    }

    private RLock getRepoLock(String repoUrl) {
        String lockKey = "oAT:lock:repo:" + com.oAT.web.common.EncryptUtil.MD5(repoUrl);
        return redissonClient.getLock(lockKey);
    }

    @Override
    public List<String> getRemoteBranches(String repoUrl, String username, String password) {
        List<String> branches = new ArrayList<>();
        try {
            LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository()
                    .setRemote(repoUrl)
                    .setHeads(true)
                    .setTags(false);

            if (password != null && !password.isEmpty()) {
                 String user = (username != null && !username.isEmpty()) ? username : "git";
                 lsRemoteCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password));
            } else if (StringUtils.hasText(defaultPassword)) {
                lsRemoteCommand.setCredentialsProvider(getCredentials(username, password));
            }

            Collection<org.eclipse.jgit.lib.Ref> refs = lsRemoteCommand.call();
            for (org.eclipse.jgit.lib.Ref ref : refs) {
                String name = ref.getName();
                if (name.startsWith("refs/heads/")) {
                    branches.add(name.substring("refs/heads/".length()));
                }
            }
            Collections.sort(branches);
        } catch (Exception e) {
            logger.error("Failed to fetch branches for repo: {}", repoUrl, e);
            throw new RuntimeException("获取远程分支失败: " + getFriendlyErrorMessage(e));
        }
        return branches;
    }

    @Override
    public void checkGitPull(String repoUrl, String username, String password, String branch, String commitId) {
        try {
            LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository()
                    .setRemote(repoUrl)
                    .setHeads(true)
                    .setTags(true);

            UsernamePasswordCredentialsProvider credentialsProvider = getCredentials(username, password);
            if (credentialsProvider != null) {
                lsRemoteCommand.setCredentialsProvider(credentialsProvider);
            }

            Collection<org.eclipse.jgit.lib.Ref> refs = lsRemoteCommand.call();
            boolean branchFound = false;
            String branchRef = "refs/heads/" + branch;

            for (org.eclipse.jgit.lib.Ref ref : refs) {
                String name = ref.getName();
                if (name.equals(branchRef) || name.equals(branch)) {
                    branchFound = true;
                    // Check if specified Commit ID is exactly this branch's HEAD
                    if (StringUtils.hasText(commitId) && ref.getObjectId().name().equalsIgnoreCase(commitId)) {
                        return; // Perfect match
                    }
                    break;
                }
            }

            if (!branchFound) {
                throw new RuntimeException("远程分支 " + branch + " 不存在");
            }

            // Depth validation for Commit ID if it's not the branch HEAD
            if (StringUtils.hasText(commitId)) {
                checkCommitIdExists(repoUrl, username, password, commitId);
            }

        } catch (Exception e) {
            logger.error("Git check failed: {}", e.getMessage(), e);
            throw new RuntimeException("Git检测失败: " + getFriendlyErrorMessage(e));
        }
    }

    private void checkCommitIdExists(String repoUrl, String username, String password, String commitId) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("oAT_git_check_commit");
            // Perform a dry-run fetch to verify if the object exists on remote
            try (Git git = Git.init().setDirectory(tempDir.toFile()).call()) {
                org.eclipse.jgit.api.RemoteAddCommand remoteAdd = git.remoteAdd();
                remoteAdd.setName("origin");
                remoteAdd.setUri(new org.eclipse.jgit.transport.URIish(repoUrl));
                remoteAdd.call();

                org.eclipse.jgit.api.FetchCommand fetch = git.fetch();
                fetch.setRemote("origin");
                fetch.setRefSpecs(new org.eclipse.jgit.transport.RefSpec(commitId));
                fetch.setDryRun(true);
                UsernamePasswordCredentialsProvider credentialsProvider = getCredentials(username, password);
                if (credentialsProvider != null) {
                    fetch.setCredentialsProvider(credentialsProvider);
                }
                fetch.call();
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("not found") || msg.contains("couldn't find remote ref") || msg.contains("Invalid remote"))) {
                throw new RuntimeException("Commit ID '" + commitId + "' 在远程仓库中不存在");
            }
            throw new RuntimeException("验证 Commit ID 失败: " + e.getMessage());
        } finally {
            if (tempDir != null) {
                deleteFile(tempDir.toFile());
            }
        }
    }

    @Override
    public String getLatestCommitId(String repoUrl, String username, String password, String branch) {
        try {
            LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository()
                    .setRemote(repoUrl)
                    .setHeads(true)
                    .setTags(false);

            if (password != null && !password.isEmpty()) {
                String user = (username != null && !username.isEmpty()) ? username : "git";
                lsRemoteCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, password));
            } else if (StringUtils.hasText(defaultPassword)) {
                lsRemoteCommand.setCredentialsProvider(getCredentials(username, password));
            }

            Collection<org.eclipse.jgit.lib.Ref> refs = lsRemoteCommand.call();
            for (org.eclipse.jgit.lib.Ref ref : refs) {
                String name = ref.getName();
                if (name.equals("refs/heads/" + branch)) {
                    return ref.getObjectId().name();
                }
            }
            throw new RuntimeException("分支 " + branch + " 不存在");
        } catch (Exception e) {
            logger.error("Failed to get commit id: {}", e.getMessage(), e);
            throw new RuntimeException("获取 CommitID失败: " + getFriendlyErrorMessage(e));
        }
    }

    @Override
    public List<GitCommitOptionVo> getRecentCommits(String repoUrl, String username, String password, String branch, int limit) {
        List<GitCommitOptionVo> commits = new ArrayList<>();
        if (!StringUtils.hasText(branch)) {
            return commits;
        }

        int finalLimit = limit > 0 ? limit : 20;
        try {
            Collection<Ref> refs = Git.lsRemoteRepository()
                    .setRemote(repoUrl)
                    .setHeads(true)
                    .setTags(false)
                    .setCredentialsProvider(getCredentials(username, password))
                    .call();

            Ref targetRef = null;
            for (Ref ref : refs) {
                if (("refs/heads/" + branch).equals(ref.getName())) {
                    targetRef = ref;
                    break;
                }
            }
            if (targetRef == null || targetRef.getObjectId() == null) {
                throw new RuntimeException("分支 " + branch + " 不存在");
            }

            try (InMemoryRepository repository = new InMemoryRepository(new DfsRepositoryDescription(repoUrl + "#" + branch));
                 RevWalk revWalk = new RevWalk(repository)) {
                RevCommit startCommit = revWalk.parseCommit(targetRef.getObjectId());
                revWalk.markStart(startCommit);
                int count = 0;
                for (RevCommit commit : revWalk) {
                    if (count++ >= finalLimit) {
                        break;
                    }
                    String commitId = commit.getName();
                    String shortCommitId = commitId.length() > 8 ? commitId.substring(0, 8) : commitId;
                    String message = commit.getShortMessage();
                    if (!StringUtils.hasText(message)) {
                        message = "-";
                    }
                    String author = commit.getAuthorIdent() != null ? commit.getAuthorIdent().getName() : "";
                    commits.add(new GitCommitOptionVo(commitId, shortCommitId, message, author));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get recent commits for repo: {} branch: {}", repoUrl, branch, e);
            throw new RuntimeException("获取 Commit 列表失败: " + getFriendlyErrorMessage(e));
        }
        return commits;
    }

    @Override
    public GitPullEstimateVo estimateGitPull(String repoUrl, String username, String password, String branch, String commitId, String excludePaths) {
        GitPullEstimateVo estimate = new GitPullEstimateVo();
        estimate.setBranch(branch);
        estimate.setCommitId(commitId);

        String finalBranch = branch != null ? branch.trim() : "";
        String finalCommitId = commitId != null ? commitId.trim() : "";
        String taskKey = repoUrl + "#" + finalBranch + "#" + finalCommitId + (StringUtils.hasText(excludePaths) ? "#" + excludePaths : "");
        String existingJobId = runningTaskMap.get(taskKey);
        if (existingJobId != null) {
            Job<GitJobVo> existingJob = jobs.get(existingJobId);
            if (existingJob != null && existingJob.getData() != null) {
                GitJobVo data = existingJob.getData();
                if (data.getPullDurationMs() != null) {
                    estimate.setEstimatedDurationMs(data.getPullDurationMs());
                }
                if (data.getPackageSizeBytes() != null) {
                    estimate.setEstimatedPackageSizeBytes(data.getPackageSizeBytes());
                }
            }
        }

        if (estimate.getEstimatedDurationMs() == null) {
            estimate.setEstimatedDurationMs(30000L);
        }
        if (estimate.getEstimatedPackageSizeBytes() == null) {
            estimate.setEstimatedPackageSizeBytes(50L * 1024 * 1024);
        }
        return estimate;
    }

    @Override
    public void downloadAndPackage(String repoUrl, String username, String password, String branch, String commitId, File targetZipFile) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("oAT_git_clone");
            try (Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(tempDir.toFile())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider((username != null && !username.isEmpty()) ? username : "git", password))
                    .setBranch(branch)
                    .call()) {

                if (commitId != null && !commitId.trim().isEmpty()) {
                    git.checkout().setName(commitId).call();
                }
            }

            // Remove .git directory
            deleteFile(new File(tempDir.toFile(), ".git"));

            // Zip the content
            zipDirectory(tempDir.toFile(), targetZipFile);

        } catch (Exception e) {
            logger.error("Git download failed", e);
            throw new RuntimeException("代码拉取打包失败: " + getFriendlyErrorMessage(e));
        } finally {
            if (tempDir != null) {
                deleteFile(tempDir.toFile());
            }
        }
    }

    private void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFile(f);
                }
            }
        }
        if (!file.delete()) {
            logger.warn("Failed to delete file: {}", file.getAbsolutePath());
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos);
             java.util.stream.Stream<Path> stream = Files.walk(sourceDir.toPath())) {

            Path sourcePath = sourceDir.toPath();
            stream.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @Override
    public String startGitPullJob(String repoUrl, String username, String password, String branch, String commitId, String excludePaths) {
        final String finalBranch = branch != null ? branch.trim() : "";
        final String finalCommitId = commitId != null ? commitId.trim() : "";
        String taskKey = repoUrl + "#" + finalBranch + "#" + finalCommitId + (StringUtils.hasText(excludePaths) ? "#" + excludePaths : "");
        String existingJobId = runningTaskMap.get(taskKey);
        if (existingJobId != null) {
            Job<GitJobVo> existingJob = jobs.get(existingJobId);
            if (existingJob != null) {
                // 如果任务正在运行，或者已经成功完成且缓存文件依然存在，则复用
                if (existingJob.state == Job.JobState.active || existingJob.state == Job.JobState.wait) {
                    return existingJobId;
                }
                if (existingJob.state == Job.JobState.finish && existingJob.getData().isSuccess()) {
                    String cachePath = existingJob.getData().getCachePath();
                    if (cachePath != null && new File(resourceService.getCacheRoot(), cachePath).exists()) {
                        return existingJobId;
                    }
                }
            }
        }

        GitJobVo gitJobVo = new GitJobVo();
        Job<GitJobVo> job = new Job<>(gitJobVo);
        jobs.put(job.getId(), job);
        runningTaskMap.put(taskKey, job.getId());

        executorService.submit(() -> {
            job.state = Job.JobState.active;
            long pullStartTime = System.currentTimeMillis();
            File tempZip = null;
            Path tempDir = null;
            try {
                job.getProgress().next("正在拉取代码...", 70);
                job.getProgress().total = 100;
                tempDir = Files.createTempDirectory("oAT_git_clone");
                File cloneDir = tempDir.toFile();

                try (Git git = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(cloneDir)
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider((username != null && !username.isEmpty()) ? username : "git", password))
                        .setBranch(finalBranch)
                        .setProgressMonitor(new ProgressMonitor() {
                            int totalWork = 0;
                            int workDone = 0;
                            String taskName = "";
                            @Override
                            public void start(int totalTasks) {
                            }
                            @Override
                            public void beginTask(String title, int totalWork) {
                                this.taskName = title;
                                this.totalWork = totalWork;
                                this.workDone = 0;
                                job.getProgress().updateName("Git: " + title); // Update title, keep proportion
                            }
                            @Override
                            public void update(int completed) {
                                workDone += completed;
                                if (totalWork > 0) {
                                    job.getProgress().loaded = (int) ((float) workDone / totalWork * 100);
                                }
                            }
                            @Override
                            public void endTask() {
                                job.getProgress().loaded = 100;
                            }
                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        })
                        .call()) {

                    if (!finalCommitId.isEmpty()) {
                        job.getProgress().next("切换 Commit...", 5);
                        git.checkout().setName(finalCommitId).call();
                    }
                    // 获取真实的 Commit ID (处理短 ID 情况)
                    String realCommitId = git.getRepository().resolve("HEAD").getName();
                    gitJobVo.setRepoCommitId(realCommitId);
                }

                if (StringUtils.hasText(excludePaths)) {
                    job.getProgress().next("排除指定路径...", 5);
                    String[] paths = excludePaths.split(",");
                    for (String path : paths) {
                        String trimmedPath = path.trim();
                        if (trimmedPath.isEmpty()) continue;
                        Path sanitizedPath;
                        try {
                            sanitizedPath = Paths.get(trimmedPath).normalize();
                        } catch (InvalidPathException ex) {
                            throw new RuntimeException("排除路径格式不合法: " + trimmedPath);
                        }
                        if (sanitizedPath.isAbsolute() || sanitizedPath.startsWith("..")) {
                            throw new RuntimeException("排除路径格式不合法: " + trimmedPath);
                        }
                        File toDelete = new File(cloneDir, sanitizedPath.toString());
                        if (toDelete.exists()) {
                            deleteFile(toDelete);
                        }
                    }
                }

                // Remove .git directory
                deleteFile(new File(cloneDir, ".git"));

                job.getProgress().next("正在打包...", 20);
                tempZip = File.createTempFile("oat_git_", ".zip");
                zipDirectory(cloneDir, tempZip);

                // MD5 and Cache
                job.getProgress().next("处理文件...", 10);

                String md5;
                try (InputStream fis = Files.newInputStream(tempZip.toPath())) {
                    byte[] buffer = new byte[8192];
                    java.security.MessageDigest complete = java.security.MessageDigest.getInstance("MD5");
                    int numRead;
                    while ((numRead = fis.read(buffer)) != -1) {
                        complete.update(buffer, 0, numRead);
                    }
                    byte[] data = complete.digest();
                    StringBuilder sb = new StringBuilder();
                    for (byte b : data) {
                        sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
                    }
                    md5 = sb.toString();
                }

                String fileName = "git-" + finalBranch + "-" + (StringUtils.hasText(gitJobVo.getRepoCommitId()) ? gitJobVo.getRepoCommitId() : "head") + ".zip";

                File targetFile = resourceService.createCacheFile(md5, fileName);
                if (!targetFile.exists() || targetFile.length() != tempZip.length()) {
                    Files.copy(tempZip.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                gitJobVo.setMd5(md5);
                gitJobVo.setFileName(fileName);
                gitJobVo.setCachePath(resourceService.getCachePath(md5, fileName));
                gitJobVo.setPackageSizeBytes(targetFile.length());
                gitJobVo.setPullDurationMs(System.currentTimeMillis() - pullStartTime);
                gitJobVo.setSuccess(true);
                gitJobVo.setFinish(true);
                job.getProgress().finish("完成");
                job.state = Job.JobState.finish;

            } catch (Exception e) {
                logger.error("Git Job Failed", e);
                job.state = Job.JobState.error;
                gitJobVo.setSuccess(false);
                gitJobVo.setMessage(getFriendlyErrorMessage(e));
                gitJobVo.setFinish(true);
                // 失败了才从 runningTaskMap 移除，允许重试
                runningTaskMap.remove(taskKey);
            } finally {
                if (tempZip != null) {
                    if (!tempZip.delete()) {
                        logger.warn("Failed to delete temp zip: {}", tempZip.getAbsolutePath());
                    }
                }
                if (tempDir != null) {
                    deleteFile(tempDir.toFile());
                }
            }
        });

        return job.getId();
    }

    @Override
    public GitJobVo getGitJob(String jobId) {
        Job<GitJobVo> job = jobs.get(jobId);
        if (job == null) {
            return null;
        }
        GitJobVo vo = job.getData();
        vo.setId(job.getId()); // ensure ID is set
        if (job.getProgress() != null) {
            vo.setProgress(job.getProgress().getPercent());
            vo.setProgressName(job.getProgress().getName());
        }
        return vo;
    }

    @Override
    public void deleteCache(String cachePath) {
        if (!StringUtils.hasText(cachePath)) return;
        String cacheRootStr = resourceService.getCacheRoot();
        File cacheRoot = new File(cacheRootStr);
        File file = new File(cacheRoot, cachePath);
        if (file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.equals(cacheRoot)) {
                // 删除包含文件的整个目录（上一级）
                deleteFile(parent);
                logger.info("Deleted cache directory: {}", parent.getAbsolutePath());
                // 继续向上删除空的父目录
                deleteEmptyParents(parent.getParentFile(), cacheRoot);
            } else {
                if (file.delete()) {
                    logger.info("Deleted cache file: {}", file.getAbsolutePath());
                }
            }
        }

        // 清理任务映射和任务对象，“删除后可再拉取”
        // 移到 if 外面是为了保证即使物理文件提前消失也能重置内存状态。
        runningTaskMap.entrySet().removeIf(entry -> {
            String jobId = entry.getValue();
            Job<GitJobVo> job = jobs.get(jobId);
            if (job != null && cachePath.equals(job.getData().getCachePath())) {
                jobs.remove(jobId); // 同时也从任务列表中移除，防止状态残留
                return true;
            }
            return false;
        });
    }

    private void deleteEmptyParents(File directory, File root) {
        File current = directory;
        while (current != null && current.exists() && current.isDirectory() && !current.equals(root)) {
            File[] files = current.listFiles();
            if (files != null && files.length > 0) {
                break;
            }
            File parent = current.getParentFile();
            if (current.delete()) {
                logger.info("Deleted empty parent directory: {}", current.getAbsolutePath());
                current = parent;
            } else {
                logger.warn("Failed to delete empty parent directory: {}", current.getAbsolutePath());
                break;
            }
        }
    }

    private String getFriendlyErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.toString();

        if (msg.contains("401") || msg.contains("Not authorized") || msg.contains("Auth fail") || msg.contains("not authorized")) {
            return "认证失败: 请检查用户名密码或访问令牌(Token)权限";
        }
        if (msg.contains("forbidden") || msg.contains("403")) {
            return "访问拒绝: 账户权限不足或被服务端拦截";
        }
        if (msg.contains("404") || msg.contains("not found") || msg.contains("cannot open")) {
            return "仓库地址无效或仓库已删除(404)";
        }
        if (msg.contains("Transport") || msg.contains("connection") || msg.contains("resolve host") || msg.contains("timeout")) {
            return "网络错误: 无法连接至远程 Git 服务，请检查网络或防火墙";
        }
        if (msg.contains("pre-receive hook declined") || msg.contains("hook-declined")) {
            return "服务端拒绝: Git Hook 检查不通过";
        }
        if (msg.contains("consistency check") || msg.contains("corrupt")) {
            return "仓库损坏: 远程仓库对象一致性校验失败";
        }
        if (msg.contains("No space left") || msg.contains("Disk full")) {
            return "环境异常: 本地磁盘空间不足";
        }
        if (msg.contains("Permission denied") && msg.contains("filesystem")) {
            return "系统错误: 文件系统访问权限受限";
        }

        return msg;
    }

    @Override
    public List<GitDiffVo> getDiffDetail(String repoUrl, String username, String password, String oldCommit, String newCommit) {
        List<GitDiffVo> diffList = new ArrayList<>();
        if (!StringUtils.hasText(newCommit)) return diffList;

        RLock lock = getRepoLock(repoUrl);
        lock.lock();
        try {
            String repoHash = com.oAT.web.common.EncryptUtil.MD5(repoUrl);
            File gitCacheDir = new File(resourceService.getGitCacheRoot(), repoHash);

            Git clonedGit;
            if (new File(gitCacheDir, ".git").exists()) {
                clonedGit = Git.open(gitCacheDir);
                Repository repository = clonedGit.getRepository();
                if ((StringUtils.hasText(oldCommit) && repository.resolve(oldCommit) == null) || repository.resolve(newCommit) == null) {
                    clonedGit.fetch().setCredentialsProvider(getCredentials(username, password)).call();
                }
            } else {
                clonedGit = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(gitCacheDir)
                        .setCredentialsProvider(getCredentials(username, password))
                        .setNoCheckout(true)
                        .call();
            }

            try (Git git = clonedGit) {
                Repository repository = git.getRepository();
                ObjectId oldId = StringUtils.hasText(oldCommit) ? repository.resolve(oldCommit) : null;
                ObjectId newId = repository.resolve(newCommit);

                if (newId == null) {
                    logger.warn("Could not resolve new commit: {} in {}", newCommit, repoUrl);
                    return diffList;
                }

                try (RevWalk walk = new RevWalk(repository)) {
                    RevCommit oldRev = oldId != null ? walk.parseCommit(oldId) : null;
                    RevCommit newRev = walk.parseCommit(newId);

                    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                        df.setRepository(repository);
                        df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
                        df.setDetectRenames(true);

                        List<DiffEntry> diffs = df.scan(oldRev == null ? null : oldRev.getTree(), newRev.getTree());
                        for (DiffEntry entry : diffs) {
                            String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath();
                            if (path.endsWith(".java")) {
                                String className = path;
                                if (path.contains("src/main/java/")) {
                                    className = path.substring(path.indexOf("src/main/java/") + "src/main/java/".length());
                                } else if (path.contains("src/test/java/")) {
                                    className = path.substring(path.indexOf("src/test/java/") + "src/test/java/".length());
                                }
                                className = className.replace(".java", "").replace("/", ".");

                                List<Integer> changedLines = new ArrayList<>();
                                if (entry.getChangeType() != DiffEntry.ChangeType.DELETE) {
                                    FileHeader fileHeader = df.toFileHeader(entry);
                                    for (HunkHeader hunk : fileHeader.getHunks()) {
                                        for (Edit edit : hunk.toEditList()) {
                                            if (edit.getType() != Edit.Type.DELETE) {
                                                for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
                                                    changedLines.add(i + 1);
                                                }
                                            }
                                        }
                                    }
                                }
                                diffList.add(new GitDiffVo(className, changedLines, entry.getChangeType().name(), path));
                            }
                        }
                    }
                }
            }
            if (!gitCacheDir.setLastModified(System.currentTimeMillis())) {
                logger.debug("Failed to set last modified for {}", gitCacheDir);
            }
        } catch (Exception e) {
            logger.error("Failed to get git diff detail: {}", e.getMessage(), e);
        } finally {
            lock.unlock();
        }
        return diffList;
    }

    @Override
    @Deprecated
    public Map<String, List<Integer>> getDiff(String repoUrl, String username, String password, String oldCommit, String newCommit) {
        Map<String, List<Integer>> diffMap = new HashMap<>();
        if (!StringUtils.hasText(oldCommit) || !StringUtils.hasText(newCommit)) return diffMap;

        RLock lock = getRepoLock(repoUrl);
        lock.lock();
        try {
            String repoHash = com.oAT.web.common.EncryptUtil.MD5(repoUrl);
            File gitCacheDir = new File(resourceService.getGitCacheRoot(), repoHash);

            Git clonedGit;
            if (new File(gitCacheDir, ".git").exists()) {
                clonedGit = Git.open(gitCacheDir);
                Repository repository = clonedGit.getRepository();
                if (repository.resolve(oldCommit) == null || repository.resolve(newCommit) == null) {
                    clonedGit.fetch().setCredentialsProvider(getCredentials(username, password)).call();
                }
            } else {
                clonedGit = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(gitCacheDir)
                        .setCredentialsProvider(getCredentials(username, password))
                        .setNoCheckout(true)
                        .call();
            }

            try (Git git = clonedGit) {
                Repository repository = git.getRepository();
                ObjectId oldId = repository.resolve(oldCommit);
                ObjectId newId = repository.resolve(newCommit);

                if (oldId == null || newId == null) {
                    logger.warn("Could not resolve commits: {} or {} in {}", oldCommit, newCommit, repoUrl);
                    return diffMap;
                }

                try (RevWalk walk = new RevWalk(repository)) {
                    RevCommit oldRev = walk.parseCommit(oldId);
                    RevCommit newRev = walk.parseCommit(newId);

                    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                        df.setRepository(repository);
                        df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
                        df.setDetectRenames(true);

                        List<DiffEntry> diffs = df.scan(oldRev.getTree(), newRev.getTree());
                        for (DiffEntry entry : diffs) {
                            String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE ? entry.getOldPath() : entry.getNewPath();
                            if (path.endsWith(".java")) {
                                String className = path;
                                if (path.contains("src/main/java/")) {
                                    className = path.substring(path.indexOf("src/main/java/") + "src/main/java/".length());
                                } else if (path.contains("src/test/java/")) {
                                    className = path.substring(path.indexOf("src/test/java/") + "src/test/java/".length());
                                }
                                className = className.replace(".java", "").replace("/", ".");

                                List<Integer> changedLines = new ArrayList<>();
                                if (entry.getChangeType() != DiffEntry.ChangeType.DELETE) {
                                    FileHeader fileHeader = df.toFileHeader(entry);
                                    for (HunkHeader hunk : fileHeader.getHunks()) {
                                        for (Edit edit : hunk.toEditList()) {
                                            if (edit.getType() != Edit.Type.DELETE) {
                                                for (int i = edit.getBeginB(); i < edit.getEndB(); i++) {
                                                    changedLines.add(i + 1);
                                                }
                                            }
                                        }
                                    }
                                }
                                diffMap.put(className + (entry.getChangeType() == DiffEntry.ChangeType.DELETE ? ":DELETED" : ""), changedLines);
                            }
                        }
                    }
                }
            }
            // 更新目录最后修改时间，用于清理策略
            if (!gitCacheDir.setLastModified(System.currentTimeMillis())) {
                logger.debug("Failed to set last modified for {}", gitCacheDir);
            }
        } catch (Exception e) {
            logger.error("Failed to get git diff: {}", e.getMessage(), e);
        } finally {
            lock.unlock();
        }
        return diffMap;
    }

    @Override
    public String getFileContent(String repoUrl, String username, String password, String commitId, String filePath) {
        if (!StringUtils.hasText(commitId) || !StringUtils.hasText(filePath)) return null;
        RLock lock = getRepoLock(repoUrl);
        lock.lock();
        try {
            String repoHash = com.oAT.web.common.EncryptUtil.MD5(repoUrl);
            File gitCacheDir = new File(resourceService.getGitCacheRoot(), repoHash);

            Git clonedGit;
            if (new File(gitCacheDir, ".git").exists()) {
                clonedGit = Git.open(gitCacheDir);
                try {
                    clonedGit.fetch().setCredentialsProvider(getCredentials(username, password)).call();
                } catch (Exception e) {
                    // ignore fetch errors, we'll still try to read objects available locally
                    logger.warn("Fetch failed when getting file content: {}", e.getMessage());
                }
            } else {
                clonedGit = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(gitCacheDir)
                        .setCredentialsProvider(getCredentials(username, password))
                        .setNoCheckout(true)
                        .call();
            }

            try (Git git = clonedGit) {
                Repository repository = git.getRepository();
                ObjectId commitObj = repository.resolve(commitId);
                if (commitObj == null) {
                    logger.warn("Could not resolve commit {} in {}", commitId, repoUrl);
                    return null;
                }
                try (RevWalk revWalk = new RevWalk(repository)) {
                    RevCommit revCommit = revWalk.parseCommit(commitObj);

                    List<String> candidates = new ArrayList<>();
                    candidates.add(filePath);
                    // if filePath looks like a dotted class name, try source paths
                    if (!filePath.contains("/") && filePath.contains(".")) {
                        for (String pathLike : CoverageSourceClassUtil.buildSourcePathCandidates(filePath)) {
                            candidates.add("src/main/java/" + pathLike);
                            candidates.add("src/test/java/" + pathLike);
                            candidates.add(pathLike);
                        }
                    } else {
                        // if provided path is like com/xxx/YYY.java or src/... keep variants
                        if (filePath.endsWith(".java")) {
                            if (filePath.startsWith("src/main/java/")) {
                                candidates.add(filePath.substring("src/main/java/".length()));
                            } else if (filePath.startsWith("src/test/java/")) {
                                candidates.add(filePath.substring("src/test/java/".length()));
                            }
                        }
                    }

                    for (String candidate : candidates) {
                        // normalize candidate to unix style
                        String normalized = candidate.replace('\\', '/').replaceAll("^/+", "");
                        try (TreeWalk treeWalk = new TreeWalk(repository)) {
                            treeWalk.addTree(revCommit.getTree());
                            treeWalk.setRecursive(true);
                            treeWalk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(normalized));
                            if (treeWalk.next()) {
                                ObjectId objectId = treeWalk.getObjectId(0);
                                try (org.eclipse.jgit.lib.ObjectReader reader = repository.newObjectReader()) {
                                    org.eclipse.jgit.lib.ObjectLoader loader = reader.open(objectId);
                                    byte[] bytes = loader.getBytes();
                                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get file content {}@{}: {}", filePath, commitId, e.getMessage());
        } finally {
            lock.unlock();
        }
        return null;
    }
}
