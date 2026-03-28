package com.oAT.agent.collect.feign;

import com.oAT.agent.common.Assert;
import com.oAT.agent.common.ReflectUtil;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;

public class ResultAdapter {
    private final static Log logger = LogFactory.getLog(ResultAdapter.class);
    Object target;
    private final Method _getBody;

    public ResultAdapter(Object target) {
        Assert.notNull(target);
        try {
            _getBody = target.getClass().getMethod("body");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong com.apache.dubbo.rpc.RpcInvocation");
        }
        this.target = target;
    }

    public String getBodyString() {
        StringBuilder sb = new StringBuilder();
        try {
            Object targetInvoke = ReflectUtil.invoker(_getBody, this.target);
            if (targetInvoke == null) {
                return "";
            }
            Class<?> aClass = targetInvoke.getClass();
            Method asInputStream = aClass.getMethod("asInputStream");
            AccessibleObject.setAccessible(new AccessibleObject[]{asInputStream}, true);
            InputStream ais = (InputStream) ReflectUtil.invoker(asInputStream, targetInvoke);
            if (ais == null) {
                return "";
            }
            byte[] bytes = new byte[1024];
            int len;
            while ((len = ais.read(bytes)) != -1) {
                sb.append(new String(bytes, 0, len));
            }
        } catch (NoSuchMethodException | IOException e) {
            logger.error("[Agent-getBodyString]error", e);
            return "";
        }
        return sb.toString();
    }

}
