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

    public MethodInstrumenter(final MethodVisitor mv,
                              final IProbeInserter probeInserter) {
        super(mv);
        this.probeInserter = probeInserter;
    }

    @Override
    public void visitProbe(final int probeId, final boolean branchProbe,
                           final int branchLine, final int branchTargetId) {
        probeInserter.insertProbe(probeId, branchProbe, branchLine, branchTargetId);
    }

    @Override
    public void visitInsnWithProbe(final int opcode, final int probeId,
                                   final int branchLine, final int branchTargetId) {
        probeInserter.insertProbe(probeId, false, -1, -1);
        mv.visitInsn(opcode);
    }

    @Override
    public void visitJumpInsnWithProbe(final int opcode, final Label label,
                                       final int probeId, final IFrame frame,
                                       final int branchLine, final int branchTargetId) {
        if (opcode == Opcodes.GOTO) {
            probeInserter.insertProbe(probeId, true, branchLine, branchTargetId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
        } else {
            final Label intermediate = new Label();
            mv.visitJumpInsn(getInverted(opcode), intermediate);
            probeInserter.insertProbe(probeId, true, branchLine, branchTargetId);
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
                                               final Label dflt, final Label[] labels, final IFrame frame,
                                               final int branchLine, final int[] branchTargetIds) {
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        final Label newDflt = createIntermediate(dflt);
        final Label[] newLabels = createIntermediates(labels);
        mv.visitTableSwitchInsn(min, max, newDflt, newLabels);
        insertIntermediateProbes(dflt, labels, frame, branchLine, branchTargetIds);
    }

    @Override
    public void visitLookupSwitchInsnWithProbes(final Label dflt,
                                                final int[] keys, final Label[] labels, final IFrame frame,
                                                final int branchLine, final int[] branchTargetIds) {
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        final Label newDflt = createIntermediate(dflt);
        final Label[] newLabels = createIntermediates(labels);
        mv.visitLookupSwitchInsn(newDflt, keys, newLabels);
        insertIntermediateProbes(dflt, labels, frame, branchLine, branchTargetIds);
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

    private void insertIntermediateProbe(final Label label, final IFrame frame,
                                         final int branchLine, final int branchTargetId) {
        final int probeId = LabelInfo.getProbeId(label);
        if (probeId != LabelInfo.NO_PROBE && !LabelInfo.isDone(label)) {
            mv.visitLabel(LabelInfo.getIntermediateLabel(label));
            frame.accept(mv);
            probeInserter.insertProbe(probeId, true, branchLine, branchTargetId);
            mv.visitJumpInsn(Opcodes.GOTO, label);
            LabelInfo.setDone(label);
        }
    }

    private void insertIntermediateProbes(final Label dflt, final Label[] labels, final IFrame frame,
                                          final int branchLine, final int[] branchTargetIds) {
        LabelInfo.resetDone(dflt);
        LabelInfo.resetDone(labels);
        int idx = 0;
        insertIntermediateProbe(dflt, frame, branchLine,
                branchTargetIds != null && idx < branchTargetIds.length ? branchTargetIds[idx] : -1);
        idx++;
        for (final Label l : labels) {
            insertIntermediateProbe(l, frame, branchLine,
                    branchTargetIds != null && idx < branchTargetIds.length ? branchTargetIds[idx] : -1);
            idx++;
        }
    }
}
