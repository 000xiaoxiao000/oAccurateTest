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

    public StackCodeLayer(StackNodeVo[] codeNodes, String snapshotId, Map<String, Map<String, StaticSourceMethodInfo>> staticMethodLookup) {
        this.codeNodes = codeNodes != null ? codeNodes : new StackNodeVo[0];
        this.snapshotId = snapshotId;
        this.staticMethodLookup = staticMethodLookup != null ? staticMethodLookup : Collections.emptyMap();
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

        // 添加入口与代码节点之间的调用关系；缺失中间父节点时连接到最近存在的祖先，避免全部退化为 root 连线。
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
            if (node == null || !hasText(node.getClassName()) || !hasText(node.getMethodName())) {
                continue;
            }
            StackNodeVo parent = findNearestExistingParent(node, nodeByStackId);
            String source = parent == null ? snapshotId : graphNodeId(parent);
            String target = graphNodeId(node);
            if (!hasText(source) || !hasText(target) || source.equals(target)) {
                continue;
            }

            ImageData edgeData = new ImageData(source + " ->invoke-> " + target);
            edgeData.source = source;
            edgeData.target = target;
            edgeData.name = parent == null ? "entry" : "invoke";
            ImageElement element = buildDefaultEdge(edgeData);
            element.classes = parent == null ? new String[]{"start_invoke", "invoke"} : new String[]{"invoke"};
            edgeMap.putIfAbsent(edgeData.source + "\u0001" + edgeData.target, element);
        }
        return new ArrayList<>(edgeMap.values());
    }

    private StackNodeVo findNearestExistingParent(StackNodeVo node, Map<String, StackNodeVo> nodeByStackId) {
        if (node == null || !hasText(node.getId())) {
            return null;
        }
        String parentId = directParentStackId(node.getId());
        while (hasText(parentId) && !"ROOT".equals(parentId)) {
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
