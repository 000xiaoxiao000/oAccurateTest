package com.oAT.agent.sandbox.core;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class BootstrapBridgeInstaller {
    private static final Log logger = LogFactory.getLog(BootstrapBridgeInstaller.class);
    private static volatile boolean installed;

    private BootstrapBridgeInstaller() {
    }

    public static synchronized void install(Instrumentation instrumentation, Object bridge) {
        try {
            if (!installed) {
                File bridgeJar = File.createTempFile("oat-context-bridge-", ".jar");
                bridgeJar.deleteOnExit();
                writeBridgeJar(bridgeJar);
                instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(bridgeJar));
                installed = true;
            }
            Class bridgeClass = Class.forName("com.oAT.agent.bootstrap.OatAsyncBridge", true, null);
            Method init = bridgeClass.getMethod("init", new Class[]{Object.class});
            init.invoke(null, new Object[]{bridge});
            logger.info("[Sandbox-ContextPropagation] bootstrap bridge installed");
        } catch (Throwable t) {
            logger.error("[Sandbox-ContextPropagation] bootstrap bridge install failed: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public static void freeze() {
        try {
            Class bridgeClass = Class.forName("com.oAT.agent.bootstrap.OatAsyncBridge", false, null);
            Method freeze = bridgeClass.getMethod("freeze", new Class[0]);
            freeze.invoke(null, new Object[0]);
        } catch (Throwable ignored) {
        }
    }

    private static void writeBridgeJar(File file) throws Exception {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(file));
        try {
            writeClass(out, "com/oAT/agent/bootstrap/OatAsyncBridge.class");
            writeClass(out, "com/oAT/agent/bootstrap/OatContextBridge.class");
        } finally {
            out.close();
        }
    }

    private static void writeClass(JarOutputStream out, String resourceName) throws Exception {
        InputStream in = BootstrapBridgeInstaller.class.getClassLoader().getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalStateException("missing bootstrap bridge resource: " + resourceName);
        }
        try {
            out.putNextEntry(new JarEntry(resourceName));
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.closeEntry();
        } finally {
            in.close();
        }
    }
}
