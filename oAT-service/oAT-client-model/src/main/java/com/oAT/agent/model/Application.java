package com.oAT.agent.model;

import java.io.Serializable;

public class Application implements Serializable {
    private static final long serialVersionUID = -7156032079009497957L;
    //应用Id
    private String appId;
    //应用名称
    private String appName;
    //项目工程名称
    private String projectSrcName;

    public Application(){
    }

    public Application(String appId, String appName, String projectSrcName){
        this.appId = appId;
        this.appName = appName;
        this.projectSrcName = projectSrcName;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getProjectSrcName() {
        return projectSrcName;
    }

    public void setProjectSrcName(String projectSrcName) {
        this.projectSrcName = projectSrcName;
    }
}
