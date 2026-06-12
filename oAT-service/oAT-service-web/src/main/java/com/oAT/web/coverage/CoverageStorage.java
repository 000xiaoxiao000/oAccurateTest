package com.oAT.web.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oAT.agent.model.StackNodeVo;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 覆盖率数据对象存储客户端。
 *
 * 写入路径（异步）：TraceNode 上报时提取 codeNodes → 序列化为 MessagePack → 写入 MinIO
 * 读取路径（同步）：按 traceId 从 MinIO 加载 → 反序列化 → 返回 StackNodeVo[]
 * Fallback：MinIO 不可用或对象不存在时返回 null，由调用方降级到旧 ES 数据
 */
@Component
public class CoverageStorage {

    private static final Logger logger = LoggerFactory.getLogger(CoverageStorage.class);
    private static final String OBJECT_SUFFIX = ".msgpack";

    private final CoverageStorageProperties props;
    private final ObjectMapper msgpackMapper;
    private final MinioClient minioClient;

    // 有界队列：最多积压 500 个任务，满了降级为同步写，绝不丢弃主链路
    private final ThreadPoolExecutor asyncWriter;

    public CoverageStorage(CoverageStorageProperties props) {
        this.props = props;
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());

        if (props.isEnabled()) {
            this.minioClient = MinioClient.builder()
                    .endpoint(props.getEndpoint())
                    .credentials(props.getAccessKey(), props.getSecretKey())
                    .build();
            ensureBucketExists();
        } else {
            this.minioClient = null;
        }

        this.asyncWriter = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "coverage-store-worker");
                    t.setDaemon(true);
                    return t;
                },
                // 队列满时降级为调用方线程同步执行，不丢数据
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 异步写入覆盖率数据。队列满时自动降级为同步写，不阻塞主链路返回。
     */
    public void asyncStore(String traceId, StackNodeVo[] codeNodes) {
        if (!props.isEnabled() || minioClient == null || codeNodes == null || codeNodes.length == 0) {
            return;
        }
        asyncWriter.execute(() -> doStore(traceId, codeNodes));
    }

    /**
     * 按 traceId 加载覆盖率数据。未找到返回空列表，调用方按需降级。
     */
    public List<StackNodeVo> load(String traceId) {
        if (!props.isEnabled() || minioClient == null) {
            return Collections.emptyList();
        }
        try {
            InputStream is = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(buildKey(traceId))
                            .build());
            StackNodeVo[] nodes = msgpackMapper.readValue(is, StackNodeVo[].class);
            return nodes != null ? Arrays.asList(nodes) : Collections.emptyList();
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                // 对象不存在是正常情况（旧数据在 ES），静默返回
                return Collections.emptyList();
            }
            logger.warn("Failed to load coverage data from MinIO: traceId={}, error={}", traceId, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            logger.warn("Failed to load coverage data from MinIO: traceId={}, error={}", traceId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 检查 MinIO 中是否存在指定 traceId 的覆盖率数据。
     */
    public boolean exists(String traceId) {
        if (!props.isEnabled() || minioClient == null) {
            return false;
        }
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(buildKey(traceId))
                            .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            logger.warn("Failed to stat coverage object: traceId={}, error={}", traceId, e.getMessage());
            return false;
        }
    }

    private void doStore(String traceId, StackNodeVo[] codeNodes) {
        try {
            byte[] data = msgpackMapper.writeValueAsBytes(codeNodes);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(buildKey(traceId))
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType("application/octet-stream")
                            .build());
            logger.debug("Stored coverage data: traceId={}, nodes={}, bytes={}", traceId, codeNodes.length, data.length);
        } catch (Exception e) {
            logger.error("Failed to store coverage data: traceId={}, error={}", traceId, e.getMessage());
        }
    }

    private String buildKey(String traceId) {
        return traceId + OBJECT_SUFFIX;
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(props.getBucket()).build());
                logger.info("Created MinIO bucket: {}", props.getBucket());
            }
        } catch (Exception e) {
            logger.warn("Failed to ensure MinIO bucket exists: bucket={}, error={}", props.getBucket(), e.getMessage());
        }
    }
}
