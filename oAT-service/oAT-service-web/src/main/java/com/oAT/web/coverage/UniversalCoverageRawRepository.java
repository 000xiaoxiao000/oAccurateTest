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
public class UniversalCoverageRawRepository {
    private final JdbcTemplate jdbcTemplate;

    public UniversalCoverageRawRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String save(UniversalCoverageRawReport report) {
        if (!StringUtils.hasText(report.id)) {
            report.id = UUID.randomUUID().toString();
        }
        jdbcTemplate.update("""
                        INSERT INTO oat_universal_coverage_report (
                            id, project_id, app_id, source_type, commit_id, version_number,
                            branch, case_name, timestamp, coverage_data
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                report.id,
                report.projectId,
                report.appId,
                report.sourceType,
                report.commitId,
                report.versionNumber,
                report.branch,
                report.caseName,
                report.timestamp,
                report.coverageData);
        return report.id;
    }

    public List<UniversalCoverageRawReport> findByAppAndVersion(String appId, String sourceType, String versionNumber, String commitId) {
        if (StringUtils.hasText(commitId)) {
            return jdbcTemplate.query("""
                            SELECT * FROM oat_universal_coverage_report
                            WHERE app_id = ? AND source_type = ? AND commit_id = ?
                            ORDER BY create_time ASC
                            """,
                    this::mapRow, appId, sourceType, commitId);
        }
        return jdbcTemplate.query("""
                        SELECT * FROM oat_universal_coverage_report
                        WHERE app_id = ? AND source_type = ? AND version_number = ?
                        ORDER BY create_time ASC
                        """,
                this::mapRow, appId, sourceType, versionNumber);
    }

    private UniversalCoverageRawReport mapRow(ResultSet rs, int rowNum) throws SQLException {
        UniversalCoverageRawReport report = new UniversalCoverageRawReport();
        report.id = rs.getString("id");
        report.projectId = rs.getString("project_id");
        report.appId = rs.getString("app_id");
        report.sourceType = rs.getString("source_type");
        report.commitId = rs.getString("commit_id");
        report.versionNumber = rs.getString("version_number");
        report.branch = rs.getString("branch");
        report.caseName = rs.getString("case_name");
        report.timestamp = rs.getLong("timestamp");
        report.coverageData = rs.getString("coverage_data");
        return report;
    }

    public static class UniversalCoverageRawReport {
        public String id;
        public String projectId;
        public String appId;
        public String sourceType;
        public String commitId;
        public String versionNumber;
        public String branch;
        public String caseName;
        public Long timestamp;
        public String coverageData;
    }
}
