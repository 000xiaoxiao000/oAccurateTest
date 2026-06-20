package com.oAT.agent.collect.jdbc;

import com.oAT.agent.collect.AbstractByteTransformCollect;
import com.oAT.agent.collect.AgentByteBuild;
import com.oAT.agent.collect.ICollect;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.model.SqlTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.ProtectionDomain;
import java.sql.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JdbcCommonCollects extends AbstractByteTransformCollect implements ICollect {
    private final static Log logger = LogFactory.getLog(JdbcCommonCollects.class);

    public static JdbcCommonCollects INSTANCE;
    private TraceContext traceContext;
    private Set<String> jdbcDriverSet;
    private static final SqlTraceNode.Results SQL_RESULTS = new SqlTraceNode.Results();

    public JdbcCommonCollects(TraceContext traceContext, Instrumentation instrumentation, String... jdbcDriver) {
        super(instrumentation);
        init(traceContext, jdbcDriver);
    }

    public JdbcCommonCollects(TraceContext traceContext, String... jdbcDriver) {
        super();
        init(traceContext, jdbcDriver);
    }

    private void init(TraceContext traceContext, String... jdbcDriver) {
        INSTANCE = this;
        this.traceContext = traceContext;
        this.jdbcDriverSet = jdbcDriver == null ? java.util.Collections.<String>emptySet() :
                new HashSet(Arrays.asList(jdbcDriver));
    }

    private static final String[] CONNECTION_AGENT_METHODS = new String[]{"prepareStatement"};
    private static final String[] PREPARED_STATEMENT_METHODS = new String[]{"execute", "executeUpdate", "executeQuery"};
    private static final String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;

    static {
        //connect
        BEGIN_SRC = JdbcCommonCollects.class.getName() + " inst = " + JdbcCommonCollects.class.getName() + ".INSTANCE;";
        END_SRC = "_result=inst.proxyConnection((java.sql.Connection)_result);";
        ERROR_SRC = "inst.error(null, e);";
    }

    private SqlTraceNode begin() {
        if (traceContext == null || traceContext.getTraceSession() == null) {
            return null;
        }
        TraceSession session = traceContext.getTraceSession();
        SqlTraceNode node = new SqlTraceNode();

        node.setResults(SQL_RESULTS);
        node.setBeginTime(System.currentTimeMillis());
        node.setTraceId(session.getTraceId());
        node.setTraceNodeId(session.getNextNodeId());

        return node;
    }

    public void end(SqlTraceNode node) {
        if (node == null) {
            return;
        }

        try {
            long endTime = System.currentTimeMillis();
            long beginTime = node.getBeginTime();
            long userTime = endTime > beginTime ? endTime - beginTime : 0;

            node.setEndTime(endTime);
            node.setUseTime(userTime);

            node.setStatus(node.getError() != null
                    ? TraceNode.Status.fail.toString()
                    : TraceNode.Status.succeed.toString());

            TraceSession session = traceContext != null ? traceContext.getTraceSession() : null;
            if (session == null) {
                return;
            }

            if (node.getTraceNodeId() != null
                    && node.getTraceId() != null
                    && !node.getTraceId().isEmpty()) {
                try {
                    session.saveNode(node);
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]保存 TraceNode 失败" + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]结束 TraceNode 时发生异常" + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public void error(SqlTraceNode stat, Throwable throwable) {
        if (stat == null || traceContext == null || traceContext.getTraceSession() == null) {
            return;
        }
        stat.setError(buildError(throwable));
    }

    public Connection proxyConnection(final Connection connection) {
        if (connection == null) {
            return null;
        }
        try {
            Object conn = Proxy.newProxyInstance(
                    JdbcCommonCollects.class.getClassLoader(),
                    new Class[]{Connection.class},
                    new ConnectionHandler(connection));
            return (Connection) conn;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]创建 Connection 代理失败" + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return connection;
        }
    }

    public PreparedStatement proxyPreparedStatement(final PreparedStatement statement, SqlTraceNode jdbcStat) {
        if (statement == null) {
            return null;
        }
        try {
            Object c = Proxy.newProxyInstance(
                    JdbcCommonCollects.class.getClassLoader(),
                    new Class[]{PreparedStatement.class},
                    new PreparedStatementHandler(statement, jdbcStat));
            return (PreparedStatement) c;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]创建 PreparedStatement 代理失败" + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return statement;
        }
    }

    public ResultSet proxyResultSet(final ResultSet resultSet, SqlTraceNode jdbcStat) {
        if (resultSet == null) {
            return null;
        }
        try {
            Object c = Proxy.newProxyInstance(
                    JdbcCommonCollects.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    new ResultSetHandler(resultSet, jdbcStat));
            return (ResultSet) c;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]创建 ResultSet 代理失败" + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return resultSet;
        }
    }

    /**
     * connection 代理处理
     */
    public class ConnectionHandler implements InvocationHandler {
        private final Connection connection;

        private ConnectionHandler(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (connection == null || method == null) {
                return null;
            }
            boolean isTargetMethod = false;
            for (String agentM : CONNECTION_AGENT_METHODS) {
                if (agentM.equals(method.getName())) {
                    isTargetMethod = true;
                    break;
                }
            }
            Object result;
            SqlTraceNode jdbcStat = null;
            try {
                jdbcStat = begin();
                if (isTargetMethod && jdbcStat != null) {
                    try {
                        if (connection.getMetaData() != null) {
                            jdbcStat.setJdbcUrl(connection.getMetaData().getURL());
                        }
                    } catch (Throwable t) {
                        logger.error("[Agent-EXCError]获取JDBC URL失败", t);
                    }
                }
                result = method.invoke(connection, args);
                if (isTargetMethod && result instanceof PreparedStatement && jdbcStat != null) {
                    PreparedStatement ps = (PreparedStatement) result;
                    result = proxyPreparedStatement(ps, jdbcStat);
                }
            } catch (InvocationTargetException e) {
                JdbcCommonCollects.this.end(jdbcStat);
                JdbcCommonCollects.this.error(jdbcStat, e.getTargetException());
                throw e.getTargetException();
            } catch (Throwable t) {
                JdbcCommonCollects.this.end(jdbcStat);
                JdbcCommonCollects.this.error(jdbcStat, t);
                throw t;
            }
            return result;
        }
    }

    /**
     * PreparedStatement 代理处理
     */
    public class PreparedStatementHandler implements InvocationHandler {
        private final PreparedStatement statement;
        private final SqlTraceNode jdbcStat;

        public PreparedStatementHandler(PreparedStatement statement, SqlTraceNode jdbcStat) {
            this.statement = statement;
            this.jdbcStat = jdbcStat;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (statement == null || method == null) {
                return null;
            }
            boolean isTargetMethod = false;
            for (String agentm : PREPARED_STATEMENT_METHODS) {
                if (agentm.equals(method.getName())) {
                    isTargetMethod = true;
                    break;
                }
            }
            Object result;
            String sql = null;
            Object[] paramSnapshot = null; // 参数快照
            try {
                // 捕获参数快照
                if (args != null) {
                    paramSnapshot = Arrays.copyOf(args, args.length);
                    // 对二进制参数进行特殊标记，避免在日志中直接输出
                    for (int i = 0; i < paramSnapshot.length; i++) {
                        if (paramSnapshot[i] instanceof java.io.InputStream ||
                                paramSnapshot[i] instanceof byte[]) {
                            paramSnapshot[i] = "[BINARY_DATA]";
                        }
                    }
                }
                result = method.invoke(statement, args);
                if (statement.isClosed()) {
                    return result;
                }
                // 只记录 SQL 模板，不做参数替换
                sql = getSqlFromStatement(statement);
                jdbcStat.setSql(sql);
                // 将参数单独存储到 jdbcStat，如果有对应字段
                // jdbcStat.setParams(paramSnapshot);
            } catch (InvocationTargetException e) {
                Throwable targetEx = e.getTargetException();
                if (isTargetMethod) {
                    // 日志输出，SQL 和参数分开打印，避免拼接错误
                    String logSql = getSqlFromStatement(statement);
                    String logParams = (paramSnapshot != null) ? Arrays.toString(paramSnapshot) : "[]";
                    if (targetEx instanceof SQLIntegrityConstraintViolationException) {
                        logger.error("[Agent-error]SQLIntegrityConstraintViolationException 主键冲突，SQL: " + logSql + ", "
                                + "参数: " + logParams + ", \n"
                                + StackTraceFormatter.formatExceptionWithAgentMark(targetEx));
                    } else if (targetEx instanceof SQLSyntaxErrorException) {
                        String msg = targetEx.getMessage();
                        logger.error("[Agent-error]SQLSyntaxErrorException 语法错误，SQL: " + logSql + ", "
                                + "参数: " + logParams + ", \n"
                                + StackTraceFormatter.formatExceptionWithAgentMark(targetEx));
                        if (msg != null && msg.contains("doesn't exist")) {
                            // 提示表不存在
                            String tableName = "";
                            // 尝试从异常信息中提取表名
                            int idx = msg.indexOf("Table '");
                            if (idx >= 0) {
                                int start = idx + 7;
                                int end = msg.indexOf("'", start);
                                if (end > start) {
                                    tableName = msg.substring(start, end);
                                }
                            }
                            logger.error("[Agent-error]检测到表不存在: " + tableName + "，请检查该表是否已创建，或检查数据库连接配置是否正确。");
                        }
                    } else if (targetEx instanceof SQLTimeoutException) {
                        logger.error("[Agent-error]SQLTimeoutException 执行超时，SQL: " + logSql + ", 参数: " + logParams +
                                ", \n"
                                + StackTraceFormatter.formatExceptionWithAgentMark(targetEx));
                    } else if (targetEx instanceof SQLDataException) {
                        logger.error("[Agent-error]SQLDataException 数据错误，SQL: " + logSql + ", 参数: " + logParams + ", \n"
                                + StackTraceFormatter.formatExceptionWithAgentMark(targetEx));
                    } else if (targetEx instanceof SQLNonTransientConnectionException) {
                        logger.error("[Agent-error]SQLNonTransientConnectionException 连接丢失，SQL: " + logSql + ", 参数: " + logParams + ", \n"
                                + StackTraceFormatter.formatExceptionWithAgentMark(targetEx));
                    } else if (targetEx instanceof SQLTransactionRollbackException) {
                        logger.error("[Agent-error]SQLTransactionRollbackException 事务回滚，SQL: " + logSql + ", 参数: " + logParams + ", \n"
                                + StackTraceFormatter.formatExceptionWithAgentMark(targetEx));
                    }
                    JdbcCommonCollects.this.error(jdbcStat, targetEx);
                }
                throw targetEx;
            } catch (Throwable e) {
                if (isTargetMethod) {
                    String logSql = (sql != null) ? sql : getSqlFromStatement(statement);
                    // 检查是否包含二进制数据
                    if (logSql.contains("** STREAM DATA ***") || logSql.contains("?") && hasBinaryData(paramSnapshot)) {
                        logSql = logSql.replace("** STREAM DATA ***", "?") + " /* 包含二进制数据 */";
                    }
                    String logParams = (paramSnapshot != null) ? Arrays.toString(paramSnapshot) : "[]";
                    if (e instanceof SQLIntegrityConstraintViolationException) {
                        logger.error("[Agent-error]SQLIntegrityConstraintViolationException 主键冲突，SQL: " + logSql + "," +
                                " " +
                                "参数: " + logParams + ", \n" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    } else if (e instanceof SQLSyntaxErrorException) {
                        logger.error("[Agent-error]SQLSyntaxErrorException 语法错误，SQL: " + logSql + ", 参数: " + logParams
                                + ", \n" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    } else if (e instanceof SQLTimeoutException) {
                        logger.error("[Agent-error]SQLTimeoutException 执行超时，SQL: " + logSql + ", 参数: " + logParams
                                + ", \n" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    } else if (e instanceof SQLDataException) {
                        logger.error("[Agent-error]SQLDataException 数据错误，SQL: " + logSql + ", 参数: " + logParams
                                + ", \n" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    } else if (e instanceof SQLNonTransientConnectionException) {
                        logger.error("[Agent-error]SQLNonTransientConnectionException 连接丢失，SQL: " + logSql + ", 参数: " + logParams
                                + ", \n" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    } else if (e instanceof SQLTransactionRollbackException) {
                        logger.error("[Agent-error]SQLTransactionRollbackException 事务回滚，SQL: " + logSql + ", 参数: " + logParams
                                + ", \n" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                    }
                    JdbcCommonCollects.this.error(jdbcStat, e);
                }
                throw e;
            } finally {
                if (isTargetMethod) {
                    try {
                        if (jdbcStat != null) {
                            JdbcCommonCollects.this.end(jdbcStat);
                        } else {
                            logger.warn("[Agent-warn] jdbcStat 为 null，无法结束跟踪");
                        }
                    } catch (Throwable t) {
                        logger.error("[Agent-EXCError]结束跟踪时发生异常. " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    }
                }
            }
            return result;
        }

        /**
         * 检查参数中是否包含二进制数据
         */
        private boolean hasBinaryData(Object[] params) {
            if (params == null) return false;
            for (Object param : params) {
                if (param instanceof java.io.InputStream || param instanceof byte[]) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 尝试从PreparedStatement获取SQL字符串
         */
        private String getSqlFromStatement(PreparedStatement statement) {
            try {
                String s = statement.toString();
                if (s != null) {
                    s = s.replaceAll("com\\.mysql\\.cj\\.jdbc\\.ClientPreparedStatement: ", "")
                            .replaceAll("com\\.mysql\\.jdbc\\.JDBC42PreparedStatement@[a-f0-9]+: ", "")
                            .replaceAll("com\\.mysql\\.jdbc\\.JDBC4PreparedStatement@[a-f0-9]+: ", "")
                            .replace("** NOT SPECIFIED **", " ? ")
                            // 处理二进制流数据标记
                            .replace("** STREAM DATA ***", " ? ")
                            // 处理其他可能的二进制数据标记
                            .replaceAll("\\*\\*\\s*STREAM\\s+DATA\\s*\\*\\*\\*", " ? ");

                    // 进一步清理：移除可能包含二进制数据的复杂表达式
                    if (s.contains("**") && s.contains("STREAM") && s.contains("DATA")) {
                        // 使用正则表达式匹配并替换所有类似的二进制数据标记
                        s = s.replaceAll("\\*\\*\\s*[A-Z_]+\\s*\\*\\*\\*", " ? ");
                    }
                }
                return s;
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]获取 SQL 失败: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return "/* 无法解析的SQL语句 */";
            }
        }
    }

    /**
     * ResultSet代理处理
     */
    public static class ResultSetHandler implements InvocationHandler {
        private final ResultSet resultSet;
        private final SqlTraceNode jdbcStat;

        ResultSetHandler(ResultSet resultSet, SqlTraceNode jdbcStat) {
            this.resultSet = resultSet;
            this.jdbcStat = jdbcStat;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (resultSet == null || method == null) {
                return new Object();
            }
            Object sqlResult = null;
            try {
                sqlResult = method.invoke(resultSet, args);
                if ("getString".equals(method.getName())
                        && method.getParameterTypes().length > 0
                        && "int".equals(method.getParameterTypes()[0].getName())
                        && args != null
                        && jdbcStat != null
                        && jdbcStat.getResults() != null
                        && jdbcStat.getResults().getContents() != null) {
                    String sSqlResult = String.valueOf(sqlResult);
                    int length = jdbcStat.getResults().getContents().length;
                    for (int i = 0; i < length; i++) {
                        String[] ss = jdbcStat.getResults().getContents()[i];
                        for (int j = 0; j < ss.length; j++) {
                            if (ss[j] == null) {
                                ss[j] = sSqlResult;
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                logger.error("[Agent-EXCError]JdbcCommonCollects ResultSetHandler error", e);
            }
            return sqlResult;
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!jdbcDriverSet.contains(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        CtClass ctClass = null;
        try {
            ctClass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctClass);
            CtMethod ctMethod = ctClass.getMethod("connect", "(Ljava/lang/String;Ljava/util/Properties;)" +
                    "Ljava/sql/Connection;");
            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setEndSrc(END_SRC);
            build.setErrorSrc(ERROR_SRC);
            byteLoade.updateMethod(ctMethod, build);
            logger.info("[Agent-info]完成 JDBC 采集器初始化.");
            return byteLoade.toByteCode();
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]JDBC字节码增强失败: " + className + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        } finally {
            if (ctClass != null) {
                try {
                    ctClass.detach();
                } catch (Throwable ignore) {
                }
            }
        }
    }
}
