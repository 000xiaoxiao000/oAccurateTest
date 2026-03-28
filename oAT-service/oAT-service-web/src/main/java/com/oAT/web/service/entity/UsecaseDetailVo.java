package com.oAT.web.service.entity;

import com.oAT.web.esDao.entity.UsecaseRemote;
import com.oAT.web.esDao.entity.UsecaseSql;

public class UsecaseDetailVo extends UsecaseVo {
    private UsecaseDetaiSql[] sqls;            // sql 信息
    private UsecaseRemote remote;      // 远程调用信息

    public UsecaseRemote getRemote() {
        return remote;
    }

    public void setRemote(UsecaseRemote remote) {
        this.remote = remote;
    }

    public UsecaseDetaiSql[] getSqls() {
        return sqls;
    }

    public void setSqls(UsecaseDetaiSql[] sqls) {
        this.sqls = sqls;
    }

    public static class UsecaseDetaiSql {
        String dbType;
        String dbName;
        String sql;

        public UsecaseDetaiSql(String dbType, String dbName, String sql) {
            this.dbType = dbType;
            this.dbName = dbName;
            this.sql = sql;
        }


        public UsecaseDetaiSql() {
        }

        public String getDbType() {
            return dbType;
        }

        public void setDbType(String dbType) {
            this.dbType = dbType;
        }

        public String getDbName() {
            return dbName;
        }

        public void setDbName(String dbName) {
            this.dbName = dbName;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }
    }
}
