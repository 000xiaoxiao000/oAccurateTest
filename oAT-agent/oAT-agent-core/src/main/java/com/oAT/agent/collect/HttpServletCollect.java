package com.oAT.agent.collect;

import com.oAT.agent.collect.http.*;
import com.oAT.agent.common.NetUtils;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.context.AgentContext;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.jacoco.data.StackNodeVoBuilder;
import com.oAT.agent.model.HttpTraceNode;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.model.TraceNode;
import com.oAT.agent.trace.ISessionDestroy;
import com.oAT.agent.trace.TraceContext;
import com.oAT.agent.trace.TraceRequest;
import com.oAT.agent.trace.TraceSession;
import com.oAT.shaded.javassist.CtClass;
import com.oAT.shaded.javassist.CtMethod;

import javax.servlet.ServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;


public class HttpServletCollect extends AbstractByteTransformCollect {
    private final static Log logger = LogFactory.getLog(HttpServletCollect.class);
    private static final Map<String, HttpServletTraceNodeWrapper> PENDING_ASYNC_HTTP_NODES = new ConcurrentHashMap<>();

    public static HttpServletCollect INSTANCE;
    private final List<String> httpDrivers;
    private static String TARGET_CLASS = "";
    private static String TARGET_METHOD = "";
    // 支持更多 Servlet 容器的入口类和方法
    private static final Map<String, String> CLASS_AND_METHOD_MAP = new HashMap<String, String>() {{
        put("javax.servlet.http.HttpServlet", "service");
        put("jakarta.servlet.http.HttpServlet", "service");
        put("org.eclipse.jetty.server.handler.HandlerWrapper", "handle");
        put("com.alipay.sofa.ark.web.embed.tomcat.ArkTomcatServlet", "service");
        // 可扩展更多
    }};

    private static final String JAVAX_SERVLET_BEGIN_SRC;
    private static final String JAKARTA_SERVLET_BEGIN_SRC;
    private static final String JETTY_BEGIN_SRC;
    private static final String ALIBABA_BEGIN_SRC;
    private static String BEGIN_SRC;
    private static final String END_SRC;
    private static final String ERROR_SRC;
    private static final String DEFAULT_EXCLUDE = ".*\\.(css|js|gif|png|jpg|jpeg|pdf|swf|flv|rm|mov|mpeg|mp3|mp4)$";

    static {
        JAVAX_SERVLET_BEGIN_SRC =
                HttpServletCollect.class.getName() + " instance = " + HttpServletCollect.class.getName() +
                        ".INSTANCE;\r\n" +
                        // 包裹response，必须在业务代码前
                        "com.oAT.agent.collect.http.JavaxServletResponseWrapper " +
                        "customizeResponse = " +
                        "new com.oAT.agent.collect.http.JavaxServletResponseWrapper($2);" +
                        "\r\n" +
                        "$2 = customizeResponse;\r\n" +
                        "com.oAT.agent.collect.http.JavaxServletRequestWrapper " +
                        "customizeRequest = " +
                        "new com.oAT.agent.collect.http.JavaxServletRequestWrapper($1, customizeResponse);\r\n" +
                        "$1 = customizeRequest;\r\n" +
                        HttpServletTraceNodeWrapper.class.getName() + " node = instance.begin(new Object[]{$1, $2});";
        JAKARTA_SERVLET_BEGIN_SRC =
                HttpServletCollect.class.getName() + " instance = " + HttpServletCollect.class.getName() +
                        ".INSTANCE;\r\n" +
                        "com.oAT.agent.collect.http.JakartaHttpServletRequestWrapper " +
                        "customizeRequest = " +
                        "new com.oAT.agent.collect.http.JakartaHttpServletRequestWrapper($1);" +
                        "\r\n" +
                        "$1 = customizeRequest;\r\n" +
                        // 包裹response，必须在业务代码前
                        "com.oAT.agent.collect.http.JakartaHttpServletResponseWrapper " +
                        "customizeResponse = " +
                        "new com.oAT.agent.collect.http.JakartaHttpServletResponseWrapper($2);" +
                        "\r\n" +
                        "$2 = customizeResponse;\r\n" +
                        HttpServletTraceNodeWrapper.class.getName() + " node = instance.begin(new Object[]{$1, $2});";

        JETTY_BEGIN_SRC = HttpServletCollect.class.getName() + " instance = " + HttpServletCollect.class.getName() +
                ".INSTANCE;\r\n" +
                HttpServletTraceNodeWrapper.class.getName() + " node = instance.begin($args);";

        ALIBABA_BEGIN_SRC = HttpServletCollect.class.getName() + " instance = " + HttpServletCollect.class.getName() +
                ".INSTANCE;\r\n" +
                HttpServletTraceNodeWrapper.class.getName() + " node = instance.begin($args);";

        END_SRC = "instance.end(node, $args);";
        ERROR_SRC = "instance.error(node, e);";
    }

    private final TraceContext traceContext;

    public HttpServletCollect(TraceContext context, Instrumentation instrumentation, String... httpDrivers) {
        super(instrumentation);
        this.traceContext = context;
        this.httpDrivers = Arrays.asList(httpDrivers);
    }

    public HttpServletTraceNodeWrapper begin(Object[] params) {
        if (traceContext == null) {
            return null;
        }
        TraceSession traceSession = traceContext.getTraceSession();
        HttpServletRequestAdapter requestAdapter;
        Object req = params[0];
        String reqClassName = req.getClass().getName();
        try {
            // 兼容所有主流 Servlet 容器和 alibaba 等特殊实现
            if ("javax.servlet.ServletRequest".equals(reqClassName) || req instanceof ServletRequest) {
                if (!reqClassName.contains("Wrapper")) {
                    try {
                        req = new JavaxServletRequestWrapper((ServletRequest) req);
                        params[0] = req;
                    } catch (Throwable ignore) {
                    }
                }
                requestAdapter = new HttpServletRequestAdapter(req);
            } else if ("jakarta.servlet.http.HttpServletRequest".equals(reqClassName) || req instanceof jakarta.servlet.http.HttpServletRequest) {
                if (!reqClassName.contains("Wrapper")) {
                    try {
                        req = new JakartaHttpServletRequestWrapper((jakarta.servlet.http.HttpServletRequest) req);
                        params[0] = req;
                    } catch (Throwable ignore) {
                    }
                }
                requestAdapter = new HttpServletRequestAdapter(req);
            } else if ("org.eclipse.jetty.server.Request".equals(reqClassName)) {
                // Jetty
                requestAdapter = new HttpServletRequestAdapter(req);
            } else if (reqClassName.startsWith("org.jboss.")) {
                // JBoss
                requestAdapter = new HttpServletRequestAdapter(req);
            } else if (reqClassName.startsWith("com.alipay.sofa.ark.web.embed.tomcat")) {
                // Alibaba Ark
                requestAdapter = new HttpServletRequestAdapter(req);
            } else {
                // 兼容更多自定义 Servlet 实现
                requestAdapter = new HttpServletRequestAdapter(req);
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]requestAdapter init error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }
        String urlExcludeRegex = this.traceContext.getConfig("urlExclude", DEFAULT_EXCLUDE);
        try {
            String uri = requestAdapter.getRequestURI();
            if (uri == null) {
                return null;
            }
            if (Pattern.matches(urlExcludeRegex, uri)) {
                return null;
            }
            if ("/actuator/health".equals(uri)) {
                return null;
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]urlExclude pattern error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            return null;
        }

        String userHeader = requestAdapter.getAttribute("userheader");
        if (userHeader == null) {
            userHeader = requestAdapter.getHeader("userheader");
        }
        String traceId = requestAdapter.getAttribute("parentTraceId");
        if (traceId == null) {
            traceId = requestAdapter.getHeader("parentTraceId");
        }
        String parentTraceNodeId = requestAdapter.getAttribute("parentTraceNodeId");
        if (parentTraceNodeId == null) {
            parentTraceNodeId = requestAdapter.getHeader("parentTraceNodeId");
        }
        String traceNodeId = "0";
        TraceRequest traceRequest;
        if (traceSession == null) {
            traceRequest = new TraceRequest();
            traceRequest.setParentNodeCallId("0");
            if (traceId == null) {
                String createTraceId = traceContext.createTraceId();
                traceRequest.setTraceId(createTraceId);
                requestAdapter.setAttribute("parentTraceId", createTraceId);
            } else {
                traceRequest.setTraceId(traceId);
                requestAdapter.setAttribute("parentTraceId", traceId);
            }
            traceRequest.setProperties(new Properties());
            traceRequest.setUserHeader(userHeader);
            requestAdapter.setAttribute("userheader", userHeader);
            traceSession = this.traceContext.openTraceSession(TARGET_CLASS, TARGET_METHOD, traceRequest);
            if (traceId != null) {
                traceNodeId = parentTraceNodeId + ".remote";
            }
        } else {
            traceNodeId = traceSession.getNextNodeId();
            traceRequest = traceSession.getTraceRequest();
            traceSession = traceContext.openTraceSession(TARGET_CLASS, TARGET_METHOD, traceRequest);
            userHeader = traceRequest.getUserHeader();
        }
        requestAdapter.setAttribute("parentTraceNodeId", traceNodeId);

        HttpTraceNode node = new HttpTraceNode();
        node.setTraceId(traceSession.getTraceId());
        node.setTraceNodeId(traceNodeId);
        node.setBeginTime(System.currentTimeMillis());
        node.setRequestMethod(requestAdapter.getMethod());
        node.setRequestUrl(requestAdapter.getRequestURL());
        node.setClientIp(requestAdapter.getClientIp());
        node.setServerIp(NetUtils.getLocalHost());
        node.setServerPort(String.valueOf(requestAdapter.getServerPort()));

        //设置 http 参数
        try {
            boolean collectParams = Boolean.parseBoolean(this.traceContext.getConfig(
                    "collect.httpRequestParams", "true"));
            if (collectParams) {
                Map<String, String[]> paramMap = requestAdapter.getParameterMap();
                node.setRequestParamNames(paramMap.keySet().toArray(new String[0]));
                String[] values = new String[paramMap.size()];
                int i = 0;
                for (String key : paramMap.keySet()) {
                    String[] v = paramMap.get(key);
                    String val = (v != null && v.length == 1) ? v[0] : Arrays.toString(v);
                    if (val != null && val.length() > 2048) {
                        val = val.substring(0, 2048) + "...";
                    }
                    values[i++] = val;
                }
                node.setRequestParamValues(values);
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]获取 RequestParameter 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }

        boolean collectBody = Boolean.parseBoolean(this.traceContext.getConfig(
                "collect.httpRequestBody", "true"));
        if (collectBody) {
            String body = requestAdapter.getInputStream();
            if (body != null && body.length() > 8192) {
                body = body.substring(0, 8192) + "...";
            }
            node.setRequestBody(body);
        }

        Object[] cookies = requestAdapter.getCookies();
        StringBuilder cookieStr = new StringBuilder();
        if (cookies != null) {
            Set<Object> cookieset = new HashSet<>();
            Collections.addAll(cookieset, cookies);
            for (Object cookie : cookieset) {
                String k;
                String v;
                try {
                    k = String.valueOf(cookie.getClass().getMethod("getName").invoke(cookie));
                    v = String.valueOf(cookie.getClass().getMethod("getValue").invoke(cookie));
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]获取 Cookie 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                    continue;
                }
                cookieStr.append(k).append("=").append(v).append("; ");
            }
        }

        HttpTraceNode.RequestHeader requestHeader = new HttpTraceNode.RequestHeader();
        requestHeader.setCookie(cookieStr.toString());
        requestHeader.setReferer(requestAdapter.getHeader("referer"));
        requestHeader.setUserAgent(requestAdapter.getHeader("User-Agent"));
        requestHeader.setAuthorization(requestAdapter.getHeader("Authorization"));
        requestHeader.setUserHeader(userHeader);
        node.setRequestHeader(requestHeader);
        node.setAjax(requestAdapter.getHeader("x-requested-with") != null);
        node.setParentTraceId(requestAdapter.getHeader("parentTraceId"));

        HttpServletTraceNodeWrapper nodeWrapper = new HttpServletTraceNodeWrapper(traceSession, node);
        if (traceId == null && (StringUtils.hasText(this.traceContext.getConfig("codeStack.include"))
                || StringUtils.hasText(this.traceContext.getConfig("conf_codeStack.include")))) {
            nodeWrapper.coverageCollector = CoverageCollector.begin();
            AgentContext.setCoverageCollector(nodeWrapper.coverageCollector);
            AgentContext.setActiveAsyncTaskCount(new java.util.concurrent.atomic.AtomicInteger(0));
        }

        if (Boolean.parseBoolean(this.traceContext.getConfig("collect.systemLog", "true"))) {
            OutputStream logOut = new ByteArrayOutputStream(8192);
            SystemLogCollect.INSTANCE.setOutput(logOut);
            nodeWrapper.logOut = logOut;
        }
        return nodeWrapper;
    }

    public void end(HttpServletTraceNodeWrapper nodeWrapper, Object[] params) {
        if (nodeWrapper == null || this.traceContext.getTraceSession() == null) {
            return;
        }
        boolean deferred = false;
        try {
            TraceSession traceSession = traceContext.getTraceSession();
            HttpTraceNode node = nodeWrapper.node;
            node.setEndTime(System.currentTimeMillis());
            long userTime = node.getEndTime() - node.getBeginTime();
            node.setUseTime(userTime);

            HttpServletResponseAdapter responseAdapter = null;
            try {
                Object rawResponse = resolveResponseWrapper(params);
                if (rawResponse != null) {
                    responseAdapter = new HttpServletResponseAdapter(rawResponse);
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]获取响应异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }

            if (responseAdapter != null) {
                try {
                    node.setResponseCode(String.valueOf(responseAdapter.getStatus()));
                } catch (Throwable ignore) {
                }
                // 响应体采集增强
                try {
                    boolean collectRespBody = Boolean.parseBoolean(this.traceContext.getConfig(
                            "collect.httpResponseBody", "true"));
                    if (collectRespBody) {
                        String respBody = responseAdapter.getResponseBody();
                        if (respBody != null && respBody.length() > 8192) {
                            respBody = respBody.substring(0, 8192) + "...";
                        }
                        if (respBody != null) {
                            node.setResponseContent(respBody);
                        }
                    }
                } catch (Throwable ignore) {
                }
            }

            if (node.getError() != null) {
                node.setStatus(TraceNode.Status.fail.toString());
            } else {
                node.setStatus(TraceNode.Status.succeed.toString());
            }
            if (nodeWrapper.coverageCollector != null) {
                boolean hasPendingAsyncTasks = AgentContext.hasPendingAsyncTasks();
                if (!hasPendingAsyncTasks) {
                    finalizeCoverageAndSaveNode(nodeWrapper, traceSession);
                } else {
                    deferred = true;
                    nodeWrapper.markDeferred();
                    PENDING_ASYNC_HTTP_NODES.put(traceSession.getTraceId(), nodeWrapper);
                    logger.info("[Agent-info]检测到异步任务仍在执行，延迟当前 HTTP 请求的覆盖率汇总，pendingAsyncTasks="
                            + AgentContext.getActiveAsyncTaskCount().get());
                }
            } else {
                traceSession.saveNode(node);
            }
        } catch (Throwable e) {
            logger.error("[Agent-EXCError]HttpServletCollect end error: " + StackTraceFormatter.formatExceptionWithAgentMark(e));
        } finally {
            if (!deferred) {
                try {
                    //关闭会话
                    nodeWrapper.doDestroy();
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]doDestroy error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        }
    }

    public void error(HttpServletTraceNodeWrapper nodeWrapper, Throwable e) {
        if (nodeWrapper == null || this.traceContext.getTraceSession() == null) {
            return;
        }
        try {
            nodeWrapper.node.setError(buildError(e));
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]error method error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public static void tryFinalizeDeferredNode() {
        if (INSTANCE == null) {
            return;
        }
        TraceSession traceSession = AgentContext.getTraceSession();
        if (traceSession == null) {
            return;
        }
        if (AgentContext.hasPendingAsyncTasks()) {
            return;
        }
        HttpServletTraceNodeWrapper nodeWrapper = PENDING_ASYNC_HTTP_NODES.remove(traceSession.getTraceId());
        if (nodeWrapper == null) {
            return;
        }
        try {
            INSTANCE.finalizeCoverageAndSaveNode(nodeWrapper, traceSession);
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]异步覆盖率补报失败: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        } finally {
            try {
                nodeWrapper.clearDeferred();
                nodeWrapper.doDestroy();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]异步补报 doDestroy 失败: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }
    }

    private void finalizeCoverageAndSaveNode(HttpServletTraceNodeWrapper nodeWrapper, TraceSession traceSession) {
        if (nodeWrapper == null || traceSession == null) {
            return;
        }
        HttpTraceNode node = nodeWrapper.node;
        if (nodeWrapper.logOut != null) {
            String logStr = nodeWrapper.logOut.toString().trim();
            if (logStr.length() > 1024) {
                logStr = logStr.substring(0, 1024) + "...";
            }
            node.setLog(logStr);
        }
        if (nodeWrapper.coverageCollector != null) {
            CoverageCollector collector = AgentContext.getCoverageCollector();
            if (collector != null) {
                nodeWrapper.coverageCollector = collector;
            }
            nodeWrapper.coverageCollector.collectSnapshots();
            if (!nodeWrapper.coverageCollector.getProbeSnapshots().isEmpty()) {
                try {
                    StackNodeVo[] codeNodes = new StackNodeVoBuilder()
                            .buildCodeNodes(nodeWrapper.coverageCollector);
                    node.setCodeNodes(codeNodes);
                } catch (Throwable t) {
                    logger.error("[Agent-EXCError]buildCodeNodes 异常: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                }
            }
        }
        traceSession.saveNode(node);
    }

    private Object resolveResponseWrapper(Object[] params) {
        if (params == null) {
            return null;
        }
        if (params.length > 2 && isResponseWrapper(params[2])) {
            return params[2];
        }
        if (params.length > 1 && isResponseWrapper(params[1])) {
            return params[1];
        }
        return null;
    }

    private boolean isResponseWrapper(Object candidate) {
        if (candidate == null) {
            return false;
        }
        String className = candidate.getClass().getName();
        return "com.oAT.agent.collect.http.JavaxServletResponseWrapper".equals(className)
                || "com.oAT.agent.collect.http.JavaxHttpServletResponseWrapper".equals(className)
                || "com.oAT.agent.collect.http.JakartaHttpServletResponseWrapper".equals(className);
    }

    public class HttpServletTraceNodeWrapper implements ISessionDestroy {
        private final TraceSession traceSession;
        private final HttpTraceNode node;
        private CoverageCollector coverageCollector;
        private OutputStream logOut;
        private boolean deferred;
        private boolean destroyed;

        public HttpServletTraceNodeWrapper(TraceSession traceSession, HttpTraceNode node) {
            this.traceSession = traceSession;
            this.node = node;
        }

        public void markDeferred() {
            this.deferred = true;
        }

        public void clearDeferred() {
            this.deferred = false;
        }

        @Override
        public synchronized void doDestroy() {
            if (destroyed) {
                return;
            }
            if (deferred) {
                return;
            }
            destroyed = true;
            try {
                if (coverageCollector != null) {
                    CoverageCollector.remove();
                }
                AgentContext.removeCoverageCollector();
                AgentContext.removeActiveAsyncTaskCount();
                if (logOut != null) {
                    SystemLogCollect.INSTANCE.removeOutput(logOut);
                }
                traceContext.closeTraceSession(traceSession);
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]doDestroy error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
        }

        public HttpTraceNode getHttpTraceNode() {
            return node;
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (!this.httpDrivers.contains(className)) {
            return null;
        }
        getJarAndVersion(protectionDomain);
        TARGET_CLASS = className;
        TARGET_METHOD = CLASS_AND_METHOD_MAP.get(className);
        CtClass ctclass = null;
        try {
            ctclass = AgentByteBuild.toCtClass(loader, className, classfileBuffer);
            if (ctclass.isFrozen()) {
                logger.warn("[Agent-warn]Class " + className + " is frozen, cannot instrument.");
                return null;
            }
            CtMethod m = null;
            try {
                if ("javax.servlet.http.HttpServlet".equals(TARGET_CLASS)) {
                    // 判断类加载器特征来区分 Tomcat 和 JBoss
                    String loaderName = loader != null ? loader.getClass().getName() : "";
                    if (loaderName.contains("jboss") || loaderName.contains("JBoss")) {
                        // JBoss 环境
                        m = ctclass.getDeclaredMethod(TARGET_METHOD, new CtClass[]{
                                AgentByteBuild.toCtClass(loader, "javax.servlet.http.HttpServletRequest"),
                                AgentByteBuild.toCtClass(loader, "javax.servlet.http.HttpServletRequest")
                        });
                    } else {
                        // 默认认为是 Tomcat 环境
                        m = ctclass.getDeclaredMethod(TARGET_METHOD, new CtClass[]{
                                AgentByteBuild.toCtClass(loader, "javax.servlet.ServletRequest"),
                                AgentByteBuild.toCtClass(loader, "javax.servlet.ServletResponse")
                        });
                    }
                    BEGIN_SRC = JAVAX_SERVLET_BEGIN_SRC;
                } else if ("jakarta.servlet.http.HttpServlet".equals(TARGET_CLASS)) {
                    m = ctclass.getDeclaredMethod(TARGET_METHOD, new CtClass[]{
                            AgentByteBuild.toCtClass(loader, "jakarta.servlet.http.HttpServletRequest"),
                            AgentByteBuild.toCtClass(loader, "jakarta.servlet.http.HttpServletResponse")});
                    BEGIN_SRC = JAKARTA_SERVLET_BEGIN_SRC;
                } else if ("org.eclipse.jetty.server.handler.HandlerWrapper".equals(TARGET_CLASS)) {
                    m = ctclass.getDeclaredMethod("handle", new CtClass[]{
                            AgentByteBuild.toCtClass(loader, "java.lang.String"),
                            AgentByteBuild.toCtClass(loader, "org.eclipse.jetty.server.Request"),
                            AgentByteBuild.toCtClass(loader, "javax.servlet.http.HttpServletRequest"),
                            AgentByteBuild.toCtClass(loader, "javax.servlet.http.HttpServletResponse")
                    });
                    BEGIN_SRC = JETTY_BEGIN_SRC;
                } else if ("com.alipay.sofa.ark.web.embed.tomcat.ArkTomcatServlet".equals(TARGET_CLASS)) {
                    m = ctclass.getDeclaredMethod("service", new CtClass[]{
                            AgentByteBuild.toCtClass(loader, "javax.servlet.ServletRequest"),
                            AgentByteBuild.toCtClass(loader, "javax.servlet.ServletResponse")
                    });
                    BEGIN_SRC = ALIBABA_BEGIN_SRC;
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]getDeclaredMethod error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }

            AgentByteBuild.MethodSrcBuild build = new AgentByteBuild.MethodSrcBuild();
            build.setBeginSrc(BEGIN_SRC);
            build.setErrorSrc(ERROR_SRC);
            build.setEndSrc(END_SRC);

            AgentByteBuild byteLoade = new AgentByteBuild(className, loader, ctclass);
            try {
                if (m != null) {
                    byteLoade.updateMethod(m, build);
                }
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]updateMethod error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
                return null;
            }

            logger.info("[Agent-info]完成 HttpServlet 采集器初始化: " + TARGET_CLASS);

            byte[] byteCode = null;
            try {
                byteCode = byteLoade.toByteCode();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]toByteCode error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            try {
                ctclass.detach();
            } catch (Throwable t) {
                logger.error("[Agent-EXCError]detach error: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
            }
            return byteCode;
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]transform error for class: " + className + StackTraceFormatter.formatExceptionWithAgentMark(t));
            if (ctclass != null) {
                try {
                    ctclass.detach();
                } catch (Throwable ignore) {
                }
            }
            return null;
        }
    }
}
