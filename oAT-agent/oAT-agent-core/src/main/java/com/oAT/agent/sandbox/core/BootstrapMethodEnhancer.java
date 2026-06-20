package com.oAT.agent.sandbox.core;

import com.oAT.shaded.asm97.MethodVisitor;

public interface BootstrapMethodEnhancer {
    MethodVisitor create(MethodVisitor methodVisitor, int access, String name, String descriptor);
}
