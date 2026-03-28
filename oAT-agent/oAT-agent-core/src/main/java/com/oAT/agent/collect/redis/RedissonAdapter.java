package com.oAT.agent.collect.redis;

import com.oAT.agent.common.ReflectUtil;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

public class RedissonAdapter {
    private final Object target;
    private final Method _getEntrySet;

    public RedissonAdapter(Object target) {
        this.target = target;
        try {
            _getEntrySet = target.getClass().getMethod("getEntrySet");
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("[Agent-EXCError]error: " + e.getMessage() + ". "
                    + "probable cause the target is not belong " + target.getClass().getName());
        }
    }

    public String[] getAddress() {
        Object getEntrySetObj = ReflectUtil.invoker(_getEntrySet, target);
        if (getEntrySetObj == null) {
            return new String[0];
        }
        List<?> list = (List<?>) getEntrySetObj;
        Object entry = list.get(0);
        try {
            Method getConfigMethod = entry.getClass().getMethod("getConfig");
            Object getConfigObj = ReflectUtil.invoker(getConfigMethod, entry);
            Method getMasterAddressMethod = null;
            if (getConfigObj != null) {
                getMasterAddressMethod = getConfigObj.getClass().getMethod("getMasterAddress");
            }
//            Method getSlaveAddressesMethod = getConfigObj.getClass().getMethod("getSlaveAddresses");
            String getMasterAddressResult = Objects.requireNonNull(ReflectUtil.invoker(getMasterAddressMethod, getConfigObj)).toString();
//            String getSlaveAddressResult = ReflectUtil.invoker(getSlaveAddressesMethod, getConfigObj).toString()
//            .replace("[]","");
//            if(getSlaveAddressResult.isEmpty()){
            return new String[]{getMasterAddressResult};
//            }
//            return new String[]{getMasterAddressResult, getSlaveAddressResult};
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
