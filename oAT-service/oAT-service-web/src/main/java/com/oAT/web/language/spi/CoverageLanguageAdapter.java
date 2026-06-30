package com.oAT.web.language.spi;

import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.coveragecore.model.CoverageUnit;

import java.util.List;

public interface CoverageLanguageAdapter {
    CoverageLanguage language();

    List<CoverageUnit> parse(byte[] payload, CoverageIngestMetadata metadata);
}
