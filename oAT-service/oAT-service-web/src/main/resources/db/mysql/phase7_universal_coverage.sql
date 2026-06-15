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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多语言覆盖率原始上报表';
