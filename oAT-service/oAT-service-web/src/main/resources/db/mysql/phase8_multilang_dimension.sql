CREATE DATABASE IF NOT EXISTS `oaccurate_test` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `oaccurate_test`;

ALTER TABLE `oat_coverage_report`
  ADD COLUMN `language` VARCHAR(32) DEFAULT 'JAVA' AFTER `source_type`,
  ADD COLUMN `build_id` VARCHAR(128) DEFAULT NULL AFTER `language`,
  ADD COLUMN `test_stage` VARCHAR(64) DEFAULT NULL AFTER `build_id`;

ALTER TABLE `oat_class_coverage`
  ADD COLUMN `language` VARCHAR(32) DEFAULT 'JAVA' AFTER `source_type`,
  ADD COLUMN `display_name` VARCHAR(512) DEFAULT NULL AFTER `language`,
  ADD COLUMN `source_path` VARCHAR(1024) DEFAULT NULL AFTER `display_name`;

ALTER TABLE `oat_app`
  ADD COLUMN `language` VARCHAR(32) DEFAULT 'JAVA' AFTER `src_name`,
  ADD COLUMN `language_config_json` JSON DEFAULT NULL AFTER `language`;

ALTER TABLE `oat_frontend_coverage_report`
  ADD COLUMN `build_id` VARCHAR(128) DEFAULT NULL AFTER `case_name`,
  ADD COLUMN `test_stage` VARCHAR(64) DEFAULT NULL AFTER `build_id`,
  ADD INDEX `idx_frontend_cov_build_stage` (`app_id`, `build_id`, `test_stage`);

ALTER TABLE `oat_universal_coverage_report`
  ADD COLUMN `build_id` VARCHAR(128) DEFAULT NULL AFTER `case_name`,
  ADD COLUMN `test_stage` VARCHAR(64) DEFAULT NULL AFTER `build_id`,
  ADD COLUMN `trace_id` VARCHAR(128) DEFAULT NULL AFTER `test_stage`,
  ADD INDEX `idx_universal_cov_build_stage` (`app_id`, `source_type`, `build_id`, `test_stage`);

UPDATE `oat_coverage_report`
SET `language` = COALESCE(NULLIF(`source_type`, ''), 'JAVA')
WHERE `language` IS NULL OR `language` = '';

UPDATE `oat_class_coverage`
SET
  `language` = COALESCE(NULLIF(`source_type`, ''), 'JAVA'),
  `display_name` = COALESCE(NULLIF(`display_name`, ''), `class_name`),
  `source_path` = COALESCE(NULLIF(`source_path`, ''), `class_name`)
WHERE `language` IS NULL
   OR `language` = ''
   OR `display_name` IS NULL
   OR `source_path` IS NULL;

UPDATE `oat_app`
SET
  `language` = COALESCE(NULLIF(`language`, ''), 'JAVA'),
  `language_config_json` = COALESCE(`language_config_json`, CAST('{}' AS JSON))
WHERE `language` IS NULL
   OR `language` = ''
   OR `language_config_json` IS NULL;
