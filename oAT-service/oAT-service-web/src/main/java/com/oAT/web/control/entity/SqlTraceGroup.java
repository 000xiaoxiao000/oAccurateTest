package com.oAT.web.control.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SqlTraceGroup implements Serializable {

    //数据库地址
    private String addressIp;
    //端口号
    private String port;
    //数据库名
    private String name;
    //数据库类别
    private String type;
    //sql 语句
    private String sql;
    private List<String[]> params = new ArrayList<>();
    //执行了哪些操作 insert, update, select, delete;
    private String executes[];
    private String jdbcUrl;
    //执行次数
    private Integer count;

    public SqlTraceGroup(String addressIp, String name, String port, String databaseType, String sql) {
        this.addressIp = addressIp;
        this.name = name;
        this.port = port;
        this.type = databaseType;
        this.sql = sql;
    }

    public String getAddressIp() {
        return addressIp;
    }

    public void setAddressIp(String addressIp) {
        this.addressIp = addressIp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean addParam(String[] param) {
        return params.add(param);
    }

    public List<String[]> getParams() {
        return params;
    }

    public void setParams(List<String[]> params) {
        this.params = params;
    }

    public String[] getExecutes() {
        return executes;
    }

    public void setExecutes(String[] executes) {
        this.executes = executes;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {return true;}
        if (obj == null || getClass() != obj.getClass()){ return false;}

        SqlTraceGroup that = (SqlTraceGroup) obj;

        if (addressIp != null ? !addressIp.equals(that.addressIp) : that.addressIp != null) {return false;}
        if (port != null ? !port.equals(that.port) : that.port != null){ return false;}
        if (name != null ? !name.equals(that.name) : that.name != null){ return false;}
        if (sql != null ? !sql.equals(that.sql) : that.sql != null) {return false;}

        return true;
    }

    @Override
    public int hashCode() {
        int result = addressIp != null ? addressIp.hashCode() : 0;
        result = 31 * result + (port != null ? port.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (sql != null ? sql.hashCode() : 0);
        return result;
    }

}
