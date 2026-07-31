ALTER TABLE `oat_coverage_report`
  ADD COLUMN `source_type` VARCHAR(32) DEFAULT 'JAVA' AFTER `create_time`;

ALTER TABLE `oat_class_coverage`
  ADD COLUMN `source_type` VARCHAR(32) DEFAULT 'JAVA' AFTER `class_name`;

CREATE TABLE IF NOT EXISTS `oat_frontend_coverage_report` (
  `id` VARCHAR(64) PRIMARY KEY,
  `request_id` VARCHAR(128),
  `project_id` VARCHAR(64) NOT NULL,
  `app_id` VARCHAR(64) NOT NULL,
  `commit_id` VARCHAR(128),
  `version_number` VARCHAR(64),
  `branch` VARCHAR(128),
  `case_name` VARCHAR(255),
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
  INDEX `idx_frontend_cov_create_time` (`create_time`),
  INDEX `idx_frontend_cov_object_key` (`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端 Istanbul 覆盖率原始上报表';
