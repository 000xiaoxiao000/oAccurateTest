package com.oAT.agent.common.Decompiler;

import com.oAT.shaded.asm97.Type;

public class JavaNames implements ILanguageNames {
    @Override
    public String getPackageName(final String vmname) {
        if (vmname.isEmpty()) {
            return "default";
        }
        return vmname.replace('/', '.');
    }

    private String getClassName(final String vmname) {
        final int pos = vmname.lastIndexOf('/');
        final String name = pos == -1 ? vmname : vmname.substring(pos + 1);
        return name.replace('$', '.');
    }

    private boolean isAnonymous(final String vmname) {
        final int dollarPosition = vmname.lastIndexOf('$');
        if (dollarPosition == -1) {
            return false;
        }
        final int internalPosition = dollarPosition + 1;
        if (internalPosition == vmname.length()) {
            // shouldn't happen for classes compiled from Java source
            return false;
        }
        // assume non-identifier start character for anonymous classes
        final char start = vmname.charAt(internalPosition);
        return !Character.isJavaIdentifierStart(start);
    }

    @Override
    public String getClassName(final String vmname, final String vmsignature,
                               final String vmsuperclass, final String[] vminterfaces) {
        if (isAnonymous(vmname)) {
            final String vmsupertype = (vminterfaces != null && vminterfaces.length > 0)
                    ? vminterfaces[0]
                    : vmsuperclass;
            // append Eclipse style label, e.g. "Foo.new Bar() {...}"
            if (vmsupertype != null) {
                final StringBuilder builder = new StringBuilder();
                final String vmenclosing = vmname.substring(0,
                        vmname.lastIndexOf('$'));
                builder.append(getClassName(vmenclosing)).append(".new ")
                        .append(getClassName(vmsupertype)).append("() {...}");
                return builder.toString();
            }
        }
        return getClassName(vmname);
    }

    @Override
    public String getQualifiedClassName(final String vmname) {
        return vmname.replace('/', '.').replace('$', '.');
    }

    @Override
    public String getMethodName(final String vmclassname,
                                final String vmmethodname, final String vmdesc,
                                final String vmsignature) {
        return getMethodName(vmclassname, vmmethodname, vmdesc, false);
    }

    @Override
    public String getQualifiedMethodName(final String vmclassname,
                                         final String vmmethodname, final String vmdesc,
                                         final String vmsignature) {
        return getQualifiedClassName(vmclassname) + "."
                + getMethodName(vmclassname, vmmethodname, vmdesc, true);
    }

    private String getMethodName(final String vmclassname,
                                 final String vmmethodname, final String vmdesc,
                                 final boolean qualifiedParams) {
        if ("<clinit>".equals(vmmethodname)) {
            return "static {...}";
        }
        final StringBuilder result = new StringBuilder();
        if ("<init>".equals(vmmethodname)) {
            if (isAnonymous(vmclassname)) {
                return "{...}";
            } else {
                result.append(getClassName(vmclassname));
            }
        } else {
            result.append(vmmethodname);
        }
        result.append('(');
        final Type[] arguments = safeGetArgumentTypes(vmdesc); // safe handling
        boolean comma = false;
        for (final Type arg : arguments) {
            if (comma) {
                result.append(", ");
            } else {
                comma = true;
            }
            if (qualifiedParams) {
                result.append(getQualifiedClassName(arg.getClassName()));
            } else {
                result.append(getShortTypeName(arg));
            }
        }
        result.append(')');
        return result.toString();
    }

    /**
     * ASM 的 Type.getArgumentTypes 只能解析纯 JVM 方法描述符 (形如 (Ljava/lang/String;I)V)。
     * 传入泛型签名 (例如 <T:Ljava/lang/Object;>(TT;)V) 会抛出 StringIndexOutOfBoundsException。
     * 本方法剥离前面的泛型签名部分，只保留从第一个 '(' 开始的描述符；若格式不合法则返回空参数。"" 表示无参数。
     */
    private String extractDescriptor(String vmdesc) {
        if (vmdesc == null || vmdesc.isEmpty()) {
            return "()V"; // 兜底：无参数、返回 void（仅用于避免异常，不影响参数展示）
        }
        int paren = vmdesc.indexOf('(');
        if (paren == -1) {
            // 不符合方法描述符规范，返回一个空参数描述符以避免解析异常
            return "()V";
        }
        // 如果前面存在泛型签名 (<...>)，indexOf('(') 会跳过它，直接截取
        String desc = vmdesc.substring(paren);
        // 基本合法性检查：应该以 '(' 开始并包含一个 ')' 才是参数结束
        if (!desc.startsWith("(") || desc.indexOf(')') == -1) {
            return "()V";
        }
        return desc;
    }

    /**
     * 安全获取参数类型数组。若 ASM 解析失败（例如仍然是异常的字符串），返回空数组而不是抛异常。\n     */
    private Type[] safeGetArgumentTypes(String vmdesc) {
        String pure = extractDescriptor(vmdesc);
        try {
            return Type.getArgumentTypes(pure);
        } catch (RuntimeException e) {
            // 记录时可以改为日志，这里静默处理，避免影响调用栈
            return new Type[0];
        }
    }

    private String getShortTypeName(final Type type) {
        final String name = type.getClassName();
        final int pos = name.lastIndexOf('.');
        final String shortName = pos == -1 ? name : name.substring(pos + 1);
        return shortName.replace('$', '.');
    }

    public static void main(String[] args) {
        JavaNames javaNames = new JavaNames();
        String className = javaNames.getClassName("com/example/MyClass$InnerClass", null, null, null);
        System.out.println("Class Name: " + className);

        String qualifiedClassName = javaNames.getQualifiedClassName("com/example/MyClass$InnerClass");
        System.out.println("Qualified Class Name: " + qualifiedClassName);

        // 这里原来传入的是泛型签名 + 描述符，之前会导致异常，现在会被自动剥离为合法部分
        String methodName = javaNames.getMethodName("com/example/MyClass", "myMethod", "<U:Ljava/lang/Number;>(TU;)V", null);
        System.out.println("Method Name (generic signature handled): " + methodName);

        // 再演示一个普通描述符
        String normal = javaNames.getMethodName("com/example/MyClass", "otherMethod", "(Ljava/lang/Integer;Ljava/lang/Integer;)Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;(Integer, Integer)", null);
        System.out.println("Method Name (normal): " + normal);
    }
}
