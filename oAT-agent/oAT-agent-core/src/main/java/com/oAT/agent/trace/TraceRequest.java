package com.oAT.agent.trace;

import java.io.Serializable;
import java.util.Properties;


/**
 * 保存session node节点相关信息
 *
 * @since 0.1.0
 */
public class TraceRequest implements Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    /**
     * 调用链全局唯一标识
     */
    private String traceId;

    /**
     * 调用链父节点调用标识
     */
    private String parentNodeCallId;

    private Properties properties;

    private String userHeader;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getParentNodeCallId() {
        return parentNodeCallId;
    }

    public void setParentNodeCallId(String parentNodeCallId) {
        this.parentNodeCallId = parentNodeCallId;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public String getUserHeader() {
        return userHeader;
    }

    public void setUserHeader(String userHeader) {
        this.userHeader = userHeader;
    }
}
