CREATE DATABASE IF NOT EXISTS `oaccurate_test`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `oaccurate_test`;

-- These are ES read models per coverage-storage-plan.md, not MySQL tables.
DROP TABLE IF EXISTS `oat_system_log`;
DROP TABLE IF EXISTS `oat_probe_alert_event`;
DROP TABLE IF EXISTS `oat_method_coverage`;
