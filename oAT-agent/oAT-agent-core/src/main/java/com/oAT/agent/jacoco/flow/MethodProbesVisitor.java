package com.oAT.agent.jacoco.flow;

import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.shaded.asm97.Label;
import com.oAT.shaded.asm97.MethodVisitor;

/**
 * A {@link MethodVisitor} with additional methods to get probe insertion
 * information.
 */
public abstract class MethodProbesVisitor extends MethodVisitor {

	public MethodProbesVisitor() {
		this(null);
	}

	public MethodProbesVisitor(final MethodVisitor mv) {
		super(InstrSupport.ASM_API_VERSION, mv);
	}

	@SuppressWarnings("unused")
	public void visitProbe(final int probeId, final boolean branchProbe,
			final int branchLine, final int branchTargetId) {
	}

	@SuppressWarnings("unused")
	public void visitJumpInsnWithProbe(final int opcode, final Label label,
			final int probeId, final IFrame frame, final int branchLine,
			final int branchTargetId) {
	}

	@SuppressWarnings("unused")
	public void visitJumpInsnWithProbes(final int opcode, final Label label,
			final int takenProbeId, final int fallthroughProbeId,
			final IFrame frame, final int branchLine,
			final int takenBranchTargetId, final int fallthroughBranchTargetId) {
	}

	@SuppressWarnings("unused")
	public void visitInsnWithProbe(final int opcode, final int probeId,
			final int branchLine, final int branchTargetId) {
	}

	@SuppressWarnings("unused")
	public void visitTableSwitchInsnWithProbes(final int min, final int max,
			final Label dflt, final Label[] labels, final IFrame frame,
			final int branchLine, final int[] branchTargetIds) {
	}

	@SuppressWarnings("unused")
	public void visitTableSwitchInsnWithProbes(final int min, final int max,
			final Label dflt, final Label[] labels, final IFrame frame,
			final int branchLine, final int dfltProbeId,
			final int[] labelProbeIds, final int[] branchTargetIds) {
	}

	@SuppressWarnings("unused")
	public void visitLookupSwitchInsnWithProbes(final Label dflt,
			final int[] keys, final Label[] labels, final IFrame frame,
			final int branchLine, final int[] branchTargetIds) {
	}

	@SuppressWarnings("unused")
	public void visitLookupSwitchInsnWithProbes(final Label dflt,
			final int[] keys, final Label[] labels, final IFrame frame,
			final int branchLine, final int dfltProbeId,
			final int[] labelProbeIds, final int[] branchTargetIds) {
	}
}
