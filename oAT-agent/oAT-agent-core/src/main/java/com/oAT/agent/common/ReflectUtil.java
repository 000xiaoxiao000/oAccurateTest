package com.oAT.agent.common;

import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.lang.reflect.Method;

public class ReflectUtil {
    private final static Log logger = LogFactory.getLog(ReflectUtil.class);

    public static Object invoker(Method method, Object target, Object... args) {
        try {
            // 兼容JDK8/11/17+，避免canAccess/setAccessible警告
            if (!method.isAccessible()) {
                try {
                    method.setAccessible(true);
                } catch (Throwable ignore) {}
            }
            return method.invoke(target, args);
        } catch (Throwable e) {
            logger.error("[Agent-ReflectUtil-invoker]error on " +
                    (target != null ? target.getClass().getName() : "static") +
                    "#" + method.getName() + StackTraceFormatter.formatExceptionWithAgentMark(e));
            return null;
        }
    }
}
