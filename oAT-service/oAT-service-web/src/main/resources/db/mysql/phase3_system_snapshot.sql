CREATE DATABASE IF NOT EXISTS `oaccurate_test` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `oaccurate_test`;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统快照兼容表';
