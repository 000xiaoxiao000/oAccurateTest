package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

public class Usecase implements Serializable, StandardDate{

    /**
    标题
     */
//    @Field(type = FieldType.Text)
    private String title;
    /**
    主题图片
     */
    @Field(type = FieldType.Keyword, index = false)
    private String headImage;
    /**
     内容
    */
    @Field(type = FieldType.Text)
    private String content;
    @Field(type = FieldType.Keyword)
    private String projectId;
    /**
     目录 ID
     */
    @Field(type = FieldType.Keyword)
    private String directory;
    /**
     绑定的快照id
     */
    @Field(type = FieldType.Keyword)
    private String snapshots[];
    @Field(type = FieldType.Keyword)
    /**
    标签
     */
    private String labels[];
    /**
     作者
     */
    @Field(type = FieldType.Keyword)
    private String authors[];
    /**
     最后修改人
     */
    @Field(type = FieldType.Keyword)
    private String lastUpdateAuthor;
    /**
    sql
     */
    @Field(type = FieldType.Object)
    private UsecaseSql sql;
    /**
     远程调用
     */
    @Field(type = FieldType.Object)
    private UsecaseRemote remote;
    // 该字段值有可能为超出256 keyword 的限制
    // 执行的源代码堆栈 格式：类名 方法名 方法签名 示例如下：
    //org/eclipse/jetty/servlet/DefaultServlet doGet (Ljavax/servlet/http/HttpServletRequest;Ljavax/servlet/http/HttpServletResponse;)V
    private String srcStack[];


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHeadImage() {
        return headImage;
    }

    public void setHeadImage(String headImage) {
        this.headImage = headImage;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String[] getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(String[] snapshots) {
        this.snapshots = snapshots;
    }

    public String[] getLabels() {
        return labels;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    public String[] getAuthors() {
        return authors;
    }

    public void setAuthors(String[] authors) {
        this.authors = authors;
    }

    public String getLastUpdateAuthor() {
        return lastUpdateAuthor;
    }

    public void setLastUpdateAuthor(String lastUpdateAuthor) {
        this.lastUpdateAuthor = lastUpdateAuthor;
    }

    public UsecaseSql getSql() {
        return sql;
    }

    public void setSql(UsecaseSql sql) {
        this.sql = sql;
    }

    public UsecaseRemote getRemote() {
        return remote;
    }

    public void setRemote(UsecaseRemote remote) {
        this.remote = remote;
    }

    public String[] getSrcStack() {
        return srcStack;
    }

    public void setSrcStack(String[] srcStack) {
        this.srcStack = srcStack;
    }

}
