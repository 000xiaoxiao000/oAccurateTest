package com.oAT.web.esDao;

import com.oAT.web.common.UtilJson;
import com.oAT.web.esDao.entity.ChangeLog;
import com.oAT.web.esDao.entity.Comment;
import com.oAT.web.esDao.entity.Remote;
import com.oAT.web.esDao.entity.Sql;
import com.oAT.web.esDao.entity.SystemSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Repository
public class SystemSnapshotRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SystemSnapshot> rowMapper = this::mapRow;

    @Value("${oat.storage.large-payload.path:${user.home}/oAT/codeData/large-payload/}")
    private String largePayloadPath;

    @Value("${oat.storage.snapshot-artifact.inline-threshold-bytes:8192}")
    private long artifactInlineThresholdBytes;

    public SystemSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SystemSnapshot> findById(String id) {
        List<SystemSnapshot> snapshots = jdbcTemplate.query("SELECT * FROM oat_system_snapshot WHERE id = ?",
                rowMapper,
                id);
        return snapshots.stream().findFirst();
    }

    public List<SystemSnapshot> findAllById(Iterable<String> ids) {
        List<String> idList = StreamSupport.stream(ids.spliterator(), false)
                .filter(StringUtils::hasText)
                .toList();
        if (idList.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(idList.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM oat_system_snapshot WHERE id IN (" + placeholders + ")",
                rowMapper,
                idList.toArray());
    }

    public List<SystemSnapshot> findByProjectIdAndAppIdAndDirectory(String projectId, String appId, String directory) {
        return query("project_id = ? AND app_id = ? AND directory = ?", projectId, appId, directory);
    }

    public List<SystemSnapshot> findByProjectIdAndAppId(String projectId, String appId) {
        return query("project_id = ? AND app_id = ?", projectId, appId);
    }

    public List<SystemSnapshot> findByProjectId(String projectId) {
        return query("project_id = ?", projectId);
    }

    public List<SystemSnapshot> findByAppId(String appId) {
        return query("app_id = ?", appId);
    }

    public List<SystemSnapshot> findByProjectIdAndTraceId(String projectId, String traceId) {
        return query("project_id = ? AND trace_id = ?", projectId, traceId);
    }

    @Transactional
    public SystemSnapshot save(SystemSnapshot snapshot) {
        normalize(snapshot);
        jdbcTemplate.update("""
                        INSERT INTO oat_system_snapshot (
                            id, project_id, app_id, trace_id, title, sub_title, topic_image,
                            snapshot_describe, directory, version, version_cycle, version_last_update,
                            report_status, payload_json, create_time, update_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            project_id = VALUES(project_id),
                            app_id = VALUES(app_id),
                            trace_id = VALUES(trace_id),
                            title = VALUES(title),
                            sub_title = VALUES(sub_title),
                            topic_image = VALUES(topic_image),
                            snapshot_describe = VALUES(snapshot_describe),
                            directory = VALUES(directory),
                            version = VALUES(version),
                            version_cycle = VALUES(version_cycle),
                            version_last_update = VALUES(version_last_update),
                            report_status = VALUES(report_status),
                            payload_json = VALUES(payload_json),
                            update_time = VALUES(update_time)
                        """,
                snapshot.getId(),
                snapshot.getProjectId(),
                snapshot.getAppId(),
                snapshot.getTraceId(),
                snapshot.getTitle(),
                snapshot.getSubTitle(),
                snapshot.getTopicImage(),
                snapshot.getDescribe(),
                snapshot.getDirectory(),
                snapshot.getVersion(),
                snapshot.getVersionCycle(),
                toTimestamp(snapshot.getVersionLastUpdate()),
                snapshot.getReportStatus(),
                UtilJson.writeValueAsString(snapshot),
                toTimestamp(snapshot.getCreateTime()),
                toTimestamp(snapshot.getUpdateTime()));
        saveSnapshotDetails(snapshot);
        return snapshot;
    }

    @Transactional
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM oat_snapshot_comment WHERE snapshot_id = ?", id);
        jdbcTemplate.update("DELETE FROM oat_snapshot_change_log WHERE snapshot_id = ?", id);
        jdbcTemplate.update("DELETE FROM oat_system_snapshot_artifact WHERE snapshot_id = ?", id);
        jdbcTemplate.update("DELETE FROM oat_system_snapshot WHERE id = ?", id);
    }

    private void saveSnapshotDetails(SystemSnapshot snapshot) {
        jdbcTemplate.update("DELETE FROM oat_snapshot_comment WHERE snapshot_id = ?", snapshot.getId());
        jdbcTemplate.update("DELETE FROM oat_snapshot_change_log WHERE snapshot_id = ?", snapshot.getId());
        Comment[] comments = snapshot.getComments();
        if (comments != null) {
            for (int i = 0; i < comments.length; i++) {
                jdbcTemplate.update("""
                                INSERT INTO oat_snapshot_comment (id, snapshot_id, comment_order, payload_json, create_time)
                                VALUES (?, ?, ?, CAST(? AS JSON), ?)
                                """,
                        snapshot.getId() + "_" + i,
                        snapshot.getId(),
                        i,
                        UtilJson.writeValueAsString(comments[i]),
                        toTimestamp(snapshot.getUpdateTime()));
            }
        }
        ChangeLog[] changeLogs = snapshot.getChangeLogs();
        if (changeLogs != null) {
            for (int i = 0; i < changeLogs.length; i++) {
                jdbcTemplate.update("""
                                INSERT INTO oat_snapshot_change_log (id, snapshot_id, change_order, payload_json, create_time)
                                VALUES (?, ?, ?, CAST(? AS JSON), ?)
                                """,
                        snapshot.getId() + "_" + i,
                        snapshot.getId(),
                        i,
                        UtilJson.writeValueAsString(changeLogs[i]),
                        toTimestamp(snapshot.getUpdateTime()));
            }
        }
        saveArtifacts(snapshot);
    }

    private void saveArtifacts(SystemSnapshot snapshot) {
        jdbcTemplate.update("DELETE FROM oat_system_snapshot_artifact WHERE snapshot_id = ?", snapshot.getId());
        saveStringArtifacts(snapshot.getId(), "CODE", snapshot.getCodes());
        saveJsonArtifacts(snapshot.getId(), "SQL", snapshot.getSqls());
        saveJsonArtifacts(snapshot.getId(), "REMOTE", snapshot.getRemotes());
    }

    private void saveStringArtifacts(String snapshotId, String type, String[] values) {
        if (values == null) return;
        for (int i = 0; i < values.length; i++) {
            saveArtifact(snapshotId, type, i, values[i], null);
        }
    }

    private void saveJsonArtifacts(String snapshotId, String type, Object[] values) {
        if (values == null) return;
        for (int i = 0; i < values.length; i++) {
            saveArtifact(snapshotId, type, i, null, UtilJson.writeValueAsString(values[i]));
        }
    }

    private void saveArtifact(String snapshotId, String type, int order, String textContent, String jsonPayload) {
        String content = textContent != null ? textContent : jsonPayload;
        ArtifactPayload payload = storeArtifact(snapshotId, type, order, content);
        jdbcTemplate.update("""
                        INSERT INTO oat_system_snapshot_artifact (
                            id, snapshot_id, artifact_type, artifact_order, storage_type, content_path,
                            content_hash, content_size, content_text, payload_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                        """,
                snapshotId + "_" + type + "_" + order,
                snapshotId,
                type,
                order,
                payload.storageType(),
                payload.path(),
                payload.hash(),
                payload.size(),
                payload.inlineContent(),
                jsonPayload);
    }

    private ArtifactPayload storeArtifact(String snapshotId, String type, int order, String content) {
        if (content == null) {
            return new ArtifactPayload("DB", null, null, null, null);
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        if (bytes.length <= artifactInlineThresholdBytes) {
            return new ArtifactPayload("DB", null, hash, (long) bytes.length, content);
        }
        try {
            Path relativePath = Path.of("snapshot-artifact", snapshotId, type + "-" + order + "-" + hash + ".json");
            Path root = Path.of(largePayloadPath);
            Path file = root.resolve(relativePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return new ArtifactPayload("FILE", relativePath.toString(), hash, (long) bytes.length, null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store snapshot artifact", e);
        }
    }

    private void loadArtifacts(SystemSnapshot snapshot) {
        List<String> codes = new ArrayList<>();
        List<Sql> sqls = new ArrayList<>();
        List<Remote> remotes = new ArrayList<>();
        jdbcTemplate.query("""
                        SELECT artifact_type, content_text, content_path, payload_json
                        FROM oat_system_snapshot_artifact
                        WHERE snapshot_id = ?
                        ORDER BY artifact_type ASC, artifact_order ASC
                        """, rs -> {
                    String type = rs.getString("artifact_type");
                    String payload = loadArtifact(rs.getString("content_text"), rs.getString("content_path"));
                    String payloadJson = rs.getString("payload_json");
                    if ("CODE".equals(type) && payload != null) {
                        codes.add(payload);
                    } else if ("SQL".equals(type)) {
                        sqls.add(UtilJson.convertValue(payloadJson != null ? payloadJson : payload, Sql.class));
                    } else if ("REMOTE".equals(type)) {
                        remotes.add(UtilJson.convertValue(payloadJson != null ? payloadJson : payload, Remote.class));
                    }
                },
                snapshot.getId());
        if (!codes.isEmpty()) snapshot.setCodes(codes.toArray(new String[0]));
        if (!sqls.isEmpty()) snapshot.setSqls(sqls.toArray(new Sql[0]));
        if (!remotes.isEmpty()) snapshot.setRemotes(remotes.toArray(new Remote[0]));
    }

    private String loadArtifact(String inlineContent, String path) {
        if (StringUtils.hasText(inlineContent)) return inlineContent;
        if (!StringUtils.hasText(path)) return null;
        try {
            return Files.readString(Path.of(largePayloadPath).resolve(path), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record ArtifactPayload(String storageType, String path, String hash, Long size, String inlineContent) {}

    private List<SystemSnapshot> query(String whereClause, Object... args) {
        return jdbcTemplate.query("SELECT * FROM oat_system_snapshot WHERE " + whereClause + " ORDER BY create_time DESC",
                rowMapper,
                args);
    }

    private void normalize(SystemSnapshot snapshot) {
        if (!StringUtils.hasText(snapshot.getId())) {
            snapshot.setId(UUID.randomUUID().toString());
        }
        if (!StringUtils.hasText(snapshot.getProjectId())) {
            throw new IllegalArgumentException("projectId must not be empty");
        }
        Date now = new Date();
        if (snapshot.getCreateTime() == null) {
            snapshot.setCreateTime(now);
        }
        if (snapshot.getUpdateTime() == null) {
            snapshot.setUpdateTime(now);
        }
    }

    private SystemSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        SystemSnapshot snapshot = UtilJson.convertValue(rs.getString("payload_json"), SystemSnapshot.class);
        if (snapshot == null) {
            snapshot = new SystemSnapshot();
        }
        snapshot.setId(rs.getString("id"));
        snapshot.setProjectId(rs.getString("project_id"));
        snapshot.setAppId(rs.getString("app_id"));
        snapshot.setTraceId(rs.getString("trace_id"));
        snapshot.setTitle(rs.getString("title"));
        snapshot.setSubTitle(rs.getString("sub_title"));
        snapshot.setTopicImage(rs.getString("topic_image"));
        snapshot.setDescribe(rs.getString("snapshot_describe"));
        snapshot.setDirectory(rs.getString("directory"));
        snapshot.setVersion(rs.getString("version"));
        snapshot.setVersionCycle(getInteger(rs, "version_cycle"));
        snapshot.setVersionLastUpdate(toDate(rs.getTimestamp("version_last_update")));
        snapshot.setReportStatus(getInteger(rs, "report_status"));
        loadArtifacts(snapshot);
        snapshot.setCreateTime(toDate(rs.getTimestamp("create_time")));
        snapshot.setUpdateTime(toDate(rs.getTimestamp("update_time")));
        return snapshot;
    }

    private Integer getInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private Timestamp toTimestamp(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
    }
}
