package com.oAT.agent.jacoco;

import com.oAT.agent.Agent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class DescriptorParser {

    public String descriptorParser(String className, String methodName) {
        // 提取方法名
        String methodname = methodName.substring(0, methodName.indexOf('(')).trim();

        // 提取参数类型描述符
        String parameterDescriptors = methodName.substring(methodName.indexOf('(') + 1, methodName.indexOf(')'));
        List<Class<?>> parameterTypes = new ArrayList();

        // 解析参数类型描述符
        if (!parameterDescriptors.isEmpty()) {
            String[] descriptors = parameterDescriptors.split(";");
            for (String descriptor : descriptors) {
                if (!descriptor.isEmpty()) {
                    Class<?> clazz = null;
                    switch (descriptor.charAt(0)) {
                        case 'I':
                            clazz = int.class;
                            break;
                        case 'J':
                            clazz = long.class;
                            break;
                        case 'F':
                            clazz = float.class;
                            break;
                        case 'D':
                            clazz = double.class;
                            break;
                        case 'Z':
                            clazz = boolean.class;
                            break;
                        case 'C':
                            clazz = char.class;
                            break;
                        case 'B':
                            clazz = byte.class;
                            break;
                        case 'S':
                            clazz = short.class;
                            break;
                        case 'L':
                            // 去掉前缀'L'
                            String classname = descriptor.substring(1).replace('/', '.');
                            for (Class<?> allLoadedClass : Agent.instrumentation.getAllLoadedClasses()) {
                                if (allLoadedClass.getName().equals(classname)) {
                                    clazz = allLoadedClass;
                                    break;
                                }
                            }
                            break;
                        default:
                            throw new RuntimeException("未知类型描述符: " + descriptor);
                    }
                    parameterTypes.add(clazz);
                }
            }
        }

        // 获取类对象
        Class<?> clazz = null;

        for (Class<?> allLoadedClass : Agent.instrumentation.getAllLoadedClasses()) {
            if (allLoadedClass.getName().equals(className)) {
                clazz = allLoadedClass;
                break;
            }
        }
        if (clazz == null) {
            try {
                throw new ClassNotFoundException("类未找到: " + Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        Method method = null;
        try {
            // 获取方法对象
            method = clazz.getDeclaredMethod(methodname, parameterTypes.toArray(new Class<?>[0]));
            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return simplify(method.getName(), method.toString()); // 简化描述符
    }

    public static String simplify(String methodName, String input) {
        String[] splits = input.split(" ", 2);
        String split = splits[1];

        // 第一步：提取空格前的字符串
        String[] parts = split.split(" ");
        String prefix = parts[0];

        // 第二步：取最后一个点的字符串
        String[] prefixParts = prefix.split("\\.");
        String number = prefixParts[prefixParts.length - 1];

        // 第三步：提取()里的内容
        String paramsPart = parts[1];
        String params = "";
        int startIndex = paramsPart.indexOf('(') + 1;
        int endIndex = paramsPart.indexOf(')');
        if (startIndex < endIndex) {
            params = paramsPart.substring(startIndex, endIndex);
        }
        String[] paramList = params.split(",");

        // 第四步：取最后一个点的字符串
        List<String> finalParams = new ArrayList();
        for (String param : paramList) {
            String[] paramParts = param.split("\\.");
            finalParams.add(paramParts[paramParts.length - 1]);
        }

        // 第五步：组装为字符串
        StringBuilder resultBuilder = new StringBuilder(number).append(" ").append(methodName).append(" (");
        for (int i = 0; i < finalParams.size(); i++) {
            if (i > 0) {
                resultBuilder.append(", ");
            }
            resultBuilder.append(finalParams.get(i));
        }
        resultBuilder.append(")");

        return resultBuilder.toString();
    }

}

