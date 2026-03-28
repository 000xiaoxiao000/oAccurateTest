package com.oAT.agent.jacoco;

import com.oAT.agent.collect.HttpServletCollect;
import com.oAT.agent.collect.ServiceCollect;
import com.oAT.agent.collect.SofaServerCollect;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.context.AgentContext;
import com.oAT.agent.jacoco.data.StackNodeVoBuilder;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceSession;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class StackSession {
    private static final Log logger = LogFactory.getLog(StackSession.class);
    // 递归深度和最大节点数可通过 JVM 参数动态配置
    private static final int DEFAULT_MAX_RECURSION_DEPTH = 1;
    private static final int DEFAULT_MAX_SIZE = 99999;
    private static final int MAX_RECURSION_DEPTH;
    private static final int MAX_SIZE;

    private Object objectWrapper;

    static {
        String recursionDepthProp = System.getProperty("oAT.jacoco.stack.maxrecursiondepth");
        int recursionDepth = DEFAULT_MAX_RECURSION_DEPTH;
        if (recursionDepthProp != null) {
            try {
                recursionDepth = Integer.parseInt(recursionDepthProp);
            } catch (NumberFormatException e) {
                // ignore, use default
            }
        }
        MAX_RECURSION_DEPTH = recursionDepth;

        String maxSizeProp = System.getProperty("oAT.jacoco.stack.maxsize");
        int mSize = DEFAULT_MAX_SIZE;
        if (maxSizeProp != null) {
            try {
                mSize = Integer.parseInt(maxSizeProp);
            } catch (NumberFormatException e) {
                // ignore, use default
            }
        }
        MAX_SIZE = mSize;
    }

    // 线程组共享变量
    private static final Object DUMMY_OBJECT = new Object();

    // 添加递归深度控制
    private static final ThreadLocal<Integer> RECURSION_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private final StackNode rootNode;
    private StackNode hotNode;
    private int invokeCount = 0;
    private int nodeSize = 0;
    private int errorSize = 0;

    /**
     * 一个线程只能开启一次
     */
    public StackSession(String originClass, String originMethod, Object objectWrapper) {
        if (StackSession.getCurrent() != null) {
//            throw new RuntimeException("[Agent-EXCError]当前 StackSession 已存在，不再创建。");
            logger.warn("[Agent-warn]当前 StackSession 已存在，不再创建。");
            StackSession current = StackSession.getCurrent();
            boolean rootNodesNotNull = current.getRootNode() != null;

            this.invokeCount = 1;
            this.rootNode = new StackNode(-1L, originClass, originMethod, "");
            this.rootNode.id = "0";
            this.rootNode.setBeginTime(System.nanoTime());
            this.hotNode = rootNode;

            RECURSION_DEPTH.set(0);
            this.objectWrapper = objectWrapper;

            logger.warn("[Agent-warn]当前 StackSession 已存在，不再创建。rootNode 不为空: " + rootNodesNotNull);
            return;
        }
        this.invokeCount = 1;
        this.rootNode = new StackNode(-1L, originClass, originMethod, "");
        this.hotNode = this.rootNode;
        this.rootNode.id = "0";
        this.rootNode.setBeginTime(System.nanoTime());

        // 初始化递归深度
        RECURSION_DEPTH.set(0);
        // TraceNodeWrapper，如 HttpServletTraceNodeWrapper, SofaRpcRemoteTraceNodeWrapper 等
        this.objectWrapper = objectWrapper;

        AgentContext.setStackSession(this);
    }

    /**
     * 关闭会话，释放资源
     */
    public void close() {
        if (getCurrent() == this) {
            AgentContext.removeStackSession();
            // 清理递归深度记录
            RECURSION_DEPTH.remove();
        } else {
            throw new RuntimeException("[Agent-EXCError]当前 StackSession 与本实例不一致，无法关闭会话。");
        }
    }

    public static Object $begin(long classId, String originClassName, String originMethodName,
                                String originMethodDesc, int execMethodLineNumber, String totalMethods,
                                String lineInMethodCounts, String totalBranches, int cyclomatic,
                                boolean isRecursive, boolean isAsync) {
        // 检查递归深度
        Integer currentDepth = RECURSION_DEPTH.get();
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            return DUMMY_OBJECT;
        }
        if (isRecursive) {
            RECURSION_DEPTH.set(currentDepth + 1);
        }

        StackSession session = StackSession.getCurrent();

        // 非递归且没有 session 时才创建新会话
        if (session == null) {
            return DUMMY_OBJECT;
        }
        StackNode node = createAndInitStackNode(session, classId, originClassName, originMethodName, originMethodDesc,
                execMethodLineNumber, totalMethods, lineInMethodCounts, totalBranches, cyclomatic, isRecursive,
                isAsync);
        if (node == null) {
            return DUMMY_OBJECT;
        }
        return node;
    }

    // 创建并初始化 StackNode
    private static StackNode createAndInitStackNode(StackSession session, long classId, String originClassName,
                                                    String originMethodName, String originMethodDesc,
                                                    int execMethodLineNumber, String totalMethods,
                                                    String lineInMethodCounts,
                                                    String totalBranches, int cyclomatic, boolean isRecursive,
                                                    boolean isAsync) {
        StackNode stackNode = new StackNode(classId, originClassName, originMethodName, originMethodDesc);
        StackNode node = session.addNode(stackNode);
        if (node == null) {
            return null;
        }
        node.setExecuteMethodTotal(Collections.singletonList(execMethodLineNumber));
        node.setMethodTotal(totalMethods);
        node.setLineTotal(lineInMethodCounts);
        node.setBranchTotal(totalBranches);
        node.setCyclo(cyclomatic);
        node.setRecursive(isRecursive);
        node.setAsync(isAsync);
        return node;
    }

    public static void $end(Object stackNode, String executeBranches, boolean isRecursive) {
        // 如果是递归调用，减少深度计数
        if (isRecursive) {
            Integer currentDepth = RECURSION_DEPTH.get();
            if (currentDepth > 0) {
                RECURSION_DEPTH.set(currentDepth - 1);
            }
            return;
        }
        StackSession session = StackSession.getCurrent();

        if (session != null && stackNode instanceof StackNode) {
            StackNode node = (StackNode) stackNode;
            // 执行到的分支代码行
            node.setExecuteBranch(executeBranches);
            Map<Integer, String> executeConditionMap = node.getExecuteConditionMap();
            node.setExecuteCondition(executeConditionMap.toString());
            // 计算当前调用的时间
            long currentCallTime = System.nanoTime() - node.getBeginTime();
            // 对于递归节点，特殊处理
            node.setUseTime(node.getUseTime() + currentCallTime);

            session.doneNode(node);

            if (node.isAsync() && node.isDone()
                    && node.getDoLines() != null
                    && !node.getDoLines().isEmpty()) {
                session.doneSession();
                session.asyncNodeSaveAndUpload();
            }
        }
    }

    public static void $recordBranchCondition(Object stackNode, int branchLine, int conditionIdx,
                                              int totalConditions, boolean value,
                                              String executeBranchConditionNumbers, boolean isRecursive) {
        // 如果是递归调用且深度超过限制，直接返回
        if (isRecursive && RECURSION_DEPTH.get() >= MAX_RECURSION_DEPTH) {
            return;
        }

        if (stackNode instanceof StackNode) {
            StackNode node = (StackNode) stackNode;

            // 执行到的分支代码行和第几个条件分支
            Map<Integer, String> executeConditionMap = node.getExecuteConditionMap();
            executeConditionMap.put(branchLine, executeBranchConditionNumbers);
            node.setExecuteConditionMap(executeConditionMap);

            try {
                String branchLineStr = String.valueOf(branchLine);
                Map<String, List<List<String>>> execBranchConditionIsTrue = node.getExecBranchConditionIsTrue();
                if (execBranchConditionIsTrue == null) {
                    execBranchConditionIsTrue = new ConcurrentHashMap<String, List<List<String>>>(8);
                    node.setExecBranchConditionIsTrue(execBranchConditionIsTrue);
                }

                // 获取或创建该分支行的条件列表
                List<List<String>> conditionList = execBranchConditionIsTrue.get(branchLineStr);
                if (conditionList == null) {
                    List<List<String>> newList = new ArrayList<List<String>>();
                    List<List<String>> existing = null;
                    if (execBranchConditionIsTrue instanceof ConcurrentMap) {
                        existing =
                                ((ConcurrentMap<String, List<List<String>>>) execBranchConditionIsTrue).putIfAbsent(branchLineStr, newList);
                    } else {
                        synchronized (execBranchConditionIsTrue) {
                            if (!execBranchConditionIsTrue.containsKey(branchLineStr)) {
                                execBranchConditionIsTrue.put(branchLineStr, newList);
                            } else {
                                existing = execBranchConditionIsTrue.get(branchLineStr);
                            }
                        }
                    }
                    conditionList = (existing != null) ? existing : newList;
                }

                // 每个分支行只有一个条件组（索引0）
                // 如果还没有条件组，创建一个
                if (conditionList.isEmpty()) {
                    List<String> conditionGroup = new ArrayList<String>(Collections.nCopies(totalConditions, "false"));
                    conditionList.add(conditionGroup);
                }

                // 获取唯一的条件组（索引0）
                List<String> conditionGroup = conditionList.get(0);

                // 确保条件组大小正确
                if (conditionGroup.size() != totalConditions) {
                    // 调整条件组大小
                    List<String> newConditionGroup = new ArrayList<>(Collections.nCopies(totalConditions, "false"));
                    // 复制已有的值
                    int copyCount = Math.min(conditionGroup.size(), totalConditions);
                    for (int i = 0; i < copyCount; i++) {
                        newConditionGroup.set(i, conditionGroup.get(i));
                    }
                    conditionList.set(0, newConditionGroup);
                    conditionGroup = newConditionGroup;
                }

                // 更新特定条件的值（conditionIdx从1开始，表示条件在组内的位置）
                if (conditionIdx >= 1 && conditionIdx <= totalConditions) {
                    conditionGroup.set(conditionIdx - 1, value ? "true" : "false");
                }
            } catch (Throwable e) {
                logger.error("[Agent-EXCError] recordBranchCondition异常: " +
                        StackTraceFormatter.formatExceptionWithAgentMark(e));
            }
        }
    }

    // 异步保存并上传节点
    private void asyncNodeSaveAndUpload() {
        StackSession stackSession = StackSession.getCurrent();
        if (stackSession == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug]StackSession 当前线程不存在，异步节点数据未能保存与上传");
            }
            return;
        }
        StackNode rootNode = stackSession.getRootNode();
        if (rootNode == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug]rootNode 为空，异步节点数据未能保存与上传");
            }
            return;
        }
        TraceSession traceSession = TraceContext.getCurrentTraceSession();
        if (traceSession == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug]TraceSession 当前线程不存在，异步节点数据未能保存与上传");
            }
            return;
        }
        if (objectWrapper == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug]objectWrapper 为空，异步节点数据未能保存与上传");
            }
            return;
        }
        try {
            StackNodeVoBuilder stackNodeVoBuilder = new StackNodeVoBuilder();
            // HttpServlet 场景
            if (objectWrapper instanceof HttpServletCollect.HttpServletTraceNodeWrapper) {
                HttpServletCollect.HttpServletTraceNodeWrapper httpWrapper =
                        (HttpServletCollect.HttpServletTraceNodeWrapper) objectWrapper;
                httpWrapper.getHttpTraceNode().setCodeNodes(stackNodeVoBuilder.buildCodeNodes(this));
                traceSession.saveNode(httpWrapper.getHttpTraceNode());
                if (StackSession.getCurrent() == this) {
                    httpWrapper.doDestroy();
                }
                return;
            }
            // Sofa RPC Provider 场景
            if (objectWrapper instanceof SofaServerCollect.SofaRpcRemoteTraceNodeWrapper) {
                SofaServerCollect.SofaRpcRemoteTraceNodeWrapper sofaWrapper =
                        (SofaServerCollect.SofaRpcRemoteTraceNodeWrapper) objectWrapper;
                sofaWrapper.getSofaRpcRemoteTraceNode().setCodeNodes(stackNodeVoBuilder.buildCodeNodes(this));
                traceSession.saveNode(sofaWrapper.getSofaRpcRemoteTraceNode());
                if (StackSession.getCurrent() == this) {
                    sofaWrapper.doDestroy();
                }
                return;
            }
            if (objectWrapper instanceof ServiceCollect.ServiceTraceNodeWrapper) {
                ServiceCollect.ServiceTraceNodeWrapper serviceWrapper =
                        (ServiceCollect.ServiceTraceNodeWrapper) objectWrapper;
                serviceWrapper.getServiceTraceNode().setCodeNodes(stackNodeVoBuilder.buildCodeNodes(this));
                traceSession.saveNode(serviceWrapper.getServiceTraceNode());
                if (StackSession.getCurrent() == this) {
                    serviceWrapper.doDestroy();
                }
                return;
            }
            // 未识别的 wrapper 类型
            if (logger.isDebugEnabled()) {
                logger.debug("[Agent-debug] asyncNodeSaveAndUpload 未识别的 objectWrapper 类型: " + objectWrapper.getClass().getName());
            }
        } catch (Throwable e) {
            logger.error("[Agent-EXCError] asyncNodeSaveAndUpload 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
    }


    /**
     * 添加节点
     *
     * @param node
     * @return
     */
    public StackNode addNode(StackNode node) {
        if (node == null) {
            logger.debug("[Agent-debug]node is null");
            return null;
        }
        if (hotNode == null) {
            logger.debug("[Agent-debug]hotNode is null");
            return null;
        }

        // 防止自引用或环
        if (node == hotNode || isAncestor(node, hotNode)) {
            return null;
        }

        invokeCount++;
        if (nodeSize >= MAX_SIZE) {
            return null;
        }

        boolean exist = false;
        StackNode existingNode = null;

        // 直接遍历即可
        for (StackNode child : hotNode.childs) {
            if (child == null) {
                logger.debug("[Agent-debug]child is null");
                continue;
            }
            if (child.getClassName().equals(node.getClassName())
                    && child.getMethodName().equals(node.getMethodName())
                    && child.getMethodDesc().equals(node.getMethodDesc())) {
                existingNode = child;
                exist = true;
                break;
            }
        }

        if (exist) {
            node = existingNode;
            // 安全地增加节点大小
            synchronized (node) {
                node.size++;
            }
        } else {
            nodeSize++;
            // 直接添加，CopyOnWriteArrayList 是线程安全的
            hotNode.childs.add(node);
            node.stackSession = this;
            node.id = hotNode.id + "." + (hotNode.childs.size()); // 注意：这里可能需要更准确的计数
            node.size = 1;
        }

        node.parent = hotNode;
        hotNode = node;
        node.setBeginTime(System.nanoTime());
        return node;
    }

    // 判断 node 是否为 hotNode 的祖先，防止环
    private boolean isAncestor(StackNode node, StackNode current) {
        StackNode p = current.parent; // 从父节点开始检查
        int depth = 0;
        int maxDepth = 20; // 防止无限循环的安全限制

        while (p != null && depth < maxDepth) {
            if (p == node) return true;
            p = p.parent;
            depth++;
        }
        return false;
    }

    public static StackSession getCurrent() {
        return AgentContext.getStackSession();
    }

    public void doneNode(StackNode node) {
        node.done = true;
        // 确保 hotNode 正确回退到父节点
        if (node.parent != null) {
            hotNode = node.parent;
        } else {
            hotNode = rootNode;
        }
    }

    /**
     * 整个采集会话完成
     */
    public void doneSession() {
        rootNode.done = true;
        rootNode.setUseTime(System.nanoTime() - rootNode.getBeginTime());
    }

    public StackNode getRootNode() {
        return rootNode;
    }

    public StackNode getHotStack() {
        return hotNode;
    }

    protected StackNode setHotStack(StackNode hot) {
        return hotNode = hot;
    }

    public void printStack(PrintStream out) {
        // iterative printing to avoid recursion
        printIterative(rootNode, out);
    }

    // iterative pre-order traversal printing using identity-based visited set
    private void printIterative(StackNode node, PrintStream out) {
        if (node == null) return;
        // 使用 ConcurrentHashMap 作为 visited 集合的基础
        Set<StackNode> visited = Collections.newSetFromMap(new ConcurrentHashMap<StackNode, Boolean>());
        Deque<StackNode> stack = new ArrayDeque<StackNode>();
        stack.push(node);

        while (!stack.isEmpty()) {
            StackNode n = stack.pop();
            if (visited.contains(n)) {
                continue;
            }
            visited.add(n);
            out.println(n);

            // 直接获取子节点，不再需要同步块
            if (n.childs.isEmpty()) {
                continue;
            }

            // 将子节点逆序压入栈中
            for (int i = n.childs.size() - 1; i >= 0; i--) {
                StackNode c = n.childs.get(i);
                if (c != null && !visited.contains(c)) {
                    stack.push(c);
                }
            }
        }
    }

    public List<StackNode> getAllNodes() {
        List<StackNode> result = new ArrayList<StackNode>(nodeSize);
        result.add(rootNode);
        // 使用线程安全的 IdentityHashMap
        Set<StackNode> visited = Collections.newSetFromMap(new ConcurrentHashMap<StackNode, Boolean>());
        putNodes(rootNode, result, visited);
        return result;
    }

    private void putNodes(StackNode parent, List<StackNode> list, Set<StackNode> visited) {
        if (parent == null || visited.contains(parent)) {
            return;
        }

        visited.add(parent);

        // 直接访问，不再需要同步块
        if (parent.childs.isEmpty()) {
            return;
        }

        // 使用栈进行 DFS 遍历
        Deque<StackNode> stack = new ArrayDeque<>();
        for (int i = parent.childs.size() - 1; i >= 0; i--) {
            StackNode child = parent.childs.get(i);
            if (child != null && !visited.contains(child)) {
                stack.push(child);
            }
        }

        while (!stack.isEmpty()) {
            StackNode node = stack.pop();
            if (visited.contains(node)) {
                continue;
            }

            visited.add(node);
            list.add(node);

            // 直接访问子节点
            if (node.childs.isEmpty()) {
                continue;
            }

            // 将子节点按逆序压入栈中
            for (int i = node.childs.size() - 1; i >= 0; i--) {
                StackNode child = node.childs.get(i);
                if (child != null && !visited.contains(child)) {
                    stack.push(child);
                }
            }
        }
    }

}
