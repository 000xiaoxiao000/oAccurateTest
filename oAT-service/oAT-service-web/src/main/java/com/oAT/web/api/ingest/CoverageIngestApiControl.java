package com.oAT.web.api.ingest;

import com.oAT.web.api.ingest.CoverageIngestFacade.CoverageIngestResult;
import com.oAT.web.control.entity.ResultNotified;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/ingest")
@CrossOrigin(originPatterns = "*", allowCredentials = "true", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
public class CoverageIngestApiControl {
    private final CoverageIngestFacade coverageIngestFacade;

    public CoverageIngestApiControl(CoverageIngestFacade coverageIngestFacade) {
        this.coverageIngestFacade = coverageIngestFacade;
    }

    @PostMapping(value = "/coverage", consumes = {"application/json", "text/plain", "*/*"})
    public ResultNotified<CoverageIngestResult> report(@RequestHeader HttpHeaders headers,
                                                       @RequestBody String requestBody) {
        CoverageIngestResult result = coverageIngestFacade.ingestUnified(headers, requestBody);
        return new ResultNotified<>(true, "统一覆盖率上报成功", result);
    }
}
