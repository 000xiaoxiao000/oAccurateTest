package com.oAT.web.control.entity;

import com.oAT.agent.model.HttpTraceNode;
import eu.bitwalker.useragentutils.UserAgent;

public class ClientGraphNode extends GraphNode{

    private HttpTraceNode traceNode;
    private String title;

    public ClientGraphNode(HttpTraceNode traceNode) {
        this.traceNode = traceNode;
        setId("user-agent");
        setType(GraphNodeType.CLIENT);
        title = traceNode.getRequestUrl();
        if (traceNode.getRequestHeader() != null) {
            UserAgent userAgent = UserAgent.parseUserAgentString(traceNode.getRequestHeader().getUserAgent());
            if("unknown".equalsIgnoreCase(userAgent.getBrowser().getName())){
                setName(traceNode.getRequestHeader().getUserAgent());
            }else {
                setName(userAgent.getBrowser().getName());
            }
        } else {
            setName("browser");
        }
    }

    public HttpTraceNode getTraceNode() {
        return traceNode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}
