package com.oAT.web.coverage.storage;

import com.oAT.web.coverage.CoverageStorage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CoverageStorageMigrationService {
    private final JdbcTemplate jdbcTemplate;
    private final CoverageStorage coverageStorage;

    public CoverageStorageMigrationService(JdbcTemplate jdbcTemplate, CoverageStorage coverageStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.coverageStorage = coverageStorage;
    }

    public Map<String, Integer> pendingCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("frontendRaw", count("""
                SELECT COUNT(1)
                FROM oat_frontend_coverage_report
                WHERE coverage_json IS NOT NULL AND object_key IS NULL
                """));
        result.put("universalRaw", count("""
                SELECT COUNT(1)
                FROM oat_universal_coverage_report
                WHERE coverage_data IS NOT NULL AND object_key IS NULL
                """));
        result.put("staticSource", count("""
                SELECT COUNT(1)
                FROM oat_static_source_class
                WHERE source_code IS NOT NULL AND source_code_path IS NULL
                """));
        result.put("snapshotArtifact", count("""
                SELECT COUNT(1)
                FROM oat_system_snapshot_artifact
                WHERE content_text IS NOT NULL AND (content_path IS NULL OR storage_type = 'DB')
                """));
        result.put("versionCompare", count("""
                SELECT COUNT(1)
                FROM oat_version_compare_report
                WHERE (job_log IS NOT NULL AND job_log_object_key IS NULL)
                   OR (differences_json IS NOT NULL AND differences_object_key IS NULL)
                   OR (cases_json IS NOT NULL AND cases_object_key IS NULL)
                """));
        result.put("classMethodsJson", count("""
                SELECT COUNT(1)
                FROM oat_class_coverage
                WHERE methods_json IS NOT NULL
                """));
        return result;
    }

    @Transactional
    public Map<String, Integer> migrateBatch(int requestedLimit) {
        if (!coverageStorage.isAvailable()) {
            throw new IllegalStateException("MinIO coverage storage is not available");
        }
        int limit = requestedLimit <= 0 ? 100 : Math.min(requestedLimit, 1000);
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("frontendRaw", migrateFrontendRaw(limit));
        result.put("universalRaw", migrateUniversalRaw(limit));
        result.put("staticSource", migrateStaticSource(limit));
        result.put("snapshotArtifact", migrateSnapshotArtifacts(limit));
        result.put("versionCompare", migrateVersionCompare(limit));
        result.put("classMethodsJsonCleared", clearClassMethodsJson(limit));
        return result;
    }

    private int migrateFrontendRaw(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, app_id, coverage_json
                FROM oat_frontend_coverage_report
                WHERE coverage_json IS NOT NULL AND object_key IS NULL
                LIMIT ?
                """, limit);
        int count = 0;
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String appId = text(row.get("app_id"));
            String content = text(row.get("coverage_json"));
            CoverageStorage.StoredObject object = coverageStorage.storeText(
                    "coverage/frontend/" + safe(appId) + "/migrated/" + id + ".json.gz", content, "application/json");
            count += jdbcTemplate.update("""
                    UPDATE oat_frontend_coverage_report
                    SET coverage_json = NULL, object_key = ?, content_hash = ?, content_size = ?,
                        compressed_size = ?, compress_type = ?, content_type = ?
                    WHERE id = ?
                    """, object.objectKey(), object.contentHash(), object.contentSize(), object.compressedSize(),
                    object.compressType(), object.contentType(), id);
        }
        return count;
    }

    private int migrateUniversalRaw(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, app_id, source_type, coverage_data
                FROM oat_universal_coverage_report
                WHERE coverage_data IS NOT NULL AND object_key IS NULL
                LIMIT ?
                """, limit);
        int count = 0;
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String appId = text(row.get("app_id"));
            String sourceType = text(row.get("source_type"));
            String content = text(row.get("coverage_data"));
            CoverageStorage.StoredObject object = coverageStorage.storeText(
                    "coverage/raw/" + safe(appId) + "/" + safe(sourceType) + "/migrated/" + id + ".json.gz",
                    content, "application/json");
            count += jdbcTemplate.update("""
                    UPDATE oat_universal_coverage_report
                    SET coverage_data = NULL, object_key = ?, content_hash = ?, content_size = ?,
                        compressed_size = ?, compress_type = ?, content_type = ?
                    WHERE id = ?
                    """, object.objectKey(), object.contentHash(), object.contentSize(), object.compressedSize(),
                    object.compressType(), object.contentType(), id);
        }
        return count;
    }

    private int migrateStaticSource(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, app_id, class_name, source_code
                FROM oat_static_source_class
                WHERE source_code IS NOT NULL AND source_code_path IS NULL
                LIMIT ?
                """, limit);
        int count = 0;
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String appId = text(row.get("app_id"));
            String className = text(row.get("class_name"));
            String content = text(row.get("source_code"));
            CoverageStorage.StoredObject object = coverageStorage.storeText(
                    "source/" + safe(appId) + "/" + safe(className) + "-" + id + ".java.gz",
                    content, "text/x-java-source");
            count += jdbcTemplate.update("""
                    UPDATE oat_static_source_class
                    SET source_code = NULL, source_code_path = ?, source_code_hash = ?, source_code_size = ?,
                        payload_json = JSON_REMOVE(payload_json, '$.classInfo.sourceCode')
                    WHERE id = ?
                    """, object.objectKey(), object.contentHash(), object.contentSize(), id);
        }
        return count;
    }

    private int migrateSnapshotArtifacts(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, snapshot_id, artifact_type, artifact_order, content_text
                FROM oat_system_snapshot_artifact
                WHERE content_text IS NOT NULL AND (content_path IS NULL OR storage_type = 'DB')
                LIMIT ?
                """, limit);
        int count = 0;
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String snapshotId = text(row.get("snapshot_id"));
            String artifactType = text(row.get("artifact_type"));
            String content = text(row.get("content_text"));
            CoverageStorage.StoredObject object = coverageStorage.storeText(
                    "snapshot/" + safe(snapshotId) + "/" + safe(artifactType) + "/" + id + ".json.gz",
                    content, "application/json");
            count += jdbcTemplate.update("""
                    UPDATE oat_system_snapshot_artifact
                    SET storage_type = 'MINIO', content_text = NULL, content_path = ?,
                        content_hash = ?, content_size = ?
                    WHERE id = ?
                    """, object.objectKey(), object.contentHash(), object.contentSize(), id);
        }
        if (count > 0) {
            jdbcTemplate.update("""
                    UPDATE oat_system_snapshot
                    SET payload_json = JSON_REMOVE(payload_json, '$.codes', '$.sqls', '$.remotes')
                    WHERE JSON_CONTAINS_PATH(payload_json, 'one', '$.codes', '$.sqls', '$.remotes')
                    """);
        }
        return count;
    }

    private int migrateVersionCompare(int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, app_id, job_log, differences_json, cases_json
                FROM oat_version_compare_report
                WHERE (job_log IS NOT NULL AND job_log_object_key IS NULL)
                   OR (differences_json IS NOT NULL AND differences_object_key IS NULL)
                   OR (cases_json IS NOT NULL AND cases_object_key IS NULL)
                LIMIT ?
                """, limit);
        int count = 0;
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String appId = text(row.get("app_id"));
            String jobLogKey = null;
            String differencesKey = null;
            String casesKey = null;
            if (StringUtils.hasText(text(row.get("job_log")))) {
                jobLogKey = coverageStorage.storeText("version-report/" + safe(appId) + "/" + id + "/job-log.txt.gz",
                        text(row.get("job_log")), "text/plain").objectKey();
            }
            if (StringUtils.hasText(text(row.get("differences_json")))) {
                differencesKey = coverageStorage.storeText("version-report/" + safe(appId) + "/" + id + "/differences.json.gz",
                        text(row.get("differences_json")), "application/json").objectKey();
            }
            if (StringUtils.hasText(text(row.get("cases_json")))) {
                casesKey = coverageStorage.storeText("version-report/" + safe(appId) + "/" + id + "/cases.json.gz",
                        text(row.get("cases_json")), "application/json").objectKey();
            }
            count += jdbcTemplate.update("""
                    UPDATE oat_version_compare_report
                    SET job_log = NULL,
                        job_log_object_key = COALESCE(job_log_object_key, ?),
                        differences_json = NULL,
                        differences_object_key = COALESCE(differences_object_key, ?),
                        cases_json = NULL,
                        cases_object_key = COALESCE(cases_object_key, ?),
                        payload_json = JSON_REMOVE(payload_json,
                            '$.compareReport.jobLog', '$.compareReport.differences', '$.compareReport.cases')
                    WHERE id = ?
                    """, jobLogKey, differencesKey, casesKey, id);
        }
        return count;
    }

    private int clearClassMethodsJson(int limit) {
        return jdbcTemplate.update("""
                UPDATE oat_class_coverage
                SET methods_json = NULL
                WHERE methods_json IS NOT NULL
                LIMIT ?
                """, limit);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[^a-zA-Z0-9._-]", "_") : "unknown";
    }
}
