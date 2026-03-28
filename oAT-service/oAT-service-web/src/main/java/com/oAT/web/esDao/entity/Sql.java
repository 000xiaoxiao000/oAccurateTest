package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class Sql implements Serializable {

    /**
     * 数据库
     */
    @Field(type = FieldType.Keyword)
    private String database;
    /**
     * 数据库类型
     */
    @Field(type = FieldType.Keyword)
    private String databaseType;
    /**
     * 语句内容
     */
    @Field(type = FieldType.Text)
    private String content;
    /**
     * 操作集
     */
    @Field(type = FieldType.Object)
    private Action actions[];
    /**
     * 执行次数
     */
    @Field(type = FieldType.Keyword)
    private Integer count;


    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Action[] getActions() {
        return actions;
    }

    public void setActions(Action[] actions) {
        this.actions = actions;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public static class Action  implements Serializable{
        @Field(type = FieldType.Keyword)
        private String type;
        @Field(type = FieldType.Keyword)
        private String table;

        public Action() {
        }

        public Action(String type, String table) {
            this.type = type;
            this.table = table;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }
    }

}
