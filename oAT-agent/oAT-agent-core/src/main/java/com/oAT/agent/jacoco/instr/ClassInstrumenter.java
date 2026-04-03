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
import com.oAT.agent.jacoco.ClassProbeInfo;
import com.oAT.agent.jacoco.ClassProbeInfoRegistry;
import com.oAT.agent.jacoco.flow.ClassProbesVisitor;
import com.oAT.agent.jacoco.flow.MethodProbesVisitor;
import com.oAT.shaded.asm97.*;
import java.util.Map;

/**
 * Adapter that instruments a class for coverage tracing.
 * <p>
 * Generates:
 * <ul>
 *   <li>A static boolean[] field $jacocoData to hold probe data</li>
 *   <li>A static $jacocoInit() method that lazily initializes and registers the probe array</li>
 * </ul>
 */
public class ClassInstrumenter extends ClassProbesVisitor {

    private final static Log logger = LogFactory.getLog(ClassInstrumenter.class);

    private final ClassInfo probeArrayStrategy;
    private String className;
    private int classAccess;
    private int currentProbeIdx = 0;

    /**
     * Emits an instrumented version of this class to the given class visitor.
     *
     * @param probeArrayStrategy this strategy will be used to access the probe array
     * @param cv                 next delegate in the visitor chain will receive the
     *                           instrumented class
     */
    public ClassInstrumenter(final ClassInfo probeArrayStrategy, final ClassVisitor cv) {
        super(cv);
        this.probeArrayStrategy = probeArrayStrategy;
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature, final String superName,
                      final String[] interfaces) {
        this.className = name;
        this.classAccess = access;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
                                   final Object value) {
        InstrSupport.assertNotInstrumented(name, className);
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public MethodProbesVisitor visitMethod(final int access, final String name, final String desc, final String signature,
                                           final String[] exceptions) {

        InstrSupport.assertNotInstrumented(name, className);

        final MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);

        if (mv == null) {
            return null;
        }
        final MethodVisitor frameEliminator = new DuplicateFrameEliminator(mv);
        final ProbeInserter probeVariableInserter = new ProbeInserter(access, name, desc, signature, frameEliminator, probeArrayStrategy, this);
        return new MethodInstrumenter(probeVariableInserter, probeVariableInserter);
    }

    @Override
    public void visitTotalProbeCount(final int count) {
        if (count == 0) {
            return;
        }

        final boolean isInterface = (classAccess & Opcodes.ACC_INTERFACE) != 0;
        final int fieldAccess = isInterface ? InstrSupport.DATAFIELD_INTF_ACC : InstrSupport.DATAFIELD_ACC;

        // 1. Generate $jacocoData static field: private static transient boolean[] $jacocoData;
        cv.visitField(fieldAccess, InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC, null, null);

        // 2. Generate $jacocoInit() method
        final MethodVisitor mv = cv.visitMethod(InstrSupport.INITMETHOD_ACC, InstrSupport.INITMETHOD_NAME,
                InstrSupport.INITMETHOD_DESC, null, null);
        mv.visitCode();
        mv.visitLabel(new Label());

        // Load $jacocoData field
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);
        // if ($jacocoData != null) return $jacocoData;
        Label notNull = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);

        // Create new boolean[probeCount]
        InstrSupport.push(mv, count);
        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, className, InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);

        // Register with CoverageData: CoverageData.register(classId, $jacocoData)
        mv.visitLdcInsn(probeArrayStrategy.getClassId());
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, InstrSupport.COVERAGE_DATA_INTERNAL_NAME,
                "register", "(J[Z)V", false);

        // Return $jacocoData
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);
        mv.visitInsn(Opcodes.ARETURN);

        // notNull label
        mv.visitLabel(notNull);
        mv.visitFieldInsn(Opcodes.GETSTATIC, className, InstrSupport.DATAFIELD_NAME, InstrSupport.DATAFIELD_DESC);
        mv.visitInsn(Opcodes.ARETURN);

        // Frame
        mv.visitFrame(Opcodes.F_NEW, 0, new Object[0], 1, new Object[]{InstrSupport.DATAFIELD_DESC});
        mv.visitLabel(new Label());
        mv.visitMaxs(3, 0); // maxStack: 3 (classId + probeArray + field op), maxLocals: 0 (static method)
        mv.visitEnd();

        // 3. Register ClassProbeInfo
        try {
            ProbeInserter.ProbeAssignment assignment = ProbeInserter.getAndClearProbeAssignment();
            ClassProbeInfo probeInfo = new ClassProbeInfo(
                    probeArrayStrategy.getClassId(),
                    probeArrayStrategy.getClassName(),
                    count
            );

            // Copy probe metadata from the assignment
            for (Map.Entry<Integer, Integer> entry : assignment.probeToLineNumber.entrySet()) {
                probeInfo.setProbeLineNumber(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, Boolean> entry : assignment.probeIsBranch.entrySet()) {
                probeInfo.setProbeIsBranch(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, Integer> entry : assignment.probeToMethodEntry.entrySet()) {
                probeInfo.setProbeMethodEntryIndex(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Integer, ProbeInserter.MethodMeta> entry : assignment.methodMetaMap.entrySet()) {
                ProbeInserter.MethodMeta meta = entry.getValue();
                probeInfo.setMethodInfo(
                        entry.getKey(),
                        meta.methodNameDesc,
                        meta.lineTotals,
                        meta.branchTotals,
                        meta.cyclo,
                        meta.recursive,
                        meta.async
                );
            }
            for (Map.Entry<Integer, ProbeInserter.BranchMeta> entry : assignment.branchMetaMap.entrySet()) {
                ProbeInserter.BranchMeta meta = entry.getValue();
                probeInfo.setBranchInfo(entry.getKey(), meta.branchLine, meta.conditionNumber);
            }

            ClassProbeInfoRegistry.register(probeArrayStrategy.getClassId(), probeInfo);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]注册 ClassProbeInfo 异常: " + className + ", " +
                    StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    /**
     * Get current probe index (called by ProbeInserter to assign probe IDs).
     */
    public int nextProbeIndex() {
        return currentProbeIdx++;
    }

    /**
     * Get current probe count so far (called by ProbeInserter).
     */
    public int getCurrentProbeCount() {
        return currentProbeIdx;
    }
}
