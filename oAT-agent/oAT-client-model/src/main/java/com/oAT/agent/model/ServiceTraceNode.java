package com.oAT.agent.model;

import java.io.Serializable;
import java.util.Map;

public class ServiceTraceNode extends TraceNode implements CodeNodeBean, StatementError, Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    //服务名称
    private String serviceName;
    //服务简称
    private String simpleName;
    //方法名称
    private String methodName;

    private Error error;

    /**
     * 代码覆盖率报告数据（由 agent-core 端 StackNodeVoBuilder 从探针快照构建）。
     */
    private StackNodeVo[] codeNodes;

    /**
     * 覆盖率探针快照数据：classId -> boolean[]
     * <p>
     * 运行时仅收集 boolean[] 探针数组快照，在 agent 端结合 ClassProbeInfo 元信息构建 codeNodes。
     * </p>
     */
    private Map<Long, boolean[]> coverageSnapshots;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public StackNodeVo[] getCodeNodes() {
        return codeNodes;
    }

    public void setCodeNodes(StackNodeVo[] codeNodes) {
        this.codeNodes = codeNodes;
    }

    public Map<Long, boolean[]> getCoverageSnapshots() {
        return coverageSnapshots;
    }

    public void setCoverageSnapshots(Map<Long, boolean[]> coverageSnapshots) {
        this.coverageSnapshots = coverageSnapshots;
    }

    public void setError(Error error) {
        this.error = error;
    }

    @Override
    public Error getError() {
        return error;
    }

    @Override
    public String toType() {
        return "service";
    }
}
