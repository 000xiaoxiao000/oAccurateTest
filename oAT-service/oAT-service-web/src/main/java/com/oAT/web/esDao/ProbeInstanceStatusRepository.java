package com.oAT.web.esDao;

import com.oAT.web.esDao.entity.ProbeInstanceStatus;
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
import java.util.Optional;

@Repository
public class ProbeInstanceStatusRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ProbeInstanceStatus> rowMapper = this::mapRow;

    public ProbeInstanceStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ProbeInstanceStatus> findById(String probeKey) {
        List<ProbeInstanceStatus> statuses = jdbcTemplate.query("""
                        SELECT * FROM oat_probe_instance_status
                        WHERE probe_key = ?
                        """,
                rowMapper, probeKey);
        return statuses.stream().findFirst();
    }

    public List<ProbeInstanceStatus> findByStatus(String status) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_probe_instance_status
                        WHERE status = ?
                        ORDER BY update_time DESC
                        """,
                rowMapper, status);
    }

    public List<ProbeInstanceStatus> findByAppId(String appId) {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_probe_instance_status
                        WHERE app_id = ?
                        ORDER BY update_time DESC
                        """,
                rowMapper, appId);
    }

    @Transactional
    public ProbeInstanceStatus save(ProbeInstanceStatus status) {
        normalizeProbeKey(status);
        jdbcTemplate.update("""
                        INSERT INTO oat_probe_instance_status (
                            probe_key, project_id, app_id, app_name, session_id, address_ip,
                            pid, system_dir, agent_version, status, login_time, last_heartbeat_time,
                            last_status_change_time, online_since, offline_since, last_alert_event_type,
                            last_alert_time, create_time, update_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            project_id = VALUES(project_id),
                            app_id = VALUES(app_id),
                            app_name = VALUES(app_name),
                            session_id = VALUES(session_id),
                            address_ip = VALUES(address_ip),
                            pid = VALUES(pid),
                            system_dir = VALUES(system_dir),
                            agent_version = VALUES(agent_version),
                            status = VALUES(status),
                            login_time = VALUES(login_time),
                            last_heartbeat_time = VALUES(last_heartbeat_time),
                            last_status_change_time = VALUES(last_status_change_time),
                            online_since = VALUES(online_since),
                            offline_since = VALUES(offline_since),
                            last_alert_event_type = VALUES(last_alert_event_type),
                            last_alert_time = VALUES(last_alert_time),
                            update_time = VALUES(update_time)
                        """,
                status.getProbeKey(),
                status.getProjectId(),
                status.getAppId(),
                status.getAppName(),
                status.getSessionId(),
                status.getAddressIp(),
                status.getPid(),
                status.getSystemDir(),
                status.getAgentVersion(),
                status.getStatus(),
                toTimestamp(status.getLoginTime()),
                status.getLastHeartbeatTime(),
                toTimestamp(status.getLastStatusChangeTime()),
                toTimestamp(status.getOnlineSince()),
                toTimestamp(status.getOfflineSince()),
                status.getLastAlertEventType(),
                toTimestamp(status.getLastAlertTime()),
                toTimestamp(status.getCreateTime()),
                toTimestamp(status.getUpdateTime()));
        return status;
    }

    private void normalizeProbeKey(ProbeInstanceStatus status) {
        String probeKey = StringUtils.hasText(status.getProbeKey()) ? status.getProbeKey() : status.getId();
        if (!StringUtils.hasText(probeKey)) {
            throw new IllegalArgumentException("probeKey must not be empty");
        }
        Date now = new Date();
        status.setProbeKey(probeKey);
        if (!StringUtils.hasText(status.getId())) {
            status.setId(probeKey);
        }
        if (status.getCreateTime() == null) {
            status.setCreateTime(now);
        }
        if (status.getUpdateTime() == null) {
            status.setUpdateTime(now);
        }
    }

    private ProbeInstanceStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
        ProbeInstanceStatus status = new ProbeInstanceStatus();
        String probeKey = rs.getString("probe_key");
        status.setId(probeKey);
        status.setProbeKey(probeKey);
        status.setProjectId(rs.getString("project_id"));
        status.setAppId(rs.getString("app_id"));
        status.setAppName(rs.getString("app_name"));
        status.setSessionId(rs.getString("session_id"));
        status.setAddressIp(rs.getString("address_ip"));
        status.setPid(rs.getString("pid"));
        status.setSystemDir(rs.getString("system_dir"));
        status.setAgentVersion(rs.getString("agent_version"));
        status.setStatus(rs.getString("status"));
        status.setLoginTime(toDate(rs.getTimestamp("login_time")));
        status.setLastHeartbeatTime(getLong(rs, "last_heartbeat_time"));
        status.setLastStatusChangeTime(toDate(rs.getTimestamp("last_status_change_time")));
        status.setOnlineSince(toDate(rs.getTimestamp("online_since")));
        status.setOfflineSince(toDate(rs.getTimestamp("offline_since")));
        status.setLastAlertEventType(rs.getString("last_alert_event_type"));
        status.setLastAlertTime(toDate(rs.getTimestamp("last_alert_time")));
        status.setCreateTime(toDate(rs.getTimestamp("create_time")));
        status.setUpdateTime(toDate(rs.getTimestamp("update_time")));
        return status;
    }

    private Long getLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Timestamp toTimestamp(Date date) {
        return date == null ? null : new Timestamp(date.getTime());
    }

    private Date toDate(Timestamp timestamp) {
        return timestamp == null ? null : new Date(timestamp.getTime());
    }
}
