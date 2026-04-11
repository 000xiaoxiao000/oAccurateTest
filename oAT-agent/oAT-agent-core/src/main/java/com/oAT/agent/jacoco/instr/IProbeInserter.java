package com.oAT.agent.jacoco.instr;

/**
 * Internal interface for insertion of probes into in the instruction sequence
 * of a method.
 */
interface IProbeInserter {

	/**
	 * Inserts the probe with the given id.
	 *
	 * @param id id of the probe to insert
	 * @param branchProbe whether this probe represents a JaCoCo-style branch target probe
	 * @param branchLine source branch line for the probe, or -1 if not a branch probe
	 * @param branchTargetId target ordinal within the branch line, or -1 if unavailable
	 */
	void insertProbe(final int id, final boolean branchProbe, final int branchLine,
				 final int branchTargetId);

}
