package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class UsecaseSql implements Serializable {
    @Field(type = FieldType.Text)
    /*
    格式：${db_type} ${db_name}  ${sql}
     */
    private String[] contents;
    @Field(type = FieldType.Object)
    /*
    插入操作
     */
    private SqlAction[] inserts;
    @Field(type = FieldType.Object)
    /*
    修改操作
     */
    private SqlAction[] updates;
    @Field(type = FieldType.Object)
    /*
    删除插操
     */
    private SqlAction[] deletes;
    @Field(type = FieldType.Object)
    /*
    查询操作
     */
    private SqlAction[] selects;
    @Field(type = FieldType.Object)
    /*
    drop操作
     */
    private SqlAction[] drops;
    @Field(type = FieldType.Object)
    /*
    创建操作
     */
    private SqlAction[] creates;


    public String[] getContents() {
        return contents;
    }

    public void setContents(String[] contents) {
        this.contents = contents;
    }

    public SqlAction[] getInserts() {
        return inserts;
    }

    public void setInserts(SqlAction[] inserts) {
        this.inserts = inserts;
    }

    public SqlAction[] getUpdates() {
        return updates;
    }

    public void setUpdates(SqlAction[] updates) {
        this.updates = updates;
    }

    public SqlAction[] getDeletes() {
        return deletes;
    }

    public void setDeletes(SqlAction[] deletes) {
        this.deletes = deletes;
    }


    public SqlAction[] getDrops() {
        return drops;
    }

    public void setDrops(SqlAction[] drops) {
        this.drops = drops;
    }

    public SqlAction[] getCreates() {
        return creates;
    }

    public void setCreates(SqlAction[] creates) {
        this.creates = creates;
    }

    public SqlAction[] getSelects() {
        return selects;
    }

    public void setSelects(SqlAction[] selects) {
        this.selects = selects;
    }

    public static class SqlAction {
        @Field(type = FieldType.Text)
        private String name;       //格式：DataBase.table.cloumn
        @Field(type = FieldType.Integer)
        private int index;
        @Field(type = FieldType.Keyword)
        private String snapshot;

        public SqlAction(String name, int index, String snapshot) {
            this.name = name;
            this.index = index;
            this.snapshot = snapshot;
        }

        public SqlAction() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getSnapshot() {
            return snapshot;
        }

        public void setSnapshot(String snapshot) {
            this.snapshot = snapshot;
        }

    }
}
