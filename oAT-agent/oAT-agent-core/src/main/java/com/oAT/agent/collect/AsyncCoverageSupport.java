package com.oAT.agent.collect;

import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.StringUtils;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;
import com.oAT.agent.context.AgentContext;
import com.oAT.agent.jacoco.CoverageCollector;
import com.oAT.agent.jacoco.data.StackNodeVoBuilder;
import com.oAT.agent.model.CodeNodeBean;
import com.oAT.agent.model.StackNodeVo;
import com.oAT.agent.trace.TraceContext;

import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncCoverageSupport {
    private static final Log logger = LogFactory.getLog(AsyncCoverageSupport.class);

    private AsyncCoverageSupport() {
    }

    public static boolean enabled(TraceContext traceContext) {
        return traceContext != null
                && (StringUtils.hasText(traceContext.getConfig("codeStack.include"))
                || StringUtils.hasText(traceContext.getConfig("conf_codeStack.include")));
    }

    public static CoverageCollector begin(AgentContext.AsyncCompletionListener listener) {
        CoverageCollector collector = CoverageCollector.begin();
        AgentContext.setCoverageCollector(collector);
        AgentContext.setActiveAsyncTaskCount(new AtomicInteger(0));
        AgentContext.setAsyncCompletionListener(listener);
        return collector;
    }

    public static void collect(CodeNodeBean node, CoverageCollector collector) {
        if (node == null || collector == null) {
            return;
        }
        try {
            collector.collectSnapshots();
            if (!collector.getProbeSnapshots().isEmpty()) {
                StackNodeVo[] codeNodes = new StackNodeVoBuilder().buildCodeNodes(collector);
                node.setCodeNodes(codeNodes);
            }
        } catch (Throwable t) {
            logger.error("[Agent-EXCError]异步覆盖率汇总失败: " + StackTraceFormatter.formatExceptionWithAgentMark(t));
        }
    }

    public static void detach(CoverageCollector collector) {
        if (collector != null) {
            CoverageCollector.remove();
        }
        AgentContext.removeCoverageCollector();
        AgentContext.removeActiveAsyncTaskCount();
        AgentContext.removeAsyncCompletionListener();
    }
}
