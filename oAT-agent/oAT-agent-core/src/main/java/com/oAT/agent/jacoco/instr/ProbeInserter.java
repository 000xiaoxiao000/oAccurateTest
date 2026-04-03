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
 *
 * <p>
 * NEW APPROACH: Instead of calling StackSession.$begin()/$end()/$recordBranchCondition()
 * for every method entry/exit/branch, we simply do:
 * <ol>
 *   <li>At method start: boolean[] $jacocoData = ClassName.$jacocoInit(); (1 time)</li>
 *   <li>At each probe point: $jacocoData[probeIdx] = true; (1 BASTORE instruction)</li>
 * </ol>
 * This reduces per-method overhead from ~10μs to ~10ns (a single array write).
 * </p>
 */
class ProbeInserter extends MethodVisitor implements IProbeInserter {
    private final ClassInfo clazzInfo;
    private final ClassInstrumenter classInstrumenter;

    /**
     * Position of the inserted variable (boolean[] for probe array).
     */
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

    // Current line number tracking
    private int currentLine = -1;
    private int lastInsertedLine = Integer.MIN_VALUE;

    // Branch tracking (for metadata only, not for runtime recording)
    private final Set<Integer> branchLines = new HashSet<>();
    private final Map<Integer, Integer> branchLineConditionCounter = new HashMap<>();
    // Probe index for the current method's entry probe
    private int methodEntryProbeIdx = -1;

    // Method metadata for class-level registration
    private int[] lineTotals;
    private int[] branchTotals;
    private int cyclo;
    private boolean isRecursive;
    private boolean isAsync;
    private int execMethodLineNumber = -1;

    // ========== Static probe assignment storage (per-class) ==========
    // Cleared by ClassInstrumenter.visitTotalProbeCount() after registration
    private static final ThreadLocal<ProbeAssignment> PROBE_ASSIGNMENT = ThreadLocal.withInitial(ProbeAssignment::new);

    /**
     * Method metadata for ClassProbeInfo registration.
     */
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

    /**
     * Branch metadata for ClassProbeInfo registration.
     */
    static class BranchMeta {
        final int branchLine;
        final int conditionNumber;

        BranchMeta(int branchLine, int conditionNumber) {
            this.branchLine = branchLine;
            this.conditionNumber = conditionNumber;
        }
    }

    /**
     * Probe assignment data accumulated for the entire class.
     */
    static class ProbeAssignment {
        final Map<Integer, Integer> probeToLineNumber = new LinkedHashMap<>();
        final Map<Integer, Boolean> probeIsBranch = new LinkedHashMap<>();
        final Map<Integer, Integer> probeToMethodEntry = new LinkedHashMap<>();
        final Map<Integer, MethodMeta> methodMetaMap = new LinkedHashMap<>();
        final Map<Integer, BranchMeta> branchMetaMap = new LinkedHashMap<>();
    }

    /**
     * Get and clear the probe assignment for the current thread.
     * Called by ClassInstrumenter.visitTotalProbeCount().
     */
    static ProbeAssignment getAndClearProbeAssignment() {
        ProbeAssignment assignment = PROBE_ASSIGNMENT.get();
        PROBE_ASSIGNMENT.remove();
        return assignment;
    }

    /**
     * Creates a new {@link ProbeInserter}.
     *
     * @param access             access flags of the adapted method
     * @param name               the method's name
     * @param desc               the method's descriptor
     * @param signature          the method's signature
     * @param mv                 the method visitor to which this adapter delegates calls
     * @param classInfo          class info
     * @param classInstrumenter  the parent class instrumenter (for probe index allocation)
     */
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

        // Method filter expressions
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
        if (compilerGeneratedMethod) {
            return true;
        }
        if ("<init>".equals(this.methodName) && this.methodDesc.contains("(") && !this.methodDesc.contains("()")) {
            return false;
        }
        return methodExcludes.matches(this.methodName);
    }

    /**
     * Called by the instrumentation framework to insert a probe at a specific point.
     * In the new approach, this simply sets $jacocoData[probeIdx] = true.
     */
    @Override
    public void insertProbe(final int id) {
        if (codeStackMethodExclude() || codeStackMethodInclude()) {
            return;
        }
        insertProbeToLine();
    }

    /**
     * Insert a simple boolean array write: $jacocoData[probeIdx] = true;
     * This is the core of the new approach — replaces object.equals(lineNumber) calls.
     */
    private void insertProbeToLine() {
        if (mv != null) {
            // Merge consecutive duplicate probe insertions
            if (currentLine == lastInsertedLine || currentLine == -1) {
                return;
            }
            lastInsertedLine = currentLine;

            int probeIdx = classInstrumenter.nextProbeIndex();
            ProbeAssignment assignment = PROBE_ASSIGNMENT.get();
            assignment.probeToLineNumber.put(probeIdx, currentLine);
            assignment.probeIsBranch.put(probeIdx, false);
            if (methodEntryProbeIdx >= 0) {
                assignment.probeToMethodEntry.put(probeIdx, methodEntryProbeIdx);
            }

            // Generate: $jacocoData[probeIdx] = true;
            mv.visitVarInsn(Opcodes.ALOAD, variable);  // aload <variable> ($jacocoData)
            InstrSupport.push(mv, probeIdx);            // push probeIdx
            mv.visitInsn(Opcodes.ICONST_1);             // push true
            mv.visitInsn(Opcodes.BASTORE);              // bastore
        }
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        branchLines.add(currentLine);
        super.visitTableSwitchInsn(min, max, dflt, labels);
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
        branchLines.add(currentLine);
        super.visitLookupSwitchInsn(dflt, keys, labels);
    }

    /**
     * Insert branch probes for branch coverage.
     * <p>
     * For each branch (if/else), we insert TWO probes:
     * <ul>
     *   <li>True branch probe: executed when condition is true</li>
     *   <li>False branch probe: executed when condition is false</li>
     * </ul>
     * </p>
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

        // Only instrument conditional jumps (skip GOTO)
        if (opcode == Opcodes.GOTO) {
            super.visitJumpInsn(opcode, label);
            return;
        }

        // Record branch line
        this.branchLines.add(currentLine);
        int conditionNumber = branchLineConditionCounter.getOrDefault(currentLine, 0) + 1;
        branchLineConditionCounter.put(currentLine, conditionNumber);

        ProbeAssignment assignment = PROBE_ASSIGNMENT.get();

        // Allocate two probe indices: trueProbeIdx and falseProbeIdx
        int trueProbeIdx = classInstrumenter.nextProbeIndex();
        int falseProbeIdx = classInstrumenter.nextProbeIndex();

        // Record metadata
        assignment.probeToLineNumber.put(trueProbeIdx, currentLine);
        assignment.probeIsBranch.put(trueProbeIdx, true);
        if (methodEntryProbeIdx >= 0) {
            assignment.probeToMethodEntry.put(trueProbeIdx, methodEntryProbeIdx);
        }
        assignment.probeToLineNumber.put(falseProbeIdx, currentLine);
        assignment.probeIsBranch.put(falseProbeIdx, true);
        if (methodEntryProbeIdx >= 0) {
            assignment.probeToMethodEntry.put(falseProbeIdx, methodEntryProbeIdx);
        }

        // Both branch probes belong to the same source line, so either path
        // should count the conditional line as executed branch coverage.
        assignment.branchMetaMap.put(trueProbeIdx, new BranchMeta(currentLine, conditionNumber));
        assignment.branchMetaMap.put(falseProbeIdx, new BranchMeta(currentLine, conditionNumber));

        // Generate instrumented branch code:
        // Original jump -> jumpTaken (true branch probe)
        // Fallthrough -> false branch probe -> continuation

        Label jumpTaken = new Label();
        Label continuation = new Label();

        // Original conditional jump -> jumpTaken
        super.visitJumpInsn(opcode, jumpTaken);

        // Fallthrough (False branch): $jacocoData[falseProbeIdx] = true;
        mv.visitVarInsn(Opcodes.ALOAD, variable);
        InstrSupport.push(mv, falseProbeIdx);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.BASTORE);
        super.visitJumpInsn(Opcodes.GOTO, continuation);

        // Jump Taken (True branch): $jacocoData[trueProbeIdx] = true;
        super.visitLabel(jumpTaken);
        mv.visitVarInsn(Opcodes.ALOAD, variable);
        InstrSupport.push(mv, trueProbeIdx);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.BASTORE);
        super.visitJumpInsn(Opcodes.GOTO, label);

        // Continuation label
        super.visitLabel(continuation);
    }

    @Override
    public void visitInsn(final int opcode) {
        // No $end() call needed in the new approach
        // Return instructions are clean - no instrumentation
        super.visitInsn(opcode);
    }

    @Override
    public void visitLabel(Label label) {
        super.visitLabel(label);
    }

    /**
     * Method entry: insert $jacocoInit() call and store probe array in local variable.
     * This replaces the old $begin() call with 11 parameters.
     * <p>
     * Generated code:
     * <pre>
     * boolean[] $jacocoData = ClassName.$jacocoInit();
     * </pre>
     * </p>
     */
    @Override
    public void visitCode() {
        if (codeStackMethodExclude() || codeStackMethodInclude()) {
            super.visitCode();
            return;
        }

        if (mv != null) {
            // Allocate method entry probe index
            methodEntryProbeIdx = classInstrumenter.nextProbeIndex();
            ProbeAssignment assignment = PROBE_ASSIGNMENT.get();

            // Record method metadata for ClassProbeInfo
            String targetMethodNameDesc = this.clazzName + " " + this.methodNameDescCombined;

            // Calculate line totals for this method
            Set<Integer> tempSet = new HashSet<>();
            methodLineNumberMap.forEach((key, value) -> {
                String[] split = key.split(" ");
                String methodNameDescStr = String.join(" ", Arrays.copyOfRange(split, 0, split.length));
                if (targetMethodNameDesc.equals(methodNameDescStr)) {
                    tempSet.addAll(value);
                }
            });
            lineTotals = tempSet.stream().mapToInt(Integer::intValue).toArray();

            // Calculate branch totals for this method
            List<Integer> totalBranchList = new ArrayList<>();
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
            branchTotals = totalBranchList.stream().mapToInt(Integer::intValue).toArray();

            cyclo = cyclomaticComplexity.containsKey(this.methodNameDescCombined) ?
                    cyclomaticComplexity.get(this.methodNameDescCombined) : 0;
            isRecursive = recursiveMap.getOrDefault(targetMethodNameDesc, false);
            isAsync = asyncMethodMap.getOrDefault(this.clazzName + " " + this.methodNameDescCombined, false);

            // Find method entry line number
            for (Map.Entry<String, Set<Integer>> entry : methodLineNumberMap.entrySet()) {
                if (targetMethodNameDesc.equals(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    execMethodLineNumber = Collections.min(entry.getValue());
                    break;
                }
            }

            // Register method entry probe metadata
            assignment.probeToLineNumber.put(methodEntryProbeIdx, execMethodLineNumber);
            assignment.probeIsBranch.put(methodEntryProbeIdx, false);
            assignment.probeToMethodEntry.put(methodEntryProbeIdx, methodEntryProbeIdx);
            assignment.methodMetaMap.put(methodEntryProbeIdx, new MethodMeta(
                    methodNameDescCombined, lineTotals, branchTotals, cyclo, isRecursive, isAsync
            ));

            // Generate: boolean[] $jacocoData = ClassName.$jacocoInit();
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, clazzName,
                    InstrSupport.INITMETHOD_NAME, InstrSupport.INITMETHOD_DESC, false);
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
        // Probe code only uses 3 stack slots (aload + push + iconst_1 + bastore = peak 3)
        int accessorStackSize = 3;
        // Method entry $jacocoInit() uses 1 slot (areturn value)
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
                // Insert probe array variable: boolean[] ($jacocoData)
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
