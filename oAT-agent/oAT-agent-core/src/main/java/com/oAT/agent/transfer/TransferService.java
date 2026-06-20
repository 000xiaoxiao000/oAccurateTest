package com.oAT.agent.transfer;


import com.oAT.agent.model.CoverageUploadVo;

public interface TransferService {
    void uploadNode(String traceId, String type, Object date);

    void uploadCoverage(CoverageUploadVo coverage);
}
