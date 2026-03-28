package com.oAT.web.service;

import com.oAT.agent.model.TraceNode;

public interface TraceNodeFilter {

    TraceNode doFilter(TraceNode node);

}
