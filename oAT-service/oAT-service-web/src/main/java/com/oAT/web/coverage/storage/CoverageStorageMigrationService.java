package com.oAT.web.coverage.storage;

import com.oAT.web.coverage.CoverageStorage;
import com.oAT.web.coveragecore.index.CoverageEsIndexService;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.springframework.dao.DataAccessException;
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
    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;
    private final CoverageEsIndexService coverageEsIndexService;

    public CoverageStorageMigrationService(JdbcTemplate jdbcTemplate,
                                           CoverageStorage coverageStorage,
                                           CoverageReportRepository coverageReportRepository,
                                           ClassCoverageRepository classCoverageRepository,
                                           CoverageEsIndexService coverageEsIndexService) {
        this.jdbcTemplate = jdbcTemplate;
        this.coverageStorage = coverageStorage;
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
        this.coverageEsIndexService = coverageEsIndexService;
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
        return result;
    }

    public Map<String, Integer> rebuildCoverageEsReadModel(int requestedLimit) {
        return rebuildCoverageEsReadModel(requestedLimit, 0);
    }

    public Map<String, Integer> rebuildCoverageEsReadModel(int requestedLimit, int requestedOffset) {
        int limit = requestedLimit <= 0 ? 100 : Math.min(requestedLimit, 1000);
        int offset = Math.max(requestedOffset, 0);
        Map<String, Integer> result = new LinkedHashMap<>();
        int reportsIndexed = 0;
        if (tableExists("oat_coverage_report")) {
            List<CoverageReportIndex> reports = coverageReportRepository.findBatchForEsRebuild(limit, offset);
            for (CoverageReportIndex report : reports) {
                coverageEsIndexService.indexTrend(report);
                reportsIndexed++;
            }
        }
        int classesIndexed = 0;
        if (tableExists("oat_class_coverage")) {
            List<ClassCoverageIndex> classes = classCoverageRepository.findBatchWithMethodsForEsRebuild(limit, offset);
            coverageEsIndexService.indexClassCoverage(classes);
            classesIndexed = classes.size();
        }
        result.put("offset", offset);
        result.put("coverageTrendsIndexed", reportsIndexed);
        result.put("coverageMethodSearchClassesIndexed", classesIndexed);
        return result;
    }

    private int migrateFrontendRaw(int limit) {
        if (!tableExists("oat_frontend_coverage_report")) {
            return 0;
        }
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
        if (!tableExists("oat_universal_coverage_report")) {
            return 0;
        }
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
        if (!tableExists("oat_static_source_class")) {
            return 0;
        }
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
        if (!tableExists("oat_system_snapshot_artifact")) {
            return 0;
        }
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
        if (count > 0 && tableExists("oat_system_snapshot")) {
            jdbcTemplate.update("""
                    UPDATE oat_system_snapshot
                    SET payload_json = JSON_REMOVE(payload_json, '$.codes', '$.sqls', '$.remotes')
                    WHERE JSON_CONTAINS_PATH(payload_json, 'one', '$.codes', '$.sqls', '$.remotes')
                    """);
        }
        return count;
    }

    private int migrateVersionCompare(int limit) {
        if (!tableExists("oat_version_compare_report")) {
            return 0;
        }
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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int count(String sql) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
            return value == null ? 0 : value;
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private boolean tableExists(String tableName) {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return value != null && value > 0;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[^a-zA-Z0-9._-]", "_") : "unknown";
    }
}
