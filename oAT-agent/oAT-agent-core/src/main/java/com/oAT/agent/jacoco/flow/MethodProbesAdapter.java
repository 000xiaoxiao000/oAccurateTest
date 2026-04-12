package com.oAT.agent.jacoco.flow;

import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.shaded.asm97.Label;
import com.oAT.shaded.asm97.MethodVisitor;
import com.oAT.shaded.asm97.Opcodes;
import com.oAT.shaded.asm97.commons.AnalyzerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that creates additional visitor events for probes to be inserted into
 * a method.
 */
public final class MethodProbesAdapter extends MethodVisitor {

    private static final int NO_BRANCH_TARGET = -1;

    private final MethodProbesVisitor probesVisitor;

    private final IProbeIdGenerator idGenerator;

    private AnalyzerAdapter analyzer;

    private final Map<Label, Label> tryCatchProbeLabels;
    private int currentLine = -1;
    private final Map<Integer, Integer> branchTargetCounterByLine = new HashMap<>();

    public MethodProbesAdapter(final MethodProbesVisitor probesVisitor,
                               final IProbeIdGenerator idGenerator) {
        super(InstrSupport.ASM_API_VERSION, probesVisitor);
        this.probesVisitor = probesVisitor;
        this.idGenerator = idGenerator;
        this.tryCatchProbeLabels = new HashMap<>();
    }

    public void setAnalyzer(final AnalyzerAdapter analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public void visitTryCatchBlock(Label start, final Label end,
                                   final Label handler, final String type) {
        if (tryCatchProbeLabels.containsKey(start)) {
            start = tryCatchProbeLabels.get(start);
        } else if (LabelInfo.needsProbe(start)) {
            final Label probeLabel = new Label();
            LabelInfo.setSuccessor(probeLabel);
            tryCatchProbeLabels.put(start, probeLabel);
            start = probeLabel;
        }
        probesVisitor.visitTryCatchBlock(start, end, handler, type);
    }

    @Override
    public void visitLineNumber(final int line, final Label start) {
        currentLine = line;
        probesVisitor.visitLineNumber(line, start);
        if (line > 0) {
            probesVisitor.visitProbe(idGenerator.nextId(), false, -1, NO_BRANCH_TARGET);
        }
    }

    @Override
    public void visitLabel(final Label label) {
        if (LabelInfo.needsProbe(label)) {
            if (tryCatchProbeLabels.containsKey(label)) {
                probesVisitor.visitLabel(tryCatchProbeLabels.get(label));
            }
            final int probeId = idGenerator.nextId();
            probesVisitor.visitProbe(probeId, false, -1, NO_BRANCH_TARGET);
        }
        probesVisitor.visitLabel(label);
    }

    @Override
    public void visitInsn(final int opcode) {
        switch (opcode) {
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ATHROW:
                probesVisitor.visitInsnWithProbe(opcode, idGenerator.nextId(), -1, NO_BRANCH_TARGET);
                break;
            default:
                probesVisitor.visitInsn(opcode);
                break;
        }
    }

    @Override
    public void visitJumpInsn(final int opcode, final Label label) {
        if (LabelInfo.isMultiTarget(label)) {
            if (opcode == Opcodes.GOTO) {
                probesVisitor.visitJumpInsnWithProbe(opcode, label,
                        idGenerator.nextId(), frame(jumpPopCount(opcode)), -1,
                        NO_BRANCH_TARGET);
            } else {
                probesVisitor.visitJumpInsnWithProbe(opcode, label,
                        idGenerator.nextId(), frame(jumpPopCount(opcode)), currentLine,
                        nextBranchTargetId(currentLine));
            }
        } else {
            probesVisitor.visitJumpInsn(opcode, label);
        }
    }

    private int jumpPopCount(final int opcode) {
        switch (opcode) {
            case Opcodes.GOTO:
                return 0;
            case Opcodes.IFEQ:
            case Opcodes.IFNE:
            case Opcodes.IFLT:
            case Opcodes.IFGE:
            case Opcodes.IFGT:
            case Opcodes.IFLE:
            case Opcodes.IFNULL:
            case Opcodes.IFNONNULL:
                return 1;
            default:
                return 2;
        }
    }

    @Override
    public void visitLookupSwitchInsn(final Label dflt, final int[] keys,
                                      final Label[] labels) {
        int[] branchTargetIds = allocateBranchTargetIds(labels.length + 1);
        if (markLabels(dflt, labels)) {
            probesVisitor.visitLookupSwitchInsnWithProbes(dflt, keys, labels,
                    frame(1), currentLine, branchTargetIds);
        } else {
            probesVisitor.visitLookupSwitchInsn(dflt, keys, labels);
        }
    }

    @Override
    public void visitTableSwitchInsn(final int min, final int max,
                                     final Label dflt, final Label... labels) {
        int[] branchTargetIds = allocateBranchTargetIds(labels.length + 1);
        if (markLabels(dflt, labels)) {
            probesVisitor.visitTableSwitchInsnWithProbes(min, max, dflt,
                    labels, frame(1), currentLine, branchTargetIds);
        } else {
            probesVisitor.visitTableSwitchInsn(min, max, dflt, labels);
        }
    }

    private int nextBranchTargetId(final int line) {
        if (line <= 0) {
            return NO_BRANCH_TARGET;
        }
        int nextId = branchTargetCounterByLine.getOrDefault(line, 0) + 1;
        branchTargetCounterByLine.put(line, nextId);
        return nextId;
    }

    private int[] allocateBranchTargetIds(final int count) {
        if (count <= 0) {
            return new int[0];
        }
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = nextBranchTargetId(currentLine);
        }
        return ids;
    }

    private boolean markLabels(final Label dflt, final Label[] labels) {
        boolean probe = false;
        LabelInfo.resetDone(labels);
        if (LabelInfo.isMultiTarget(dflt)) {
            LabelInfo.setProbeId(dflt, idGenerator.nextId());
            probe = true;
        }
        LabelInfo.setDone(dflt);
        for (final Label l : labels) {
            if (LabelInfo.isMultiTarget(l) && !LabelInfo.isDone(l)) {
                LabelInfo.setProbeId(l, idGenerator.nextId());
                probe = true;
            }
            LabelInfo.setDone(l);
        }
        return probe;
    }

    private IFrame frame(final int popCount) {
        return FrameSnapshot.create(analyzer, popCount);
    }
}
