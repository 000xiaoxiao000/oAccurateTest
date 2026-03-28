package com.oAT.agent.jacoco;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class StackNode implements java.io.Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    public String id = null;
    private long classId = -1L;
    private String className = "";
    private String methodName = "";
    private String methodDesc="";

    /*
      行覆盖率（Statement/line Coverage）：测试用例执行的代码行数与总代码行数的比例。通常，行覆盖率的通过标准会设定在一定百分比以上，例如80%或更高。

      方法覆盖率（Function/method Coverage）：测试用例是否调用了代码中的每个函数。这个指标通常用来衡量测试用例是否全面覆盖了代码中的公共接口。

      分支覆盖率（Branch Coverage）：测试用例执行的代码分支数与总分支数的比例。这个比例通常也需要达到一个特定的阈值，比如90%，才能认为通过。

      条件覆盖率（Condition Coverage）：测试用例是否覆盖了所有的条件判断，例如if语句中的每个条件都至少被评估为真和假一次。

      路径覆盖率（Path Coverage）：测试用例是否覆盖了所有可能的执行路径。这是最全面的覆盖率类型，但也是最难实现的，因为它要求测试用例覆盖代码中所有可能的执行路径。
     */

    /**
     * 执行代码行数，多行字节码被合为一行代码
     * 举例：
     * 代码行：System.out.println("line_12");
     * 字节码反编译后的代码：
     * PrintStream var10000 = System.out;
     * var10000.println("line_12");
     * 注：void方法会有一个默认的return语句，也算一条代码行
     */
    // 线程安全集合，适合高频写入
    private List<Integer> doLines = new CopyOnWriteArrayList<>();

    /**
     * 代码行数，方法中代码总行数
     * private ArrayList<Integer> lineTotal = new ArrayList<>(4);
     */
    private String lineTotal;

    //方法覆盖率
    //执行到的方法数
    private List<Integer> executeMethodTotal = new CopyOnWriteArrayList<>();
    //方法总数
//    private ArrayList<Integer> methodTotal = new ArrayList<>(4);
    private String methodTotal;

    //分支覆盖率
    //执行到的分支数, private ArrayList<Integer> executeBranch = new ArrayList<>(4);
    private String executeBranch;
    //所有分支行号, private ArrayList<Integer> branchTotal = new ArrayList<>(4);
    private String branchTotal;

    //条件覆盖率
    //执行到的条件数,map<分支行，第几个条件>, private Map<String, List<String>> executeCondition = new ConcurrentHashMap<>(8);
    // 分支中的执行到的条件
    private String executeCondition;   //分支中的执行到的条件

    /**
     * 分支中的执行到的条件,用于条件组合的覆盖
     * if(真),if(假),两种分别执行到，为全部覆盖，执行到其中一个为部分覆盖，最后还有没执行到为未覆盖
     * 通过执行多次的记录比对决定最后覆盖情况
     */
    private Map<String, List<List<String>>> execBranchConditionIsTrue = new ConcurrentHashMap<>(8);
    /**
     * 在实践中，圈覆盖率通常用于指导测试用例的设计，以确保测试尽可能覆盖更多的代码路径。理想情况下，圈覆盖率应该是100%，这意味着所有的独立路径都被测试用例覆盖到了。然而，在实际中，由于资源和时间的限制，可能无法达到100
     * %的圈覆盖率，因此测试团队需要根据实际情况来确定可接受的覆盖率水平。
     * 圈复杂度，V (G) = P + 1
     */
    //所有圈复杂度
    private String execCyclo = "0";    //方法中执行的圈复杂度
    private int cyclo = 0;  //方法中的圈复杂度

    private int invocationCount; // 调用次数计数器

    /**
     * 是否是递归方法
     */
    private boolean isRecursive = false;    // 本节点是递归方法
    /**
     * 是否是异步方法
     */
    private boolean isAsync = false;    // 本节点是异步方法

    /**
     * 节点大小
     */
    protected int size;
    public boolean done;
    /**
     * nano-time
     */
    private Long useTime = 0L;

    /**
     * 以下字段在序列化时将被勿略
     */
    private transient Long beginTime;
    public transient StackNode parent;
    public final transient List<StackNode> childs = new CopyOnWriteArrayList<>();
    public transient StackSession stackSession;
    private transient Map<Integer, String> executeConditionMap = new ConcurrentHashMap<>();

    public StackNode() {
    }

    public StackNode(Long classId, String className, String methodName,String methodDesc) {
        super();
        this.classId = classId;
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        size = 1;
    }

    // ========== Getter 和 Setter 方法 ==========

    public long getClassId() {
        return classId;
    }

    public List<Integer> getDoLines() {
        return doLines;
    }

    public void setLineTotal(String lineTotal) {
        this.lineTotal = lineTotal;
    }

    public String getLineTotal() {
        return lineTotal;
    }

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public void setMethodDesc(String methodDesc) {
        this.methodDesc = methodDesc;
    }

    public Long getUseTime() {
        return useTime;
    }

    public void setUseTime(Long useTime) {
        this.useTime = useTime;
    }

    public Long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Long beginTime) {
        this.beginTime = beginTime;
    }

    public Map<Integer, String> getExecuteConditionMap() {
        return executeConditionMap;
    }

    public void setExecuteConditionMap(Map<Integer, String> executeConditionMap) {
        this.executeConditionMap = executeConditionMap;
    }

    public boolean isDone() {
        return done;
    }

    @Override
    public boolean equals(Object obj) {
        try {
            if (obj instanceof Integer) {
                Integer lineNum = (Integer) obj;
                if (doLines == null) {
                    doLines = new CopyOnWriteArrayList<>();
                }
                if (doLines.isEmpty() || !doLines.contains(lineNum)) {
                    doLines.add(lineNum);
                }
                if (stackSession != null) {
                    stackSession.setHotStack(this);
                }
                return false;
            }
            return super.equals(obj);
        } catch (Throwable e) {
            // 记录异常但不抛出
            return false;
        }
    }


    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getCyclo() {
        return cyclo;
    }

    public void setCyclo(int cyclo) {
        this.cyclo = cyclo;
    }

    public String getExecCyclo() {
        return execCyclo;
    }

    public void setExecCyclo(String execCyclo) {
        this.execCyclo = execCyclo;
    }

    public List<Integer> getExecuteMethodTotal() {
        return executeMethodTotal;
    }

    public void setExecuteMethodTotal(List<Integer> executeMethodTotal) {
        this.executeMethodTotal.clear();
        if (executeMethodTotal != null) {
            this.executeMethodTotal.addAll(executeMethodTotal);
        }
    }

    public String getMethodTotal() {
        return methodTotal;
    }

    public void setMethodTotal(String methodTotal) {
        this.methodTotal = methodTotal;
    }

    public String getExecuteBranch() {
        return executeBranch;
    }

    public void setExecuteBranch(String executeBranch) {
        this.executeBranch = executeBranch;
    }

    public String getBranchTotal() {
        return branchTotal;
    }

    public void setBranchTotal(String branchTotal) {
        this.branchTotal = branchTotal;
    }

    public void setExecuteCondition(String executeCondition) {
        this.executeCondition = executeCondition;
    }

    public String getExecuteCondition() {
        return executeCondition;
    }

    public Map<String, List<List<String>>> getExecBranchConditionIsTrue() {
        if (execBranchConditionIsTrue == null) {
            execBranchConditionIsTrue = new ConcurrentHashMap<>(8);
        }
        return execBranchConditionIsTrue;
    }


    public void setExecBranchConditionIsTrue(Map<String, List<List<String>>> execBranchConditionIsTrue) {
        this.execBranchConditionIsTrue = execBranchConditionIsTrue;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public void setInvocationCount(int invocationCount) {
        this.invocationCount = invocationCount;
    }

    public boolean isRecursive() {
        return isRecursive;
    }

    public void setRecursive(boolean recursive) {
        isRecursive = recursive;
    }

    public boolean isAsync() {
        return isAsync;
    }

    public void setAsync(boolean async) {
        isAsync = async;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(128);

        if (id != null && !id.isEmpty()) {
            builder.append(id).append(" ");
        }
        if (!done) {
            builder.append("[DONE]");
        }
        if (className != null && !className.isEmpty()) {
            builder.append(className).append(".");
        }
        if (methodName != null && !methodName.isEmpty()) {
            builder.append(methodName).append("()");
        }

        appendCollection(builder, "lines", doLines);
        if (lineTotal != null && !lineTotal.isEmpty()) {
            builder.append(lineTotal);
        }
//        appendCollection(builder, "totalLines", lineTotal);
        appendCollection(builder, "execMethods", executeMethodTotal);
        if (methodTotal != null && !methodTotal.isEmpty()) {
            builder.append(methodTotal);
        }
//        appendCollection(builder, "methods", methodTotal);
        if (executeBranch != null && !executeBranch.isEmpty()) {
            builder.append(executeBranch);
        }
//        appendCollection(builder, "execBranches", executeBranch);
        if (branchTotal != null && !branchTotal.isEmpty()) {
            builder.append(branchTotal);
        }
//        appendCollection(builder, "branches", branchTotal);

//        appendMap(builder, "conditions", executeCondition);
        if (executeCondition != null && !executeCondition.isEmpty()) {
            builder.append(executeCondition);
        }
        appendMap(builder, "branchConditions", execBranchConditionIsTrue);

        if (execCyclo != null && !"0".equals(execCyclo)) {
            builder.append("[cyclo:").append(execCyclo).append("]");
        }
        if (cyclo != 0) {
            builder.append("[totalCyclo:").append(cyclo).append("]");
        }
        if (useTime != null && useTime != 0L) {
            builder.append(" time:").append(useTime).append("ns");
        }

        builder.append(" size:").append(size);
        if (isRecursive) builder.append(" [RECURSIVE]");
        if (isAsync) builder.append(" [ASYNC]");

        return builder.toString();
    }

    private void appendCollection(StringBuilder builder, String name, Collection<?> collection) {
        if (collection != null && !collection.isEmpty()) {
            builder.append("[").append(name).append(":");
            for (Object item : collection) {
                builder.append(item).append(",");
            }
            builder.setLength(builder.length() - 1);
            builder.append("]");
        }
    }

    private void appendMap(StringBuilder builder, String name, Map<?, ?> map) {
        if (map != null && !map.isEmpty()) {
            builder.append("[").append(name).append(":");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                builder.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
            }
            builder.setLength(builder.length() - 1);
            builder.append("]");
        }
    }
}
