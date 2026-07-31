package com.oAT.web.coverage;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FrontendCoverageSchemaInitializer {
    private final JdbcTemplate jdbcTemplate;

    public FrontendCoverageSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        ensureCoreTables();
        ensureSourceTypeColumn("oat_coverage_report", "create_time");
        ensureSourceTypeColumn("oat_class_coverage", "class_name");
        ensureColumn("oat_coverage_report", "language",
                "ALTER TABLE `oat_coverage_report` ADD COLUMN `language` VARCHAR(32) DEFAULT 'JAVA' AFTER `source_type`");
        ensureColumn("oat_coverage_report", "build_id",
                "ALTER TABLE `oat_coverage_report` ADD COLUMN `build_id` VARCHAR(128) DEFAULT NULL AFTER `language`");
        ensureColumn("oat_coverage_report", "test_stage",
                "ALTER TABLE `oat_coverage_report` ADD COLUMN `test_stage` VARCHAR(64) DEFAULT NULL AFTER `build_id`");
        ensureColumn("oat_class_coverage", "language",
                "ALTER TABLE `oat_class_coverage` ADD COLUMN `language` VARCHAR(32) DEFAULT 'JAVA' AFTER `source_type`");
        ensureColumn("oat_class_coverage", "display_name",
                "ALTER TABLE `oat_class_coverage` ADD COLUMN `display_name` VARCHAR(512) DEFAULT NULL AFTER `language`");
        ensureColumn("oat_class_coverage", "source_path",
                "ALTER TABLE `oat_class_coverage` ADD COLUMN `source_path` VARCHAR(1024) DEFAULT NULL AFTER `display_name`");
        ensureIndex("oat_coverage_report", "idx_cov_report_app_create_time",
                "CREATE INDEX `idx_cov_report_app_create_time` ON `oat_coverage_report` (`app_id`, `create_time`)");
        ensureIndex("oat_class_coverage", "idx_class_cov_report_rates",
                "CREATE INDEX `idx_class_cov_report_rates` ON `oat_class_coverage` (`report_id`, `line_rate`, `branch_rate`, `method_rate`, `total_complexity`)");
        ensureColumn("oat_version_compare_report", "job_log_object_key",
                "ALTER TABLE `oat_version_compare_report` ADD COLUMN `job_log_object_key` VARCHAR(768) DEFAULT NULL AFTER `job_log`");
        ensureColumn("oat_version_compare_report", "differences_object_key",
                "ALTER TABLE `oat_version_compare_report` ADD COLUMN `differences_object_key` VARCHAR(768) DEFAULT NULL AFTER `differences_json`");
        ensureColumn("oat_version_compare_report", "cases_object_key",
                "ALTER TABLE `oat_version_compare_report` ADD COLUMN `cases_object_key` VARCHAR(768) DEFAULT NULL AFTER `cases_json`");
        ensureIndex("oat_version_compare_report", "idx_version_compare_object_keys",
                "CREATE INDEX `idx_version_compare_object_keys` ON `oat_version_compare_report` (`differences_object_key`(191), `cases_object_key`(191))");
        ensureColumn("oat_app", "language",
                "ALTER TABLE `oat_app` ADD COLUMN `language` VARCHAR(32) DEFAULT 'JAVA' AFTER `src_name`");
        ensureColumn("oat_app", "language_config_json",
                "ALTER TABLE `oat_app` ADD COLUMN `language_config_json` JSON DEFAULT NULL AFTER `language`");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_frontend_coverage_report` (
                  `id` VARCHAR(64) PRIMARY KEY,
                  `request_id` VARCHAR(128),
                  `project_id` VARCHAR(64) NOT NULL,
                  `app_id` VARCHAR(64) NOT NULL,
                  `commit_id` VARCHAR(128),
                  `version_number` VARCHAR(64),
                  `branch` VARCHAR(128),
                  `case_name` VARCHAR(255),
                  `build_id` VARCHAR(128),
                  `test_stage` VARCHAR(64),
                  `timestamp` BIGINT,
                  `coverage_json` LONGTEXT,
                  `object_key` VARCHAR(768),
                  `content_hash` VARCHAR(128),
                  `content_size` BIGINT,
                  `compressed_size` BIGINT,
                  `compress_type` VARCHAR(32),
                  `content_type` VARCHAR(128),
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  INDEX `idx_frontend_cov_request_id` (`request_id`),
                  INDEX `idx_frontend_cov_app_commit` (`app_id`, `commit_id`),
                  INDEX `idx_frontend_cov_app_version` (`app_id`, `version_number`),
                  INDEX `idx_frontend_cov_create_time` (`create_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端 Istanbul 覆盖率原始上报表'
                """);
        ensureColumn("oat_frontend_coverage_report", "request_id",
                "ALTER TABLE `oat_frontend_coverage_report` ADD COLUMN `request_id` VARCHAR(128) AFTER `id`");
        ensureColumn("oat_frontend_coverage_report", "build_id",
                "ALTER TABLE `oat_frontend_coverage_report` ADD COLUMN `build_id` VARCHAR(128) DEFAULT NULL AFTER `case_name`");
        ensureColumn("oat_frontend_coverage_report", "test_stage",
                "ALTER TABLE `oat_frontend_coverage_report` ADD COLUMN `test_stage` VARCHAR(64) DEFAULT NULL AFTER `build_id`");
        ensureNullableLongText("oat_frontend_coverage_report", "coverage_json");
        ensureObjectColumns("oat_frontend_coverage_report", "coverage_json");
        ensureIndex("oat_frontend_coverage_report", "idx_frontend_cov_request_id",
                "CREATE INDEX `idx_frontend_cov_request_id` ON `oat_frontend_coverage_report` (`request_id`)");
        ensureIndex("oat_frontend_coverage_report", "idx_frontend_cov_build_stage",
                "CREATE INDEX `idx_frontend_cov_build_stage` ON `oat_frontend_coverage_report` (`app_id`, `build_id`, `test_stage`)");
        ensureIndex("oat_frontend_coverage_report", "idx_frontend_cov_object_key",
                "CREATE INDEX `idx_frontend_cov_object_key` ON `oat_frontend_coverage_report` (`object_key`)");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_universal_coverage_report` (
                  `id` VARCHAR(64) PRIMARY KEY,
                  `project_id` VARCHAR(64) NOT NULL,
                  `app_id` VARCHAR(64) NOT NULL,
                  `source_type` VARCHAR(32) NOT NULL,
                  `commit_id` VARCHAR(128),
                  `version_number` VARCHAR(64),
                  `branch` VARCHAR(128),
                  `case_name` VARCHAR(255),
                  `build_id` VARCHAR(128),
                  `test_stage` VARCHAR(64),
                  `trace_id` VARCHAR(128),
                  `timestamp` BIGINT,
                  `coverage_data` LONGTEXT,
                  `object_key` VARCHAR(768),
                  `content_hash` VARCHAR(128),
                  `content_size` BIGINT,
                  `compressed_size` BIGINT,
                  `compress_type` VARCHAR(32),
                  `content_type` VARCHAR(128),
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  INDEX `idx_universal_cov_app_type_commit` (`app_id`, `source_type`, `commit_id`),
                  INDEX `idx_universal_cov_app_type_version` (`app_id`, `source_type`, `version_number`),
                  INDEX `idx_universal_cov_create_time` (`create_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多语言覆盖率原始上报表'
                """);
        ensureColumn("oat_universal_coverage_report", "build_id",
                "ALTER TABLE `oat_universal_coverage_report` ADD COLUMN `build_id` VARCHAR(128) DEFAULT NULL AFTER `case_name`");
        ensureColumn("oat_universal_coverage_report", "test_stage",
                "ALTER TABLE `oat_universal_coverage_report` ADD COLUMN `test_stage` VARCHAR(64) DEFAULT NULL AFTER `build_id`");
        ensureColumn("oat_universal_coverage_report", "trace_id",
                "ALTER TABLE `oat_universal_coverage_report` ADD COLUMN `trace_id` VARCHAR(128) DEFAULT NULL AFTER `test_stage`");
        ensureNullableLongText("oat_universal_coverage_report", "coverage_data");
        ensureObjectColumns("oat_universal_coverage_report", "coverage_data");
        ensureIndex("oat_universal_coverage_report", "idx_universal_cov_build_stage",
                "CREATE INDEX `idx_universal_cov_build_stage` ON `oat_universal_coverage_report` (`app_id`, `source_type`, `build_id`, `test_stage`)");
        ensureIndex("oat_universal_coverage_report", "idx_universal_cov_object_key",
                "CREATE INDEX `idx_universal_cov_object_key` ON `oat_universal_coverage_report` (`object_key`)");
    }

    private void ensureCoreTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_coverage_report` (
                  `id` VARCHAR(64) PRIMARY KEY,
                  `app_id` VARCHAR(64) NOT NULL,
                  `version_number` VARCHAR(64),
                  `repo_branch` VARCHAR(128),
                  `repo_commit_id` VARCHAR(128),
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `last_processed_time` VARCHAR(64),
                  `total_classes` BIGINT DEFAULT 0,
                  `covered_classes` BIGINT DEFAULT 0,
                  `total_methods` BIGINT DEFAULT 0,
                  `covered_methods` BIGINT DEFAULT 0,
                  `total_branches` BIGINT DEFAULT 0,
                  `covered_branches` BIGINT DEFAULT 0,
                  `total_branch_targets` BIGINT DEFAULT 0,
                  `covered_branch_targets` BIGINT DEFAULT 0,
                  `total_lines` BIGINT DEFAULT 0,
                  `covered_lines` BIGINT DEFAULT 0,
                  `total_complexity` INT DEFAULT 0,
                  `report_type` TINYINT DEFAULT 0,
                  `base_version_number` VARCHAR(64),
                  `base_repo_commit_id` VARCHAR(128),
                  `snapshot_fingerprint` VARCHAR(128),
                  `snapshot_last_update_time` VARCHAR(64),
                  `snapshot_count` INT,
                  `snapshot_ids` TEXT,
                  `inc_total_classes` BIGINT DEFAULT 0,
                  `inc_covered_classes` BIGINT DEFAULT 0,
                  `inc_total_lines` BIGINT DEFAULT 0,
                  `inc_covered_lines` BIGINT DEFAULT 0,
                  `inc_total_methods` BIGINT DEFAULT 0,
                  `inc_covered_methods` BIGINT DEFAULT 0,
                  `inc_total_branches` BIGINT DEFAULT 0,
                  `inc_covered_branches` BIGINT DEFAULT 0,
                  `inc_total_branch_targets` BIGINT DEFAULT 0,
                  `inc_covered_branch_targets` BIGINT DEFAULT 0,
                  `inc_total_complexity` INT DEFAULT 0,
                  INDEX `idx_app_version_commit_type` (`app_id`, `version_number`, `repo_commit_id`, `report_type`),
                  INDEX `idx_cov_report_app_create_time` (`app_id`, `create_time`),
                  INDEX `idx_app_branch` (`app_id`, `repo_branch`),
                  INDEX `idx_create_time` (`create_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='覆盖率报告头表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_class_coverage` (
                  `id` VARCHAR(128) PRIMARY KEY,
                  `report_id` VARCHAR(64) NOT NULL,
                  `app_id` VARCHAR(64),
                  `class_name` VARCHAR(512) NOT NULL,
                  `total_methods` INT DEFAULT 0,
                  `covered_methods` INT DEFAULT 0,
                  `total_branches` INT DEFAULT 0,
                  `covered_branches` INT DEFAULT 0,
                  `total_branch_targets` INT DEFAULT 0,
                  `covered_branch_targets` INT DEFAULT 0,
                  `total_lines` INT DEFAULT 0,
                  `covered_lines` INT DEFAULT 0,
                  `total_complexity` INT DEFAULT 0,
                  `line_rate` DOUBLE,
                  `branch_rate` DOUBLE,
                  `method_rate` DOUBLE,
                  `has_code_changes` BOOLEAN,
                  `methods_json` JSON,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX `idx_report_class` (`report_id`, `class_name`),
                  INDEX `idx_class_cov_report_rates` (`report_id`, `line_rate`, `branch_rate`, `method_rate`, `total_complexity`),
                  INDEX `idx_report_line_rate` (`report_id`, `line_rate`),
                  INDEX `idx_report_branch_rate` (`report_id`, `branch_rate`),
                  INDEX `idx_report_method_rate` (`report_id`, `method_rate`),
                  INDEX `idx_report_complexity` (`report_id`, `total_complexity`),
                  INDEX `idx_app` (`app_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='类覆盖率明细表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_method_coverage` (
                  `id` VARCHAR(160) PRIMARY KEY,
                  `class_coverage_id` VARCHAR(128) NOT NULL,
                  `report_id` VARCHAR(64) NOT NULL,
                  `app_id` VARCHAR(64),
                  `class_name` VARCHAR(512) NOT NULL,
                  `method_name` VARCHAR(512),
                  `method_desc` VARCHAR(1024),
                  `method_order` INT NOT NULL,
                  `total_lines` INT DEFAULT 0,
                  `covered_lines` INT DEFAULT 0,
                  `total_branches` INT DEFAULT 0,
                  `covered_branches` INT DEFAULT 0,
                  `total_branch_targets` INT DEFAULT 0,
                  `covered_branch_targets` INT DEFAULT 0,
                  `complexity` INT DEFAULT 0,
                  `covered` BOOLEAN,
                  `branch_rate` DOUBLE,
                  `has_code_changes` BOOLEAN,
                  `detail_json` JSON NOT NULL,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX `idx_class_order` (`class_coverage_id`, `method_order`),
                  INDEX `idx_report_method` (`report_id`, `method_name`),
                  INDEX `idx_report_class` (`report_id`, `class_name`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方法覆盖率明细表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_system_snapshot` (
                  `id` VARCHAR(64) PRIMARY KEY,
                  `project_id` VARCHAR(64) NOT NULL,
                  `app_id` VARCHAR(64),
                  `trace_id` VARCHAR(128),
                  `title` VARCHAR(512),
                  `sub_title` VARCHAR(512),
                  `topic_image` VARCHAR(1024),
                  `snapshot_describe` TEXT,
                  `directory` VARCHAR(64),
                  `version` VARCHAR(64),
                  `version_cycle` INT,
                  `version_last_update` DATETIME,
                  `report_status` INT DEFAULT 0,
                  `payload_json` JSON NOT NULL,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX `idx_project_app_directory` (`project_id`, `app_id`, `directory`, `create_time`),
                  INDEX `idx_project_app` (`project_id`, `app_id`, `create_time`),
                  INDEX `idx_project_trace` (`project_id`, `trace_id`),
                  INDEX `idx_app` (`app_id`, `create_time`),
                  INDEX `idx_project` (`project_id`, `create_time`),
                  INDEX `idx_report_status` (`report_status`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统快照兼容表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_version_compare_report` (
                  `id` VARCHAR(64) PRIMARY KEY,
                  `project_id` VARCHAR(64),
                  `app_id` VARCHAR(64),
                  `job_id` VARCHAR(128),
                  `job_name` VARCHAR(256),
                  `job_log` TEXT,
                  `job_log_object_key` VARCHAR(768),
                  `source_version` TEXT,
                  `target_version` TEXT,
                  `git_branch` VARCHAR(512),
                  `git_old_commit` VARCHAR(512),
                  `git_new_commit` VARCHAR(512),
                  `add_class_count` INT DEFAULT 0,
                  `update_class_count` INT DEFAULT 0,
                  `delete_class_count` INT DEFAULT 0,
                  `add_method_count` INT DEFAULT 0,
                  `update_method_count` INT DEFAULT 0,
                  `delete_method_count` INT DEFAULT 0,
                  `impact_case_count` INT DEFAULT 0,
                  `differences_json` JSON,
                  `differences_object_key` VARCHAR(768),
                  `cases_json` JSON,
                  `cases_object_key` VARCHAR(768),
                  `payload_json` JSON NOT NULL,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX `idx_project_app_time` (`project_id`, `app_id`, `create_time`),
                  INDEX `idx_job` (`job_id`),
                  INDEX `idx_version_compare_object_keys` (`differences_object_key`(191), `cases_object_key`(191))
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本比对报告表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_static_source_class` (
                  `id` VARCHAR(64) PRIMARY KEY,
                  `app_id` VARCHAR(64) NOT NULL,
                  `type` VARCHAR(32) NOT NULL DEFAULT 'classInfo',
                  `class_id` VARCHAR(128),
                  `class_name` VARCHAR(512) NOT NULL,
                  `method_maps_json` JSON,
                  `source_code` MEDIUMTEXT,
                  `source_code_path` VARCHAR(1024),
                  `source_code_hash` VARCHAR(128),
                  `source_code_size` BIGINT,
                  `payload_json` JSON NOT NULL,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  UNIQUE KEY `uk_app_class` (`app_id`, `class_name`),
                  INDEX `idx_app` (`app_id`),
                  INDEX `idx_class_id` (`class_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='静态源码类信息表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_system_snapshot_artifact` (
                  `id` VARCHAR(160) PRIMARY KEY,
                  `snapshot_id` VARCHAR(64) NOT NULL,
                  `artifact_type` VARCHAR(32) NOT NULL,
                  `artifact_order` INT NOT NULL,
                  `storage_type` VARCHAR(16) NOT NULL DEFAULT 'DB',
                  `content_path` VARCHAR(1024),
                  `content_hash` VARCHAR(128),
                  `content_size` BIGINT,
                  `content_text` MEDIUMTEXT,
                  `payload_json` JSON,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  INDEX `idx_snapshot_type_order` (`snapshot_id`, `artifact_type`, `artifact_order`),
                  INDEX `idx_hash` (`content_hash`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统快照大字段制品表'
                """);
    }

    private void ensureSourceTypeColumn(String tableName, String afterColumn) {
        if (!tableExists(tableName)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = 'source_type'
                """, Integer.class, tableName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` ADD COLUMN `source_type` VARCHAR(32) DEFAULT 'JAVA' AFTER `" + afterColumn + "`");
    }

    private void ensureColumn(String tableName, String columnName, String alterSql) {
        if (!tableExists(tableName)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(alterSql);
    }

    private void ensureObjectColumns(String tableName, String afterColumn) {
        ensureColumn(tableName, "object_key",
                "ALTER TABLE `" + tableName + "` ADD COLUMN `object_key` VARCHAR(768) DEFAULT NULL AFTER `" + afterColumn + "`");
        ensureColumn(tableName, "content_hash",
                "ALTER TABLE `" + tableName + "` ADD COLUMN `content_hash` VARCHAR(128) DEFAULT NULL AFTER `object_key`");
        ensureColumn(tableName, "content_size",
                "ALTER TABLE `" + tableName + "` ADD COLUMN `content_size` BIGINT DEFAULT NULL AFTER `content_hash`");
        ensureColumn(tableName, "compressed_size",
                "ALTER TABLE `" + tableName + "` ADD COLUMN `compressed_size` BIGINT DEFAULT NULL AFTER `content_size`");
        ensureColumn(tableName, "compress_type",
                "ALTER TABLE `" + tableName + "` ADD COLUMN `compress_type` VARCHAR(32) DEFAULT NULL AFTER `compressed_size`");
        ensureColumn(tableName, "content_type",
                "ALTER TABLE `" + tableName + "` ADD COLUMN `content_type` VARCHAR(128) DEFAULT NULL AFTER `compress_type`");
    }

    private void ensureNullableLongText(String tableName, String columnName) {
        if (!tableExists(tableName)) {
            return;
        }
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, String.class, tableName, columnName);
        if (nullable == null) {
            return;
        }
        if ("YES".equalsIgnoreCase(nullable)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + columnName + "` LONGTEXT NULL");
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        if (!tableExists(tableName)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(createSql);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }
}
