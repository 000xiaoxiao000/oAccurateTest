package com.oAT.agent.model;


import java.io.Serializable;

public class SqlTraceNode extends TraceNode implements StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String jdbcUrl;
    //数据库信息
    private Database database;
    //SQL语句
    private String sql;
    //参数
    private String[] params;
    //执行操作select，insert，update，delete
    private String[] executes;
    //返回结果信息
    private Results results;
    private Error error;

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String[] getParams() {
        return params;
    }

    public void setParams(String[] params) {
        this.params = params;
    }

    public String[] getExecutes() {
        return executes;
    }

    public void setExecutes(String[] executes) {
        this.executes = executes;
    }

    public Results getResults() {
        return results;
    }

    public void setResults(Results results) {
        this.results = results;
    }

    @Override
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public String toType() {
        return "sql";
    }

    public static class Database implements Serializable {
        private static final long serialVersionUID = -7156032079009497957L;

        //数据库名称
        private String name;
        //数据库地址
        private String addressIp;
        //端口号
        private String port;
        //用户名
        private String user;
        //数据库类别，mysql，oracle，sqlServer，dbw
        private String type;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAddressIp() {
            return addressIp;
        }

        public void setAddressIp(String addressIp) {
            this.addressIp = addressIp;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Results implements Serializable {
        private static final long serialVersionUID = -7156032079009497957L;

        //返回结果字段
        private String[] resultFields;
        //结果大小
        private int resultCount = 0;
        //内容
        private String[][] contents;

        public String[] getResultFields() {
            return resultFields;
        }

        public void setResultFields(String[] resultFields) {
            this.resultFields = resultFields;
        }

        public int getResultCount() {
            return resultCount;
        }

        public void setResultCount(int resultCount) {
            this.resultCount = resultCount;
        }

        public String[][] getContents() {
            return contents;
        }

        public void setContents(String[][] contents) {
            this.contents = contents;
        }
    }

    public enum Execute {
        select, insert, update, delete
    }
}
