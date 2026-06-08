import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

public class EsToMysqlMigrator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String ES = "http://localhost:9200";
    private static final String DB = "jdbc:mysql://127.0.0.1:3306/oaccurate_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true";
    private static final String USER = "root";
    private static final String PASSWORD = "123456";
    private static final int PAGE_SIZE = 500;
    private static final int INLINE_THRESHOLD_BYTES = 8192;
    private static final Path LARGE_PAYLOAD_ROOT = Path.of(System.getProperty("user.home"), "oAT", "codeData", "large-payload");

    private static final DateTimeFormatter ES_DATE = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendLiteral(',')
            .appendFraction(ChronoField.MILLI_OF_SECOND, 1, 3, false)
            .optionalEnd()
            .toFormatter();

    private final Connection conn;
    private final Map<String, Migrator> migrators = new LinkedHashMap<>();
    private final Map<String, Integer> mysqlCounts = new LinkedHashMap<>();
    private final Map<String, Integer> esCounts = new LinkedHashMap<>();

    public EsToMysqlMigrator(Connection conn) {
        this.conn = conn;
        migrators.put("snapshot_commit_mapping", this::snapshotCommitMapping);
        migrators.put("probe-instance-status", this::probeInstanceStatus);
        migrators.put("client", this::clientSession);
        migrators.put("system", this::systemDocument);
        migrators.put("version_center", this::versionCenter);
        migrators.put("api_endpoint_index", this::apiEndpoint);
        migrators.put("coverage_report", this::coverageReport);
        migrators.put("probe-alert-event", this::probeAlertEvent);
        migrators.put("case_center", this::caseCenter);
        migrators.put("system_snapshot", this::systemSnapshot);
        migrators.put("static_source_info", this::staticSourceInfo);
        migrators.put("class_coverage", this::classCoverage);
    }

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(DB, USER, PASSWORD)) {
            conn.setAutoCommit(false);
            EsToMysqlMigrator migrator = new EsToMysqlMigrator(conn);
            migrator.run();
        }
    }

    private void run() throws Exception {
        for (String index : migrators.keySet()) {
            int total = migrateIndex(index, migrators.get(index));
            esCounts.put(index, total);
            conn.commit();
            System.out.println("migrated " + index + ": " + total);
        }
        verify();
    }

    private int migrateIndex(String index, Migrator migrator) throws Exception {
        String scrollId = null;
        int total = 0;
        try {
            JsonNode response = postJson("/" + index + "/_search?scroll=2m", "{\"size\":" + PAGE_SIZE + ",\"sort\":[\"_doc\"]}");
            scrollId = text(response, "_scroll_id");
            while (true) {
                JsonNode hits = response.path("hits").path("hits");
                if (!hits.isArray() || hits.size() == 0) {
                    break;
                }
                for (JsonNode hit : hits) {
                    String id = text(hit, "_id");
                    JsonNode source = hit.path("_source");
                    migrator.migrate(id, source);
                    total++;
                }
                conn.commit();
                response = postJson("/_search/scroll", MAPPER.writeValueAsString(Map.of("scroll", "2m", "scroll_id", scrollId)));
                scrollId = text(response, "_scroll_id");
            }
            return total;
        } finally {
            if (scrollId != null && !scrollId.isBlank()) {
                try {
                    request("DELETE", "/_search/scroll", MAPPER.writeValueAsString(Map.of("scroll_id", List.of(scrollId))));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void snapshotCommitMapping(String id, JsonNode s) throws SQLException {
        update("""
                INSERT INTO oat_snapshot_commit_mapping (id, snapshot_id, app_id, project_id, version_number, repo_branch, repo_commit_id, mapping_source, create_time, snapshot_create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE app_id=VALUES(app_id), project_id=VALUES(project_id), version_number=VALUES(version_number), repo_branch=VALUES(repo_branch), repo_commit_id=VALUES(repo_commit_id), mapping_source=VALUES(mapping_source), create_time=VALUES(create_time), snapshot_create_time=VALUES(snapshot_create_time)
                """, firstText(s, "id", id), text(s, "snapshotId"), text(s, "appId"), text(s, "projectId"), text(s, "versionNumber"), text(s, "repoBranch"), text(s, "repoCommitId"), text(s, "mappingSource"), ts(s, "createTime"), ts(s, "snapshotCreateTime"));
    }

    private void probeInstanceStatus(String id, JsonNode s) throws SQLException {
        update("""
                INSERT INTO oat_probe_instance_status (probe_key, project_id, app_id, app_name, session_id, address_ip, pid, system_dir, agent_version, status, login_time, last_heartbeat_time, last_status_change_time, online_since, offline_since, last_alert_event_type, last_alert_time, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), app_id=VALUES(app_id), app_name=VALUES(app_name), session_id=VALUES(session_id), address_ip=VALUES(address_ip), pid=VALUES(pid), system_dir=VALUES(system_dir), agent_version=VALUES(agent_version), status=VALUES(status), login_time=VALUES(login_time), last_heartbeat_time=VALUES(last_heartbeat_time), last_status_change_time=VALUES(last_status_change_time), online_since=VALUES(online_since), offline_since=VALUES(offline_since), last_alert_event_type=VALUES(last_alert_event_type), last_alert_time=VALUES(last_alert_time), create_time=VALUES(create_time), update_time=VALUES(update_time)
                """, firstText(s, "probeKey", id), text(s, "projectId"), text(s, "appId"), text(s, "appName"), text(s, "sessionId"), text(s, "addressIp"), text(s, "pid"), text(s, "systemDir"), text(s, "agentVersion"), text(s, "status"), ts(s, "loginTime"), longVal(s, "lastHeartbeatTime"), ts(s, "lastStatusChangeTime"), ts(s, "onlineSince"), ts(s, "offlineSince"), text(s, "lastAlertEventType"), ts(s, "lastAlertTime"), ts(s, "createTime"), ts(s, "updateTime"));
    }

    private void clientSession(String id, JsonNode s) throws SQLException {
        JsonNode session = s.path("session");
        JsonNode info = session.path("clientInfo");
        update("""
                INSERT INTO oat_client_session (id, type, status, app_id, agent_version, system_dir, pid, address_ip, login_time, last_heartbeat_time, agent_logs, package_verify_data, session_json, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                ON DUPLICATE KEY UPDATE type=VALUES(type), status=VALUES(status), app_id=VALUES(app_id), agent_version=VALUES(agent_version), system_dir=VALUES(system_dir), pid=VALUES(pid), address_ip=VALUES(address_ip), login_time=VALUES(login_time), last_heartbeat_time=VALUES(last_heartbeat_time), agent_logs=VALUES(agent_logs), package_verify_data=VALUES(package_verify_data), session_json=VALUES(session_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                """, firstText(s, "id", id), firstText(s, "type", "session"), text(session, "status"), text(info, "appKey"), text(info, "agentVersion"), text(info, "systemDir"), text(info, "pid"), text(info, "addressIp"), text(session, "loginTime"), longVal(session, "lastHeartbeatTime"), jsonOrNull(session.path("agentLogs")), jsonOrNull(session.path("packageVerifyData")), json(s), ts(s, "createTime"), ts(s, "updateTime"));
    }

    private void systemDocument(String id, JsonNode s) throws SQLException {
        String type = text(s, "type");
        String rowId = firstText(s, "id", id);
        if ("user".equals(type)) {
            JsonNode user = s.path("user");
            update("""
                    INSERT INTO oat_user (id, name, nick_name, email, password, header, phone, readme, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE name=VALUES(name), nick_name=VALUES(nick_name), email=VALUES(email), password=VALUES(password), header=VALUES(header), phone=VALUES(phone), readme=VALUES(readme), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(user, "name"), text(user, "nickName"), text(user, "email"), text(user, "password"), text(user, "header"), text(user, "phone"), text(user, "readme"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("project".equals(type)) {
            JsonNode project = s.path("project");
            update("""
                    INSERT INTO oat_project (id, name, project_describe, owner_id, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE name=VALUES(name), project_describe=VALUES(project_describe), owner_id=VALUES(owner_id), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(project, "name"), text(project, "describe"), text(project, "create"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("app".equals(type)) {
            JsonNode app = s.path("app");
            update("""
                    INSERT INTO oat_app (id, name, project_id, range_type, src_name, app_describe, properties_text, current_version, current_branch, current_commit_id, repo_address, repo_user_name, repo_password, create_user_id, probe_alert_enabled, probe_offline_threshold_seconds, probe_webhook_url, probe_alert_on_online, probe_alert_on_offline, probe_alert_on_recovered, snapshot_dirs_json, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE name=VALUES(name), project_id=VALUES(project_id), range_type=VALUES(range_type), src_name=VALUES(src_name), app_describe=VALUES(app_describe), properties_text=VALUES(properties_text), current_version=VALUES(current_version), current_branch=VALUES(current_branch), current_commit_id=VALUES(current_commit_id), repo_address=VALUES(repo_address), repo_user_name=VALUES(repo_user_name), repo_password=VALUES(repo_password), create_user_id=VALUES(create_user_id), probe_alert_enabled=VALUES(probe_alert_enabled), probe_offline_threshold_seconds=VALUES(probe_offline_threshold_seconds), probe_webhook_url=VALUES(probe_webhook_url), probe_alert_on_online=VALUES(probe_alert_on_online), probe_alert_on_offline=VALUES(probe_alert_on_offline), probe_alert_on_recovered=VALUES(probe_alert_on_recovered), snapshot_dirs_json=VALUES(snapshot_dirs_json), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(app, "name"), text(app, "createProjectId"), text(app, "range"), text(app, "srcName"), text(app, "describe"), text(app, "properties"), text(app, "currentVersion"), text(app, "currentBranch"), text(app, "currentCommitId"), text(app, "repoAddress"), text(app, "repoUserName"), text(app, "repoPassword"), text(app, "createUserId"), boolVal(app, "probeAlertEnabled"), intVal(app, "probeOfflineThresholdSeconds"), text(app, "probeWebhookUrl"), boolVal(app, "probeAlertOnOnline"), boolVal(app, "probeAlertOnOffline"), boolVal(app, "probeAlertOnRecovered"), jsonOrEmptyArray(app.path("snapshotDirs")), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("labelGroup".equals(type)) {
            JsonNode label = s.path("labelGroup");
            update("""
                    INSERT INTO oat_label_group (id, project_id, label_type, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), label_type=VALUES(label_type), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(label, "projectid"), text(label, "type"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("projectMember".equals(type)) {
            JsonNode member = s.path("projectMember");
            update("""
                    INSERT INTO oat_project_member (id, project_id, member_id, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), member_id=VALUES(member_id), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(member, "projectId"), text(member, "memberId"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("systemLog".equals(type)) {
            JsonNode log = s.path("systemLog");
            update("""
                    INSERT INTO oat_system_log (id, project_id, user_id, user_name, action, title, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), user_id=VALUES(user_id), user_name=VALUES(user_name), action=VALUES(action), title=VALUES(title), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(log, "projectId"), text(log, "userId"), text(log, "userName"), text(log, "action"), text(log, "title"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        }
    }

    private void versionCenter(String id, JsonNode s) throws SQLException {
        String type = text(s, "type");
        String rowId = firstText(s, "id", id);
        if ("versionItem".equals(type)) {
            JsonNode version = firstExisting(s, "versionItem", "version");
            update("""
                    INSERT INTO oat_version (id, project_id, app_id, version_number, repo_branch, repo_commit_id, source_type, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), app_id=VALUES(app_id), version_number=VALUES(version_number), repo_branch=VALUES(repo_branch), repo_commit_id=VALUES(repo_commit_id), source_type=VALUES(source_type), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(version, "projectId"), text(version, "appId"), text(version, "versionNumber"), text(version, "repoBranch"), text(version, "repoCommitId"), text(version, "sourceType"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("compareReport".equals(type)) {
            JsonNode report = s.path("compareReport");
            update("""
                    INSERT INTO oat_version_compare_report (id, project_id, app_id, job_id, job_name, job_log, source_version, target_version, git_branch, git_old_commit, git_new_commit, add_class_count, update_class_count, delete_class_count, add_method_count, update_method_count, delete_method_count, impact_case_count, differences_json, cases_json, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), app_id=VALUES(app_id), job_id=VALUES(job_id), job_name=VALUES(job_name), job_log=VALUES(job_log), source_version=VALUES(source_version), target_version=VALUES(target_version), git_branch=VALUES(git_branch), git_old_commit=VALUES(git_old_commit), git_new_commit=VALUES(git_new_commit), add_class_count=VALUES(add_class_count), update_class_count=VALUES(update_class_count), delete_class_count=VALUES(delete_class_count), add_method_count=VALUES(add_method_count), update_method_count=VALUES(update_method_count), delete_method_count=VALUES(delete_method_count), impact_case_count=VALUES(impact_case_count), differences_json=VALUES(differences_json), cases_json=VALUES(cases_json), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(report, "projectId"), text(report, "appId"), text(report, "jobId"), text(report, "jobName"), text(report, "jobLog"), text(report, "sourceVersion"), text(report, "targetVersion"), text(report, "gitBranch"), text(report, "gitOldCommit"), text(report, "gitNewCommit"), intVal(report, "addClassCount"), intVal(report, "updateClassCount"), intVal(report, "deleteClassCount"), intVal(report, "addMethodCount"), intVal(report, "updateMethodCount"), intVal(report, "deleteMethodCount"), intVal(report, "impactCaseCount"), jsonOrEmptyArray(report.path("differences")), jsonOrEmptyArray(report.path("cases")), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        }
    }

    private void apiEndpoint(String id, JsonNode s) throws SQLException {
        update("""
                INSERT INTO oat_api_endpoint (id, app_id, source_type, source_name, source_names, source_type_names, endpoint_type, url, http_method, class_name, class_names, method_name, method_names, method_desc, method_descs, coverage_status, covered, hit_count, merged_source_count, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE app_id=VALUES(app_id), source_type=VALUES(source_type), source_name=VALUES(source_name), source_names=VALUES(source_names), source_type_names=VALUES(source_type_names), endpoint_type=VALUES(endpoint_type), url=VALUES(url), http_method=VALUES(http_method), class_name=VALUES(class_name), class_names=VALUES(class_names), method_name=VALUES(method_name), method_names=VALUES(method_names), method_desc=VALUES(method_desc), method_descs=VALUES(method_descs), coverage_status=VALUES(coverage_status), covered=VALUES(covered), hit_count=VALUES(hit_count), merged_source_count=VALUES(merged_source_count), create_time=VALUES(create_time), update_time=VALUES(update_time)
                """, firstText(s, "id", id), text(s, "appId"), text(s, "sourceType"), text(s, "sourceName"), text(s, "sourceNames"), text(s, "sourceTypeNames"), text(s, "endpointType"), text(s, "url"), text(s, "httpMethod"), text(s, "className"), text(s, "classNames"), text(s, "methodName"), text(s, "methodNames"), text(s, "methodDesc"), text(s, "methodDescs"), text(s, "coverageStatus"), boolVal(s, "covered"), intVal(s, "hitCount"), intVal(s, "mergedSourceCount"), ts(s, "createTime"), ts(s, "updateTime"));
    }

    private void coverageReport(String id, JsonNode s) throws SQLException {
        update("""
                INSERT INTO oat_coverage_report (id, app_id, version_number, repo_branch, repo_commit_id, create_time, last_processed_time, total_classes, covered_classes, total_methods, covered_methods, total_branches, covered_branches, total_branch_targets, covered_branch_targets, total_lines, covered_lines, total_complexity, report_type, base_version_number, base_repo_commit_id, snapshot_fingerprint, snapshot_last_update_time, snapshot_count, snapshot_ids, inc_total_classes, inc_covered_classes, inc_total_lines, inc_covered_lines, inc_total_methods, inc_covered_methods, inc_total_branches, inc_covered_branches, inc_total_branch_targets, inc_covered_branch_targets, inc_total_complexity)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE app_id=VALUES(app_id), version_number=VALUES(version_number), repo_branch=VALUES(repo_branch), repo_commit_id=VALUES(repo_commit_id), create_time=VALUES(create_time), last_processed_time=VALUES(last_processed_time), total_classes=VALUES(total_classes), covered_classes=VALUES(covered_classes), total_methods=VALUES(total_methods), covered_methods=VALUES(covered_methods), total_branches=VALUES(total_branches), covered_branches=VALUES(covered_branches), total_branch_targets=VALUES(total_branch_targets), covered_branch_targets=VALUES(covered_branch_targets), total_lines=VALUES(total_lines), covered_lines=VALUES(covered_lines), total_complexity=VALUES(total_complexity), report_type=VALUES(report_type), base_version_number=VALUES(base_version_number), base_repo_commit_id=VALUES(base_repo_commit_id), snapshot_fingerprint=VALUES(snapshot_fingerprint), snapshot_last_update_time=VALUES(snapshot_last_update_time), snapshot_count=VALUES(snapshot_count), snapshot_ids=VALUES(snapshot_ids), inc_total_classes=VALUES(inc_total_classes), inc_covered_classes=VALUES(inc_covered_classes), inc_total_lines=VALUES(inc_total_lines), inc_covered_lines=VALUES(inc_covered_lines), inc_total_methods=VALUES(inc_total_methods), inc_covered_methods=VALUES(inc_covered_methods), inc_total_branches=VALUES(inc_total_branches), inc_covered_branches=VALUES(inc_covered_branches), inc_total_branch_targets=VALUES(inc_total_branch_targets), inc_covered_branch_targets=VALUES(inc_covered_branch_targets), inc_total_complexity=VALUES(inc_total_complexity)
                """, firstText(s, "id", id), text(s, "appId"), text(s, "versionNumber"), text(s, "repoBranch"), text(s, "repoCommitId"), ts(s, "createTime"), text(s, "lastProcessedTime"), longVal(s, "totalClasses"), longVal(s, "coveredClasses"), longVal(s, "totalMethods"), longVal(s, "coveredMethods"), longVal(s, "totalBranches"), longVal(s, "coveredBranches"), longVal(s, "totalBranchTargets"), longVal(s, "coveredBranchTargets"), longVal(s, "totalLines"), longVal(s, "coveredLines"), intVal(s, "totalComplexity"), intVal(s, "reportType"), text(s, "baseVersionNumber"), text(s, "baseRepoCommitId"), text(s, "snapshotFingerprint"), text(s, "snapshotLastUpdateTime"), intVal(s, "snapshotCount"), textOrJson(s, "snapshotIds"), longVal(s, "incTotalClasses"), longVal(s, "incCoveredClasses"), longVal(s, "incTotalLines"), longVal(s, "incCoveredLines"), longVal(s, "incTotalMethods"), longVal(s, "incCoveredMethods"), longVal(s, "incTotalBranches"), longVal(s, "incCoveredBranches"), longVal(s, "incTotalBranchTargets"), longVal(s, "incCoveredBranchTargets"), intVal(s, "incTotalComplexity"));
    }

    private void probeAlertEvent(String id, JsonNode s) throws SQLException {
        update("""
                INSERT INTO oat_probe_alert_event (id, project_id, app_id, app_name, probe_key, session_id, address_ip, pid, system_dir, agent_version, event_type, event_time, last_heartbeat_time, offline_duration_millis, message, notify_enabled, notify_status, notify_channel, notify_response, notify_error, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), app_id=VALUES(app_id), app_name=VALUES(app_name), probe_key=VALUES(probe_key), session_id=VALUES(session_id), address_ip=VALUES(address_ip), pid=VALUES(pid), system_dir=VALUES(system_dir), agent_version=VALUES(agent_version), event_type=VALUES(event_type), event_time=VALUES(event_time), last_heartbeat_time=VALUES(last_heartbeat_time), offline_duration_millis=VALUES(offline_duration_millis), message=VALUES(message), notify_enabled=VALUES(notify_enabled), notify_status=VALUES(notify_status), notify_channel=VALUES(notify_channel), notify_response=VALUES(notify_response), notify_error=VALUES(notify_error), create_time=VALUES(create_time), update_time=VALUES(update_time)
                """, firstText(s, "id", id), text(s, "projectId"), text(s, "appId"), text(s, "appName"), text(s, "probeKey"), text(s, "sessionId"), text(s, "addressIp"), text(s, "pid"), text(s, "systemDir"), text(s, "agentVersion"), text(s, "eventType"), ts(s, "eventTime"), longVal(s, "lastHeartbeatTime"), longVal(s, "offlineDurationMillis"), text(s, "message"), boolVal(s, "notifyEnabled"), text(s, "notifyStatus"), text(s, "notifyChannel"), text(s, "notifyResponse"), text(s, "notifyError"), ts(s, "createTime"), ts(s, "updateTime"));
    }

    private void caseCenter(String id, JsonNode s) throws SQLException {
        String type = text(s, "type");
        String rowId = firstText(s, "id", id);
        if ("snapshot".equals(type)) {
            JsonNode snapshot = s.path("snapshot");
            update("""
                    INSERT INTO oat_snapshot (id, project_id, app_id, trace_id, snapshot_name, create_user, snapshot_describe, share_flag, labels_json, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), app_id=VALUES(app_id), trace_id=VALUES(trace_id), snapshot_name=VALUES(snapshot_name), create_user=VALUES(create_user), snapshot_describe=VALUES(snapshot_describe), share_flag=VALUES(share_flag), labels_json=VALUES(labels_json), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(snapshot, "projectId"), text(snapshot, "appId"), text(snapshot, "traceId"), text(snapshot, "name"), text(snapshot, "createUser"), text(snapshot, "describe"), boolVal(snapshot, "share"), jsonOrEmptyArray(snapshot.path("labels")), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("usecase".equals(type)) {
            JsonNode usecase = s.path("usecase");
            update("""
                    INSERT INTO oat_usecase (id, project_id, directory_id, title, content, head_image, snapshots_json, system_snapshots_json, defects_json, prd_requirements_json, labels_json, authors_json, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), directory_id=VALUES(directory_id), title=VALUES(title), content=VALUES(content), head_image=VALUES(head_image), snapshots_json=VALUES(snapshots_json), system_snapshots_json=VALUES(system_snapshots_json), defects_json=VALUES(defects_json), prd_requirements_json=VALUES(prd_requirements_json), labels_json=VALUES(labels_json), authors_json=VALUES(authors_json), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(usecase, "projectId"), text(usecase, "directory"), text(usecase, "title"), text(usecase, "content"), text(usecase, "headImage"), jsonOrEmptyArray(usecase.path("snapshots")), jsonOrEmptyArray(usecase.path("systemSnapshots")), jsonOrEmptyArray(usecase.path("defects")), jsonOrEmptyArray(usecase.path("prdRequirements")), jsonOrEmptyArray(usecase.path("labels")), jsonOrEmptyArray(usecase.path("authors")), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        } else if ("directory".equals(type)) {
            JsonNode dir = s.path("directory");
            update("""
                    INSERT INTO oat_usecase_directory (id, project_id, parent_id, directory_name, payload_json, create_time, update_time)
                    VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                    ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), parent_id=VALUES(parent_id), directory_name=VALUES(directory_name), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                    """, rowId, text(dir, "projectId"), text(dir, "parentId"), text(dir, "name"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        }
    }

    private void systemSnapshot(String id, JsonNode s) throws Exception {
        String snapshotId = firstText(s, "id", id);
        update("""
                INSERT INTO oat_system_snapshot (id, project_id, app_id, trace_id, title, sub_title, topic_image, snapshot_describe, directory, version, version_cycle, version_last_update, report_status, payload_json, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), app_id=VALUES(app_id), trace_id=VALUES(trace_id), title=VALUES(title), sub_title=VALUES(sub_title), topic_image=VALUES(topic_image), snapshot_describe=VALUES(snapshot_describe), directory=VALUES(directory), version=VALUES(version), version_cycle=VALUES(version_cycle), version_last_update=VALUES(version_last_update), report_status=VALUES(report_status), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                """, snapshotId, text(s, "projectId"), text(s, "appId"), text(s, "traceId"), text(s, "title"), text(s, "subTitle"), text(s, "topicImage"), text(s, "describe"), text(s, "directory"), text(s, "version"), intVal(s, "versionCycle"), ts(s, "versionLastUpdate"), intVal(s, "reportStatus"), json(s), ts(s, "createTime"), ts(s, "updateTime"));
        saveSnapshotDetails(snapshotId, s);
    }

    private void saveSnapshotDetails(String snapshotId, JsonNode s) throws Exception {
        update("DELETE FROM oat_snapshot_comment WHERE snapshot_id = ?", snapshotId);
        update("DELETE FROM oat_snapshot_change_log WHERE snapshot_id = ?", snapshotId);
        update("DELETE FROM oat_system_snapshot_artifact WHERE snapshot_id = ?", snapshotId);
        saveSnapshotJsonRows("oat_snapshot_comment", "comment_order", snapshotId, s.path("comments"), ts(s, "updateTime"));
        saveSnapshotJsonRows("oat_snapshot_change_log", "change_order", snapshotId, s.path("changeLogs"), ts(s, "updateTime"));
        saveArtifacts(snapshotId, "CODE", s.path("codes"), false);
        saveArtifacts(snapshotId, "SQL", s.path("sqls"), true);
        saveArtifacts(snapshotId, "REMOTE", s.path("remotes"), true);
    }

    private void saveSnapshotJsonRows(String table, String orderColumn, String snapshotId, JsonNode values, Timestamp createTime) throws SQLException {
        if (!values.isArray()) return;
        for (int i = 0; i < values.size(); i++) {
            update("INSERT INTO " + table + " (id, snapshot_id, " + orderColumn + ", payload_json, create_time) VALUES (?, ?, ?, CAST(? AS JSON), ?)",
                    snapshotId + "_" + i, snapshotId, i, json(values.get(i)), createTime);
        }
    }

    private void saveArtifacts(String snapshotId, String type, JsonNode values, boolean jsonPayload) throws Exception {
        if (!values.isArray()) return;
        for (int i = 0; i < values.size(); i++) {
            JsonNode value = values.get(i);
            String content = jsonPayload ? json(value) : (value.isTextual() ? value.asText() : value.toString());
            StoredPayload payload = storeLargePayload(Path.of("snapshot-artifact", snapshotId), type + "-" + i, ".json", content);
            update("""
                    INSERT INTO oat_system_snapshot_artifact (id, snapshot_id, artifact_type, artifact_order, storage_type, content_path, content_hash, content_size, content_text, payload_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                    """,
                    snapshotId + "_" + type + "_" + i,
                    snapshotId,
                    type,
                    i,
                    payload.storageType,
                    payload.path,
                    payload.hash,
                    payload.size,
                    payload.inlineContent,
                    jsonPayload ? content : null);
        }
    }

    private void staticSourceInfo(String id, JsonNode s) throws Exception {
        JsonNode c = s.path("classInfo");
        String sourceCode = text(c, "sourceCode");
        StoredPayload sourcePayload = storeLargePayload(Path.of("static-source", safePathPart(text(s, "appId"))), safePathPart(text(c, "className")), ".java", sourceCode);
        update("""
                INSERT INTO oat_static_source_class (id, app_id, type, class_id, class_name, method_maps_json, source_code, source_code_path, source_code_hash, source_code_size, payload_json, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                ON DUPLICATE KEY UPDATE id=VALUES(id), type=VALUES(type), class_id=VALUES(class_id), method_maps_json=VALUES(method_maps_json), source_code=VALUES(source_code), source_code_path=VALUES(source_code_path), source_code_hash=VALUES(source_code_hash), source_code_size=VALUES(source_code_size), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                """, firstText(s, "id", id), text(s, "appId"), firstText(s, "type", "classInfo"), text(c, "classId"), text(c, "className"), jsonOrEmptyObject(c.path("methodMaps")), sourcePayload.inlineContent, sourcePayload.path, sourcePayload.hash, sourcePayload.size, json(s), ts(s, "createTime"), ts(s, "updateTime"));
    }

    private void classCoverage(String id, JsonNode s) throws SQLException {
        update("""
                INSERT INTO oat_class_coverage (id, report_id, app_id, class_name, total_methods, covered_methods, total_branches, covered_branches, total_branch_targets, covered_branch_targets, total_lines, covered_lines, total_complexity, line_rate, branch_rate, method_rate, has_code_changes, methods_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE report_id=VALUES(report_id), app_id=VALUES(app_id), class_name=VALUES(class_name), total_methods=VALUES(total_methods), covered_methods=VALUES(covered_methods), total_branches=VALUES(total_branches), covered_branches=VALUES(covered_branches), total_branch_targets=VALUES(total_branch_targets), covered_branch_targets=VALUES(covered_branch_targets), total_lines=VALUES(total_lines), covered_lines=VALUES(covered_lines), total_complexity=VALUES(total_complexity), line_rate=VALUES(line_rate), branch_rate=VALUES(branch_rate), method_rate=VALUES(method_rate), has_code_changes=VALUES(has_code_changes), methods_json=VALUES(methods_json), update_time=CURRENT_TIMESTAMP
                """, firstText(s, "id", id), text(s, "reportId"), text(s, "appId"), text(s, "className"), intVal(s, "totalMethods"), intVal(s, "coveredMethods"), intVal(s, "totalBranches"), intVal(s, "coveredBranches"), intVal(s, "totalBranchTargets"), intVal(s, "coveredBranchTargets"), intVal(s, "totalLines"), intVal(s, "coveredLines"), intVal(s, "totalComplexity"), dblVal(s, "lineRate"), dblVal(s, "branchRate"), dblVal(s, "methodRate"), boolVal(s, "hasCodeChanges"), jsonOrEmptyArray(s.path("methods")));
        saveMethodCoverage(firstText(s, "id", id), s);
    }

    private void saveMethodCoverage(String classCoverageId, JsonNode s) throws SQLException {
        update("DELETE FROM oat_method_coverage WHERE class_coverage_id = ?", classCoverageId);
        JsonNode methods = s.path("methods");
        if (!methods.isArray()) {
            return;
        }
        for (int i = 0; i < methods.size(); i++) {
            JsonNode method = methods.get(i);
            update("""
                    INSERT INTO oat_method_coverage (id, class_coverage_id, report_id, app_id, class_name, method_name, method_desc, method_order, total_lines, covered_lines, total_branches, covered_branches, total_branch_targets, covered_branch_targets, complexity, covered, branch_rate, has_code_changes, detail_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                    ON DUPLICATE KEY UPDATE detail_json=VALUES(detail_json), method_name=VALUES(method_name), method_desc=VALUES(method_desc), update_time=CURRENT_TIMESTAMP
                    """,
                    classCoverageId + "_" + i,
                    classCoverageId,
                    text(s, "reportId"),
                    text(s, "appId"),
                    text(s, "className"),
                    text(method, "methodName"),
                    text(method, "methodDesc"),
                    i,
                    intVal(method, "totalLines"),
                    intVal(method, "coveredLines"),
                    intVal(method, "totalBranches"),
                    intVal(method, "coveredBranches"),
                    intVal(method, "totalBranchTargets"),
                    intVal(method, "coveredBranchTargets"),
                    intVal(method, "complexity"),
                    boolVal(method, "covered"),
                    dblVal(method, "branchRate"),
                    boolVal(method, "hasCodeChanges"),
                    json(method));
        }
    }

    private void verify() throws Exception {
        String[][] pairs = {
                {"snapshot_commit_mapping", "oat_snapshot_commit_mapping"},
                {"probe-instance-status", "oat_probe_instance_status"},
                {"client", "oat_client_session"},
                {"system", "normalized:system"},
                {"version_center", "normalized:version"},
                {"api_endpoint_index", "oat_api_endpoint"},
                {"coverage_report", "oat_coverage_report"},
                {"probe-alert-event", "oat_probe_alert_event"},
                {"case_center", "normalized:case"},
                {"system_snapshot", "oat_system_snapshot"},
                {"static_source_info", "oat_static_source_class"},
                {"class_coverage", "oat_class_coverage"}
        };
        System.out.println("\nverification:");
        for (String[] pair : pairs) {
            int dbCount = queryCount(pair[1]);
            mysqlCounts.put(pair[1], dbCount);
            System.out.printf(Locale.ROOT, "%s -> %s: es=%d mysql=%d%n", pair[0], pair[1], esCounts.getOrDefault(pair[0], -1), dbCount);
        }
    }

    private int queryCount(String table) throws SQLException {
        String sql = switch (table) {
            case "normalized:system" -> "SELECT SUM(cnt) FROM (SELECT COUNT(*) cnt FROM oat_user UNION ALL SELECT COUNT(*) FROM oat_project UNION ALL SELECT COUNT(*) FROM oat_app UNION ALL SELECT COUNT(*) FROM oat_label_group UNION ALL SELECT COUNT(*) FROM oat_project_member UNION ALL SELECT COUNT(*) FROM oat_system_log) t";
            case "normalized:version" -> "SELECT SUM(cnt) FROM (SELECT COUNT(*) cnt FROM oat_version UNION ALL SELECT COUNT(*) FROM oat_version_compare_report) t";
            case "normalized:case" -> "SELECT SUM(cnt) FROM (SELECT COUNT(*) cnt FROM oat_snapshot UNION ALL SELECT COUNT(*) FROM oat_usecase UNION ALL SELECT COUNT(*) FROM oat_usecase_directory) t";
            default -> "SELECT COUNT(*) FROM " + table;
        };
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void update(String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    private static StoredPayload storeLargePayload(Path directory, String fileNamePrefix, String extension, String content) throws Exception {
        if (content == null) {
            return new StoredPayload("DB", null, null, null, null);
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        if (bytes.length <= INLINE_THRESHOLD_BYTES) {
            return new StoredPayload("DB", null, hash, (long) bytes.length, content);
        }
        Path relativePath = directory.resolve(fileNamePrefix + "-" + hash + extension);
        Path target = LARGE_PAYLOAD_ROOT.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return new StoredPayload("FILE", relativePath.toString(), hash, (long) bytes.length, null);
    }

    private static String safePathPart(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(bytes);
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte b : hashed) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private static JsonNode postJson(String path, String body) throws Exception {
        return MAPPER.readTree(request("POST", path, body));
    }

    private static String request(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(ES + path))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(30));
        if ("POST".equals(method)) builder.POST(HttpRequest.BodyPublishers.ofString(body));
        else if ("DELETE".equals(method)) builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body));
        else builder.GET();
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new IOException(method + " " + path + " failed: " + response.statusCode() + " " + response.body());
        }
        return response.body();
    }


    private JsonNode firstExisting(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return MAPPER.createObjectNode();
    }

    private static String json(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "{}" : node.toString();
    }

    private static String jsonOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.toString();
    }

    private static String jsonOrEmptyArray(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "[]" : node.toString();
    }

    private static String jsonOrEmptyObject(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "{}" : node.toString();
    }

    private static String textOrJson(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        return value.isTextual() ? value.asText() : value.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        return value.toString();
    }

    private static String firstText(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Integer intVal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static Long longVal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private static Double dblVal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asDouble();
    }

    private static Boolean boolVal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asBoolean();
    }

    private static Timestamp ts(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) return null;
        try {
            if (value.matches("^-?\\d+$")) {
                long epoch = Long.parseLong(value);
                return new Timestamp(epoch > 9_999_999_999L ? epoch : epoch * 1000);
            }
            if (value.contains("T")) {
                try { return Timestamp.from(Instant.parse(value)); } catch (Exception ignored) {}
                return Timestamp.from(OffsetDateTime.parse(value).toInstant());
            }
            LocalDateTime dt = LocalDateTime.parse(value, ES_DATE);
            return Timestamp.valueOf(dt);
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    interface Migrator {
        void migrate(String id, JsonNode source) throws Exception;
    }

    private record StoredPayload(String storageType, String path, String hash, Long size, String inlineContent) {
    }
}
