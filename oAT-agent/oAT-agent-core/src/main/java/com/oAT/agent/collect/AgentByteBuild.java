package com.oAT.agent.collect;

import com.oAT.agent.common.Assert;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.shaded.javassist.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * agent字节器 主要作用 1、构建代理监听环境；2、为目标类载入代理监听
 */
public class AgentByteBuild {
    private final static Log logger = LogFactory.getLog(AgentByteBuild.class);
    private static Map<Object, ClassPool> classPoolMap = new ConcurrentHashMap();
    private static final Object BOOTSTRAP_LOADER_KEY = new Object();

    private final CtClass ctClass;

    public AgentByteBuild(String className, ClassLoader loader, CtClass ctClazz) {
        this.ctClass = ctClazz;
    }

    /**
     * 插入监听method
     */
    public void updateMethod(CtMethod method, MethodSrcBuild srcBuild) throws CannotCompileException {
        Assert.isTrue(method.getDeclaringClass() == ctClass);
        String methodName = method.getName();
        //重构被代理的方法名称
        //基于原方法复制生成代理方法
        CtMethod agentMethod = CtNewMethod.copy(method, methodName, ctClass, null);
        agentMethod.setName(methodName + "$agent");
        ctClass.addMethod(agentMethod);
        //原方法重置为代理执行
        method.setBody(srcBuild.buildSrc(method));
    }

    /**
     * 生成新的class字节码
     */
    public byte[] toByteCode() throws IOException, CannotCompileException {
        if (logger.isDebugEnabled()) {
            ctClass.writeFile(System.getProperty("user.dir") + "/target");
        }
        return ctClass.toBytecode();
    }

    public static class MethodSrcBuild {
        private String beginSrc;
        private String endSrc;
        private String errorSrc;

        public MethodSrcBuild setBeginSrc(String beginSrc) {
            this.beginSrc = beginSrc;
            return this;
        }

        public MethodSrcBuild setEndSrc(String endSrc) {
            this.endSrc = endSrc;
            return this;
        }

        public MethodSrcBuild setErrorSrc(String errorSrc) {
            this.errorSrc = errorSrc;
            return this;
        }

        public String buildSrc(CtMethod method) {
            try {
                String template = "void".equals(method.getReturnType().getName()) ? voidSource : source;
                String bsrc = beginSrc == null ? "" : beginSrc;
                String eSrc = endSrc == null ? "" : endSrc;
                String errSrc = errorSrc == null ? "" : errorSrc;
                return String.format(template, bsrc, method.getName(), errSrc, eSrc);
            } catch (Throwable e) {
                logger.error("[Agent-EXCError]buildSrc error" + StackTraceFormatter.formatExceptionWithAgentMark(e));
                return "";
            }
        }

        final static String source = "{\n"
                + "       %s"
                + "       Object _result = null;\n"
                + "       try {\n"
                + "            _result = ($w)%s$agent($$);\n"
                + "        } catch (Throwable e) {\n"
                + "            %s"
                + "            throw e;\n"
                + "        }finally{\n"
                + "            %s"
                + "        }\n"
                + "        return ($r) _result;\n"
                + "}\n";

        /**
         * 示例
         * {
         * HttpServletCollect var3 = HttpServletCollect.INSTANCE;
         * JavaxHttpServletRequestWrapper var4 = new JavaxHttpServletRequestWrapper(var1);
         * JavaxHttpServletRequestWrapper var11 = var4;
         * HttpServletCollect.HttpServletTraceNodeWrapper var5 = var3.begin(new Object[]{var4, var2});
         * try {
         * this.service$agent(var11, var2);
         * } catch (Throwable var9) {
         * var3.error(var5, var9);
         * throw var9;
         * } finally {
         * var3.end(var5, new Object[]{var4, var2});
         * }
         * }
         */
        final static String voidSource = "{\n"
                + "        %s"
                + "        try {\n"
                + "            %s$agent($$);\n"
                + "        } catch (Throwable e) {\n"
                + "            %s"
                + "            throw e;\n"
                + "        }finally{\n"
                + "            %s"
                + "        }\n"
                + "}\n";
    }

    public static CtClass toCtClass(ClassLoader loader, String className) throws NotFoundException {
        return toCtClass(loader, className, null);
    }

    public static CtClass toCtClass(ClassLoader loader, String className, byte[] classfileBuffer) throws NotFoundException {
        Object mapKey = loader == null ? BOOTSTRAP_LOADER_KEY : loader;

        if (!classPoolMap.containsKey(mapKey)) {
            ClassPool classPool = new ClassPool();
            if (loader != null) {
                classPool.insertClassPath(new LoaderClassPath(loader));
            } else {
                // 对于 BootstrapClassLoader，添加基本类路径
                classPool.appendSystemPath();
            }
            classPoolMap.put(mapKey, classPool);
        }
        ClassPool cp = classPoolMap.get(mapKey);
        className = className.replaceAll("/", ".");
        if (classfileBuffer != null) {
            try {
                return cp.makeClass(new ByteArrayInputStream(classfileBuffer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return cp.get(className);
    }

}
