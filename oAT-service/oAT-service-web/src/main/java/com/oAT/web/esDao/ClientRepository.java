package com.oAT.web.esDao;

import com.oAT.web.common.UtilJson;
import com.oAT.web.esDao.entity.ClientIndex;
import com.oAT.web.esDao.entity.ClientInfo;
import com.oAT.web.esDao.entity.ClientSession;
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
import java.util.UUID;

@Repository
public class ClientRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<ClientIndex> rowMapper = this::mapRow;

    public ClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ClientIndex> findById(String sessionId) {
        List<ClientIndex> sessions = jdbcTemplate.query("""
                        SELECT * FROM oat_client_session
                        WHERE id = ?
                        """,
                rowMapper, sessionId);
        return sessions.stream().findFirst();
    }

    public List<ClientIndex> findAll() {
        return jdbcTemplate.query("""
                        SELECT * FROM oat_client_session
                        ORDER BY update_time DESC
                        """,
                rowMapper);
    }

    @Transactional
    public ClientIndex save(ClientIndex index) {
        normalize(index);
        ClientSession session = index.getSession();
        ClientInfo clientInfo = session == null ? null : session.getClientInfo();
        jdbcTemplate.update("""
                        INSERT INTO oat_client_session (
                            id, type, status, app_id, agent_version, system_dir, pid, address_ip,
                            login_time, last_heartbeat_time, agent_logs, package_verify_data,
                            session_json, create_time, update_time
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            type = VALUES(type),
                            status = VALUES(status),
                            app_id = VALUES(app_id),
                            agent_version = VALUES(agent_version),
                            system_dir = VALUES(system_dir),
                            pid = VALUES(pid),
                            address_ip = VALUES(address_ip),
                            login_time = VALUES(login_time),
                            last_heartbeat_time = VALUES(last_heartbeat_time),
                            agent_logs = VALUES(agent_logs),
                            package_verify_data = VALUES(package_verify_data),
                            session_json = VALUES(session_json),
                            update_time = VALUES(update_time)
                        """,
                index.getId(),
                index.getType(),
                session == null ? null : session.getStatus(),
                clientInfo == null ? null : clientInfo.getAppKey(),
                clientInfo == null ? null : clientInfo.getAgentVersion(),
                clientInfo == null ? null : clientInfo.getSystemDir(),
                clientInfo == null ? null : clientInfo.getPid(),
                clientInfo == null ? null : clientInfo.getAddressIp(),
                session == null ? null : session.getLoginTime(),
                session == null ? null : session.getLastHeartbeatTime(),
                session == null ? null : session.getAgentLogs(),
                session == null ? null : session.getPackageVerifyData(),
                UtilJson.writeValueAsString(session),
                toTimestamp(index.getCreateTime()),
                toTimestamp(index.getUpdateTime()));
        return index;
    }

    private void normalize(ClientIndex index) {
        if (!StringUtils.hasText(index.getId())) {
            index.setId(UUID.randomUUID().toString());
        }
        if (!StringUtils.hasText(index.getType())) {
            index.setType("session");
        }
        Date now = new Date();
        if (index.getCreateTime() == null) {
            index.setCreateTime(now);
        }
        if (index.getUpdateTime() == null) {
            index.setUpdateTime(now);
        }
    }

    private ClientIndex mapRow(ResultSet rs, int rowNum) throws SQLException {
        ClientIndex index = new ClientIndex();
        index.setId(rs.getString("id"));
        index.setType(rs.getString("type"));
        index.setCreateTime(toDate(rs.getTimestamp("create_time")));
        index.setUpdateTime(toDate(rs.getTimestamp("update_time")));
        index.setSession(readSession(rs));
        return index;
    }

    private ClientSession readSession(ResultSet rs) throws SQLException {
        ClientSession session = null;
        String sessionJson = rs.getString("session_json");
        if (StringUtils.hasText(sessionJson)) {
            session = UtilJson.convertValue(sessionJson, ClientSession.class);
        }
        if (session == null) {
            session = new ClientSession();
        }
        if (!StringUtils.hasText(session.getStatus())) {
            session.setStatus(rs.getString("status"));
        }
        if (!StringUtils.hasText(session.getLoginTime())) {
            session.setLoginTime(rs.getString("login_time"));
        }
        if (session.getLastHeartbeatTime() == null) {
            session.setLastHeartbeatTime(getLong(rs, "last_heartbeat_time"));
        }
        if (!StringUtils.hasText(session.getAgentLogs())) {
            session.setAgentLogs(rs.getString("agent_logs"));
        }
        if (!StringUtils.hasText(session.getPackageVerifyData())) {
            session.setPackageVerifyData(rs.getString("package_verify_data"));
        }
        if (session.getClientInfo() == null) {
            session.setClientInfo(readClientInfo(rs));
        }
        return session;
    }

    private ClientInfo readClientInfo(ResultSet rs) throws SQLException {
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setAppKey(rs.getString("app_id"));
        clientInfo.setAgentVersion(rs.getString("agent_version"));
        clientInfo.setSystemDir(rs.getString("system_dir"));
        clientInfo.setPid(rs.getString("pid"));
        clientInfo.setAddressIp(rs.getString("address_ip"));
        if (!StringUtils.hasText(clientInfo.getAppKey())
                && !StringUtils.hasText(clientInfo.getAgentVersion())
                && !StringUtils.hasText(clientInfo.getSystemDir())
                && !StringUtils.hasText(clientInfo.getPid())
                && !StringUtils.hasText(clientInfo.getAddressIp())) {
            return null;
        }
        return clientInfo;
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
