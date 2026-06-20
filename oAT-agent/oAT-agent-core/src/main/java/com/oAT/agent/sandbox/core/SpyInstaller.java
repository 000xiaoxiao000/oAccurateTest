package com.oAT.agent.sandbox.core;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.sandbox.spy.OatSpy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class SpyInstaller {
    private static final Log logger = LogFactory.getLog(SpyInstaller.class);
    private static volatile boolean installed;

    private SpyInstaller() {
    }

    public static synchronized void install(Instrumentation instrumentation, SpyDispatcher dispatcher) {
        if (installed) {
            initSpy(dispatcher);
            return;
        }
        try {
            File spyJar = File.createTempFile("oat-sandbox-spy-", ".jar");
            spyJar.deleteOnExit();
            writeSpyJar(spyJar);
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(spyJar));
            installed = true;
            initSpy(dispatcher);
            logger.info("[Sandbox] spy installed into bootstrap");
        } catch (Throwable t) {
            logger.error("[Sandbox] install spy failed: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            OatSpy.init(dispatcher);
        }
    }

    private static void initSpy(SpyDispatcher dispatcher) {
        try {
            Class<?> spyClass = Class.forName("com.oAT.agent.sandbox.spy.OatSpy", true, null);
            Method init = spyClass.getMethod("init", Object.class);
            init.invoke(null, dispatcher);
        } catch (Throwable t) {
            logger.warn("[Sandbox] bootstrap spy init failed, fallback to local spy: "
                    + StackTraceFormatter.formatExceptionWithAgentMark(t));
            OatSpy.init(dispatcher);
        }
    }

    private static void writeSpyJar(File file) throws Exception {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(file));
        try {
            writeClass(out, "com/oAT/agent/sandbox/spy/OatSpy.class");
            writeClass(out, "com/oAT/agent/sandbox/spy/OatSpy$SpyHandler.class");
        } finally {
            out.close();
        }
    }

    private static void writeClass(JarOutputStream out, String resourceName) throws Exception {
        InputStream in = SpyInstaller.class.getClassLoader().getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalStateException("missing spy resource: " + resourceName);
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
