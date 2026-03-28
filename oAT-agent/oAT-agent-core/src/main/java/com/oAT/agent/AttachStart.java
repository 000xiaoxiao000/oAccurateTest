package com.oAT.agent;

import com.oAT.agent.attach.AttachAgent;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

public class AttachStart {
    private final static Log logger = LogFactory.getLog(AttachStart.class);

    public static void main(String[] args) throws Exception {
        logger.info("[Agent-202512251532]Agent is starting...");
        new AttachAgent(args);
    }
}
