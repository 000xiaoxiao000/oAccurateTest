package com.oAT.web.control.entity;

import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.TraceNode;
import com.oAT.web.service.TraceEntryDescriptor;
import com.oAT.web.service.TraceEntryDescriptorBuilder;
import eu.bitwalker.useragentutils.UserAgent;

public class ClientGraphNode extends GraphNode{

    private TraceNode traceNode;
    private String title;

    public ClientGraphNode(TraceNode traceNode) {
        this.traceNode = traceNode;
        setId("user-agent");
        setType(GraphNodeType.CLIENT);
        TraceEntryDescriptor descriptor = TraceEntryDescriptorBuilder.build(traceNode);
        title = descriptor.getDisplayName();
        if (traceNode instanceof HttpTraceNode && ((HttpTraceNode) traceNode).getRequestHeader() != null) {
            HttpTraceNode httpTraceNode = (HttpTraceNode) traceNode;
            UserAgent userAgent = UserAgent.parseUserAgentString(httpTraceNode.getRequestHeader().getUserAgent());
            if("unknown".equalsIgnoreCase(userAgent.getBrowser().getName())){
                setName(httpTraceNode.getRequestHeader().getUserAgent());
            }else {
                setName(userAgent.getBrowser().getName());
            }
        } else {
            setName(descriptor.getEntryType());
        }
    }

    public TraceNode getTraceNode() {
        return traceNode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

}
