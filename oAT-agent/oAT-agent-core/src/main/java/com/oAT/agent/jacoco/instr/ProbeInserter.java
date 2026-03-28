/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainsides GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package com.oAT.agent.jacoco.instr;

import com.oAT.agent.Agent;
import com.oAT.agent.common.JsonUtil;
import com.oAT.agent.common.WildcardMatcher;
import com.oAT.agent.jacoco.StackSession;
import com.oAT.shaded.asm97.Label;
import com.oAT.shaded.asm97.MethodVisitor;
import com.oAT.shaded.asm97.Opcodes;
import com.oAT.shaded.asm97.Type;

import java.util.*;

/**
 * Internal utility to add probes into the control flow of a method. The code
 * for a probe simply sets a certain slot of a boolean array to true. In
 * addition, the probe array has to be retrieved at the beginning of the method
 * and stored in a local variable.
 */
class ProbeInserter extends MethodVisitor implements IProbeInserter {
    private final ClassInfo clazzInfo;

    /**
     * <code>true</code> if method is a class or interface initialization
     * method.
     */
    private final boolean clinit;

    /**
     * Position of the inserted variable.
     */
    private final int variable;

    private final WildcardMatcher methodIncludes;
    private final WildcardMatcher methodExcludes;

    private final Long clazzId;
    private final String clazzName;
    private final String methodName;
    private final String methodDesc;
    private final String methodNameDescCombined;

    private final Map<String, Set<Integer>> methodLineNumberMap; // 方法总数，用于方法覆盖率统计
    private final Map<String, Boolean> recursiveMap;  // 方法是否递归调用
    private final Map<String, Boolean> asyncMethodMap;  // 方法是否是异步

    //当前代码行数，用于行覆盖率；代码行数从-1开始
    private int currentLine = -1;
    // 避免未声明错误
    private int lastInsertedLine = Integer.MIN_VALUE;

    private int branchLine = 0; //记录方法中所有分支代码行号，用于分支条件覆盖率统计
    private Integer execBranchConditionNumber = 0;  //执行到的分支条件个数

    private final Label lastReturnLabel = new Label();

    private final Set<Integer> branchLines = new HashSet<>();  //记录方法中所有分支代码行号，用于分支覆盖率统计
    // 用于追踪同一逻辑表达式内的分支条件编号
    private final Map<Integer, Integer> branchLineConditionCounter = new HashMap<>();
    private final Map<Integer, Set<Integer>> execBranchLineAndConditionNumberMap = new HashMap<>(); //执行到的分支行数和条件个数
    private final Map<String, Integer> cyclomaticComplexity;  // 圈复杂度
    private static final String SESSION_CLASS_NAME;

    static {
        SESSION_CLASS_NAME = StackSession.class.getName().replaceAll("[.]", "/");
    }

/*
  插桩效果
  源码：
  25 public void hi(String hiName, int d) {
  26         int i=26;
  27         if(hiName.isEmpty()){
  28             int ii=28;
  29             System.out.println("hello_29\n");
  30             return;
  31         }
  32         int ii=32;
  33         int iii=33;
  34   }
  插桩后：
     public void hi(String hiName, int var2) {
          Object var3 = StackSession.$begin(8101662504613204910L, "test/coverage/Hello1", "hi1 (Ljava/lang/String;I)
          V");
          var3.equals(26);
          boolean var4 = true;
          var3.equals(26);
          var3.equals(27);
          boolean ii;
          if (hiName.isEmpty()) {
              var3.equals(28);
              ii = true;
              var3.equals(28);
              var3.equals(29);
              PrintStream var10000 = System.out;
              var3.equals(29);
              var10000.println("hello_29\n");
              var3.equals(30);
              StackSession.$end(var3, 8, 0);
          } else {
              var3.equals(32);
              ii = true;
              var3.equals(33);
              int iii = true;
              var3.equals(34);
          }
      }
 */

    /**
     * Creates a new {@link ProbeInserter}.
     *
     * @param access access flags of the adapted method
     * @param name   the method's name
     * @param desc   the method's descriptor
     * @param mv     the method visitor to which this adapter delegates calls
     */
    ProbeInserter(final int access, final String name, final String desc, final String signature,
                  final MethodVisitor mv, final ClassInfo classInfo) {
        super(InstrSupport.ASM_API_VERSION, mv);
        this.clinit = InstrSupport.CLINIT_NAME.equals(name);
        this.clazzId = classInfo.getClassId();
        this.clazzName = classInfo.getClassName();
        this.methodName = name;
        if (signature == null || desc != null) {
            this.methodNameDescCombined = name + " " + desc;
            this.methodDesc = desc;
        } else {
            this.methodNameDescCombined = name + " " + signature;
            this.methodDesc = signature;
        }
        this.clazzInfo = classInfo;
        int pos = (Opcodes.ACC_STATIC & access) == 0 ? 1 : 0;
        for (final Type t : Type.getArgumentTypes(desc)) {
            pos += t.getSize();
        }
        // 确保 variable 非负
        variable = Math.max(pos, 0);
        this.methodLineNumberMap = classInfo.getMethodLineNumberMap();
        this.cyclomaticComplexity = classInfo.getCyclomaticComplexityMap();
        this.recursiveMap = classInfo.getRecursiveMap();
        this.asyncMethodMap = classInfo.getAsyncMethodMap();

        // 方法包含表达式
        String methodIncludeExpr = Agent.traceContext.getConfig("codeStack.includeMethod");
        if (methodIncludeExpr == null) {
            methodIncludeExpr = Agent.traceContext.getConfig("conf_codeStack.includeMethod");
        }
        methodIncludes = new WildcardMatcher(methodIncludeExpr == null || methodIncludeExpr.isEmpty() ? "*" :
                methodIncludeExpr);

        // 方法排除表达式
        String methodExcludeExpr = Agent.traceContext.getConfig("codeStack.excludeMethod");
        if (methodExcludeExpr == null) {
            methodExcludeExpr = Agent.traceContext.getConfig("conf_codeStack.excludeMethod");
        }
        methodExcludes = new WildcardMatcher(methodExcludeExpr == null || methodExcludeExpr.isEmpty() ?
                "<init>&<clinit>&hashCode&toString&equals&Equal&canEqual&hashCode" :
                methodExcludeExpr);
    }

    // 方法过滤，包含则插桩
    private boolean codeStackMethodInclude() {
        return !methodIncludes.matches(this.methodName);
    }

    // 方法过滤，排除不插桩的方法，默认带这些的不插桩
    private boolean codeStackMethodExclude() {
        // 检查是否是显式的有参构造函数
        if ("<init>".equals(this.methodName) && this.methodDesc.contains("(") && !this.methodDesc.contains(
                "()")) {
            return false; // 显式有参构造函数需要插桩
        }
        return methodExcludes.matches(this.methodName);
    }

    @Override
    public void insertProbe(final int id) {
        // 提前过滤无需插桩的方法
        if (codeStackMethodExclude() || codeStackMethodInclude()) {
            return;
        }
        insertProbeToLine();
    }

    // 合并插桩点，递归体内部只在入口插入一次探针
    private void insertProbeToLine() {
        if (mv != null) {
            // 合并连续重复插桩点
            if (currentLine == lastInsertedLine || currentLine == -1) {
                return;
            }
            lastInsertedLine = currentLine;
            mv.visitVarInsn(Opcodes.ALOAD, variable);
            InstrSupport.push(mv, currentLine); // 确保push操作不会引入新的行号信息
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
            mv.visitInsn(Opcodes.POP);
        }
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        // Record the line number for each case
        branchLines.add(currentLine);
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
        branchLines.add(currentLine);
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    // 记录 catch 和 finally 块的入口 label
    private final Set<Label> catchLabels = new HashSet<>();
    private final Set<Label> finallyLabels = new HashSet<>();
    // 当前是否在 catch/finally 块
    private boolean inCatchBlock = false;
    private boolean inFinallyBlock = false;

    @Override
    public void visitTryCatchBlock(Label start, final Label end, final Label handler, final String type) {
        if (codeStackMethodExclude()) {
            super.visitTryCatchBlock(start, end, handler, type);
            return;
        }
        if (codeStackMethodInclude()) {
            super.visitTryCatchBlock(start, end, handler, type);
            return;
        }
        // 记录 catch/finally label
        if (type == null) {
            finallyLabels.add(handler); // finally 块
        } else {
            catchLabels.add(handler); // catch 块
        }
        super.visitTryCatchBlock(start, end, handler, type);
    }

    // 插入分支条件布尔值的插桩
    private void insertBranchConditionProbe(int branchLine, int conditionIndex, int totalConditions, boolean value) {
        if (mv != null) {
            Boolean isRecursive = recursiveMap.get(clazzName + " " + methodNameDescCombined);

            Map<Integer, Set<Integer>> execBranchLineAndConditionNumber = new HashMap<>();
            Set<Integer> conditionNumber = this.execBranchLineAndConditionNumberMap.get(branchLine);
            execBranchLineAndConditionNumber.put(branchLine,conditionNumber);

            mv.visitVarInsn(Opcodes.ALOAD, variable); // StackSession对象
            mv.visitLdcInsn(branchLine); // 分支行号
            mv.visitLdcInsn(conditionIndex); // 条件编号（从1开始）
            mv.visitLdcInsn(totalConditions); // 条件总数
            mv.visitLdcInsn(value); // 直接传boolean
            mv.visitLdcInsn(conditionNumber.toString());
            mv.visitLdcInsn(isRecursive);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SESSION_CLASS_NAME, "$recordBranchCondition",
                    "(Ljava/lang/Object;IIIZLjava/lang/String;Z)V", false);
        }
    }

    /**
     * 短路求值
     * if (user.getId() == 1 && "tester".equals(user.getName()))
     * "id": "1","name": "tester" 结果：ff 一样
     * "id": "2","name": "tester" 结果：tf
     * "id": "2","name": "tester2" 结果：tt
     * "id": "1","name": "tester2" 结果：ff 一样
     * if (user.getId() == 1 || "tester".equals(user.getName()))
     * "id": "1","name": "tester" 结果：tt
     * "id": "2","name": "tester" 结果：tf 一样
     * "id": "2","name": "tester2" 结果：ff
     * "id": "1","name": "tester2" 结果：tf 一样
     * 判断路径都是3个
     * if(user.getId().equals(user.getAge()) && "tester".equals(user.getName()) && (user.getAge() >= 10 && user
     * .getAge() <= 40))
     * "id": "30","name": "tester","age": "30", 结果：tttt
     * "id": "5","name": "tester","age": "5", 结果：ttff
     * "id": "5","name": "tester2","age": "5", 结果：tfff 一样
     * "id": "41","name": "tester2","age": "41", 结果：tfff 一样
     * "id": "5","name": "tester2","age": "41", 结果：ffff
     * "id": "41","name": "tester","age": "41", 结果：tttf
     * 判断路径5个
     */
    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        if (codeStackMethodExclude()) {
            super.visitJumpInsn(opcode, label);
            return;
        }
        if (codeStackMethodInclude()) {
            super.visitJumpInsn(opcode, label);
            return;
        }

        // 只对条件跳转插桩（GOTO除外）
        if (opcode == Opcodes.GOTO) {
            super.visitJumpInsn(opcode, label);
            return;
        }

        // 记录当前分支行号
        this.branchLine = currentLine;
        this.branchLines.add(this.branchLine);
        this.execBranchLineAndConditionNumberMap.computeIfAbsent(this.branchLine, v -> {
            this.execBranchConditionNumber = 0;
            return new HashSet<>();
        }).add(this.execBranchConditionNumber += 1);

        // 统计当前行的分支条件数
        Set<Integer> conds = this.clazzInfo.getBranchLineAndConditionNumberMap().get(this.branchLine);
        int totalConds = (conds != null) ? conds.size() : 0;
        if (totalConds == 0) totalConds = 1; // 至少1个

        // 维护每行的条件编号
        int conditionIdx = branchLineConditionCounter.getOrDefault(this.branchLine, 0) + 1;
        branchLineConditionCounter.put(this.branchLine, conditionIdx);

        // 插入true分支探针
        Label jumpTaken = new Label();
        Label continuation = new Label();

        // 原跳转指令 -> 跳转到 jumpTaken
        super.visitJumpInsn(opcode, jumpTaken);

        // Fallthrough (False branch)
        insertBranchConditionProbe(this.branchLine, conditionIdx, totalConds, false);
        super.visitJumpInsn(Opcodes.GOTO, continuation);

        // Jump Taken (True branch)
        super.visitLabel(jumpTaken);
        insertBranchConditionProbe(this.branchLine, conditionIdx, totalConds, true);
        super.visitJumpInsn(Opcodes.GOTO, label);

        // Continuation
        super.visitLabel(continuation);
    }

    @Override
    public void visitInsn(final int opcode) {
        if (codeStackMethodExclude()) {
            super.visitInsn(opcode);
            return;
        }
        if (codeStackMethodInclude()) {
            super.visitInsn(opcode);
            return;
        }
        // 检查是否是返回指令
        if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
            if (inCatchBlock) {
                insertEndProbe();
                inCatchBlock = false;
            } else if (inFinallyBlock) {
                insertEndProbe();
                inFinallyBlock = false;
            } else {
                insertEndProbe();
            }
        }
        super.visitInsn(opcode);
    }

    /**
     * 在返回return指令处插入插桩代码
     * 举例代码：
     * 1 public void hi(String hiName, int d) {
     * 2         int i=2;
     * 3         if(hiName.isEmpty()){
     * 4             int ii=4;
     * 5             System.out.println("hello_5\n");
     * 6             return;
     * 7         }
     * 8         int ii=8;
     * 9         int iii=9;
     * 10   }
     * count从第二行开始计数，累计到第九行，共7行，
     * 注：1、注释、空格、括号不做统计；
     * 2、由于void最后有一个默认return，所以是累计共8行
     */
    private void insertEndProbe() {
        if (mv != null) {
            String branchLinesToJson = JsonUtil.toJson(this.branchLines);
            Boolean isRecursive = recursiveMap.get(clazzName + " " + methodNameDescCombined);

            mv.visitVarInsn(Opcodes.ALOAD, variable);
            mv.visitLdcInsn(branchLinesToJson);
            mv.visitLdcInsn(isRecursive);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SESSION_CLASS_NAME, "$end",
                    "(Ljava/lang/Object;Ljava/lang/String;Z)V", false);
        }
    }

    @Override
    public void visitLabel(Label label) {
        if (codeStackMethodExclude()) {
            super.visitLabel(label);
            return;
        }
        if (codeStackMethodInclude()) {
            super.visitLabel(label);
            return;
        }
        // 进入 catch/finally 块
        if (catchLabels.contains(label)) {
            inCatchBlock = true;
        } else if (finallyLabels.contains(label)) {
            inFinallyBlock = true;
        }
        if (label == lastReturnLabel) {
            insertEndProbe();
        }
        super.visitLabel(label);
    }

//    @Override
//    public void visitLdcInsn(final Object cst) {
//        mv.visitVarInsn(Opcodes.ALOAD, variable);
//        InstrSupport.push(mv, currentLine);
//        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
//        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z", false);
//        mv.visitInsn(Opcodes.POP);
//
//        super.visitLdcInsn(cst);
//    }

    //方法体的开始
    @Override
    public void visitCode() {
        if (codeStackMethodExclude() || codeStackMethodInclude()) {
            super.visitCode();
            return;
        }

        // 初始当前方法索引及探针数
        if (mv != null) {
            // 类中所有方法行号
            int[] lineNumbers;
            List<Integer> tempList = new ArrayList<Integer>(methodLineNumberMap.size());
            for (Set<Integer> lineNumberSet : methodLineNumberMap.values()) {
                if (lineNumberSet != null && !lineNumberSet.isEmpty()) {
                    tempList.add(Collections.min(lineNumberSet));
                }
            }
            int lineNumbersSize = tempList.size();
            lineNumbers = new int[lineNumbersSize];
            for (int i = 0; i < lineNumbersSize; i++) {
                lineNumbers[i] = tempList.get(i);
            }

            // 方法中代码的总行数，用于计算行覆盖率
            int[] lineNumberTotals;
            Set<Integer> tempSet = new HashSet<Integer>();
            String targetMethodNameDesc = this.clazzName + " " + this.methodNameDescCombined;
            methodLineNumberMap.forEach((key, value) -> {
                String[] split = key.split(" ");
                String methodNameDescStr = String.join(" ", Arrays.copyOfRange(split, 0, split.length));
                if (targetMethodNameDesc.equals(methodNameDescStr)) {
                    tempSet.addAll(value);
                }
            });
            lineNumberTotals = tempSet.stream().mapToInt(Integer::intValue).toArray();

            //类中所有分支号分配到逐个方法中的分支行号
            int[] totalBranches;
            List<Integer> totalBranchList = new ArrayList<Integer>();
            for (Map.Entry<String, Integer> entry : this.clazzInfo.getTotalBranchMap().entrySet()) {
                String[] split = entry.getKey().split(" ");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < split.length - 1; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(split[i]);
                }
                String methodNameDescStr = sb.toString();
                if (methodNameDescCombined.equals(methodNameDescStr)) {
                    totalBranchList.add(entry.getValue());
                }
            }
            totalBranches = new int[totalBranchList.size()];
            for (int i = 0; i < totalBranchList.size(); i++) {
                totalBranches[i] = totalBranchList.get(i);
            }

            // 方法圈复杂度；记录圈复杂度
            int cyclo = cyclomaticComplexity.containsKey(this.methodNameDescCombined) ?
                    cyclomaticComplexity.get(this.methodNameDescCombined) : 0;

            int execMethodLineNumber = -1;
            for (Map.Entry<String, Set<Integer>> entry : methodLineNumberMap.entrySet()) {
                String methodNameDescStr = entry.getKey();
                if (targetMethodNameDesc.equals(methodNameDescStr) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    execMethodLineNumber = Collections.min(entry.getValue());
                    break;
                }
            }

            String totalMethodsToJson = JsonUtil.toJson(lineNumbers);    // 类中方法总数
            String lineInMethodCountToJson = JsonUtil.toJson(lineNumberTotals);  // 某方法中代码总行数
            String totalBranchesToJson = JsonUtil.toJson(totalBranches);   // 方法中所有分支代码行数

            Boolean isRecursive = recursiveMap.get(targetMethodNameDesc);
            Boolean isAsyncMethod = asyncMethodMap.get(clazzName + " " + methodNameDescCombined);

            mv.visitLdcInsn(this.clazzId);
            mv.visitLdcInsn(this.clazzName);
            mv.visitLdcInsn(this.methodName);
            mv.visitLdcInsn(this.methodDesc);
            mv.visitLdcInsn(execMethodLineNumber);

            mv.visitLdcInsn(totalMethodsToJson);
            mv.visitLdcInsn(lineInMethodCountToJson);
            mv.visitLdcInsn(totalBranchesToJson);

            mv.visitLdcInsn(cyclo);
            mv.visitLdcInsn(isRecursive);
            mv.visitLdcInsn(isAsyncMethod);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SESSION_CLASS_NAME, "$begin",
                    "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;I" +
                            "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IZZ)Ljava/lang/Object;",
                    false);
            mv.visitVarInsn(Opcodes.ASTORE, variable);
        }
        super.visitCode();
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        this.currentLine = line;
        lastInsertedLine = Integer.MIN_VALUE;
        super.visitLineNumber(line, start);
    }

    @Override
    public final void visitVarInsn(final int opcode, final int var) {
        mv.visitVarInsn(opcode, map(var));
    }

    @Override
    public final void visitIincInsn(final int var, final int increment) {
        mv.visitIincInsn(map(var), increment);
    }

    @Override
    public final void visitLocalVariable(final String name, final String desc, final String signature,
                                         final Label start, final Label end, final int index) {
        mv.visitLocalVariable(name, desc, signature, start, end, map(index));
    }

    @Override
    public void visitMaxs(final int maxStack, final int maxLocals) {
        // Max stack size of the probe code is 3 which can add to the
        // original stack size depending on the probe locations. The accessor
        // stack size is an absolute maximum, as the accessor code is inserted
        // at the very beginning of each method when the stack size is empty.
        /*
          Maximum stack usage of the code to access the probe array.
         */
        int accessorStackSize = 4;
        int calculatedStack = maxStack + 3;
        final int increasedStack = Math.max(calculatedStack, accessorStackSize); // 确保非负
        mv.visitMaxs(increasedStack, maxLocals + 1);
    }

    private int map(final int var) {
        if (var < variable) {
            return var;
        } else {
            return var + 1;
        }
    }

    @Override
    public final void visitFrame(final int type, final int nLocal, final Object[] local, final int nStack,
                                 final Object[] stack) {
        // uncompressed frame
        if (type != Opcodes.F_NEW) {
            throw new IllegalArgumentException("ClassReader.accept() should be called with EXPAND_FRAMES flag");
        }

        // 计算新局部变量数组的长度
        // 需要考虑插入的探针变量（占1个slot）以及原有变量中LONG/DOUBLE类型占2个slot的情况
        int newLocalLen;
        if (variable <= nLocal) {
            // 探针插入位置在已有局部变量范围内，需要增加一个slot
            newLocalLen = nLocal + 1;
        } else {
            // 探针插入位置超出原有局部变量范围，需要扩展到variable+1
            newLocalLen = variable + 1;
        }

        // 确保长度非负
        newLocalLen = Math.max(newLocalLen, 0);

        final Object[] newLocal = new Object[newLocalLen];

        // 复制原有局部变量，并在指定位置插入探针变量
        int oldIdx = 0;     // 原局部变量数组索引
        int newIdx = 0;     // 新局部变量数组索引
        int currentSlot = 0; // 当前处理的slot位置

        // 遍历直到处理完所有原有变量和探针插入位置
        while (oldIdx < nLocal || currentSlot <= variable) {
            if (currentSlot == variable) {
                // 插入探针变量
                newLocal[newIdx++] = InstrSupport.DATAFIELD_DESC;
                currentSlot++;
            } else {
                if (oldIdx < nLocal) {
                    // 复制原有变量
                    final Object t = local[oldIdx++];
                    newLocal[newIdx++] = t;
                    currentSlot++;
                    if (t == Opcodes.LONG || t == Opcodes.DOUBLE) {
                        currentSlot++; // LONG和DOUBLE占两个slot
                    }
                } else {
                    // 填充未使用的slot为TOP
                    newLocal[newIdx++] = Opcodes.TOP;
                    currentSlot++;
                }
            }
        }

        mv.visitFrame(type, newIdx, newLocal, nStack, stack);
    }
}
