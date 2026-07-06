package com.oAT.web.coveragecore.source;

import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.esDao.VersionCenterRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.esDao.entity.CoverageReportIndex;
import com.oAT.web.esDao.entity.VersionCenterIndex;
import com.oAT.web.esDao.entity.VersionItem;
import com.oAT.web.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class CoverageSourceContentService {
    private static final Logger logger = LoggerFactory.getLogger(CoverageSourceContentService.class);
    private static final String[] SOURCE_PREFIXES = {"", "src/main/java/", "src/test/java/", "src/", "app/", "packages/"};

    private final VersionCenterRepository versionCenterRepository;
    private final ResourceService resourceService;
    private final ConcurrentMap<String, List<String>> zipEntryCache = new ConcurrentHashMap<>();

    public CoverageSourceContentService(VersionCenterRepository versionCenterRepository,
                                        ResourceService resourceService) {
        this.versionCenterRepository = versionCenterRepository;
        this.resourceService = resourceService;
    }

    public CoverageSourceContent loadSource(CoverageReportIndex report, ClassCoverageIndex classCoverage) {
        if (report == null || classCoverage == null) {
            return CoverageSourceContent.message("Coverage data not found.");
        }
        List<String> candidates = buildSourcePathCandidates(classCoverage);
        VersionCenterIndex version = resolveVersion(report);
        if (version == null || version.getVersionItem() == null) {
            logger.warn("Source code not found in Version Center for appId: {}", report.getAppId());
            return CoverageSourceContent.message("Source code not found in Version Center for this app.");
        }
        File codeFile = resolveProgramFile(version.getVersionItem());
        if (codeFile == null) {
            return CoverageSourceContent.message("No program file recorded for this version record.");
        }
        if (!codeFile.exists()) {
            return CoverageSourceContent.message("Code file not found at " + codeFile.getAbsolutePath());
        }
        try {
            CoverageSourceContent content = codeFile.isDirectory()
                    ? loadFromDirectory(codeFile, candidates)
                    : loadFromArchive(codeFile, candidates);
            return content.getContent() == null
                    ? CoverageSourceContent.message("Source code for " + classCoverage.getClassName() + " not found in " + codeFile.getName())
                    : content;
        } catch (IOException e) {
            logger.error("Error reading source code for {}", classCoverage.getClassName(), e);
            return CoverageSourceContent.message("Error reading source code: " + e.getMessage());
        }
    }

    public File resolveSourceArtifact(CoverageReportIndex report) {
        VersionCenterIndex version = resolveVersion(report);
        return version == null ? null : resolveProgramFile(version.getVersionItem());
    }

    private VersionCenterIndex resolveVersion(CoverageReportIndex report) {
        List<VersionCenterIndex> versions = versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_VersionNumberAndVersionItem_RepoBranchAndVersionItem_RepoCommitId(
                report.getAppId(), report.getVersionNumber(), report.getRepoBranch(), report.getRepoCommitId());
        VersionCenterIndex version = first(versions);
        if (version == null && StringUtils.hasText(report.getRepoCommitId()) && !"head".equalsIgnoreCase(report.getRepoCommitId())) {
            version = first(versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_RepoCommitId(report.getAppId(), report.getRepoCommitId()));
        }
        if (version == null && StringUtils.hasText(report.getRepoBranch())) {
            versions = versionCenterRepository.findByVersionItem_AppIdAndVersionItem_RepoBranch(report.getAppId(), report.getRepoBranch());
            if (versions != null) {
                version = versions.stream()
                        .filter(candidate -> candidate.getVersionItem() != null)
                        .filter(candidate -> report.getVersionNumber().equals(candidate.getVersionItem().getVersionNumber()))
                        .findFirst()
                        .orElse(null);
            }
        }
        if (version == null && StringUtils.hasText(report.getVersionNumber())) {
            version = first(versionCenterRepository.findTop1ByVersionItem_AppIdAndVersionItem_VersionNumber(report.getAppId(), report.getVersionNumber()));
        }
        if (version == null) {
            version = first(versionCenterRepository.findTop1ByVersionItem_AppIdOrderByCreateTimeDesc(report.getAppId()));
        }
        return version;
    }

    private VersionCenterIndex first(List<VersionCenterIndex> versions) {
        return versions == null || versions.isEmpty() ? null : versions.get(0);
    }

    private File resolveProgramFile(VersionItem item) {
        if (item == null || !StringUtils.hasText(item.getProgramFile())) {
            return null;
        }
        File file = new File(item.getProgramFile());
        return file.isAbsolute() ? file : new File(resourceService.getCacheRoot(), item.getProgramFile());
    }

    private List<String> buildSourcePathCandidates(ClassCoverageIndex classCoverage) {
        Set<String> candidates = new LinkedHashSet<>();
        addPathCandidate(candidates, classCoverage.getSourcePath());
        addPathCandidate(candidates, classCoverage.getDisplayName());
        addPathCandidate(candidates, classCoverage.getClassName());
        if (StringUtils.hasText(classCoverage.getClassName()) && !CoverageSourceClassUtil.isPathLikeName(classCoverage.getClassName())) {
            candidates.addAll(CoverageSourceClassUtil.buildSourcePathCandidates(classCoverage.getClassName()));
        }
        return new ArrayList<>(candidates);
    }

    private void addPathCandidate(Set<String> candidates, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String normalized = value.replace('\\', '/').replaceAll("^/+", "");
        candidates.add(normalized);
        addPathSuffixCandidates(candidates, normalized);
        if (!normalized.contains("/") && normalized.contains(".")) {
            candidates.addAll(CoverageSourceClassUtil.buildSourcePathCandidates(normalized));
        }
    }

    private void addPathSuffixCandidates(Set<String> candidates, String normalized) {
        String[] markers = {"/src/", "/app/", "/packages/"};
        for (String marker : markers) {
            int index = normalized.indexOf(marker);
            if (index >= 0 && index + 1 < normalized.length()) {
                candidates.add(normalized.substring(index + 1));
            }
        }
    }

    private CoverageSourceContent loadFromArchive(File archive, List<String> candidates) throws IOException {
        String lowerName = archive.getName().toLowerCase();
        if (!lowerName.endsWith(".zip") && !lowerName.endsWith(".jar") && !lowerName.endsWith(".war")) {
            return CoverageSourceContent.message("Unsupported code package: " + archive.getName());
        }
        try (ZipFile zip = new ZipFile(archive)) {
            for (String sourcePath : candidates) {
                for (String prefix : SOURCE_PREFIXES) {
                    ZipEntry entry = zip.getEntry(prefix + sourcePath);
                    if (entry != null) {
                        return CoverageSourceContent.content(entry.getName(), readZipEntry(zip, entry));
                    }
                }
            }
            String bestMatch = findBestZipEntry(archive, zip, candidates);
            if (bestMatch != null) {
                ZipEntry entry = zip.getEntry(bestMatch);
                if (entry != null) {
                    return CoverageSourceContent.content(entry.getName(), readZipEntry(zip, entry));
                }
            }
        }
        return CoverageSourceContent.message(null);
    }

    private String findBestZipEntry(File archive, ZipFile zip, List<String> candidates) {
        List<String> entries = zipEntryCache.computeIfAbsent(archive.getAbsolutePath(), key -> {
            List<String> result = new ArrayList<>();
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                result.add(enumeration.nextElement().getName());
            }
            return result;
        });
        String bestMatch = null;
        for (String entryName : entries) {
            String normalizedName = entryName.replace('\\', '/');
            for (String sourcePath : candidates) {
                if (normalizedName.endsWith("/" + sourcePath) || normalizedName.equals(sourcePath)) {
                    bestMatch = pickBetterSourcePath(bestMatch, entryName);
                    break;
                }
            }
        }
        return bestMatch;
    }

    private String pickBetterSourcePath(String current, String candidate) {
        if (current == null) {
            return candidate;
        }
        return sourcePathScore(candidate) > sourcePathScore(current) ? candidate : current;
    }

    private int sourcePathScore(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        if (normalized.contains("/src/main/java/") || normalized.contains("/src/main/")) {
            return 3;
        }
        if (normalized.contains("/src/test/java/") || normalized.contains("/src/test/")) {
            return 2;
        }
        if (normalized.contains("/src/")) {
            return 1;
        }
        return 0;
    }

    private CoverageSourceContent loadFromDirectory(File root, List<String> candidates) throws IOException {
        for (String sourcePath : candidates) {
            for (String prefix : SOURCE_PREFIXES) {
                File file = new File(root, prefix + sourcePath);
                if (file.exists() && file.isFile()) {
                    return CoverageSourceContent.content(root.toPath().relativize(file.toPath()).toString().replace('\\', '/'), Files.readString(file.toPath(), StandardCharsets.UTF_8));
                }
            }
        }
        try (Stream<Path> stream = Files.walk(root.toPath())) {
            Path found = stream
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> matchesCandidate(path.toString(), candidates))
                    .sorted((left, right) -> Integer.compare(sourcePathScore(right.toString()), sourcePathScore(left.toString())))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                return CoverageSourceContent.content(root.toPath().relativize(found).toString().replace('\\', '/'), Files.readString(found, StandardCharsets.UTF_8));
            }
        }
        return CoverageSourceContent.message(null);
    }

    private boolean matchesCandidate(String path, List<String> candidates) {
        String normalized = path.replace('\\', '/');
        for (String candidate : candidates) {
            if (normalized.endsWith("/" + candidate) || normalized.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String readZipEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream stream = zip.getInputStream(entry)) {
            return new String(readAllBytes(stream), StandardCharsets.UTF_8);
        }
    }

    private byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
