package com.oAT.web.language.universal;

import com.oAT.web.coverage.universal.CoverageParser;
import com.oAT.web.coverage.universal.CoverageParserRegistry;
import com.oAT.web.coverage.universal.SourceType;
import com.oAT.web.coverage.universal.UniversalCoverageFile;
import com.oAT.web.coveragecore.model.CoverageBranch;
import com.oAT.web.coveragecore.model.CoverageFootprint;
import com.oAT.web.coveragecore.model.CoverageFunction;
import com.oAT.web.coveragecore.model.CoverageLanguage;
import com.oAT.web.coveragecore.model.CoverageLine;
import com.oAT.web.coveragecore.model.CoverageUnit;
import com.oAT.web.language.spi.CoverageIngestMetadata;
import com.oAT.web.language.spi.CoverageLanguageAdapter;

import java.util.ArrayList;
import java.util.List;

public abstract class UniversalCoverageLanguageAdapter implements CoverageLanguageAdapter {
    private final CoverageParserRegistry parserRegistry;

    protected UniversalCoverageLanguageAdapter(CoverageParserRegistry parserRegistry) {
        this.parserRegistry = parserRegistry;
    }

    @Override
    public List<CoverageUnit> parse(byte[] payload, CoverageIngestMetadata metadata) {
        CoverageParser parser = parserRegistry.get(sourceType());
        List<UniversalCoverageFile> files = parser.parse(payload);
        List<CoverageUnit> units = new ArrayList<>();
        CoverageFootprint footprint = footprint(metadata);
        for (UniversalCoverageFile file : files) {
            units.add(toCoverageUnit(file, footprint));
        }
        return units;
    }

    protected abstract SourceType sourceType();

    private CoverageUnit toCoverageUnit(UniversalCoverageFile file, CoverageFootprint footprint) {
        CoverageUnit unit = new CoverageUnit();
        unit.setLanguage(language());
        unit.setUnitKey(file.getFilePath());
        unit.setDisplayName(file.getFilePath());
        unit.setSourcePath(file.getFilePath());

        List<CoverageLine> lines = new ArrayList<>();
        for (UniversalCoverageFile.LineCoverage lineCoverage : file.getLines()) {
            CoverageLine line = new CoverageLine();
            line.setLine(lineCoverage.getLine());
            line.setHits(lineCoverage.getCoveredCount());
            if (lineCoverage.getCoveredCount() > 0) {
                line.getFootprints().add(footprint);
            }
            lines.add(line);
        }
        unit.setLines(lines);

        List<CoverageBranch> branches = new ArrayList<>();
        for (UniversalCoverageFile.BranchCoverage branchCoverage : file.getBranches()) {
            CoverageBranch branch = new CoverageBranch();
            branch.setLine(branchCoverage.getLine());
            branch.setBranchIndex(branchCoverage.getBranchIndex());
            branch.setGroupId(branchCoverage.getGroupId());
            branch.setHits(branchCoverage.getCoveredCount());
            if (branchCoverage.getCoveredCount() > 0) {
                branch.getFootprints().add(footprint);
            }
            branches.add(branch);
        }
        unit.setBranches(branches);

        List<CoverageFunction> functions = new ArrayList<>();
        for (UniversalCoverageFile.FunctionCoverage functionCoverage : file.getFunctions()) {
            CoverageFunction function = new CoverageFunction();
            function.setSignature(functionCoverage.getName());
            function.setStartLine(functionCoverage.getStartLine());
            function.setEndLine(functionCoverage.getEndLine());
            function.setComplexity(1);
            functions.add(function);
        }
        unit.setFunctions(functions);
        return unit;
    }

    private CoverageFootprint footprint(CoverageIngestMetadata metadata) {
        CoverageFootprint footprint = new CoverageFootprint();
        if (metadata != null) {
            footprint.setTraceId(metadata.getTraceId());
            footprint.setCaseName(metadata.getCaseName());
            footprint.setTestStage(metadata.getTestStage());
            footprint.setBuildId(metadata.getBuildId());
            footprint.setTimestamp(metadata.getTimestamp());
        }
        return footprint;
    }

    public static class Frontend extends UniversalCoverageLanguageAdapter {
        public Frontend(CoverageParserRegistry parserRegistry) { super(parserRegistry); }
        @Override public CoverageLanguage language() { return CoverageLanguage.FRONTEND; }
        @Override protected SourceType sourceType() { return SourceType.FRONTEND; }
    }

    public static class Go extends UniversalCoverageLanguageAdapter {
        public Go(CoverageParserRegistry parserRegistry) { super(parserRegistry); }
        @Override public CoverageLanguage language() { return CoverageLanguage.GO; }
        @Override protected SourceType sourceType() { return SourceType.GO; }
    }

    public static class Python extends UniversalCoverageLanguageAdapter {
        public Python(CoverageParserRegistry parserRegistry) { super(parserRegistry); }
        @Override public CoverageLanguage language() { return CoverageLanguage.PYTHON; }
        @Override protected SourceType sourceType() { return SourceType.PYTHON; }
    }

    public static class Cpp extends UniversalCoverageLanguageAdapter {
        public Cpp(CoverageParserRegistry parserRegistry) { super(parserRegistry); }
        @Override public CoverageLanguage language() { return CoverageLanguage.CPP; }
        @Override protected SourceType sourceType() { return SourceType.CPP; }
    }
}
