package com.oAT.web.coveragecore.source;

import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.CoverageReportRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class CoverageSourceClassIndexService {
    private static final Logger logger = LoggerFactory.getLogger(CoverageSourceClassIndexService.class);

    private final CoverageReportRepository coverageReportRepository;
    private final ClassCoverageRepository classCoverageRepository;
    private final CoverageSourceContentService coverageSourceContentService;
    private final Set<String> ensuredReports = ConcurrentHashMap.newKeySet();

    public CoverageSourceClassIndexService(CoverageReportRepository coverageReportRepository,
                                           ClassCoverageRepository classCoverageRepository,
                                           CoverageSourceContentService coverageSourceContentService) {
        this.coverageReportRepository = coverageReportRepository;
        this.classCoverageRepository = classCoverageRepository;
        this.coverageSourceContentService = coverageSourceContentService;
    }

    public void ensureSourceClassesIndexed(String reportId) {
        if (!StringUtils.hasText(reportId) || ensuredReports.contains(reportId)) {
            return;
        }
        CoverageReportIndex report = coverageReportRepository.findById(reportId).orElse(null);
        if (report == null || !StringUtils.hasText(report.getAppId())) {
            return;
        }
        synchronized (ensuredReports) {
            if (ensuredReports.contains(reportId)) {
                return;
            }
            try {
                fillMissingSourceClassCoverage(report);
            } catch (Exception e) {
                logger.warn("补齐覆盖率源码类失败, reportId={}", reportId, e);
            } finally {
                ensuredReports.add(reportId);
            }
        }
    }

    private void fillMissingSourceClassCoverage(CoverageReportIndex report) {
        File codeFile = coverageSourceContentService.resolveSourceArtifact(report);
        if (codeFile == null || !codeFile.exists()) {
            return;
        }

        Set<String> sourceClassNames = scanJavaSourceClassNames(codeFile);
        if (sourceClassNames.isEmpty()) {
            return;
        }

        Set<String> existingClassNames = classCoverageRepository.findByReportId(report.getId()).stream()
                .map(ClassCoverageIndex::getClassName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> existingRootPackages = existingClassNames.stream()
                .map(this::firstPackageSegment)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        List<ClassCoverageIndex> missingClasses = new ArrayList<>();
        for (String className : sourceClassNames) {
            String ownerClassName = CoverageSourceClassUtil.resolveSourceOwnerClassName(className);
            if (!existingRootPackages.isEmpty() && !existingRootPackages.contains(firstPackageSegment(ownerClassName))) {
                continue;
            }
            if (existingClassNames.contains(ownerClassName)) {
                continue;
            }
            ClassCoverageIndex classCoverage = createEmptyClassCoverage(report, ownerClassName);
            missingClasses.add(classCoverage);
            existingClassNames.add(ownerClassName);
        }

        if (!missingClasses.isEmpty()) {
            classCoverageRepository.saveAll(missingClasses);
            logger.info("已从源码包补齐覆盖率类, reportId={}, count={}", report.getId(), missingClasses.size());
        }
    }

    private ClassCoverageIndex createEmptyClassCoverage(CoverageReportIndex report, String className) {
        ClassCoverageIndex classCoverage = new ClassCoverageIndex();
        classCoverage.setId(report.getId() + "_" + className.hashCode());
        classCoverage.setReportId(report.getId());
        classCoverage.setAppId(report.getAppId());
        classCoverage.setClassName(className);
        classCoverage.setSourceType(report.getSourceType());
        classCoverage.setLanguage(report.getLanguage());
        classCoverage.setTotalMethods(0);
        classCoverage.setCoveredMethods(0);
        classCoverage.setTotalBranches(0);
        classCoverage.setCoveredBranches(0);
        classCoverage.setTotalBranchTargets(0);
        classCoverage.setCoveredBranchTargets(0);
        classCoverage.setTotalLines(0);
        classCoverage.setCoveredLines(0);
        classCoverage.setTotalComplexity(0);
        classCoverage.setLineRate(0.0);
        classCoverage.setBranchRate(0.0);
        classCoverage.setMethodRate(0.0);
        classCoverage.setMethods(new ArrayList<>());
        return classCoverage;
    }

    private Set<String> scanJavaSourceClassNames(File codeFile) {
        Set<String> classNames = new TreeSet<>();
        try {
            if (codeFile.isDirectory()) {
                try (Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(codeFile.toPath())) {
                    stream.filter(path -> !java.nio.file.Files.isDirectory(path))
                            .map(path -> path.toString().replace('\\', '/'))
                            .filter(path -> path.endsWith(".java"))
                            .map(this::toClassNameFromSourcePath)
                            .filter(StringUtils::hasText)
                            .forEach(classNames::add);
                }
            } else if (isArchiveFile(codeFile)) {
                try (ZipFile zip = new ZipFile(codeFile)) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (!entry.isDirectory() && entry.getName().replace('\\', '/').endsWith(".java")) {
                            String className = toClassNameFromSourcePath(entry.getName());
                            if (StringUtils.hasText(className)) {
                                classNames.add(className);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("扫描源码包类失败, file={}", codeFile.getAbsolutePath(), e);
        }
        return classNames;
    }

    private boolean isArchiveFile(File file) {
        String lowerName = file.getName().toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".zip") || lowerName.endsWith(".jar") || lowerName.endsWith(".war");
    }

    private String toClassNameFromSourcePath(String sourcePath) {
        if (!StringUtils.hasText(sourcePath)) {
            return null;
        }
        String normalized = sourcePath.replace('\\', '/');
        int sourceRootIndex = normalized.lastIndexOf("/src/main/java/");
        int startIndex = sourceRootIndex >= 0 ? sourceRootIndex + "/src/main/java/".length() : -1;
        if (startIndex < 0) {
            if (normalized.startsWith("src/main/java/")) {
                startIndex = "src/main/java/".length();
            } else {
                return null;
            }
        }
        String relativePath = normalized.substring(startIndex);
        if (!relativePath.endsWith(".java") || relativePath.contains("/target/") || relativePath.contains("/build/")) {
            return null;
        }
        return relativePath.substring(0, relativePath.length() - ".java".length()).replace('/', '.');
    }

    private String firstPackageSegment(String className) {
        if (!StringUtils.hasText(className)) {
            return null;
        }
        int dotIndex = className.indexOf('.');
        return dotIndex > 0 ? className.substring(0, dotIndex) : className;
    }
}
