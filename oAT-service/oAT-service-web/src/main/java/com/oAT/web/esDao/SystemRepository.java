package com.oAT.web.esDao;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.oAT.web.common.UtilJson;
import com.oAT.web.esDao.entity.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.StreamSupport;

@Repository
public class SystemRepository {
    private static final String SYSTEM_LOG_INDEX_ALIAS = "system_log";
    private static final String SYSTEM_LOG_INDEX_PATTERN = "system_log-*";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyy.MM");

    private final JdbcTemplate jdbcTemplate;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final RowMapper<SystemIndex> rowMapper = this::mapRow;

    public SystemRepository(JdbcTemplate jdbcTemplate,
                            ElasticsearchOperations elasticsearchOperations,
                            ElasticsearchClient elasticsearchClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchClient = elasticsearchClient;
    }

    public Optional<SystemIndex> findById(String id) {
        Optional<SystemIndex> mysqlIndex = queryUnion("id = ?", null, id).stream().findFirst();
        return mysqlIndex.isPresent() ? mysqlIndex : findSystemLogById(id);
    }

    public boolean existsById(String id) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT SUM(cnt) FROM (
                          SELECT COUNT(1) cnt FROM oat_user WHERE id = ?
                          UNION ALL SELECT COUNT(1) FROM oat_project WHERE id = ?
                          UNION ALL SELECT COUNT(1) FROM oat_app WHERE id = ?
                          UNION ALL SELECT COUNT(1) FROM oat_label_group WHERE id = ?
                          UNION ALL SELECT COUNT(1) FROM oat_project_member WHERE id = ?
                        ) t
                        """,
                Integer.class, id, id, id, id, id);
        return (count != null && count > 0) || findSystemLogById(id).isPresent();
    }

    public List<SystemIndex> findAllById(Iterable<String> ids) {
        List<String> idList = StreamSupport.stream(ids.spliterator(), false).filter(StringUtils::hasText).toList();
        if (idList.isEmpty()) return Collections.emptyList();
        String placeholders = String.join(",", Collections.nCopies(idList.size(), "?"));
        return queryUnion("id IN (" + placeholders + ")", null, idList.toArray());
    }

    public List<SystemIndex> findByUserNameOrUserEmail(String name, String email) {
        return query("SELECT 'user' type, id, payload_json, create_time, update_time FROM oat_user WHERE name = ? OR email = ? ORDER BY update_time DESC", name, email);
    }

    public List<SystemIndex> findByProjectMember_MemberId(String memberId, Pageable pageable) {
        return queryPage("projectMember", "member_id = ?", pageable, memberId);
    }

    public List<SystemIndex> findByAppCreateProjectIdOrAppRange(String createProjectId, String range) {
        return query("SELECT 'app' type, id, payload_json, create_time, update_time FROM oat_app WHERE project_id = ? OR range_type = ? ORDER BY update_time DESC", createProjectId, range);
    }

    public List<SystemIndex> findByLabelGroup_ProjectidAndLabelGroup_Type(String projectId, String type) {
        return query("SELECT 'labelGroup' type, id, payload_json, create_time, update_time FROM oat_label_group WHERE project_id = ? AND label_type = ? ORDER BY update_time DESC", projectId, type);
    }

    public List<SystemIndex> findByProjectMember_ProjectId(String projectId, Pageable pageable) {
        return queryPage("projectMember", "project_id = ?", pageable, projectId);
    }

    public List<SystemIndex> findByProjectMember_ProjectIdAndProjectMember_MemberId(String projectId, String memberId) {
        return query("SELECT 'projectMember' type, id, payload_json, create_time, update_time FROM oat_project_member WHERE project_id = ? AND member_id = ? ORDER BY update_time DESC", projectId, memberId);
    }

    public List<SystemIndex> findByType(String type, Pageable pageable) {
        return queryPage(type, "1 = 1", pageable);
    }

    public List<SystemIndex> findBySystemLog_ProjectId(String projectId, Pageable pageable) {
        return findSystemLogsByProjectId(projectId, pageable);
    }

    public List<SystemIndex> findAll() {
        return queryUnion("1 = 1", "update_time DESC");
    }

    @Transactional
    public SystemIndex save(SystemIndex index) {
        normalize(index);
        switch (index.getType()) {
            case "user" -> saveUser(index);
            case "project" -> saveProject(index);
            case "app" -> saveApp(index);
            case "labelGroup" -> saveLabelGroup(index);
            case "projectMember" -> saveProjectMember(index);
            case "systemLog" -> saveSystemLogToEs(index);
            default -> throw new IllegalArgumentException("unsupported system index type: " + index.getType());
        }
        return index;
    }

    @Transactional
    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM oat_user WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM oat_project WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM oat_app WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM oat_label_group WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM oat_project_member WHERE id = ?", id);
        deleteSystemLogFromEs(id);
    }

    @Transactional
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM oat_user");
        jdbcTemplate.update("DELETE FROM oat_project");
        jdbcTemplate.update("DELETE FROM oat_app");
        jdbcTemplate.update("DELETE FROM oat_label_group");
        jdbcTemplate.update("DELETE FROM oat_project_member");
        deleteAllSystemLogsFromEs();
    }

    @Transactional
    public void deleteAll(Iterable<SystemIndex> indexes) {
        if (indexes == null) return;
        for (SystemIndex index : indexes) {
            if (index != null && StringUtils.hasText(index.getId())) deleteById(index.getId());
        }
    }

    private void saveUser(SystemIndex index) {
        User user = index.getUser();
        jdbcTemplate.update("""
                        INSERT INTO oat_user (id, name, nick_name, email, password, header, phone, readme, payload_json, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                        ON DUPLICATE KEY UPDATE name=VALUES(name), nick_name=VALUES(nick_name), email=VALUES(email), password=VALUES(password), header=VALUES(header), phone=VALUES(phone), readme=VALUES(readme), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                        """, index.getId(), user == null ? null : user.getName(), user == null ? null : user.getNickName(), user == null ? null : user.getEmail(), user == null ? null : user.getPassword(), user == null ? null : user.getHeader(), user == null ? null : user.getPhone(), user == null ? null : user.getReadme(), json(index), ts(index.getCreateTime()), ts(index.getUpdateTime()));
    }

    private void saveProject(SystemIndex index) {
        Project project = index.getProject();
        jdbcTemplate.update("""
                        INSERT INTO oat_project (id, name, project_describe, owner_id, payload_json, create_time, update_time)
                        VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                        ON DUPLICATE KEY UPDATE name=VALUES(name), project_describe=VALUES(project_describe), owner_id=VALUES(owner_id), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                        """, index.getId(), project == null ? null : project.getName(), project == null ? null : project.getDescribe(), project == null ? null : project.getCreate(), json(index), ts(index.getCreateTime()), ts(index.getUpdateTime()));
    }

    private void saveApp(SystemIndex index) {
        App app = index.getApp();
        jdbcTemplate.update("""
                        INSERT INTO oat_app (id, name, project_id, range_type, src_name, language, language_config_json, app_describe, properties_text, current_version, current_branch, current_commit_id, repo_address, repo_user_name, repo_password, create_user_id, probe_alert_enabled, probe_offline_threshold_seconds, probe_webhook_url, probe_alert_on_online, probe_alert_on_offline, probe_alert_on_recovered, snapshot_dirs_json, payload_json, create_time, update_time)
                        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                        ON DUPLICATE KEY UPDATE name=VALUES(name), project_id=VALUES(project_id), range_type=VALUES(range_type), src_name=VALUES(src_name), language=VALUES(language), language_config_json=VALUES(language_config_json), app_describe=VALUES(app_describe), properties_text=VALUES(properties_text), current_version=VALUES(current_version), current_branch=VALUES(current_branch), current_commit_id=VALUES(current_commit_id), repo_address=VALUES(repo_address), repo_user_name=VALUES(repo_user_name), repo_password=VALUES(repo_password), create_user_id=VALUES(create_user_id), probe_alert_enabled=VALUES(probe_alert_enabled), probe_offline_threshold_seconds=VALUES(probe_offline_threshold_seconds), probe_webhook_url=VALUES(probe_webhook_url), probe_alert_on_online=VALUES(probe_alert_on_online), probe_alert_on_offline=VALUES(probe_alert_on_offline), probe_alert_on_recovered=VALUES(probe_alert_on_recovered), snapshot_dirs_json=VALUES(snapshot_dirs_json), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                        """, index.getId(), app == null ? null : app.getName(), app == null ? null : app.getCreateProjectId(), app == null ? null : app.getRange(), app == null ? null : app.getSrcName(), app == null ? null : app.getLanguage(), app == null ? null : app.getLanguageConfig(), app == null ? null : app.getDescribe(), app == null ? null : app.getProperties(), app == null ? null : app.getCurrentVersion(), app == null ? null : app.getCurrentBranch(), app == null ? null : app.getCurrentCommitId(), app == null ? null : app.getRepoAddress(), app == null ? null : app.getRepoUserName(), app == null ? null : app.getRepoPassword(), app == null ? null : app.getCreateUserId(), app == null ? null : app.getProbeAlertEnabled(), app == null ? null : app.getProbeOfflineThresholdSeconds(), app == null ? null : app.getProbeWebhookUrl(), app == null ? null : app.getProbeAlertOnOnline(), app == null ? null : app.getProbeAlertOnOffline(), app == null ? null : app.getProbeAlertOnRecovered(), app == null ? null : UtilJson.writeValueAsString(app.getSnapshotDirs()), json(index), ts(index.getCreateTime()), ts(index.getUpdateTime()));
    }

    private void saveLabelGroup(SystemIndex index) {
        LabelGroup labelGroup = index.getLabelGroup();
        jdbcTemplate.update("""
                        INSERT INTO oat_label_group (id, project_id, label_type, payload_json, create_time, update_time)
                        VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)
                        ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), label_type=VALUES(label_type), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                        """, index.getId(), labelGroup == null ? null : labelGroup.getProjectid(), labelGroup == null ? null : labelGroup.getType(), json(index), ts(index.getCreateTime()), ts(index.getUpdateTime()));
    }

    private void saveProjectMember(SystemIndex index) {
        ProjectMember member = index.getProjectMember();
        jdbcTemplate.update("""
                        INSERT INTO oat_project_member (id, project_id, member_id, payload_json, create_time, update_time)
                        VALUES (?, ?, ?, CAST(? AS JSON), ?, ?)
                        ON DUPLICATE KEY UPDATE project_id=VALUES(project_id), member_id=VALUES(member_id), payload_json=VALUES(payload_json), create_time=VALUES(create_time), update_time=VALUES(update_time)
                        """, index.getId(), member == null ? null : member.getProjectId(), member == null ? null : member.getMemberId(), json(index), ts(index.getCreateTime()), ts(index.getUpdateTime()));
    }

    private List<SystemIndex> queryPage(String type, String whereClause, Pageable pageable, Object... args) {
        if ("systemLog".equals(type)) {
            return findSystemLogsByProjectId(null, pageable);
        }
        Table table = table(type);
        String sql = "SELECT '" + type + "' type, id, payload_json, create_time, update_time FROM " + table.name + " WHERE " + whereClause + " ORDER BY update_time DESC";
        List<Object> params = new ArrayList<>(Arrays.asList(args));
        if (pageable != null && pageable.isPaged()) {
            sql += " LIMIT ? OFFSET ?";
            params.add(pageable.getPageSize());
            params.add(pageable.getOffset());
        }
        return query(sql, params.toArray());
    }

    private List<SystemIndex> queryUnion(String whereClause, String orderBy, Object... args) {
        String sql = String.join(" UNION ALL ",
                "SELECT 'user' type, id, payload_json, create_time, update_time FROM oat_user WHERE " + whereClause,
                "SELECT 'project' type, id, payload_json, create_time, update_time FROM oat_project WHERE " + whereClause,
                "SELECT 'app' type, id, payload_json, create_time, update_time FROM oat_app WHERE " + whereClause,
                "SELECT 'labelGroup' type, id, payload_json, create_time, update_time FROM oat_label_group WHERE " + whereClause,
                "SELECT 'projectMember' type, id, payload_json, create_time, update_time FROM oat_project_member WHERE " + whereClause);
        if (orderBy != null) sql = "SELECT * FROM (" + sql + ") t ORDER BY " + orderBy;
        Object[] params = repeatArgs(args, 5);
        return query(sql, params);
    }

    private void saveSystemLogToEs(SystemIndex index) {
        elasticsearchOperations.save(index, systemLogIndexFor(index));
    }

    private Optional<SystemIndex> findSystemLogById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.ids(i -> i.values(id)))
                .build();
        SearchHits<SystemIndex> hits = elasticsearchOperations.search(query,
                SystemIndex.class,
                IndexCoordinates.of(SYSTEM_LOG_INDEX_PATTERN));
        return hits.getSearchHits().stream().findFirst().map(SearchHit::getContent);
    }

    private List<SystemIndex> findSystemLogsByProjectId(String projectId, Pageable pageable) {
        var builder = NativeQuery.builder()
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"));
        if (StringUtils.hasText(projectId)) {
            builder.withQuery(TermQuery.of(t -> t.field("systemLog.projectId").value(projectId))._toQuery());
        }
        if (pageable != null && pageable.isPaged()) {
            builder.withPageable(pageable);
        }
        SearchHits<SystemIndex> hits = elasticsearchOperations.search(builder.build(),
                SystemIndex.class,
                IndexCoordinates.of(SYSTEM_LOG_INDEX_PATTERN));
        List<SystemIndex> logs = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<SystemIndex> hit : hits.getSearchHits()) {
            logs.add(hit.getContent());
        }
        return logs;
    }

    private void deleteSystemLogFromEs(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        try {
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(SYSTEM_LOG_INDEX_PATTERN)
                    .ignoreUnavailable(true)
                    .query(q -> q.ids(i -> i.values(id)))));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete system log from ES: " + id, e);
        }
    }

    private void deleteAllSystemLogsFromEs() {
        try {
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(d -> d
                    .index(SYSTEM_LOG_INDEX_PATTERN)
                    .ignoreUnavailable(true)
                    .query(q -> q.matchAll(m -> m))));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete system logs from ES", e);
        }
    }

    private Object[] repeatArgs(Object[] args, int times) {
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < times; i++) params.addAll(Arrays.asList(args));
        return params.toArray();
    }

    private List<SystemIndex> query(String sql, Object... args) {
        return jdbcTemplate.query(sql, rowMapper, args);
    }

    private void normalize(SystemIndex index) {
        if (!StringUtils.hasText(index.getId())) index.setId(UUID.randomUUID().toString());
        if (!StringUtils.hasText(index.getType())) throw new IllegalArgumentException("system index type must not be empty");
        Date now = new Date();
        if (index.getCreateTime() == null) index.setCreateTime(now);
        if (index.getUpdateTime() == null) index.setUpdateTime(now);
    }

    private SystemIndex mapRow(ResultSet rs, int rowNum) throws SQLException {
        SystemIndex index = UtilJson.convertValue(rs.getString("payload_json"), SystemIndex.class);
        if (index == null) index = new SystemIndex();
        index.setId(rs.getString("id"));
        index.setType(rs.getString("type"));
        index.setCreateTime(toDate(rs.getTimestamp("create_time")));
        index.setUpdateTime(toDate(rs.getTimestamp("update_time")));
        return index;
    }

    private String json(Object value) { return UtilJson.writeValueAsString(value); }
    private Timestamp ts(Date date) { return date == null ? null : new Timestamp(date.getTime()); }
    private Date toDate(Timestamp timestamp) { return timestamp == null ? null : new Date(timestamp.getTime()); }

    private IndexCoordinates systemLogIndexFor(SystemIndex index) {
        Date createTime = index.getCreateTime() == null ? new Date() : index.getCreateTime();
        String suffix = createTime.toInstant().atZone(ZoneId.systemDefault()).format(INDEX_SUFFIX);
        return IndexCoordinates.of(SYSTEM_LOG_INDEX_ALIAS + "-" + suffix);
    }

    private Table table(String type) {
        return switch (type) {
            case "user" -> new Table("oat_user");
            case "project" -> new Table("oat_project");
            case "app" -> new Table("oat_app");
            case "labelGroup" -> new Table("oat_label_group");
            case "projectMember" -> new Table("oat_project_member");
            default -> throw new IllegalArgumentException("unsupported system index type: " + type);
        };
    }

    private record Table(String name) {}
}
