package com.oAT.web.coveragecore.query;

import com.oAT.web.common.CoverageSourceClassUtil;
import com.oAT.web.esDao.ClassCoverageRepository;
import com.oAT.web.esDao.entity.ClassCoverageIndex;
import com.oAT.web.service.entity.CoverageTreeNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CoverageTreeQueryService {
    private final ClassCoverageRepository classCoverageRepository;

    public CoverageTreeQueryService(ClassCoverageRepository classCoverageRepository) {
        this.classCoverageRepository = classCoverageRepository;
    }

    public List<CoverageTreeNode> getTreeNodes(String reportId,
                                               String parentPackage,
                                               String classNameSearch,
                                               String methodNameSearch,
                                               Double minRate,
                                               Double maxRate,
                                               Double minBranchRate,
                                               Double maxBranchRate,
                                               Double minMethodRate,
                                               Double maxMethodRate,
                                               Integer minComplexity,
                                               Integer maxComplexity) {
        if (!StringUtils.hasText(reportId)) {
            return java.util.Collections.emptyList();
        }

        String normalizedParent = StringUtils.hasText(parentPackage) ? parentPackage.trim() : "";
        String prefix = buildTreePrefix(normalizedParent);
        List<ClassCoverageIndex> classes = classCoverageRepository.findTreeCandidates(reportId, classNameSearch, methodNameSearch,
                minRate, maxRate, minBranchRate, maxBranchRate, minMethodRate, maxMethodRate,
                minComplexity, maxComplexity, prefix);

        Map<String, CoverageTreeNode> nodesMap = new HashMap<>();
        Set<String> exactClassNames = classes.stream()
                .map(ClassCoverageIndex::getClassName)
                .map(this::normalizeCoverageTreeName)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        for (ClassCoverageIndex cc : classes) {
            String classFullName = normalizeCoverageTreeName(cc.getClassName());
            if (!StringUtils.hasText(classFullName) || !classFullName.startsWith(prefix)) {
                continue;
            }

            String remaining = classFullName.substring(prefix.length());
            TreeNodeIdentity identity = resolveImmediateTreeNode(prefix, remaining, classFullName, exactClassNames);
            String nodeKey = identity.type + ":" + identity.fullName;
            CoverageTreeNode node = nodesMap.computeIfAbsent(nodeKey, key -> newTreeNode(reportId, normalizedParent, identity));

            if (hasTreeChildren(classFullName, identity.fullName)) {
                node.setHasChildren(true);
            }

            boolean aggregateStats = "package".equals(identity.type)
                    || classFullName.equals(identity.fullName)
                    || !exactClassNames.contains(identity.fullName);
            if (aggregateStats) {
                appendStats(node, cc);
            }
        }

        List<CoverageTreeNode> nodes = new ArrayList<>(nodesMap.values());
        nodes.forEach(this::calculateRates);
        nodes.sort(Comparator.comparingInt((CoverageTreeNode node) -> "package".equals(node.getType()) ? 0 : 1)
                .thenComparing(CoverageTreeNode::getFullName, Comparator.nullsLast(String::compareTo)));
        return nodes;
    }

    private CoverageTreeNode newTreeNode(String reportId, String normalizedParent, TreeNodeIdentity identity) {
        CoverageTreeNode node = new CoverageTreeNode();
        node.setFullName(identity.fullName);
        node.setName(identity.nodeName);
        node.setType(identity.type);
        node.setParentId(normalizedParent);
        node.setId(reportId + ":" + identity.type + ":" + identity.fullName);
        return node;
    }

    private void appendStats(CoverageTreeNode node, ClassCoverageIndex cc) {
        node.setTotalMethods(node.getTotalMethods() + cc.getTotalMethods());
        node.setCoveredMethods(node.getCoveredMethods() + cc.getCoveredMethods());
        node.setTotalBranches(node.getTotalBranches() + cc.getTotalBranches());
        node.setCoveredBranches(node.getCoveredBranches() + cc.getCoveredBranches());
        node.setTotalBranchTargets(node.getTotalBranchTargets() + cc.getTotalBranchTargets());
        node.setCoveredBranchTargets(node.getCoveredBranchTargets() + cc.getCoveredBranchTargets());
        node.setTotalLines(node.getTotalLines() + cc.getTotalLines());
        node.setCoveredLines(node.getCoveredLines() + cc.getCoveredLines());
        node.setTotalComplexity(node.getTotalComplexity() + cc.getTotalComplexity());
    }

    private void calculateRates(CoverageTreeNode node) {
        node.setLineRate(rate(node.getCoveredLines(), node.getTotalLines()));
        node.setBranchRate(rate(node.getCoveredBranchTargets(), node.getTotalBranchTargets()));
        node.setMethodRate(rate(node.getCoveredMethods(), node.getTotalMethods()));
    }

    private String normalizeCoverageTreeName(String className) {
        if (!StringUtils.hasText(className)) {
            return className;
        }
        return CoverageSourceClassUtil.isPathLikeName(className)
                ? CoverageSourceClassUtil.normalizePathName(className)
                : className;
    }

    private String buildTreePrefix(String parent) {
        if (!StringUtils.hasText(parent)) {
            return "";
        }
        return CoverageSourceClassUtil.isPathLikeName(parent) ? CoverageSourceClassUtil.normalizePathName(parent) + "/" : parent + ".";
    }

    private boolean hasTreeChildren(String classFullName, String nodeFullName) {
        if (!StringUtils.hasText(classFullName) || !StringUtils.hasText(nodeFullName)) {
            return false;
        }
        String separator = CoverageSourceClassUtil.isPathLikeName(classFullName) ? "/" : ".";
        return classFullName.startsWith(nodeFullName + separator);
    }

    private double rate(long covered, long total) {
        return total <= 0 ? 0D : (double) covered / total * 100;
    }

    private TreeNodeIdentity resolveImmediateTreeNode(String prefix, String remaining, String classFullName, Set<String> exactClassNames) {
        if (CoverageSourceClassUtil.isPathLikeName(classFullName)) {
            if (!StringUtils.hasText(prefix)) {
                TreeNodeIdentity sourceRoot = resolvePathSourceRootNode(classFullName);
                if (sourceRoot != null) {
                    return sourceRoot;
                }
            }
            String normalizedRemaining = CoverageSourceClassUtil.normalizePathName(remaining);
            boolean leadingSlash = normalizedRemaining.startsWith("/");
            if (leadingSlash) {
                normalizedRemaining = normalizedRemaining.substring(1);
            }
            String[] segments = normalizedRemaining.split("/");
            if (segments.length == 0) {
                return new TreeNodeIdentity(CoverageSourceClassUtil.displayFileName(classFullName), classFullName, "class");
            }
            if (segments.length == 1) {
                String fullName = prefix + (leadingSlash && prefix.isEmpty() ? "/" : "") + segments[0];
                return new TreeNodeIdentity(segments[0], fullName, "class");
            }
            String nodeName = segments[0];
            String fullName = prefix + (leadingSlash && prefix.isEmpty() ? "/" : "") + nodeName;
            return new TreeNodeIdentity(nodeName, fullName, "package");
        }

        String[] segments = remaining.split("\\.");
        if (segments.length == 0) {
            return new TreeNodeIdentity(classFullName, classFullName, "class");
        }

        if (segments.length == 1) {
            String fullName = prefix + segments[0];
            if (exactClassNames.contains(fullName)) {
                String nodeName = CoverageSourceClassUtil.toTreeDisplayName(segments[0], "class");
                return new TreeNodeIdentity(nodeName, fullName, "class");
            }
            return new TreeNodeIdentity(segments[0], fullName, "package");
        }

        String nodeName = segments[0];
        String fullName = prefix + nodeName;
        return new TreeNodeIdentity(nodeName, fullName, "package");
    }

    private TreeNodeIdentity resolvePathSourceRootNode(String classFullName) {
        String normalized = CoverageSourceClassUtil.normalizePathName(classFullName);
        String[] sourceMarkers = {"/src/", "/app/", "/pages/", "/components/", "/lib/"};
        for (String marker : sourceMarkers) {
            int markerIndex = normalized.indexOf(marker);
            if (markerIndex <= 0) {
                continue;
            }
            String rootPath = normalized.substring(0, markerIndex);
            String rootName = CoverageSourceClassUtil.displayFileName(rootPath);
            if (StringUtils.hasText(rootName)) {
                return new TreeNodeIdentity(rootName, rootPath, "package");
            }
        }
        return null;
    }

    private static class TreeNodeIdentity {
        private final String nodeName;
        private final String fullName;
        private final String type;

        private TreeNodeIdentity(String nodeName, String fullName, String type) {
            this.nodeName = nodeName;
            this.fullName = fullName;
            this.type = type;
        }
    }
}
