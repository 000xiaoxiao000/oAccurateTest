CREATE TABLE IF NOT EXISTS `oat_user` (
  `id` VARCHAR(64) PRIMARY KEY,
  `name` VARCHAR(128),
  `nick_name` VARCHAR(128),
  `email` VARCHAR(128),
  `password` VARCHAR(256),
  `header` VARCHAR(512),
  `phone` VARCHAR(64),
  `readme` TEXT,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_email` (`email`),
  INDEX `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `oat_project` (
  `id` VARCHAR(64) PRIMARY KEY,
  `name` VARCHAR(128),
  `project_describe` TEXT,
  `owner_id` VARCHAR(64),
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

CREATE TABLE IF NOT EXISTS `oat_app` (
  `id` VARCHAR(64) PRIMARY KEY,
  `name` VARCHAR(128),
  `project_id` VARCHAR(64),
  `range_type` VARCHAR(32),
  `src_name` VARCHAR(256),
  `app_describe` TEXT,
  `properties_text` TEXT,
  `current_version` VARCHAR(128),
  `current_branch` VARCHAR(256),
  `current_commit_id` VARCHAR(256),
  `repo_address` VARCHAR(512),
  `repo_user_name` VARCHAR(128),
  `repo_password` VARCHAR(256),
  `create_user_id` VARCHAR(64),
  `probe_alert_enabled` BOOLEAN,
  `probe_offline_threshold_seconds` INT,
  `probe_webhook_url` VARCHAR(1024),
  `probe_alert_on_online` BOOLEAN,
  `probe_alert_on_offline` BOOLEAN,
  `probe_alert_on_recovered` BOOLEAN,
  `snapshot_dirs_json` JSON,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project` (`project_id`),
  INDEX `idx_range` (`range_type`),
  INDEX `idx_current_version` (`current_version`, `current_commit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用表';

CREATE TABLE IF NOT EXISTS `oat_label_group` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `label_type` VARCHAR(64),
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project_type` (`project_id`, `label_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签组表';

CREATE TABLE IF NOT EXISTS `oat_project_member` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64) NOT NULL,
  `member_id` VARCHAR(64) NOT NULL,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_project_member` (`project_id`, `member_id`),
  INDEX `idx_member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员表';

CREATE TABLE IF NOT EXISTS `oat_system_log` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `user_id` VARCHAR(64),
  `user_name` VARCHAR(128),
  `action` VARCHAR(128),
  `title` TEXT,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project_time` (`project_id`, `update_time`),
  INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统日志表';

CREATE TABLE IF NOT EXISTS `oat_version` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `app_id` VARCHAR(64),
  `version_number` VARCHAR(128),
  `repo_branch` VARCHAR(256),
  `repo_commit_id` VARCHAR(256),
  `source_type` VARCHAR(64),
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project_app` (`project_id`, `app_id`, `create_time`),
  INDEX `idx_app_version` (`app_id`, `version_number`),
  INDEX `idx_app_branch_commit` (`app_id`, `repo_branch`, `repo_commit_id`),
  INDEX `idx_app_commit` (`app_id`, `repo_commit_id`),
  INDEX `idx_app_branch` (`app_id`, `repo_branch`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本表';

CREATE TABLE IF NOT EXISTS `oat_version_compare_report` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `app_id` VARCHAR(64),
  `job_id` VARCHAR(128),
  `job_name` VARCHAR(256),
  `job_log` TEXT,
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
  `cases_json` JSON,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project_app_time` (`project_id`, `app_id`, `create_time`),
  INDEX `idx_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本比对报告表';

CREATE TABLE IF NOT EXISTS `oat_snapshot` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `app_id` VARCHAR(64),
  `trace_id` VARCHAR(128),
  `snapshot_name` VARCHAR(256),
  `create_user` VARCHAR(64),
  `snapshot_describe` TEXT,
  `share_flag` BOOLEAN,
  `labels_json` JSON,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project_user_time` (`project_id`, `create_user`, `create_time`),
  INDEX `idx_app_time` (`app_id`, `create_time`),
  INDEX `idx_project_user_trace` (`project_id`, `create_user`, `trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人快照表';

CREATE TABLE IF NOT EXISTS `oat_usecase` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `directory_id` VARCHAR(64),
  `title` VARCHAR(512),
  `content` MEDIUMTEXT,
  `head_image` VARCHAR(1024),
  `snapshots_json` JSON,
  `system_snapshots_json` JSON,
  `defects_json` JSON,
  `prd_requirements_json` JSON,
  `labels_json` JSON,
  `authors_json` JSON,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project_time` (`project_id`, `create_time`),
  INDEX `idx_project_directory` (`project_id`, `directory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例表';

CREATE TABLE IF NOT EXISTS `oat_usecase_directory` (
  `id` VARCHAR(64) PRIMARY KEY,
  `project_id` VARCHAR(64),
  `parent_id` VARCHAR(64),
  `directory_name` VARCHAR(256),
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_project_parent` (`project_id`, `parent_id`),
  INDEX `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用例目录表';

CREATE TABLE IF NOT EXISTS `oat_snapshot_comment` (
  `id` VARCHAR(128) PRIMARY KEY,
  `snapshot_id` VARCHAR(64) NOT NULL,
  `comment_order` INT NOT NULL,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_snapshot_order` (`snapshot_id`, `comment_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统快照评论表';

CREATE TABLE IF NOT EXISTS `oat_snapshot_change_log` (
  `id` VARCHAR(128) PRIMARY KEY,
  `snapshot_id` VARCHAR(64) NOT NULL,
  `change_order` INT NOT NULL,
  `payload_json` JSON NOT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX `idx_snapshot_order` (`snapshot_id`, `change_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统快照变更日志表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='静态源码类信息表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方法覆盖率明细表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统快照大字段制品表';

