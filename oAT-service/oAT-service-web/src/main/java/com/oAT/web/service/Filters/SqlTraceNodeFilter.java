package com.oAT.web.service.Filters;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;
import com.alibaba.druid.util.JdbcUtils;
import com.oAT.agent.model.CKSqlTraceNode;
import com.oAT.agent.model.SqlTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.service.TraceNodeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SqlTraceNodeFilter implements TraceNodeFilter {
    private final static Logger logger = LoggerFactory.getLogger(SqlTraceNodeFilter.class);

    @Override
    public TraceNode doFilter(TraceNode node) {
        if (node instanceof SqlTraceNode) {
            SqlTraceNode sqlTraceNode = (SqlTraceNode) node;
            if (sqlTraceNode.getDatabase() == null) {
                sqlTraceNode.setDatabase(parseJdbcUrl(sqlTraceNode.getJdbcUrl().toLowerCase()));
            }
            if (sqlTraceNode.getExecutes() == null) {
                sqlTraceNode.setExecutes(parseSqlToExecutes(sqlTraceNode.getSql(), sqlTraceNode.getDatabase().getType()));
            }
            return node;
        } else if (node instanceof CKSqlTraceNode) {
            CKSqlTraceNode cksqlTraceNode = (CKSqlTraceNode) node;
            if (cksqlTraceNode.getDatabase() == null) {
                cksqlTraceNode.setDatabase(parseCKJdbcUrl(cksqlTraceNode.getJdbcUrl().toLowerCase()));
            }
            if (cksqlTraceNode.getExecutes() == null) {
                cksqlTraceNode.setExecutes(parseSqlToExecutes(cksqlTraceNode.getSql(), cksqlTraceNode.getDatabase().getType()));
            }
            return node;
        }
        return node;

    }

    private static String[] parseSqlToExecutes(String sql, String dbType) {
        if (sql.isEmpty() || dbType.isEmpty()) {
            return null;
        }
//        logger.info("sql: " + sql);
//        logger.info("dbType: " + dbType);
        List<SQLStatement> stmtList = SQLUtils.parseStatements(sql, dbType);
        List<String> executes = new ArrayList<>();
        for (SQLStatement sqlStatement : stmtList) {
            SchemaStatVisitor statVisitor = SQLUtils.createSchemaStatVisitor(DbType.valueOf(dbType));
            sqlStatement.accept(statVisitor);
            for (TableStat value : statVisitor.getTables().values()) {
                if (!executes.contains(value.toString().toLowerCase())) {
                    executes.add(value.toString().toLowerCase());
                }
            }
        }
        return executes.toArray(new String[0]);
    }

    private SqlTraceNode.Database parseJdbcUrl(String url) {
        String dbType = JdbcUtils.getDbType(url, null);
        SqlTraceNode.Database db;

        if (JdbcConstants.MYSQL.equals(dbType)) {
            db = parseMysqlUrl(url);
            db.setType(dbType);
            return db;
        } else {
            db = new SqlTraceNode.Database();
            db.setType(dbType);
            db.setName("unable to parse");
            db.setAddressIp("unable to parse");
            db.setPort("unable to parse");
            return db;
        }
    }

    private CKSqlTraceNode.Database parseCKJdbcUrl(String url) {
        String dbType = JdbcUtils.getDbType(url, null);
        CKSqlTraceNode.Database ckdb;

        if (JdbcConstants.CLICKHOUSE.equals(dbType)) {
            ckdb = parseClickHouseUrl(url);
            ckdb.setType(dbType);
            return ckdb;
        } else {
            ckdb = new CKSqlTraceNode.Database();
            ckdb.setType(dbType);
            ckdb.setName("unable to parse");
            ckdb.setAddressIp("unable to parse");
            ckdb.setPort("unable to parse");
            return ckdb;
        }
    }

    private static SqlTraceNode.Database parseMysqlUrl(String url) {
        int i = url.indexOf("?");
        String param = "";
        String basic, dbName, port, host;
        if (i > 0) {
            param = url.substring(i + 1, url.length());
            basic = url.substring(0, i);
        } else {
            basic = url;
        }
        dbName = basic.substring(basic.lastIndexOf("/") + 1);
        port = basic.substring(basic.lastIndexOf(":") + 1, basic.lastIndexOf("/"));
        host = basic.substring(basic.lastIndexOf("://") + 3, basic.lastIndexOf(":"));

        SqlTraceNode.Database database = new SqlTraceNode.Database();
        database.setAddressIp(host);
        database.setPort(port);
        database.setName(dbName);
        return database;
    }

    private static CKSqlTraceNode.Database parseClickHouseUrl(String url) {
        int i = url.indexOf("?");
        String param = "";
        String basic, dbName, port, host;
        if (i > 0) {
            param = url.substring(i + 1, url.length());
            basic = url.substring(0, i);
        } else {
            basic = url;
        }
        dbName = basic.substring(basic.lastIndexOf("/") + 1);
        port = basic.substring(basic.lastIndexOf(":") + 1, basic.lastIndexOf("/"));
        host = basic.substring(basic.lastIndexOf("://") + 3, basic.lastIndexOf(":"));

        CKSqlTraceNode.Database database = new CKSqlTraceNode.Database();
        database.setAddressIp(host);
        database.setPort(port);
        database.setName(dbName);
        return database;
    }
}
