CREATE DATABASE IF NOT EXISTS `oaccurate_test`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `oaccurate_test`;

-- Canonical schema entrypoint for MySQL tables retained by coverage-storage-plan.md.
-- Execute the phase files below in order when bootstrapping a fresh schema:
--
--   source phase5_normalized_core.sql;
--   source phase1_snapshot_probe.sql;
--   source phase2_api_endpoint.sql;
--   source phase2_coverage_report.sql;
--   source phase3_system_snapshot.sql;
--   source phase4_class_coverage.sql;
--   source phase6_frontend_coverage.sql;
--   source phase7_universal_coverage.sql;
--   source phase8_multilang_dimension.sql;
--   source phase9_drop_non_mysql_tables.sql;
--
-- The application also runs FrontendCoverageSchemaInitializer at startup to
-- create/upgrade these same coverage tables and object-reference columns.
-- MySQL remains authoritative for business metadata and coverage summaries;
-- ES stores search/trend read models, and MinIO stores large raw/object bodies.
