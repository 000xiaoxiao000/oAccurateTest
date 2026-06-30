package com.oAT.web.coverage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
public class FrontendCoverageReportRepository {
    private final JdbcTemplate jdbcTemplate;

    public FrontendCoverageReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String save(FrontendCoverageReport report) {
        if (!StringUtils.hasText(report.id)) {
            report.id = UUID.randomUUID().toString();
        }
        jdbcTemplate.update("""
                        INSERT INTO oat_frontend_coverage_report (
                            id, request_id, project_id, app_id, commit_id, version_number, branch,
                            case_name, build_id, test_stage, timestamp, coverage_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                report.id,
                report.requestId,
                report.projectId,
                report.appId,
                report.commitId,
                report.versionNumber,
                report.branch,
                report.caseName,
                report.buildId,
                report.testStage,
                report.timestamp,
                report.coverageJson);
        return report.id;
    }

    public List<FrontendCoverageReport> findByAppAndVersion(String appId, String versionNumber, String commitId) {
        if (StringUtils.hasText(commitId)) {
            List<FrontendCoverageReport> reports = jdbcTemplate.query("""
                            SELECT * FROM oat_frontend_coverage_report
                            WHERE app_id = ? AND commit_id = ?
                            ORDER BY create_time ASC
                            """,
                    this::mapRow, appId, commitId);
            if (!reports.isEmpty()) {
                return reports;
            }
        }
        if (StringUtils.hasText(versionNumber)) {
            List<FrontendCoverageReport> reports = jdbcTemplate.query("""
                        SELECT * FROM oat_frontend_coverage_report
                        WHERE app_id = ? AND version_number = ?
                        ORDER BY create_time ASC
                        """,
                    this::mapRow, appId, versionNumber);
            if (!reports.isEmpty()) {
                return reports;
            }
        }
        return jdbcTemplate.query("""
                        SELECT * FROM oat_frontend_coverage_report
                        WHERE app_id = ?
                        ORDER BY create_time DESC
                        LIMIT 1
                        """,
                this::mapRow, appId);
    }

    public boolean existsByAppAndVersionOrCommit(String appId, String versionNumber, String commitId) {
        if (!StringUtils.hasText(appId)) {
            return false;
        }
        if (StringUtils.hasText(commitId) && count("""
                SELECT COUNT(1) FROM oat_frontend_coverage_report
                WHERE app_id = ? AND commit_id = ?
                """, appId, commitId) > 0) {
            return true;
        }
        return StringUtils.hasText(versionNumber) && count("""
                SELECT COUNT(1) FROM oat_frontend_coverage_report
                WHERE app_id = ? AND version_number = ?
                """, appId, versionNumber) > 0;
    }

    public boolean existsByApp(String appId) {
        return StringUtils.hasText(appId) && count("""
                SELECT COUNT(1) FROM oat_frontend_coverage_report
                WHERE app_id = ?
                """, appId) > 0;
    }

    public Long findLatestTimestampByApp(String appId) {
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        List<Long> values = jdbcTemplate.query("""
                        SELECT COALESCE(`timestamp`, UNIX_TIMESTAMP(create_time) * 1000) latest_time
                        FROM oat_frontend_coverage_report
                        WHERE app_id = ?
                        ORDER BY create_time DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getLong("latest_time"),
                appId);
        return values.isEmpty() ? null : values.get(0);
    }

    public List<FrontendCoverageReport> findFootprints(String projectId, String appId, String versionNumber, String commitId) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM oat_frontend_coverage_report
                WHERE project_id = ?
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(projectId);
        if (StringUtils.hasText(appId)) {
            sql.append(" AND app_id = ?");
            args.add(appId);
        }
        if (StringUtils.hasText(commitId)) {
            sql.append(" AND commit_id = ?");
            args.add(commitId);
        } else if (StringUtils.hasText(versionNumber)) {
            sql.append(" AND version_number = ?");
            args.add(versionNumber);
        }
        sql.append(" ORDER BY COALESCE(`timestamp`, UNIX_TIMESTAMP(create_time) * 1000) DESC, create_time DESC LIMIT 500");
        return jdbcTemplate.query(sql.toString(), this::mapRow, args.toArray());
    }

    public FrontendCoverageReport findById(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        List<FrontendCoverageReport> reports = jdbcTemplate.query("""
                        SELECT * FROM oat_frontend_coverage_report
                        WHERE id = ?
                        LIMIT 1
                        """,
                this::mapRow, id);
        return reports.isEmpty() ? null : reports.get(0);
    }

    public int deleteById(String id) {
        if (!StringUtils.hasText(id)) {
            return 0;
        }
        return jdbcTemplate.update("DELETE FROM oat_frontend_coverage_report WHERE id = ?", id);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private FrontendCoverageReport mapRow(ResultSet rs, int rowNum) throws SQLException {
        FrontendCoverageReport report = new FrontendCoverageReport();
        report.id = rs.getString("id");
        report.requestId = rs.getString("request_id");
        report.projectId = rs.getString("project_id");
        report.appId = rs.getString("app_id");
        report.commitId = rs.getString("commit_id");
        report.versionNumber = rs.getString("version_number");
        report.branch = rs.getString("branch");
        report.caseName = rs.getString("case_name");
        report.buildId = rs.getString("build_id");
        report.testStage = rs.getString("test_stage");
        report.timestamp = rs.getLong("timestamp");
        if (rs.wasNull()) {
            report.timestamp = null;
        }
        report.coverageJson = rs.getString("coverage_json");
        return report;
    }

    public static class FrontendCoverageReport {
        public String id;
        public String requestId;
        public String projectId;
        public String appId;
        public String commitId;
        public String versionNumber;
        public String branch;
        public String caseName;
        public String buildId;
        public String testStage;
        public Long timestamp;
        public String coverageJson;
    }
}
