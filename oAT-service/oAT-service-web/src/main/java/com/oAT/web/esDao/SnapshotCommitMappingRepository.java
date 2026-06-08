package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.SnapshotCommitMapping;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public class SnapshotCommitMappingRepository {
    private static final String TABLE_NAME = "oat_snapshot_commit_mapping";

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SnapshotCommitMapping> rowMapper = this::mapRow;

    public SnapshotCommitMappingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SnapshotCommitMapping save(SnapshotCommitMapping mapping) {
        normalizeId(mapping);
        jdbcTemplate.update("""
                        INSERT INTO oat_snapshot_commit_mapping (
                            id, snapshot_id, app_id, project_id, version_number, repo_branch,
                            repo_commit_id, mapping_source, create_time, snapshot_create_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            snapshot_id = VALUES(snapshot_id),
                            app_id = VALUES(app_id),
                            project_id = VALUES(project_id),
                            version_number = VALUES(version_number),
                            repo_branch = VALUES(repo_branch),
                            repo_commit_id = VALUES(repo_commit_id),
                            mapping_source = VALUES(mapping_source),
                            create_time = VALUES(create_time),
                            snapshot_create_time = VALUES(snapshot_create_time)
                        """,
                mapping.getId(),
                mapping.getSnapshotId(),
                mapping.getAppId(),
                mapping.getProjectId(),
                mapping.getVersionNumber(),
                mapping.getRepoBranch(),
                mapping.getRepoCommitId(),
                mapping.getMappingSource(),
                toTimestamp(mapping.getCreateTime()),
                toTimestamp(mapping.getSnapshotCreateTime()));
        return mapping;
    }

    public List<SnapshotCommitMapping> findByAppIdAndRepoCommitId(String appId, String repoCommitId) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_snapshot_commit_mapping
                        WHERE app_id = ? AND repo_commit_id = ?
                        ORDER BY snapshot_create_time DESC, create_time DESC
                        """,
                rowMapper, appId, repoCommitId);
    }

    public List<SnapshotCommitMapping> findBySnapshotId(String snapshotId) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_snapshot_commit_mapping
                        WHERE snapshot_id = ?
                        ORDER BY snapshot_create_time DESC, create_time DESC
                        """,
                rowMapper, snapshotId);
    }

    public List<SnapshotCommitMapping> findBySnapshotIds(List<String> snapshotIds) {
        if (snapshotIds == null || snapshotIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(snapshotIds.size(), "?"));
        return jdbcTemplate.query("""
                        SELECT * FROM oat_snapshot_commit_mapping
                        WHERE snapshot_id IN (%s)
                        ORDER BY snapshot_create_time DESC, create_time DESC
                        """.formatted(placeholders),
                rowMapper, snapshotIds.toArray());
    }

    public List<SnapshotCommitMapping> findByAppIdAndVersionNumber(String appId, String versionNumber) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_snapshot_commit_mapping
                        WHERE app_id = ? AND version_number = ?
                        ORDER BY snapshot_create_time DESC, create_time DESC
                        """,
                rowMapper, appId, versionNumber);
    }

    @Transactional
    public void deleteBySnapshotId(String snapshotId) {
        jdbcTemplate.update("DELETE FROM " + TABLE_NAME + " WHERE snapshot_id = ?", snapshotId);
    }

    public boolean existsBySnapshotId(String snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + TABLE_NAME + " WHERE snapshot_id = ?",
                Integer.class,
                snapshotId);
        return count != null && count > 0;
    }

    private void normalizeId(SnapshotCommitMapping mapping) {
        if (!StringUtils.hasText(mapping.getSnapshotId())) {
            throw new IllegalArgumentException("snapshotId must not be empty");
        }
        if (!StringUtils.hasText(mapping.getId())) {
            mapping.setId(StringUtils.hasText(mapping.getSnapshotId()) ? mapping.getSnapshotId() : UUID.randomUUID().toString());
        }
        if (mapping.getCreateTime() == null) {
            mapping.setCreateTime(new Date());
        }
    }

    private SnapshotCommitMapping mapRow(ResultSet rs, int rowNum) throws SQLException {
        SnapshotCommitMapping mapping = new SnapshotCommitMapping();
        mapping.setId(rs.getString("id"));
        mapping.setSnapshotId(rs.getString("snapshot_id"));
        mapping.setAppId(rs.getString("app_id"));
        mapping.setProjectId(rs.getString("project_id"));
        mapping.setVersionNumber(rs.getString("version_number"));
        mapping.setRepoBranch(rs.getString("repo_branch"));
        mapping.setRepoCommitId(rs.getString("repo_commit_id"));
        mapping.setMappingSource(rs.getString("mapping_source"));
        mapping.setCreateTime(toDate(rs.getTimestamp("create_time")));
        mapping.setSnapshotCreateTime(toDate(rs.getTimestamp("snapshot_create_time")));
        return mapping;
    }

    private Timestamp toTimestamp(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
    }
}
