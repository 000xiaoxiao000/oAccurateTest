package com.oAT.web.domain;

import com.oAT.agent.model.StackNodeVo;
import com.oAT.web.common.ClassUtil;
import com.oAT.web.esDao.entity.StaticSourceMethodInfo;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;

public class StackCodeLayer implements ImageLayer {

    StackNodeVo[] codeNodes;
    String snapshotId;
    Map<String, Map<String, StaticSourceMethodInfo>> staticMethodLookup;
    Map<String, List<String>> staticInvokeLookup;
    Set<String> entryMethodKeys;

    public StackCodeLayer(StackNodeVo[] codeNodes, String snapshotId, Map<String, Map<String, StaticSourceMethodInfo>> staticMethodLookup) {
        this(codeNodes, snapshotId, staticMethodLookup, Collections.emptyMap());
    }

    public StackCodeLayer(StackNodeVo[] codeNodes, String snapshotId, Map<String, Map<String, StaticSourceMethodInfo>> staticMethodLookup,
                          Map<String, List<String>> staticInvokeLookup) {
        this(codeNodes, snapshotId, staticMethodLookup, staticInvokeLookup, Collections.emptySet());
    }

    public StackCodeLayer(StackNodeVo[] codeNodes, String snapshotId, Map<String, Map<String, StaticSourceMethodInfo>> staticMethodLookup,
                          Map<String, List<String>> staticInvokeLookup, Set<String> entryMethodKeys) {
        this.codeNodes = codeNodes != null ? codeNodes : new StackNodeVo[0];
        this.snapshotId = snapshotId;
        this.staticMethodLookup = staticMethodLookup != null ? staticMethodLookup : Collections.emptyMap();
        this.staticInvokeLookup = staticInvokeLookup != null ? staticInvokeLookup : Collections.emptyMap();
        this.entryMethodKeys = entryMethodKeys != null ? entryMethodKeys : Collections.emptySet();
    }

    @Override
    public List<ImageElement> elements() {
        if (ArrayUtils.isEmpty(codeNodes)) {
            return Collections.emptyList();
        }

        // 1. 按 packageAndClassName 分组
        Map<String, List<StackNodeVo>> packageGroup = Arrays.stream(codeNodes)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(StackNodeVo::getClassName));

        // 2. 按包分组整体在圆环上分段均匀分布
        int totalNodeCount = Arrays.stream(codeNodes).toArray().length;
        double radius = 350 + totalNodeCount * 3;
        double centerX = 0;
        double centerY = 0;

        // 统计每组节点数
        List<List<StackNodeVo>> groupList = new ArrayList<>(packageGroup.values());

        // 计算每组在圆环上的角度区间
        int nodeGlobalIdx = 0;
        Collection<ImageElement> collect = new ArrayList<>();
        for (List<StackNodeVo> groupNodes : groupList) {
            int groupNodeCount = groupNodes.size();
            // 该组在圆环上的起止角度
            double groupStartAngle = 2 * Math.PI * nodeGlobalIdx / totalNodeCount;
            double groupEndAngle = 2 * Math.PI * (nodeGlobalIdx + groupNodeCount) / totalNodeCount;
            double groupAngleStep = groupNodeCount == 1 ? 0 : (groupEndAngle - groupStartAngle) / (groupNodeCount - 1);

            for (int i = 0; i < groupNodeCount; i++) {
                StackNodeVo a = groupNodes.get(i);
                ImageElement nodeElement = buildNode(a);
                double angle = groupStartAngle + i * groupAngleStep;
                double x = centerX + radius * Math.cos(angle);
                double y = centerY + radius * Math.sin(angle);
                if (nodeElement.data != null) {
                    nodeElement.data.x = (int) x;
                    nodeElement.data.y = (int) y;
                }
                collect.add(nodeElement);
                nodeGlobalIdx++;
            }
        }
        // 去重
        Map<String, ImageElement> idMap = collect.stream()
                .filter(e -> e.data != null && e.data.id != null)
                .collect(Collectors.toMap(e -> e.data.id, e -> e, (k1, k2) -> k1));
        List<ImageElement> results = new ArrayList<>(idMap.values());

        // 全局按weight 40,30,20,10,0顺序排序
        results.sort((e1, e2) -> {
            int w1 = e1.data != null ? e1.data.weight : 0;
            int w2 = e2.data != null ? e2.data.weight : 0;
            return Integer.compare(
                    weightOrder(w1),
                    weightOrder(w2)
            );
        });

        // 添加源码方法间调用关系，并仅将 root 连接到入口方法或子图根节点。
        results.addAll(buildCallEdges());
        return results;
    }

    // 辅助方法：将weight映射为排序优先级
    private int weightOrder(int weight) {
        if (weight == 40) return 0;
        if (weight == 30) return 1;
        if (weight == 20) return 2;
        if (weight == 10) return 3;
        return 4;
    }

    private ImageElement buildNode(StackNodeVo node) {
        String className = ClassUtil.toClassName(node.getClassName());
        String methodName = node.getMethodName();
        String classSimpleName = ClassUtil.getClassSimpleName(className);
        ImageData imageData = new ImageData(className + " " + node.getMethodName());

        // 从全量静态数据获取总数
        String methodKey = node.getMethodName() + "#" + node.getMethodDescriptor();
        Map<String, StaticSourceMethodInfo> classMethodMap = staticMethodLookup.get(node.getClassName());
        List<Integer> lineTotalList = Collections.emptyList();
        int cycloVal = 0;
        List<Integer> branchTotalList = Collections.emptyList();
        if (classMethodMap != null) {
            StaticSourceMethodInfo staticMethod = classMethodMap.get(methodKey);
            if (staticMethod != null) {
                lineTotalList = staticMethod.getMethodLineNumberMap() != null ? staticMethod.getMethodLineNumberMap() : Collections.emptyList();
                cycloVal = staticMethod.getCyclomaticComplexityMap() != null ? staticMethod.getCyclomaticComplexityMap() : 0;
                branchTotalList = staticMethod.getBranchLineNumberSet() != null ? staticMethod.getBranchLineNumberSet() : Collections.emptyList();
            }
        }

        List<Integer> executedLines = node.getDoLines() == null ? Collections.emptyList() : node.getDoLines();
        int executedLineCount = new LinkedHashSet<>(executedLines).size();
        int totalLineCount = new LinkedHashSet<>(lineTotalList).size();
        float coveragePercent = totalLineCount == 0 ? 0 : (float) executedLineCount * 100 / totalLineCount;

        imageData.name = classSimpleName + " " + methodName + " " + Math.round(coveragePercent) + "%";
        imageData.doLines = new ArrayList<>(executedLines);
        imageData.lineTotal = new ArrayList<>(lineTotalList);

        imageData.coverageRate = coveragePercent;
        imageData.executeMethodTotal = node.getExecuteMethodTotal();
        imageData.methodTotal = null; // 总数来自全量静态数据，此处不再从 StackNodeVo 获取
        imageData.executebranch = node.getExecuteBranch();
        imageData.branchTotal = new ArrayList<>(branchTotalList);
        imageData.cyclo = cycloVal;

        // 新的节点权重判断逻辑
        String[] classNamePathWords = className.substring(0, className.lastIndexOf('.')).split("\\.");
        boolean isSpecialType = false;
        boolean isImplOrService = false;
        boolean isController = false;
        for (String classNamePathWord : classNamePathWords) {
            if (
                    classNamePathWord.toLowerCase().contains("dto") || classNamePathWord.toLowerCase().contains("vo") ||
                            classNamePathWord.toLowerCase().contains("bo") || classNamePathWord.toLowerCase().contains("po") ||
                            classNamePathWord.toLowerCase().contains("cglib") || classNamePathWord.toLowerCase().contains("enum") ||
                            classNamePathWord.toLowerCase().contains("entity") || classNamePathWord.toLowerCase().contains("log") ||
                            classNamePathWord.toLowerCase().contains("common") || classNamePathWord.toLowerCase().contains("aop") ||
                            classNamePathWord.toLowerCase().contains("domain")
            ) {
                isSpecialType = true;
                break;
            }
            if (classNamePathWord.toLowerCase().contains("impl") || classNamePathWord.toLowerCase().contains("service")) {
                isImplOrService = true;
                break;
            }
            if (classNamePathWord.toLowerCase().contains("controller")) {
                isController = true;
                break;
            }
        }
        // 也要判断类名本身
        String classSimpleNameLower = classSimpleName.toLowerCase();
        if (classSimpleNameLower.toLowerCase().contains("dto") || classSimpleNameLower.toLowerCase().contains("vo") ||
                classSimpleNameLower.toLowerCase().contains("bo") || classSimpleNameLower.toLowerCase().contains("po") ||
                classSimpleNameLower.toLowerCase().contains("cglib") || classSimpleNameLower.toLowerCase().contains("enum") ||
                classSimpleNameLower.toLowerCase().contains("entity") || classSimpleNameLower.toLowerCase().contains("log") ||
                classSimpleNameLower.toLowerCase().contains("common") || classSimpleNameLower.toLowerCase().contains("aop") ||
                classSimpleNameLower.toLowerCase().contains("domain")
        ) {
            isSpecialType = true;
        }
        if (classSimpleNameLower.contains("impl") || classSimpleNameLower.contains("service")) {
            isImplOrService = true;
        }
        if (classSimpleNameLower.contains("controller")) {
            isController = true;
        }
        // 根据类型设置节点权重
        if (isSpecialType) {
            imageData.weight = 10; // 灰色节点，如：DTO、VO、PO、CGLIB、ENUM、ENTITY、LOG、Common、AOP 等
        } else if (isImplOrService) {
            imageData.weight = 20; // 暗紫色节点，方法中调用接口实现的方法
        } else if (isController) {
            imageData.weight = 30; // 黄褐色节点，controller层的方法
        } else {
            imageData.weight = 0; // 其它
        }

        imageData.packageAndClassName = className;
        imageData.methodName = methodName;
        ImageElement element = buildDefaultNode(imageData);
        element.classes = new String[]{"code_class", "stack_code"};
        return element;
    }

    private List<ImageElement> buildCallEdges() {
        if (ArrayUtils.isEmpty(codeNodes)) {
            return Collections.emptyList();
        }

        Map<String, StackNodeVo> nodeByStackId = Arrays.stream(codeNodes)
                .filter(Objects::nonNull)
                .filter(node -> hasText(node.getId()))
                .collect(Collectors.toMap(StackNodeVo::getId, node -> node, (left, right) -> left, LinkedHashMap::new));

        Map<String, ImageElement> edgeMap = new LinkedHashMap<>();
        for (StackNodeVo node : codeNodes) {
            if (!isValidNode(node)) {
                continue;
            }
            StackNodeVo parent = findNearestExistingParent(node, nodeByStackId);
            if (parent != null && isValidNode(parent)) {
                addEdge(edgeMap, graphNodeId(parent), graphNodeId(node), "invoke", new String[]{"invoke"});
            }
        }

        addStaticInvokeEdges(edgeMap);
        if (edgeMap.isEmpty()) {
            addRuntimeSequenceEdges(edgeMap);
        }

        Set<String> rootTargets = collectEntryNodeIds();
        if (rootTargets.isEmpty()) {
            rootTargets = collectTopSourceNodeIds(edgeMap);
        }
        if (rootTargets.isEmpty()) {
            String firstNodeId = firstValidNodeId();
            if (hasText(firstNodeId)) {
                rootTargets.add(firstNodeId);
            }
        }
        for (String target : rootTargets) {
            addEdge(edgeMap, snapshotId, target, "entry", new String[]{"start_invoke", "invoke"});
        }
        return new ArrayList<>(edgeMap.values());
    }

    private void addStaticInvokeEdges(Map<String, ImageElement> edgeMap) {
        if (staticInvokeLookup.isEmpty()) {
            return;
        }
        List<StackNodeVo> validNodes = Arrays.stream(codeNodes)
                .filter(this::isValidNode)
                .collect(Collectors.toList());
        Map<String, List<StackNodeVo>> nodesByClass = validNodes.stream()
                .collect(Collectors.groupingBy(node -> ClassUtil.toClassName(node.getClassName()), LinkedHashMap::new, Collectors.toList()));

        for (StackNodeVo sourceNode : validNodes) {
            List<String> invokedMethods = staticInvokeLookup.getOrDefault(sourceMethodKey(sourceNode), Collections.emptyList());
            for (String invokedMethod : invokedMethods) {
                StaticInvokeTarget target = parseStaticInvokeTarget(invokedMethod);
                if (target == null || !hasText(target.className)) {
                    continue;
                }
                List<StackNodeVo> targetNodes = nodesByClass.getOrDefault(target.className, Collections.emptyList());
                for (StackNodeVo targetNode : targetNodes) {
                    if (graphNodeId(sourceNode).equals(graphNodeId(targetNode))) {
                        continue;
                    }
                    if (hasText(target.methodName) && !methodNameMatches(target.methodName, targetNode.getMethodName())) {
                        continue;
                    }
                    addEdge(edgeMap, graphNodeId(sourceNode), graphNodeId(targetNode), "static invoke", new String[]{"invoke", "static_invoke"});
                }
            }
        }
    }

    private String sourceMethodKey(StackNodeVo node) {
        return ClassUtil.toClassName(node.getClassName()) + "#" + normalizeMethodName(node.getMethodName());
    }

    private Set<String> collectEntryNodeIds() {
        if (entryMethodKeys.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> normalizedEntryKeys = entryMethodKeys.stream()
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> nodeIds = new LinkedHashSet<>();
        for (StackNodeVo node : codeNodes) {
            if (isValidNode(node) && normalizedEntryKeys.contains(sourceMethodKey(node))) {
                nodeIds.add(graphNodeId(node));
            }
        }
        return nodeIds;
    }

    private Set<String> collectTopSourceNodeIds(Map<String, ImageElement> edgeMap) {
        Set<String> sources = new LinkedHashSet<>();
        Set<String> targets = new LinkedHashSet<>();
        for (ImageElement edge : edgeMap.values()) {
            if (edge.data == null || snapshotId.equals(edge.data.source)) {
                continue;
            }
            if (hasText(edge.data.source)) {
                sources.add(edge.data.source);
            }
            if (hasText(edge.data.target)) {
                targets.add(edge.data.target);
            }
        }
        sources.removeAll(targets);
        if (sources.isEmpty()) {
            sources.addAll(edgeMap.values().stream()
                    .filter(edge -> edge.data != null && !snapshotId.equals(edge.data.source))
                    .map(edge -> edge.data.source)
                    .filter(this::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        return sources;
    }

    private String firstValidNodeId() {
        for (StackNodeVo node : codeNodes) {
            if (isValidNode(node)) {
                return graphNodeId(node);
            }
        }
        return null;
    }

    private void addRuntimeSequenceEdges(Map<String, ImageElement> edgeMap) {
        String previousNodeId = null;
        Set<String> visitedNodeIds = new LinkedHashSet<>();
        for (StackNodeVo node : codeNodes) {
            if (!isValidNode(node)) {
                continue;
            }
            String currentNodeId = graphNodeId(node);
            if (!visitedNodeIds.add(currentNodeId)) {
                continue;
            }
            if (hasText(previousNodeId)) {
                addEdge(edgeMap, previousNodeId, currentNodeId, "runtime sequence", new String[]{"invoke", "runtime_sequence"});
            }
            previousNodeId = currentNodeId;
        }
    }

    private void addEdge(Map<String, ImageElement> edgeMap, String source, String target, String name, String[] classes) {
        if (!hasText(source) || !hasText(target) || source.equals(target)) {
            return;
        }
        ImageData edgeData = new ImageData(source + " ->" + name + "-> " + target);
        edgeData.source = source;
        edgeData.target = target;
        edgeData.name = name;
        ImageElement element = buildDefaultEdge(edgeData);
        element.classes = classes;
        edgeMap.putIfAbsent(edgeKey(source, target), element);
    }

    private String edgeKey(String source, String target) {
        return source + "\u0001" + target;
    }

    private boolean isValidNode(StackNodeVo node) {
        return node != null && hasText(node.getClassName()) && hasText(node.getMethodName());
    }

    private StaticInvokeTarget parseStaticInvokeTarget(String value) {
        if (!hasText(value)) {
            return null;
        }
        int separatorIndex = value.lastIndexOf('#');
        if (separatorIndex < 0) {
            return new StaticInvokeTarget(value.trim(), null);
        }
        return new StaticInvokeTarget(value.substring(0, separatorIndex).trim(), value.substring(separatorIndex + 1).trim());
    }

    private boolean methodNameMatches(String expected, String actual) {
        String normalizedExpected = normalizeMethodName(expected);
        String normalizedActual = normalizeMethodName(actual);
        return hasText(normalizedExpected) && normalizedExpected.equals(normalizedActual);
    }

    private String normalizeMethodName(String value) {
        if (!hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex > 0) {
            normalized = normalized.substring(0, spaceIndex);
        }
        int parenIndex = normalized.indexOf('(');
        if (parenIndex > 0) {
            normalized = normalized.substring(0, parenIndex);
        }
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0) {
            normalized = normalized.substring(dotIndex + 1);
        }
        return normalized;
    }

    private static class StaticInvokeTarget {
        private final String className;
        private final String methodName;

        private StaticInvokeTarget(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }
    }

    private StackNodeVo findNearestExistingParent(StackNodeVo node, Map<String, StackNodeVo> nodeByStackId) {
        if (node == null || !hasText(node.getId())) {
            return null;
        }
        String parentId = directParentStackId(node.getId());
        while (hasText(parentId)) {
            StackNodeVo parent = nodeByStackId.get(parentId);
            if (parent != null) {
                return parent;
            }
            parentId = directParentStackId(parentId);
        }
        return null;
    }

    private String graphNodeId(StackNodeVo node) {
        return ClassUtil.toClassName(node.getClassName()) + " " + node.getMethodName();
    }

    private String directParentStackId(String stackId) {
        if (!hasText(stackId) || "0".equals(stackId)) {
            return null;
        }
        int lastDot = stackId.lastIndexOf('.');
        return lastDot > 0 ? stackId.substring(0, lastDot) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
