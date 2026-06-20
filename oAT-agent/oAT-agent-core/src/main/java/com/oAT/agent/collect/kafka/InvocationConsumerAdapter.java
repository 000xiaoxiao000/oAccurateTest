package com.oAT.agent.collect.kafka;

import com.oAT.agent.common.ReflectUtil;
import com.oAT.agent.common.StackTraceFormatter;
import com.oAT.agent.common.logger.Log;
import com.oAT.agent.common.logger.LogFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InvocationConsumerAdapter {
    private final static Log logger = LogFactory.getLog(InvocationConsumerAdapter.class);
    private final Object target;
    private final Method _partitions;
    private final Method _records;

    public InvocationConsumerAdapter(Object target) {
        this.target = target;
        try {
            _partitions = target.getClass().getMethod("partitions");
            _records = target.getClass().getMethod("records", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    +"probable cause the target is not belong KafkaMQ");
        }
    }

    public String getPartitions() {
        String tp = "";
        try {
            Set topics = (Set) ReflectUtil.invoker(_partitions, target);
            if (topics == null) {
                return tp;
            }
            for (Object topic : topics) {
                Object tmp = ReflectUtil.invoker(topic.getClass().getMethod("topic"), topic);
                tp = tmp != null ? tmp.toString() : "";
            }
        } catch (NoSuchMethodException e) {
            logger.error("[Agent-EXCError]getPartitions error: "  + StackTraceFormatter.formatExceptionWithAgentMark(e));
            return tp;
        }
        return tp;
    }

    public Object getRecords(String arg0) {
        return ReflectUtil.invoker(_records, target, arg0);
    }

    public Map<String, String> getHeaders(Object recordes) {
        try {
            Method iteratorMethod = recordes.getClass().getMethod("iterator");
            Object iterator = ReflectUtil.invoker(iteratorMethod, recordes);
            if (iterator == null) {
                return new HashMap();
            }
            Method nextMethod = iterator.getClass().getMethod("next");
            Object iteratorHasNex = ReflectUtil.invoker(nextMethod, iterator);
            if (iteratorHasNex == null) {
                return new HashMap();
            }
            Method headersMethod = iteratorHasNex.getClass().getMethod("headers");
            Object iteratorHasNexObj = ReflectUtil.invoker(headersMethod, iteratorHasNex);
            if (iteratorHasNexObj == null) {
                return new HashMap();
            }
            return extractValue(iteratorHasNexObj.toString());
        } catch (NoSuchMethodException e) {
            logger.error("[Agent-EXCError]getHeaders error: "  + StackTraceFormatter.formatExceptionWithAgentMark(e));
            return new HashMap();
        }
    }

    private Map<String, String> extractValue(String recordHeader) {
        Pattern pattern = Pattern.compile("RecordHeader\\(key = (.*?), value = \\[(.*?)]\\)");
        Matcher matcher = pattern.matcher(recordHeader);
        Map<String, String> keyValueMap = new HashMap();
        while (matcher.find()) {
            String key = matcher.group(1);
            String[] byteValues = matcher.group(2).split(", ");
            byte[] values = new byte[byteValues.length];
            for (int i = 0; i < byteValues.length; i++) {
                values[i] = Byte.parseByte(byteValues[i]);
            }
            String value = com.oAT.agent.common.StringUtils.newStringUtf8(values);
            keyValueMap.put(key, value);
        }
        return keyValueMap;
    }
}
