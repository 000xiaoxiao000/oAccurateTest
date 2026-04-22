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

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.jacoco.data.CRC64;
import com.oAT.shaded.asm97.*;

import java.util.*;

/**
 * The strategy for regular classes adds a static field to hold the probe array
 * and a static initialization method requesting the probe array from the
 * runtime.
 */
public class ClassInfo {
    private final static Log logger = LogFactory.getLog(ClassInfo.class);
    private final long classId;
    private final String className;
    private String methodName;
    private String methodDesc;
    private final boolean withFrames;
    private final int version;
    private final boolean isInterface;
    // 方法索引 ，探针数
    private final Map<Integer, Integer> methodProbeSizes;
    private int[] methodProbes = new int[0];
    private final int count;

    /*
     * 方法对应的代码行号集合，用于方法覆盖率统计
     * Map<类+方法+desc, 行号>
     */
    private final Map<String, Set<Integer>> methodLineNumberMap = new HashMap<String, Set<Integer>>();

    /*
     * 类中所有分支总数，用于分支覆盖率统计
     * Map<方法+desc+行号（分支行号）, 行号（分支行号）>
     */
    public Map<String, Integer> totalBranchMap;
    /*
     * 每个方法的行号，可统计出方法的总数，用于方法覆盖率统计
     * 分支总数，用于分支覆盖率统计
     */
    public Map<String, Integer> totalBranches;
    /*
     * 分支代码行和条件个数
     * Map<行号, 条件个数集合>
     */
    private final Map<Integer, Set<Integer>> branchLineAndTargetProbeMap = new HashMap<Integer, Set<Integer>>();

    /*
     * 方法圈复杂度
     * Map<方法名+desc, 圈复杂度>
     */
    private final Map<String, Integer> cyclomaticComplexityMap = new HashMap<String, Integer>();  // 圈复杂度

    /*
     * 方法是否是递归
     * Map<类+方法+desc, 行号>
     */
    private final Map<String, Boolean> recursiveMap = new HashMap<String, Boolean>();

    /*
     * 方法是否是异步
     * Map<类+方法+desc, 行号>
     */
    private final Map<String, Boolean> asyncMethodMap = new HashMap<String, Boolean>();

    static boolean isAnonymousClassName(String internalClassName) {
        int dollarPosition = internalClassName == null ? -1 : internalClassName.lastIndexOf('$');
        if (dollarPosition < 0 || dollarPosition + 1 >= internalClassName.length()) {
            return false;
        }
        return Character.isDigit(internalClassName.charAt(dollarPosition + 1));
    }

    static boolean isCompilerGeneratedMethod(int access) {
        return (access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0;
    }

    public ClassInfo(final ClassReader reader) {
        className = reader.getClassName();
        methodName = "";
        methodDesc = "";
        methodProbeSizes = new HashMap<Integer, Integer>();
        version = InstrSupport.getVersion(reader);
        final int asmApiVersion = InstrSupport.getAsmApiVersion(version);
        // 检查版本号是否在支持范围内
        if (asmApiVersion < Opcodes.ASM5 || asmApiVersion > Opcodes.ASM9) {
            throw new IllegalArgumentException("非法的 ASM 版本号: " + asmApiVersion);
        }
        byte[] classBytecode = reader.b; // 保存字节码
        classId = CRC64.classId(classBytecode);
        withFrames = version >= Opcodes.V1_6;
        isInterface = InstrSupport.isInterface(reader);
        ProbeCounter probeCounter = null;
        try {
            probeCounter = InstrSupport.getProbeCounter(reader);
        } catch (Throwable e) {
            // 记录详细异常
            logger.error("[Agent-EXCError]构造异常: " + className + ", version: " + version + ", asmApiVersion: " + asmApiVersion);
            logger.error("[Agent-EXCError]异常信息: " + e.getMessage() + StackTraceFormatter.formatExceptionWithAgentMark(e));
        }
        if (probeCounter != null) {
            count = probeCounter.getCount();    // 类中所有探针数
            this.totalBranchMap = probeCounter.getTotalBranchMap(); // 记录分支总数
        } else {
            count = 0;
            this.totalBranchMap = new HashMap<>();
            this.totalBranches = new HashMap<String, Integer>();
        }
        try {
            reader.accept(new ClassVisitor(asmApiVersion) {
                private String owner;
                // 标记该类是否实现了 java.lang.Runnable
                private boolean implementsRunnable = false;

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public void visit(int version, int access, String name, String signature, String superName,
                                  String[] interfaces) {
                    owner = name;
                    // 检查是否实现了 Runnable 接口
                    if (interfaces != null) {
                        for (String intf : interfaces) {
                            if ("java/lang/Runnable".equals(intf)) {
                                implementsRunnable = true;
                                break;
                            }
                        }
                    }
                    // 如果继承自 Thread（Thread 实现了 Runnable），也视为实现了 Runnable
                    if ("java/lang/Thread".equals(superName)) {
                        implementsRunnable = true;
                    }
                }

                @Override
                public MethodVisitor visitMethod(int access, final String name, final String descriptor,
                                                 final String signature,
                                                 String[] exceptions) {
                    final Set<Integer> lineNumberSet = new HashSet<Integer>();
                    Set<String> skipMethods = new HashSet<String>(Arrays.asList("equals", "canEqual", "hashCode", "toString", "clone"));
                    boolean isInitWithParams = "<init>".equals(name) &&
                            ((descriptor != null && descriptor.contains("(") && !descriptor.contains("()")) ||
                                    (signature != null && signature.contains("(") && !signature.contains("()")));
                    boolean isAnonymousConstructor = "<init>".equals(name) && isAnonymousClassName(className);
                    boolean isCompilerGeneratedMethod = isCompilerGeneratedMethod(access);
                    boolean shouldCollectMethod = (!skipMethods.contains(name) || isInitWithParams)
                            && !isAnonymousConstructor
                            && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_INTERFACE)) == 0;

                    if (shouldCollectMethod) {
                        if (signature == null || descriptor != null) {
                            methodName = name;
                            methodDesc = descriptor;

                            methodLineNumberMap.putIfAbsent(className + " " + name + " " + descriptor, lineNumberSet);
                            recursiveMap.putIfAbsent(className + " " + name + " " + descriptor, false);
                            asyncMethodMap.putIfAbsent(className + " " + name + " " + descriptor, false);
                        } else {
                            methodName = name;
                            methodDesc = signature;

                            methodLineNumberMap.putIfAbsent(className + " " + name + " " + signature, lineNumberSet);
                            recursiveMap.putIfAbsent(className + " " + name + " " + signature, false);
                            asyncMethodMap.putIfAbsent(className + " " + name + " " + signature, false);
                        }
                    }

                    // 如果当前类实现了 Runnable，默认把它的 run 方法标记为异步（很多情况下 Runnable 的 run 会在异步线程执行）
                    if (implementsRunnable && "run".equals(name) && !isCompilerGeneratedMethod
                            && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                        if (signature == null || descriptor != null) {
                            asyncMethodMap.put(className + " " + name + " " + descriptor, true);
                        } else {
                            asyncMethodMap.put(className + " " + name + " " + signature, true);
                        }
                    }

                    return new MethodVisitor(asmApiVersion) {
                        private int currentLine = -1;   // 当前代码行
                        private int decisionPoints = 0; // 判定节点数
                        private final Map<Integer, Integer> branchLineTargetCounters = new HashMap<>();

                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            return super.visitAnnotation(descriptor, visible);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String ownerCalled, String nameCalled,
                                                    String descCalled, boolean itf) {
                            String currentMethodDesc;
                            if (signature == null || descriptor != null) {
                                currentMethodDesc = descriptor;
                            } else {
                                currentMethodDesc = signature;
                            }
                            String methodKey = name + " " + currentMethodDesc;
                            // 检测递归调用：当前方法调用自身
                            if (ownerCalled.equals(owner) && nameCalled.equals(name) && descCalled.equals(currentMethodDesc)) {
                                recursiveMap.put(className + " " + methodKey, true);
                            }

                            // 检测明确的异步API调用
                            boolean isAsyncCall =
                                    ("java/util/concurrent/CompletableFuture".equals(ownerCalled) && "runAsync".equals(nameCalled))
                                            || ("java/util/concurrent/CompletableFuture".equals(ownerCalled) &&
                                            "supplyAsync".equals(nameCalled))
                                            || ("java/util/concurrent/Executor".equals(ownerCalled)
                                            && ("execute".equals(nameCalled) || "submit".equals(nameCalled)))
                                            || (ownerCalled.endsWith("Executor")
                                            && ("execute".equals(nameCalled) || "submit".equals(nameCalled)))
                                            || (ownerCalled.endsWith("ExecutorService")
                                            && ("execute".equals(nameCalled) || "submit".equals(nameCalled)))
                                            || (ownerCalled.contains("ThreadPoolTaskExecutor")
                                            && ("execute".equals(nameCalled) || "submit".equals(nameCalled)))
                                            || (ownerCalled.contains("Thread") && "start".equals(nameCalled));

                            if (isAsyncCall) {
                                asyncMethodMap.put(className + " " + methodKey, true);
                            }

                            lineNumberSet.add(currentLine);
                            super.visitMethodInsn(opcode, ownerCalled, nameCalled, descCalled, itf);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethod,
                                                           Object... bootstrapMethodArguments) {
                            // 检测 Lambda 表达式，分析是否与异步相关
                            if (bootstrapMethodArguments != null) {
                                for (Object arg : bootstrapMethodArguments) {
                                    if (arg instanceof Handle) {
                                        Handle handle = (Handle) arg;
                                        String targetOwner = handle.getOwner();
                                        String targetName = handle.getName();
                                        String targetDesc = handle.getDesc();

                                        // 如果 Lambda 指向的方法调用了异步API，则标记该Lambda方法为异步
                                        if (targetName.contains("lambda$")) {
                                            // 检查 Lambda 方法是否包含异步操作
                                            boolean hasAsyncOperations = checkLambdaForAsyncOperations(targetOwner,
                                                    targetName, targetDesc);
                                            if (hasAsyncOperations) {
                                                String methodKey = targetName + " " + targetDesc;
                                                asyncMethodMap.put(className + " " + methodKey, true);
                                            }
                                        }
                                    }
                                }
                            }
                            lineNumberSet.add(currentLine);
                            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethod, bootstrapMethodArguments);
                        }

                        // 检查 Lambda 方法是否包含异步操作
                        private boolean checkLambdaForAsyncOperations(String owner, String methodName,
                                                                      String descriptor) {
                            // 这里可以添加更复杂的检测逻辑
                            // 基于方法名、描述符等特征判断

                            // 简单实现：检查方法名是否包含异步相关关键词
                            String lowerMethodName = methodName.toLowerCase();
                            return lowerMethodName.contains("async") ||
                                    lowerMethodName.contains("future") ||
                                    lowerMethodName.contains("executor") ||
                                    lowerMethodName.contains("thread");
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            lineNumberSet.add(currentLine);
                            super.visitInsn(opcode);
                        }

                        @Override
                        public void visitVarInsn(int opcode, int varIndex) {
                            switch (opcode) {
//                                case Opcodes.ILOAD:
                                case Opcodes.ISTORE:
                                case Opcodes.RET:
                                    lineNumberSet.add(currentLine);
                            }
                            super.visitVarInsn(opcode, varIndex);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            switch (opcode) {
                                case Opcodes.GETSTATIC:
                                case Opcodes.PUTSTATIC:
                                case Opcodes.GETFIELD:
                                case Opcodes.PUTFIELD:
                                    lineNumberSet.add(currentLine);
                            }
                            super.visitFieldInsn(opcode, owner, name, descriptor);
                        }

                        @Override
                        public void visitLineNumber(int line, Label start) {
                            currentLine = line;
                            super.visitLineNumber(line, start);
                        }

                        @Override
                        public void visitJumpInsn(final int opcode, final Label label) {
                            if ((opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE) || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL) {
                                decisionPoints++;   // 每个跳转指令都是一个判定节点
                                if (this.currentLine > 0) {
                                    int nextTarget = branchLineTargetCounters.getOrDefault(this.currentLine, 0) + 1;
                                    branchLineTargetCounters.put(this.currentLine, nextTarget);
                                    branchLineAndTargetProbeMap
                                            .computeIfAbsent(this.currentLine, key -> new LinkedHashSet<Integer>())
                                            .add(nextTarget);
                                }
                                lineNumberSet.add(currentLine);
                            } else if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR) {
                                decisionPoints++;
                            } else if (opcode == Opcodes.GOTO) {
                                lineNumberSet.add(currentLine);
                            }
                            super.visitJumpInsn(opcode, label);
                        }

                        @Override
                        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                            decisionPoints++; // try-catch 语句是一个判定节点
                            super.visitTryCatchBlock(start, end, handler, type);
                        }

                        @Override
                        public void visitEnd() {
                            int cyclo = decisionPoints + 1;
                            if (signature == null || descriptor != null) {
                                cyclomaticComplexityMap.put(name + " " + descriptor, cyclo);
                            } else {
                                cyclomaticComplexityMap.put(name + " " + signature, cyclo);
                            }
                            super.visitEnd();
                        }
                    };
                }
            }, ClassReader.EXPAND_FRAMES);
        } catch (IllegalArgumentException e) {
            logger.error("[Agent-ASM ClassVisitor]异常信息, 初始化失败: " + className + ", version: " + version + ", " +
                    "asmApiVersion:" + " " + asmApiVersion + e.getMessage(), e);
        }

    }

    // ============ getters ============

    public long getClassId() {
        return classId;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMethodDesc() {
        return methodDesc;
    }

    public boolean isWithFrames() {
        return withFrames;
    }

    public int getVersion() {
        return version;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public Map<Integer, Integer> getMethodProbeSizes() {
        return methodProbeSizes;
    }

    public Map<String, Set<Integer>> getMethodLineNumberMap() {
        return methodLineNumberMap;
    }

    public Map<String, Integer> getTotalBranchMap() {
        return totalBranchMap;
    }

    public Map<Integer, Set<Integer>> getBranchLineAndTargetProbeMap() {
        return branchLineAndTargetProbeMap;
    }

    public Map<String, Integer> getCyclomaticComplexityMap() {
        return cyclomaticComplexityMap;
    }

    public Map<String, Boolean> getRecursiveMap() {
        return recursiveMap;
    }

    public Map<String, Boolean> getAsyncMethodMap() {
        return asyncMethodMap;
    }
}
