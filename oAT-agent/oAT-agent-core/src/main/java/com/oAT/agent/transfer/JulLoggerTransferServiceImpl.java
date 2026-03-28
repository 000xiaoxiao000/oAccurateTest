package com.oAT.agent.transfer;

import com.oAT.agent.common.JsonUtil;

import java.util.logging.Logger;

public class JulLoggerTransferServiceImpl implements TransferService {
    Logger logger = Logger.getLogger(JulLoggerTransferServiceImpl.class.getName());

    @Override
    public void uploadNode(String traceId, String type, Object date) {
        logger.info("[Agent-info]traceId=" + traceId + " type=" + type + " date=" + JsonUtil.toJson(date));
    }
}
