CREATE DATABASE IF NOT EXISTS `oaccurate_test` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `oaccurate_test`;

CREATE TABLE IF NOT EXISTS `oat_snapshot_commit_mapping` (
  `id` VARCHAR(64) PRIMARY KEY,
  `snapshot_id` VARCHAR(64) NOT NULL,
  `app_id` VARCHAR(64) NOT NULL,
  `project_id` VARCHAR(64) NOT NULL,
  `version_number` VARCHAR(64),
  `repo_branch` VARCHAR(128),
  `repo_commit_id` VARCHAR(128),
  `mapping_source` VARCHAR(32),
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `snapshot_create_time` DATETIME,
  UNIQUE KEY `uk_snapshot` (`snapshot_id`),
  INDEX `idx_app_commit` (`app_id`, `repo_commit_id`),
  INDEX `idx_app_version` (`app_id`, `version_number`),
  INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快照Commit映射表';

CREATE TABLE IF NOT EXISTS `oat_probe_instance_status` (
  `probe_key` VARCHAR(128) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `app_id` VARCHAR(64),
  `app_name` VARCHAR(128),
  `session_id` VARCHAR(128),
  `address_ip` VARCHAR(64),
  `pid` VARCHAR(64),
  `system_dir` VARCHAR(512),
  `agent_version` VARCHAR(64),
  `status` VARCHAR(32) NOT NULL,
  `login_time` DATETIME,
  `last_heartbeat_time` BIGINT,
  `last_status_change_time` DATETIME,
  `online_since` DATETIME,
  `offline_since` DATETIME,
  `last_alert_event_type` VARCHAR(64),
  `last_alert_time` DATETIME,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_status` (`status`),
  INDEX `idx_app_status` (`app_id`, `status`),
  INDEX `idx_app_update_time` (`app_id`, `update_time`),
  INDEX `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='探针实例当前状态表';

CREATE TABLE IF NOT EXISTS `oat_client_session` (
  `id` VARCHAR(64) PRIMARY KEY,
  `type` VARCHAR(32) NOT NULL DEFAULT 'session',
  `status` VARCHAR(32),
  `app_id` VARCHAR(64),
  `agent_version` VARCHAR(64),
  `system_dir` VARCHAR(512),
  `pid` VARCHAR(64),
  `address_ip` VARCHAR(64),
  `login_time` VARCHAR(32),
  `last_heartbeat_time` BIGINT,
  `agent_logs` MEDIUMTEXT,
  `package_verify_data` TEXT,
  `session_json` JSON,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_app_update_time` (`app_id`, `update_time`),
  INDEX `idx_status_update_time` (`status`, `update_time`),
  INDEX `idx_last_heartbeat_time` (`last_heartbeat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端会话表';

-- system 索引已按计划拆分到 phase5_normalized_core.sql 中的 oat_user/oat_project/oat_app/oat_label_group/oat_project_member。
-- system_log 属于日志检索读模型，按 coverage-storage-plan.md 进入 ES。
