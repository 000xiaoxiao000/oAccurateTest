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
        ensureSourceTypeColumn("oat_coverage_report", "create_time");
        ensureSourceTypeColumn("oat_class_coverage", "class_name");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `oat_frontend_coverage_report` (
                  `id` VARCHAR(64) PRIMARY KEY,
                  `project_id` VARCHAR(64) NOT NULL,
                  `app_id` VARCHAR(64) NOT NULL,
                  `commit_id` VARCHAR(128),
                  `version_number` VARCHAR(64),
                  `branch` VARCHAR(128),
                  `case_name` VARCHAR(255),
                  `timestamp` BIGINT,
                  `coverage_json` LONGTEXT NOT NULL,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  INDEX `idx_frontend_cov_app_commit` (`app_id`, `commit_id`),
                  INDEX `idx_frontend_cov_app_version` (`app_id`, `version_number`),
                  INDEX `idx_frontend_cov_create_time` (`create_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端 Istanbul 覆盖率原始上报表'
                """);
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
                  `timestamp` BIGINT,
                  `coverage_data` LONGTEXT NOT NULL,
                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                  INDEX `idx_universal_cov_app_type_commit` (`app_id`, `source_type`, `commit_id`),
                  INDEX `idx_universal_cov_app_type_version` (`app_id`, `source_type`, `version_number`),
                  INDEX `idx_universal_cov_create_time` (`create_time`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多语言覆盖率原始上报表'
                """);
    }

    private void ensureSourceTypeColumn(String tableName, String afterColumn) {
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
}
