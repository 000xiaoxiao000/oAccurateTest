package com.oAT.agent.jacoco.instr;

import com.oAT.agent.Agent;
import com.oAT.agent.common.WildcardMatcher;
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
    private final ClassInstrumenter classInstrumenter;
    private final int variable;
    private final WildcardMatcher methodIncludes;
    private final WildcardMatcher methodExcludes;
    private final String clazzName;
    private final String methodName;
    private final String methodDesc;
    private final String methodNameDescCombined;
    private final boolean compilerGeneratedMethod;
    private final Map<String, Set<Integer>> methodLineNumberMap;
    private final Map<String, Boolean> recursiveMap;
    private final Map<String, Boolean> asyncMethodMap;
    private final Map<String, Integer> cyclomaticComplexity;
    private int currentLine = -1;
    private int methodEntryProbeIdx = -1;
    private final Map<Integer, Integer> branchLinePathOrdinalCounter = new HashMap<Integer, Integer>();
    private int[] lineTotals;
    private int[] branchTotals;
    private int cyclo;
    private boolean isRecursive;
    private boolean isAsync;
    private int execMethodLineNumber = -1;

    private static final ThreadLocal<ProbeAssignment> PROBE_ASSIGNMENT = new ThreadLocal<ProbeAssignment>() {
        @Override
        protected ProbeAssignment initialValue() {
            return new ProbeAssignment();
        }
    };

    static class MethodMeta {
        final String methodNameDesc;
        final int[] lineTotals;
        final int[] branchTotals;
        final int cyclo;
        final boolean recursive;
        final boolean async;

        MethodMeta(String methodNameDesc, int[] lineTotals, int[] branchTotals, int cyclo, boolean recursive, boolean async) {
            this.methodNameDesc = methodNameDesc;
            this.lineTotals = lineTotals;
            this.branchTotals = branchTotals;
            this.cyclo = cyclo;
            this.recursive = recursive;
            this.async = async;
        }
    }

    static class BranchMeta {
        final int branchLine;
        final int branchTargetId;

        BranchMeta(int branchLine, int branchTargetId) {
            this.branchLine = branchLine;
            this.branchTargetId = branchTargetId;
        }
    }

    static class ProbeAssignment {
        final Map<Integer, Integer> probeToLineNumber = new LinkedHashMap<Integer, Integer>();
        final Map<Integer, Boolean> probeIsBranch = new LinkedHashMap<Integer, Boolean>();
        final Map<Integer, Integer> probeToMethodEntry = new LinkedHashMap<Integer, Integer>();
        final Map<Integer, MethodMeta> methodMetaMap = new LinkedHashMap<Integer, MethodMeta>();
        final Map<Integer, BranchMeta> branchMetaMap = new LinkedHashMap<Integer, BranchMeta>();
    }

    static ProbeAssignment getAndClearProbeAssignment() {
        ProbeAssignment assignment = PROBE_ASSIGNMENT.get();
        PROBE_ASSIGNMENT.remove();
        return assignment;
    }

    ProbeInserter(final int access, final String name, final String desc, final String signature,
                  final MethodVisitor mv, final ClassInfo classInfo, final ClassInstrumenter classInstrumenter) {
        super(InstrSupport.ASM_API_VERSION, mv);
        this.classInstrumenter = classInstrumenter;
        this.clazzName = classInfo.getClassName();
        this.methodName = name;
        this.compilerGeneratedMethod = ClassInfo.isCompilerGeneratedMethod(access);
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
        variable = Math.max(pos, 0);
        this.methodLineNumberMap = classInfo.getMethodLineNumberMap();
        this.cyclomaticComplexity = classInfo.getCyclomaticComplexityMap();
        this.recursiveMap = classInfo.getRecursiveMap();
        this.asyncMethodMap = classInfo.getAsyncMethodMap();

        String methodIncludeExpr = Agent.traceContext.getConfig("codeStack.includeMethod");
        if (methodIncludeExpr == null) {
            methodIncludeExpr = Agent.traceContext.getConfig("conf_codeStack.includeMethod");
        }
        methodIncludes = new WildcardMatcher(methodIncludeExpr == null || methodIncludeExpr.isEmpty() ? "*" :
                methodIncludeExpr);

        String methodExcludeExpr = Agent.traceContext.getConfig("codeStack.excludeMethod");
        if (methodExcludeExpr == null) {
            methodExcludeExpr = Agent.traceContext.getConfig("conf_codeStack.excludeMethod");
        }
        methodExcludes = new WildcardMatcher(methodExcludeExpr == null || methodExcludeExpr.isEmpty() ?
                "<init>&<clinit>&hashCode&toString&equals&Equal&canEqual&hashCode" :
                methodExcludeExpr);
    }

    private boolean codeStackMethodInclude() {
        return !methodIncludes.matches(this.methodName);
    }

    private boolean codeStackMethodExclude() {
        if (compilerGeneratedMethod && !methodName.startsWith("lambda$")) {
            return true;
        }
        if ("<init>".equals(this.methodName) && this.methodDesc.contains("(") && !this.methodDesc.contains("()")) {
            return false;
        }
        return methodExcludes.matches(this.methodName);
    }

    @Override
    public void insertProbe(final int id, final boolean branchProbe, final int branchLine,
                            final int branchTargetId) {
        if (codeStackMethodExclude() || codeStackMethodInclude()) {
            return;
        }
        if (mv == null) {
            return;
        }

        ProbeAssignment assignment = PROBE_ASSIGNMENT.get();
        assignment.probeToLineNumber.put(id, currentLine);
        assignment.probeIsBranch.put(id, branchProbe);
        if (methodEntryProbeIdx >= 0) {
            assignment.probeToMethodEntry.put(id, methodEntryProbeIdx);
        }
        if (branchProbe && branchLine > 0) {
            int effectiveBranchTargetId = branchTargetId;
            if (effectiveBranchTargetId <= 0) {
                Integer currentTargetId = branchLinePathOrdinalCounter.get(branchLine);
                effectiveBranchTargetId = (currentTargetId == null ? 0 : currentTargetId.intValue()) + 1;
            }
            branchLinePathOrdinalCounter.put(branchLine, effectiveBranchTargetId);
            assignment.branchMetaMap.put(id, new BranchMeta(branchLine, effectiveBranchTargetId));
        }

        mv.visitVarInsn(Opcodes.ALOAD, variable);
        InstrSupport.push(mv, id);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.BASTORE);
    }

    @Override
    public void visitCode() {
        if (codeStackMethodExclude() || codeStackMethodInclude()) {
            super.visitCode();
            return;
        }

        if (mv != null) {
            methodEntryProbeIdx = classInstrumenter.nextProbeIndex();
            ProbeAssignment assignment = PROBE_ASSIGNMENT.get();
            String targetMethodNameDesc = this.clazzName + " " + this.methodNameDescCombined;

            Set<Integer> tempSet = new HashSet<Integer>();
            for (Map.Entry<String, Set<Integer>> entry : methodLineNumberMap.entrySet()) {
                if (targetMethodNameDesc.equals(entry.getKey()) && entry.getValue() != null) {
                    tempSet.addAll(entry.getValue());
                }
            }
            lineTotals = toIntArray(tempSet);

            List<Integer> totalBranchList = new ArrayList<Integer>();
            for (Map.Entry<String, Integer> entry : this.clazzInfo.getTotalBranchMap().entrySet()) {
                String[] split = entry.getKey().split(" ");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < split.length - 1; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(split[i]);
                }
                if (methodNameDescCombined.equals(sb.toString())) {
                    totalBranchList.add(entry.getValue());
                }
            }
            branchTotals = toIntArray(totalBranchList);

            cyclo = cyclomaticComplexity.containsKey(this.methodNameDescCombined) ?
                    cyclomaticComplexity.get(this.methodNameDescCombined) : 0;
            Boolean recursiveValue = recursiveMap.get(targetMethodNameDesc);
            Boolean asyncValue = asyncMethodMap.get(this.clazzName + " " + this.methodNameDescCombined);
            isRecursive = recursiveValue != null && recursiveValue.booleanValue();
            isAsync = asyncValue != null && asyncValue.booleanValue();

            for (Map.Entry<String, Set<Integer>> entry : methodLineNumberMap.entrySet()) {
                if (targetMethodNameDesc.equals(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    execMethodLineNumber = Collections.min(entry.getValue());
                    break;
                }
            }

            assignment.probeToLineNumber.put(methodEntryProbeIdx, execMethodLineNumber);
            assignment.probeIsBranch.put(methodEntryProbeIdx, false);
            assignment.probeToMethodEntry.put(methodEntryProbeIdx, methodEntryProbeIdx);
            assignment.methodMetaMap.put(methodEntryProbeIdx, new MethodMeta(
                    methodNameDescCombined, lineTotals, branchTotals, cyclo, isRecursive, isAsync
            ));

            mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazzName,
                    InstrSupport.INITMETHOD_NAME, InstrSupport.INITMETHOD_DESC, false);
            mv.visitVarInsn(Opcodes.ASTORE, variable);
        }
        super.visitCode();
    }

    private static int[] toIntArray(Collection<Integer> values) {
        if (values == null || values.isEmpty()) {
            return new int[0];
        }
        int[] result = new int[values.size()];
        int index = 0;
        for (Integer value : values) {
            if (value != null) {
                result[index++] = value.intValue();
            }
        }
        if (index == result.length) {
            return result;
        }
        int[] trimmed = new int[index];
        System.arraycopy(result, 0, trimmed, 0, index);
        return trimmed;
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        this.currentLine = line;
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
        int accessorStackSize = 3;
        int calculatedStack = maxStack + 1;
        final int increasedStack = Math.max(calculatedStack, accessorStackSize);
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
        if (type != Opcodes.F_NEW) {
            throw new IllegalArgumentException("ClassReader.accept() should be called with EXPAND_FRAMES flag");
        }

        int newLocalLen;
        if (variable <= nLocal) {
            newLocalLen = nLocal + 1;
        } else {
            newLocalLen = variable + 1;
        }
        newLocalLen = Math.max(newLocalLen, 0);

        final Object[] newLocal = new Object[newLocalLen];
        int oldIdx = 0;
        int newIdx = 0;
        int currentSlot = 0;

        while (oldIdx < nLocal || currentSlot <= variable) {
            if (currentSlot == variable) {
                newLocal[newIdx++] = InstrSupport.DATAFIELD_DESC;
                currentSlot++;
            } else {
                if (oldIdx < nLocal) {
                    final Object t = local[oldIdx++];
                    newLocal[newIdx++] = t;
                    currentSlot++;
                    if (t == Opcodes.LONG || t == Opcodes.DOUBLE) {
                        currentSlot++;
                    }
                } else {
                    newLocal[newIdx++] = Opcodes.TOP;
                    currentSlot++;
                }
            }
        }

        mv.visitFrame(type, newIdx, newLocal, nStack, stack);
    }
}
