package com.oAT.web.service.impl;

import com.oAT.web.common.EncryptUtil;
import com.oAT.web.esDao.ResourceRepository;
import com.oAT.web.esDao.entity.ResourceIndex;
import com.oAT.web.service.ResourceService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

@Service
public class ResourceServiceImpl implements ResourceService, InitializingBean{

    private static final Logger logger = LoggerFactory.getLogger(ResourceServiceImpl.class);

    @Autowired
    ResourceRepository resourceRepository;
    @Autowired
    ElasticsearchOperations elasticsearchOperations;

    @org.springframework.beans.factory.annotation.Value("${oat.data.path:${user.home}/.oAT/cache/}")
    private String cacheRoot;


    @Override
    public String addResource(byte[] content) {
        Assert.notNull(content, "参数content 不能为空");
        String md5 = EncryptUtil.MD5(content);
        if (!existsById(md5)) {
            _addResource(content, md5);
        }
        return md5;
    }

    private void _addResource(byte[] content, String md5) {
        ResourceIndex index = new ResourceIndex(content);
        index.setReferenceCount(0);
        index.setId(md5);
        resourceRepository.save(index);
    }


    /**
     * 增加引用，引用数加1
     *
     * @param id
     */
    @Override
    public void reference(String id) {
        _reference(id, 1);
    }

    /**
     * 取消引用，引用数减1
     *
     * @param id
     */
    @Override
    public void cancelReference(String id) {
        _reference(id, -1);
    }

    private boolean existsById(String id) {
        return resourceRepository.existsById(id);
    }

    private void _reference(String id, int count) {
        synchronized (id.intern()) {
            // 获取旧的资源
            ResourceIndex oldResource = resourceRepository.findById(id).orElse(null);
            Assert.notNull(oldResource, "找不到指定资源:" + id);

            // 修改引用数
            Document doc = Document.create();
            doc.put("updateTime", new java.util.Date());
            doc.put("referenceCount", oldResource.getReferenceCount() + count);

            UpdateQuery updateQuery = UpdateQuery.builder(id)
                    .withDocument(doc)
                    .build();
            elasticsearchOperations.update(updateQuery, IndexCoordinates.of("resources"));
        }
    }
    // 注：并发调用会出现删除两次的情况
    @Override
    public void removeResource(String id) {
        Assert.notNull(id, "参数id 不能为空");
        Assert.isTrue(existsById(id), "找不到指定资源:" + id);
        resourceRepository.deleteById(id);
    }

    @Override
    public File createCacheFile(String md5, String fileName) {
        String path = cacheRoot + (cacheRoot.endsWith("/") ? "" : "/") + buildDirectoryByMd5(md5) + "/" + fileName;
        File f = new File(path);
        if (!f.getParentFile().exists()) {
            boolean created = f.getParentFile().mkdirs();
            if (!created && !f.getParentFile().exists()) {
                throw new RuntimeException("缓存目录创建失败：" + f.getParentFile().getAbsolutePath());
            }
        }
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("缓存文件创建失败：" + f.toString(), e);
            }
        } else if (f.isDirectory()) {
            throw new RuntimeException("缓存文件创建失败！存在同名目录：" + f.toString());
        }
        return f;
    }

    @Override
    public String getCachePath(String md5, String fileName) {
        return buildDirectoryByMd5(md5) + "/" + fileName;
    }


    // 基于md5 获取文件的存储目录
    private String buildDirectoryByMd5(String md5) {
        return md5.substring(0, 16);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //版本比对缓存路径
        if (cacheRoot == null) {
             cacheRoot = System.getProperty("user.home") + "/.oAT/cache/";
        }
        checkAndCleanDiskSpace(); // 初始化时执行一次检查
    }

    @Override
    public String getCacheRoot() {
        return cacheRoot;
    }

    @Override
    public String getGitCacheRoot() {
        File root = new File(cacheRoot, "git-cache");
        if (!root.exists()) {
            root.mkdirs();
        }
        return root.getAbsolutePath();
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
    public void checkAndCleanDiskSpace() {
        checkDiskSpaceThreshold(0.15); // 常规清理，维持 15% 剩余
        cleanOldGitCache(7); // 清理 7 天未使用的 Git 缓存
    }

    private void checkDiskSpaceThreshold(double threshold) {
        File dataDir = new File(cacheRoot);
        if (!dataDir.exists()) return;

        long totalSpace = dataDir.getTotalSpace();
        long usableSpace = dataDir.getUsableSpace();

        if (totalSpace > 0) {
            double freeRatio = (double) usableSpace / totalSpace;
            if (freeRatio < threshold) {
                logger.warn("磁盘空间告警: 剩余空间比例 {}%, 低于阈值 {}%, 触发强制清理...",
                        String.format("%.2f", freeRatio * 100), String.format("%.2f", threshold * 100));
                // 紧急清理：删除旧的 git 缓存和下载的 zip 缓存
                forceCleanCache(totalSpace, threshold);
            }
        }
    }

    private void cleanOldGitCache(int days) {
        File gitCacheRoot = new File(getGitCacheRoot());
        if (!gitCacheRoot.exists()) return;

        long cutoff = System.currentTimeMillis() - (long) days * 24 * 3600 * 1000;
        File[] repos = gitCacheRoot.listFiles();
        if (repos != null) {
            for (File repo : repos) {
                if (repo.isDirectory() && repo.lastModified() < cutoff) {
                    logger.info("定期清理 Git 缓存目录: {}", repo.getAbsolutePath());
                    deleteDirectory(repo);
                }
            }
        }
    }

    private void forceCleanCache(long totalSpace, double targetFreeRatio) {
        File gitCacheRoot = new File(getGitCacheRoot());
        File[] repos = gitCacheRoot.listFiles();
        if (repos == null) return;

        // 按最后修改时间排序（最早的先删除）
        Arrays.sort(repos, Comparator.comparingLong(File::lastModified));

        for (File repo : repos) {
            long currentUsable = new File(cacheRoot).getUsableSpace();
            if ((double) currentUsable / totalSpace >= targetFreeRatio + 0.05) { // 加 5% 缓冲
                break;
            }
            logger.warn("强制清理空间: 删除仓库缓存 {}", repo.getName());
            deleteDirectory(repo);
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) deleteDirectory(f);
                    else f.delete();
                }
            }
            dir.delete();
        }
    }
}
