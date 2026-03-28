/*******************************************************************************
 * Copyright (c) 2009, 2016 Mountainminds GmbH & Co. KG and Contributors
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributors:
 *    Marc R. Hoffmann - initial API and implementation
 *
 *******************************************************************************/
package com.oAT.agent.jacoco.instr;

import com.oAT.agent.jacoco.flow.IFrame;
import com.oAT.agent.jacoco.flow.LabelInfo;
import com.oAT.agent.jacoco.flow.MethodProbesVisitor;
import com.oAT.shaded.asm97.Label;
import com.oAT.shaded.asm97.MethodVisitor;
import com.oAT.shaded.asm97.Opcodes;

/**
 * This method adapter inserts probes as requested by the
 * {@link MethodProbesVisitor} events.
 */
class MethodInstrumenter extends MethodProbesVisitor {

    private final IProbeInserter probeInserter;

    /**
     * Create a new instrumenter instance for the given method.
     *
     * @param mv            next method visitor in the chain
     * @param probeInserter call-back to insert probes where required
     */
    public MethodInstrumenter(final MethodVisitor mv,
                              final IProbeInserter probeInserter) {
        super(mv);
        this.probeInserter = probeInserter;
    }

    // === IMethodProbesVisitor ===

    @Override
    public void visitProbe(final int probeId) {
        probeInserter.insertProbe(probeId);
    }

    @Override
    public void visitInsnWithProbe(final int opcode, final int probeId) {
        probeInserter.insertProbe(probeId);
        mv.visitInsn(opcode);
    }

    @Override
    public void visitJumpInsnWithProbe(final int opcode, final Label label,
                                       final int probeId, final IFrame frame) {
        if (opcode == Opcodes.GOTO) {
            probeInserter.insertProbe(probeId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
        } else {
            final Label intermediate = new Label();
            mv.visitJumpInsn(getInverted(opcode), intermediate);
            probeInserter.insertProbe(probeId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
            mv.visitLabel(intermediate);
            frame.accept(mv);
        }
    }

    private int getInverted(final int opcode) {
        switch (opcode) {
            case Opcodes.IFEQ:
                return Opcodes.IFNE;
            case Opcodes.IFNE:
                return Opcodes.IFEQ;
            case Opcodes.IFLT:
                return Opcodes.IFGE;
            case Opcodes.IFGE:
                return Opcodes.IFLT;
            case Opcodes.IFGT:
                return Opcodes.IFLE;
            case Opcodes.IFLE:
                return Opcodes.IFGT;
            case Opcodes.IF_ICMPEQ:
                return Opcodes.IF_ICMPNE;
            case Opcodes.IF_ICMPNE:
                return Opcodes.IF_ICMPEQ;
            case Opcodes.IF_ICMPLT:
                return Opcodes.IF_ICMPGE;
            case Opcodes.IF_ICMPGE:
                return Opcodes.IF_ICMPLT;
            case Opcodes.IF_ICMPGT:
                return Opcodes.IF_ICMPLE;
            case Opcodes.IF_ICMPLE:
                return Opcodes.IF_ICMPGT;
            case Opcodes.IF_ACMPEQ:
                return Opcodes.IF_ACMPNE;
            case Opcodes.IF_ACMPNE:
                return Opcodes.IF_ACMPEQ;
            case Opcodes.IFNULL:
                return Opcodes.IFNONNULL;
            case Opcodes.IFNONNULL:
                return Opcodes.IFNULL;
        }
        throw new IllegalArgumentException();
    }

    @Override
    public void visitTableSwitchInsnWithProbes(final int min, final int max,
                                               final Label dflt, final Label[] labels, final IFrame frame) {
        // 1. Calculate intermediate labels:
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        final Label newDflt = createIntermediate(dflt);
        final Label[] newLabels = createIntermediates(labels);
        mv.visitTableSwitchInsn(min, max, newDflt, newLabels);

        // 2. Insert probes:
        insertIntermediateProbes(dflt, labels, frame);
    }

    @Override
    public void visitLookupSwitchInsnWithProbes(final Label dflt,
                                                final int[] keys, final Label[] labels, final IFrame frame) {
        // 1. Calculate intermediate labels:
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        final Label newDflt = createIntermediate(dflt);
        final Label[] newLabels = createIntermediates(labels);
        mv.visitLookupSwitchInsn(newDflt, keys, newLabels);

        // 2. Insert probes:
        insertIntermediateProbes(dflt, labels, frame);
    }

    private Label[] createIntermediates(final Label[] labels) {
        final Label[] intermediates = new Label[labels.length];
        for (int i = 0; i < labels.length; i++) {
            intermediates[i] = createIntermediate(labels[i]);
        }
        return intermediates;
    }

    private Label createIntermediate(final Label label) {
        final Label intermediate;
        if (LabelInfo.getProbeId(label) == LabelInfo.NO_PROBE) {
            intermediate = label;
        } else {
            if (LabelInfo.isDone(label)) {
                intermediate = LabelInfo.getIntermediateLabel(label);
            } else {
                intermediate = new Label();
                LabelInfo.setIntermediateLabel(label, intermediate);
                LabelInfo.setDone(label);
            }
        }
        return intermediate;
    }

    private void insertIntermediateProbe(final Label label, final IFrame frame) {
        final int probeId = LabelInfo.getProbeId(label);
        if (probeId != LabelInfo.NO_PROBE && !LabelInfo.isDone(label)) {
            mv.visitLabel(LabelInfo.getIntermediateLabel(label));
            frame.accept(mv);
            probeInserter.insertProbe(probeId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
            LabelInfo.setDone(label);
        }
    }

    private void insertIntermediateProbes(final Label dflt, final Label[] labels, final IFrame frame) {
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        insertIntermediateProbe(dflt, frame);
        for (final Label l : labels) {
            insertIntermediateProbe(l, frame);
        }
    }

    // new methods for probes
    @Override
    public void visitVarInsnWithProbes(final int opcode, final int var, final int probeId) {
        switch (opcode) {
//            case Opcodes.ILOAD:
            case Opcodes.ISTORE:
            case Opcodes.RET:
                probeInserter.insertProbe(probeId);
        }
    }

    // new methods for probes
    @Override
    public void visitFieldInsnWithProbes(final int opcode, final String owner, final String name, final String desc,
                                         final int probeId) {
        switch (opcode) {
            case Opcodes.GETSTATIC:
            case Opcodes.PUTSTATIC:
            case Opcodes.GETFIELD:
            case Opcodes.PUTFIELD:
                probeInserter.insertProbe(probeId);
        }
    }

    // new methods for probes
    @Override
    public void visitMethodInsnWithProbes(final int opcode, final String owner, final String name, final String desc,
                                          final boolean itf, final int probeId) {
        probeInserter.insertProbe(probeId);
        mv.visitMethodInsn(opcode, owner, name, desc, itf);
    }

    @Override
    public void visitLineNumberWithProbes(int line, Label start, final int probeId) {
        // 不需要在这里插入探针，因为行号本身不需要探针
        probeInserter.insertProbe(probeId); // 如果需要行号探针，可以在这里添加
        // 直接调用父类方法，避免重复插入行号
        super.visitLineNumber(line, start);
    }
}
