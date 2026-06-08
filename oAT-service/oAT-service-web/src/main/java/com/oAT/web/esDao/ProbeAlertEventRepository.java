package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ProbeAlertEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
public class ProbeAlertEventRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ProbeAlertEvent> rowMapper = this::mapRow;

    public ProbeAlertEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProbeAlertEvent> findByAppId(String appId) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_probe_alert_event
                        WHERE app_id = ?
                        ORDER BY event_time DESC, create_time DESC
                        """,
                rowMapper, appId);
    }

    public List<ProbeAlertEvent> findByProjectId(String projectId) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_probe_alert_event
                        WHERE project_id = ?
                        ORDER BY event_time DESC, create_time DESC
                        """,
                rowMapper, projectId);
    }

    @Transactional
    public ProbeAlertEvent save(ProbeAlertEvent event) {
        normalize(event);
        jdbcTemplate.update("""
                        INSERT INTO oat_probe_alert_event (
                            id, project_id, app_id, app_name, probe_key, session_id, address_ip,
                            pid, system_dir, agent_version, event_type, event_time,
                            last_heartbeat_time, offline_duration_millis, message, notify_enabled,
                            notify_status, notify_channel, notify_response, notify_error,
                            create_time, update_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            project_id = VALUES(project_id),
                            app_id = VALUES(app_id),
                            app_name = VALUES(app_name),
                            probe_key = VALUES(probe_key),
                            session_id = VALUES(session_id),
                            address_ip = VALUES(address_ip),
                            pid = VALUES(pid),
                            system_dir = VALUES(system_dir),
                            agent_version = VALUES(agent_version),
                            event_type = VALUES(event_type),
                            event_time = VALUES(event_time),
                            last_heartbeat_time = VALUES(last_heartbeat_time),
                            offline_duration_millis = VALUES(offline_duration_millis),
                            message = VALUES(message),
                            notify_enabled = VALUES(notify_enabled),
                            notify_status = VALUES(notify_status),
                            notify_channel = VALUES(notify_channel),
                            notify_response = VALUES(notify_response),
                            notify_error = VALUES(notify_error),
                            update_time = VALUES(update_time)
                        """,
                event.getId(),
                event.getProjectId(),
                event.getAppId(),
                event.getAppName(),
                event.getProbeKey(),
                event.getSessionId(),
                event.getAddressIp(),
                event.getPid(),
                event.getSystemDir(),
                event.getAgentVersion(),
                event.getEventType(),
                toTimestamp(event.getEventTime()),
                event.getLastHeartbeatTime(),
                event.getOfflineDurationMillis(),
                event.getMessage(),
                event.getNotifyEnabled(),
                event.getNotifyStatus(),
                event.getNotifyChannel(),
                event.getNotifyResponse(),
                event.getNotifyError(),
                toTimestamp(event.getCreateTime()),
                toTimestamp(event.getUpdateTime()));
        return event;
    }

    private void normalize(ProbeAlertEvent event) {
        if (!StringUtils.hasText(event.getId())) {
            event.setId(UUID.randomUUID().toString());
        }
        Date now = new Date();
        if (event.getEventTime() == null) {
            event.setEventTime(now);
        }
        if (event.getCreateTime() == null) {
            event.setCreateTime(now);
        }
        if (event.getUpdateTime() == null) {
            event.setUpdateTime(now);
        }
    }

    private ProbeAlertEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        ProbeAlertEvent event = new ProbeAlertEvent();
        event.setId(rs.getString("id"));
        event.setProjectId(rs.getString("project_id"));
        event.setAppId(rs.getString("app_id"));
        event.setAppName(rs.getString("app_name"));
        event.setProbeKey(rs.getString("probe_key"));
        event.setSessionId(rs.getString("session_id"));
        event.setAddressIp(rs.getString("address_ip"));
        event.setPid(rs.getString("pid"));
        event.setSystemDir(rs.getString("system_dir"));
        event.setAgentVersion(rs.getString("agent_version"));
        event.setEventType(rs.getString("event_type"));
        event.setEventTime(toDate(rs.getTimestamp("event_time")));
        event.setLastHeartbeatTime(getLong(rs, "last_heartbeat_time"));
        event.setOfflineDurationMillis(getLong(rs, "offline_duration_millis"));
        event.setMessage(rs.getString("message"));
        event.setNotifyEnabled(getBoolean(rs, "notify_enabled"));
        event.setNotifyStatus(rs.getString("notify_status"));
        event.setNotifyChannel(rs.getString("notify_channel"));
        event.setNotifyResponse(rs.getString("notify_response"));
        event.setNotifyError(rs.getString("notify_error"));
        event.setCreateTime(toDate(rs.getTimestamp("create_time")));
        event.setUpdateTime(toDate(rs.getTimestamp("update_time")));
        return event;
    }

    private Long getLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
        boolean value = rs.getBoolean(columnName);
        return rs.wasNull() ? null : value;
    }

    private Timestamp toTimestamp(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
    }
}
