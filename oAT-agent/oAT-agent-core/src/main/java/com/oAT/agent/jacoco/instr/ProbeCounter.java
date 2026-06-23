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

import com.oAT.agent.jacoco.flow.ClassProbesVisitor;
import com.oAT.agent.jacoco.flow.MethodProbesVisitor;
import com.oAT.shaded.asm97.Opcodes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Internal class to remember the total number of probes required for a class.
 */
public class ProbeCounter extends ClassProbesVisitor {

    private int count;
    private boolean methods;
    private Map<String, Integer> totalBranchMap; // 分支总数，用于分支覆盖率统计
    private Map<String, int[]> totalBranchTargetMap;

    ProbeCounter() {
        this.count = 0;
        this.methods = false;
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodProbesVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        Set<String> skipMethods = new HashSet(Arrays.asList("<init>", InstrSupport.CLINIT_NAME, "equals", "canEqual", "hashCode", "toString"));
        if (!skipMethods.contains(name) && (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_INTERFACE))  == 0) {
            this.methods = true;
        }

        return new MethodProbesVisitor() {
            @Override
            public void visitLineNumber(int line, com.oAT.shaded.asm97.Label start) {
                super.visitLineNumber(line, start);
            }
        };
    }

    @Override
    public void visitTotalProbeCount(final int count) {
        this.count = count;
    }

    int getCount() {
        return this.count;
    }

    /**
     * @return <code>true</code> if the class has non-abstract methods other
     * than a static initializer
     */
    boolean hasMethods() {
        return methods;
    }

    public Map<String, Integer> getTotalBranchMap() {
        return totalBranchMap;
    }

    public void setTotalBranchMap(Map<String, Integer> totalBranchMap) {
        this.totalBranchMap = totalBranchMap;
    }

    public Map<String, int[]> getTotalBranchTargetMap() {
        return totalBranchTargetMap;
    }

    public void setTotalBranchTargetMap(Map<String, int[]> totalBranchTargetMap) {
        this.totalBranchTargetMap = totalBranchTargetMap;
    }

    // 按需分配探针数组
    public boolean[] createProbes() {
        return new boolean[Math.max(0, count)];
    }
}
