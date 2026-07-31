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
    private final CoverageStorage coverageStorage;

    public UniversalCoverageRawRepository(JdbcTemplate jdbcTemplate, CoverageStorage coverageStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.coverageStorage = coverageStorage;
    }

    @Transactional
    public String save(UniversalCoverageRawReport report) {
        if (!StringUtils.hasText(report.id)) {
            report.id = UUID.randomUUID().toString();
        }
        CoverageStorage.StoredObject object = storeRawCoverage(report);
        jdbcTemplate.update("""
                        INSERT INTO oat_universal_coverage_report (
                            id, project_id, app_id, source_type, commit_id, version_number,
                            branch, case_name, build_id, test_stage, trace_id, timestamp, coverage_data,
                            object_key, content_hash, content_size, compressed_size, compress_type, content_type
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                report.id,
                report.projectId,
                report.appId,
                report.sourceType,
                report.commitId,
                report.versionNumber,
                report.branch,
                report.caseName,
                report.buildId,
                report.testStage,
                report.traceId,
                report.timestamp,
                null,
                object.objectKey(),
                object.contentHash(),
                object.contentSize(),
                object.compressedSize(),
                object.compressType(),
                object.contentType());
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

    public boolean existsByAppTypeAndVersionOrCommit(String appId, String sourceType, String versionNumber, String commitId) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(sourceType)) {
            return false;
        }
        if (StringUtils.hasText(commitId)) {
            return count("""
                    SELECT COUNT(1) FROM oat_universal_coverage_report
                    WHERE app_id = ? AND source_type = ? AND commit_id = ?
                    """, appId, sourceType, commitId) > 0;
        }
        return StringUtils.hasText(versionNumber) && count("""
                SELECT COUNT(1) FROM oat_universal_coverage_report
                WHERE app_id = ? AND source_type = ? AND version_number = ?
                """, appId, sourceType, versionNumber) > 0;
    }

    public Long findLatestTimestampByAppAndType(String appId, String sourceType) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(sourceType)) {
            return null;
        }
        List<Long> values = jdbcTemplate.query("""
                        SELECT COALESCE(`timestamp`, UNIX_TIMESTAMP(create_time) * 1000) latest_time
                        FROM oat_universal_coverage_report
                        WHERE app_id = ? AND source_type = ?
                        ORDER BY create_time DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getLong("latest_time"),
                appId,
                sourceType);
        return values.isEmpty() ? null : values.get(0);
    }

    public Long findLatestTimestampByAppTypeAndVersion(String appId, String sourceType, String versionNumber, String commitId) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(sourceType)) {
            return null;
        }
        List<Long> values;
        if (StringUtils.hasText(commitId)) {
            values = jdbcTemplate.query("""
                            SELECT COALESCE(`timestamp`, UNIX_TIMESTAMP(create_time) * 1000) latest_time
                            FROM oat_universal_coverage_report
                            WHERE app_id = ? AND source_type = ? AND commit_id = ?
                            ORDER BY latest_time DESC
                            LIMIT 1
                            """,
                    (rs, rowNum) -> rs.getLong("latest_time"),
                    appId,
                    sourceType,
                    commitId);
            if (!values.isEmpty()) {
                return values.get(0);
            }
        }
        if (!StringUtils.hasText(versionNumber)) {
            return null;
        }
        values = jdbcTemplate.query("""
                        SELECT COALESCE(`timestamp`, UNIX_TIMESTAMP(create_time) * 1000) latest_time
                        FROM oat_universal_coverage_report
                        WHERE app_id = ? AND source_type = ? AND version_number = ?
                        ORDER BY latest_time DESC
                        LIMIT 1
                        """,
                (rs, rowNum) -> rs.getLong("latest_time"),
                appId,
                sourceType,
                versionNumber);
        return values.isEmpty() ? null : values.get(0);
    }

    public List<UniversalCoverageRawReport> findFootprints(String projectId, String appId, String sourceType,
                                                           String versionNumber, String commitId) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM oat_universal_coverage_report
                WHERE project_id = ?
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(projectId);
        if (StringUtils.hasText(appId)) {
            sql.append(" AND app_id = ?");
            args.add(appId);
        }
        if (StringUtils.hasText(sourceType)) {
            sql.append(" AND source_type = ?");
            args.add(sourceType);
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

    public UniversalCoverageRawReport findById(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        List<UniversalCoverageRawReport> reports = jdbcTemplate.query("""
                        SELECT * FROM oat_universal_coverage_report
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
        return jdbcTemplate.update("DELETE FROM oat_universal_coverage_report WHERE id = ?", id);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
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
        report.buildId = rs.getString("build_id");
        report.testStage = rs.getString("test_stage");
        report.traceId = rs.getString("trace_id");
        report.timestamp = rs.getLong("timestamp");
        if (rs.wasNull()) {
            report.timestamp = null;
        }
        report.objectKey = rs.getString("object_key");
        report.contentHash = rs.getString("content_hash");
        report.contentSize = getLong(rs, "content_size");
        report.compressedSize = getLong(rs, "compressed_size");
        report.compressType = rs.getString("compress_type");
        report.contentType = rs.getString("content_type");
        if (StringUtils.hasText(report.objectKey)) {
            report.coverageData = coverageStorage.loadText(report.objectKey, report.compressType);
        }
        return report;
    }

    private CoverageStorage.StoredObject storeRawCoverage(UniversalCoverageRawReport report) {
        if (!StringUtils.hasText(report.coverageData)) {
            throw new IllegalArgumentException("coverageData must not be empty");
        }
        return coverageStorage.storeText(buildObjectKey(report), report.coverageData, "application/json");
    }

    private String buildObjectKey(UniversalCoverageRawReport report) {
        String appId = StringUtils.hasText(report.appId) ? report.appId : "unknown-app";
        String sourceType = StringUtils.hasText(report.sourceType) ? report.sourceType : "UNKNOWN";
        String date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return "coverage/raw/" + safe(appId) + "/" + safe(sourceType) + "/" + date + "/" + report.id + ".json.gz";
    }

    private String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
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
        public String buildId;
        public String testStage;
        public String traceId;
        public Long timestamp;
        public String coverageData;
        public String objectKey;
        public String contentHash;
        public Long contentSize;
        public Long compressedSize;
        public String compressType;
        public String contentType;
    }
}
