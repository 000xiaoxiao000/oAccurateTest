CREATE DATABASE IF NOT EXISTS `oaccurate_test` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `oaccurate_test`;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='类覆盖率明细表';
