package com.oAT.web.esDao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Stream;

/**
 * 系统快照
 */
public class SystemSnapshot implements Serializable, StandardDate {
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss,SSS";
    /**
     * 基础属性 ========================================
     */
    @Id
    private String id;

    private Date createTime;
    private Date updateTime;
    private String projectId;

    /**
     * 追踪ID
     */
    private String traceId;
    /**
     * 标题
     */
    private String title;
    /**
     * 子标题
     */
    private String subTitle;
    /**
     * 主题图片
     */
    private String topicImage;
    /**
     * 描述
     */
    private String describe;
    /**
     * 所属应用ID
     */
    private String appId;
    /**
     * 所属目录ID
     */
    private String directory;
    /**
     * 版本
     */
    private String version;
    /**
     * 版本有效周期
     */
    private Integer versionCycle;
    /**
     * 版本最后变更时间
     */
    private Date versionLastUpdate;
    /**
     * 标签组
     */
    private String labels[];
    /**
     * 负责人
     */
    private String principals[];
    /**
     * 评论
     */
    private Comment comments[];
    /**
     * 变更日志
     */
    private ChangeLog changeLogs[];

    /**
     * 执行源码
     */
    private String codes[];
    /**
     * 执行SQL
     */
    private Sql sqls[];
    /**
     * 远程执行
     */
    private Remote remotes[];

    /**
     * 覆盖率报告状态：0-未生成，1-生成中，2-已完成，3-失败
     */
    private Integer reportStatus = 0;

    /**
     * 覆盖率报告聚合数据
     */
    private CoverageReportIndex coverageReport;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }

    public String getTopicImage() {
        return topicImage;
    }

    public void setTopicImage(String topicImage) {
        this.topicImage = topicImage;
    }

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getVersionCycle() {
        return versionCycle;
    }

    public void setVersionCycle(Integer versionCycle) {
        this.versionCycle = versionCycle;
    }

    public Date getVersionLastUpdate() {
        return versionLastUpdate;
    }

    public void setVersionLastUpdate(Date versionLastUpdate) {
        this.versionLastUpdate = versionLastUpdate;
    }

    public String[] getLabels() {
        return labels;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    public String[] getPrincipals() {
        return principals;
    }

    public void setPrincipals(String[] principals) {
        this.principals = principals;
    }

    public Comment[] getComments() {
        return comments;
    }

    public void setComments(Comment[] comments) {
        this.comments = comments;
    }

    public ChangeLog[] getChangeLogs() {
        return changeLogs;
    }

    public void setChangeLogs(ChangeLog[] changeLogs) {
        this.changeLogs = changeLogs;
    }

    public String[] getCodes() {
        return codes;
    }

    @JsonIgnore
    public Stream<String> getCodeToClass() {
        if (codes == null) {
            return Stream.of();
        } else {
            return Arrays.stream(codes).map(a -> a.split(" ")[0]);
        }
    }

    public void setCodes(String[] codes) {
        this.codes = codes;
    }

    public Sql[] getSqls() {
        return sqls;
    }

    public void setSqls(Sql[] sqls) {
        if (this.sqls == null) {
            this.sqls = sqls;
        } else {
            this.sqls = add(this.sqls, sqls);
        }
    }

    public Remote[] getRemotes() {
        return remotes;
    }

    public void setRemotes(Remote[] remotes) {
        this.remotes = remotes;
    }

    public Integer getReportStatus() {
        return reportStatus;
    }

    public void setReportStatus(Integer reportStatus) {
        this.reportStatus = reportStatus;
    }

    public CoverageReportIndex getCoverageReport() {
        return coverageReport;
    }

    public void setCoverageReport(CoverageReportIndex coverageReport) {
        this.coverageReport = coverageReport;
    }

    /**
     * This method will add elements to an array and return the resulting array
     *
     * @param arr
     * @param elements
     * @return
     */
    private Sql[] add(Sql[] arr, Sql... elements) {
        Sql[] tempArr = new Sql[arr.length + elements.length];
        System.arraycopy(arr, 0, tempArr, 0, arr.length);

        for (int i = 0; i < elements.length; i++) {
            tempArr[arr.length + i] = elements[i];
        }
        return tempArr;
    }

}
