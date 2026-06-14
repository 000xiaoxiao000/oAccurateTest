package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.CoverageReportIndex;
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
import java.util.Optional;
import java.util.UUID;

@Repository
public class CoverageReportRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<CoverageReportIndex> rowMapper = this::mapRow;

    public CoverageReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<CoverageReportIndex> findById(String id) {
        List<CoverageReportIndex> reports = jdbcTemplate.query("SELECT * FROM oat_coverage_report WHERE id = ?",
                rowMapper,
                id);
        return reports.stream().findFirst();
    }

    public List<CoverageReportIndex> findByAppId(String appId) {
        return query("app_id = ?", appId);
    }

    public List<CoverageReportIndex> findByAppIdAndVersionNumber(String appId, String versionNumber) {
        return query("app_id = ? AND version_number = ?", appId, versionNumber);
    }

    public long countByAppIdAndVersionNumber(String appId, String versionNumber) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM oat_coverage_report WHERE app_id = ? AND version_number = ?",
                Long.class,
                appId,
                versionNumber);
        return count == null ? 0 : count;
    }

    public List<CoverageReportIndex> findByAppIdAndVersionNumberAndReportType(String appId, String versionNumber, Integer reportType) {
        return query("app_id = ? AND version_number = ? AND report_type = ?", appId, versionNumber, reportType);
    }

    public List<CoverageReportIndex> findByAppIdAndVersionNumberAndRepoCommitId(String appId, String versionNumber, String repoCommitId) {
        return query("app_id = ? AND version_number = ? AND repo_commit_id = ?", appId, versionNumber, repoCommitId);
    }

    public List<CoverageReportIndex> findByAppIdAndVersionNumberAndReportTypeAndRepoCommitId(String appId, String versionNumber, Integer reportType, String repoCommitId) {
        return query("app_id = ? AND version_number = ? AND report_type = ? AND repo_commit_id = ?", appId, versionNumber, reportType, repoCommitId);
    }

    public List<CoverageReportIndex> findByAppIdAndRepoBranch(String appId, String repoBranch) {
        return query("app_id = ? AND repo_branch = ?", appId, repoBranch);
    }

    @Transactional
    public CoverageReportIndex save(CoverageReportIndex report) {
        normalize(report);
        jdbcTemplate.update("""
                        INSERT INTO oat_coverage_report (
                            id, app_id, version_number, repo_branch, repo_commit_id, create_time, source_type,
                            last_processed_time, total_classes, covered_classes, total_methods,
                            covered_methods, total_branches, covered_branches, total_branch_targets,
                            covered_branch_targets, total_lines, covered_lines, total_complexity,
                            report_type, base_version_number, base_repo_commit_id, snapshot_fingerprint,
                            snapshot_last_update_time, snapshot_count, snapshot_ids, inc_total_classes,
                            inc_covered_classes, inc_total_lines, inc_covered_lines, inc_total_methods,
                            inc_covered_methods, inc_total_branches, inc_covered_branches,
                            inc_total_branch_targets, inc_covered_branch_targets, inc_total_complexity
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            app_id = VALUES(app_id),
                            version_number = VALUES(version_number),
                            repo_branch = VALUES(repo_branch),
                            repo_commit_id = VALUES(repo_commit_id),
                            create_time = VALUES(create_time),
                            source_type = VALUES(source_type),
                            last_processed_time = VALUES(last_processed_time),
                            total_classes = VALUES(total_classes),
                            covered_classes = VALUES(covered_classes),
                            total_methods = VALUES(total_methods),
                            covered_methods = VALUES(covered_methods),
                            total_branches = VALUES(total_branches),
                            covered_branches = VALUES(covered_branches),
                            total_branch_targets = VALUES(total_branch_targets),
                            covered_branch_targets = VALUES(covered_branch_targets),
                            total_lines = VALUES(total_lines),
                            covered_lines = VALUES(covered_lines),
                            total_complexity = VALUES(total_complexity),
                            report_type = VALUES(report_type),
                            base_version_number = VALUES(base_version_number),
                            base_repo_commit_id = VALUES(base_repo_commit_id),
                            snapshot_fingerprint = VALUES(snapshot_fingerprint),
                            snapshot_last_update_time = VALUES(snapshot_last_update_time),
                            snapshot_count = VALUES(snapshot_count),
                            snapshot_ids = VALUES(snapshot_ids),
                            inc_total_classes = VALUES(inc_total_classes),
                            inc_covered_classes = VALUES(inc_covered_classes),
                            inc_total_lines = VALUES(inc_total_lines),
                            inc_covered_lines = VALUES(inc_covered_lines),
                            inc_total_methods = VALUES(inc_total_methods),
                            inc_covered_methods = VALUES(inc_covered_methods),
                            inc_total_branches = VALUES(inc_total_branches),
                            inc_covered_branches = VALUES(inc_covered_branches),
                            inc_total_branch_targets = VALUES(inc_total_branch_targets),
                            inc_covered_branch_targets = VALUES(inc_covered_branch_targets),
                            inc_total_complexity = VALUES(inc_total_complexity)
                        """,
                report.getId(),
                report.getAppId(),
                report.getVersionNumber(),
                report.getRepoBranch(),
                report.getRepoCommitId(),
                toTimestamp(report.getCreateTime()),
                report.getSourceType(),
                report.getLastProcessedTime(),
                report.getTotalClasses(),
                report.getCoveredClasses(),
                report.getTotalMethods(),
                report.getCoveredMethods(),
                report.getTotalBranches(),
                report.getCoveredBranches(),
                report.getTotalBranchTargets(),
                report.getCoveredBranchTargets(),
                report.getTotalLines(),
                report.getCoveredLines(),
                report.getTotalComplexity(),
                report.getReportType(),
                report.getBaseVersionNumber(),
                report.getBaseRepoCommitId(),
                report.getSnapshotFingerprint(),
                report.getSnapshotLastUpdateTime(),
                report.getSnapshotCount(),
                report.getSnapshotIds(),
                report.getIncTotalClasses(),
                report.getIncCoveredClasses(),
                report.getIncTotalLines(),
                report.getIncCoveredLines(),
                report.getIncTotalMethods(),
                report.getIncCoveredMethods(),
                report.getIncTotalBranches(),
                report.getIncCoveredBranches(),
                report.getIncTotalBranchTargets(),
                report.getIncCoveredBranchTargets(),
                report.getIncTotalComplexity());
        return report;
    }

    @Transactional
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM oat_coverage_report WHERE id = ?", id);
    }

    private List<CoverageReportIndex> query(String whereClause, Object... args) {
        return jdbcTemplate.query("SELECT * FROM oat_coverage_report WHERE " + whereClause + " ORDER BY create_time DESC",
                rowMapper,
                args);
    }

    private void normalize(CoverageReportIndex report) {
        if (!StringUtils.hasText(report.getId())) {
            report.setId(UUID.randomUUID().toString());
        }
        if (!StringUtils.hasText(report.getAppId())) {
            throw new IllegalArgumentException("appId must not be empty");
        }
        if (report.getCreateTime() == null) {
            report.setCreateTime(new Date());
        }
    }

    private CoverageReportIndex mapRow(ResultSet rs, int rowNum) throws SQLException {
        CoverageReportIndex report = new CoverageReportIndex();
        report.setId(rs.getString("id"));
        report.setAppId(rs.getString("app_id"));
        report.setVersionNumber(rs.getString("version_number"));
        report.setRepoBranch(rs.getString("repo_branch"));
        report.setRepoCommitId(rs.getString("repo_commit_id"));
        report.setCreateTime(toDate(rs.getTimestamp("create_time")));
        report.setSourceType(readStringIfExists(rs, "source_type"));
        report.setLastProcessedTime(rs.getString("last_processed_time"));
        report.setTotalClasses(rs.getLong("total_classes"));
        report.setCoveredClasses(rs.getLong("covered_classes"));
        report.setTotalMethods(rs.getLong("total_methods"));
        report.setCoveredMethods(rs.getLong("covered_methods"));
        report.setTotalBranches(rs.getLong("total_branches"));
        report.setCoveredBranches(rs.getLong("covered_branches"));
        report.setTotalBranchTargets(rs.getLong("total_branch_targets"));
        report.setCoveredBranchTargets(rs.getLong("covered_branch_targets"));
        report.setTotalLines(rs.getLong("total_lines"));
        report.setCoveredLines(rs.getLong("covered_lines"));
        report.setTotalComplexity(rs.getInt("total_complexity"));
        report.setReportType(getInteger(rs, "report_type"));
        report.setBaseVersionNumber(rs.getString("base_version_number"));
        report.setBaseRepoCommitId(rs.getString("base_repo_commit_id"));
        report.setSnapshotFingerprint(rs.getString("snapshot_fingerprint"));
        report.setSnapshotLastUpdateTime(rs.getString("snapshot_last_update_time"));
        report.setSnapshotCount(getInteger(rs, "snapshot_count"));
        report.setSnapshotIds(rs.getString("snapshot_ids"));
        report.setIncTotalClasses(rs.getLong("inc_total_classes"));
        report.setIncCoveredClasses(rs.getLong("inc_covered_classes"));
        report.setIncTotalLines(rs.getLong("inc_total_lines"));
        report.setIncCoveredLines(rs.getLong("inc_covered_lines"));
        report.setIncTotalMethods(rs.getLong("inc_total_methods"));
        report.setIncCoveredMethods(rs.getLong("inc_covered_methods"));
        report.setIncTotalBranches(rs.getLong("inc_total_branches"));
        report.setIncCoveredBranches(rs.getLong("inc_covered_branches"));
        report.setIncTotalBranchTargets(rs.getLong("inc_total_branch_targets"));
        report.setIncCoveredBranchTargets(rs.getLong("inc_covered_branch_targets"));
        report.setIncTotalComplexity(rs.getInt("inc_total_complexity"));
        return report;
    }

    private Integer getInteger(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private String readStringIfExists(ResultSet rs, String columnName) throws SQLException {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    private Timestamp toTimestamp(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
    }
}
