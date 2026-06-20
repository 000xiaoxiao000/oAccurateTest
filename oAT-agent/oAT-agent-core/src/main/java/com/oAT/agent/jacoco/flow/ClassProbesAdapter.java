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
package com.oAT.agent.jacoco.flow;

import com.oAT.agent.jacoco.instr.InstrSupport;
import com.oAT.shaded.asm97.ClassVisitor;
import com.oAT.shaded.asm97.Label;
import com.oAT.shaded.asm97.MethodVisitor;
import com.oAT.shaded.asm97.Opcodes;
import com.oAT.shaded.asm97.commons.AnalyzerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ClassVisitor} that calculates probes for every
 * method.
 */
public class ClassProbesAdapter extends ClassVisitor implements IProbeIdGenerator {

    private static final MethodProbesVisitor EMPTY_METHOD_PROBES_VISITOR = new MethodProbesVisitor() {
    };

    private final ClassProbesVisitor cv;

    private final boolean trackFrames;

    private int counter = 0;

    private String name;

    public Map<String, Integer> totalBranchMap = new HashMap(); // 分支总数，用于分支覆盖率统计

    /**
     * Creates a new adapter that delegates to the given visitor.
     *
     * @param cv          instance to delegate to
     * @param trackFrames if <code>true</code> stackmap frames are tracked and provided
     */
    public ClassProbesAdapter(final ClassProbesVisitor cv,
                              final boolean trackFrames) {
        super(InstrSupport.ASM_API_VERSION, cv);
        this.cv = cv;
        this.trackFrames = trackFrames;
    }

    @Override
    public void visit(final int version, final int access, final String name,
                      final String signature, final String superName,
                      final String[] interfaces) {
        this.name = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public final MethodVisitor visitMethod(final int access, final String name,
                                           final String desc, final String signature, final String[] exceptions) {
        final MethodProbesVisitor methodProbes;
        final MethodProbesVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        if (mv == null) {
            // We need to visit the method in any case, otherwise probe ids
            // are not reproducible
            methodProbes = EMPTY_METHOD_PROBES_VISITOR;
        } else {
            methodProbes = mv;
        }
        return new MethodSanitizer(null, access, name, desc, signature,
                exceptions) {
            int currentLine = -1;

            @Override
            public void visitLineNumber(final int line, final Label start) {
                currentLine = line;
                super.visitLineNumber(line, start);
            }

            @Override
            public void visitJumpInsn(final int opcode, final Label label) {
                if ((opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE) || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL) {
                    if (signature == null || desc != null) {
                        totalBranchMap.put(name + " " + desc + " " + currentLine, currentLine);
                    } else {
                        totalBranchMap.put(name + " " + signature + " " + currentLine, currentLine);
                    }
                }
                super.visitJumpInsn(opcode, label);
            }

            @Override
            public void visitEnd() {
                super.visitEnd();
                LabelFlowAnalyzer.markLabels(this);
                final MethodProbesAdapter probesAdapter = new MethodProbesAdapter(methodProbes,
                        ClassProbesAdapter.this);
                if (trackFrames) {
                    final AnalyzerAdapter analyzer = new AnalyzerAdapter(ClassProbesAdapter.this.name, access, name,
                            desc, probesAdapter);
                    probesAdapter.setAnalyzer(analyzer);
                    this.accept(analyzer);
                } else {
                    this.accept(probesAdapter);
                }
            }
        };
    }

    @Override
    public void visitEnd() {
        cv.visitTotalProbeCount(counter);
        super.visitEnd();
    }

    // === IProbeIdGenerator ===
    @Override
    public int nextId() {
        // System.out.println(counter + 1);
        return counter++;
    }

}
