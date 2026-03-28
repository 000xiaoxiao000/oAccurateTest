package com.oAT.web.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassStructure implements java.io.Serializable{
    private String className;
    private String superName;
    private String[] interfaces;
    private int access;
    private List<InvokerMethod> invokers;

    public ClassStructure(String className, int access) {
        this.className = className;
        this.access = access;
        invokers = new ArrayList<>();
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSuperName() {
        return superName;
    }

    public void setSuperName(String superName) {
        this.superName = superName;
    }

    public String[] getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(String[] interfaces) {
        this.interfaces = interfaces;
    }

    public int getAccess() {
        return access;
    }

    public void setAccess(int access) {
        this.access = access;
    }

    public List<InvokerMethod> getInvokers() {
        return invokers;
    }

    public void setInvokers(List<InvokerMethod> invokers) {
        this.invokers = invokers;
    }

    public void addInvokerMethod(String owner, String name, int invokerCode) {
        invokers.add(new InvokerMethod(owner, name, invokerCode));
    }

    // 方法调用
    public class InvokerMethod implements java.io.Serializable {
        private String owner; // 方法所属类
        private String name;  // 方法名称
        /**
         * 调用类型
         * int INVOKEVIRTUAL = 182; // visitMethodInsn
         * int INVOKESPECIAL = 183; // -
         * int INVOKESTATIC = 184; // -
         * int INVOKEINTERFACE = 185; // -
         * int INVOKEDYNAMIC = 186; // visitInvokeDynamicInsn
         */
        private int invokerCode;

        public InvokerMethod(String owner, String name, int invokerCode) {
            this.owner = owner.replaceAll("/",".");
            this.name = name;
            this.invokerCode = invokerCode;
        }


        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public int getInvokerCode() {
            return invokerCode;
        }

        @Override
        public String toString() {
            return "InvokerMethod{" +
                   "owner='" + owner + '\'' +
                   ", name='" + name + '\'' +
                   ", invokerCode=" + invokerCode +
                   '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {return true;}
            if (o == null || getClass() != o.getClass()){ return false;}

            InvokerMethod that = (InvokerMethod) o;

            if (invokerCode != that.invokerCode){ return false;}
            if (!owner.equals(that.owner)){ return false;}
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            int result = owner.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + invokerCode;
            return result;
        }
    }

    @Override
    public String toString() {
        return "ClassStructure{" +
               "className='" + className + '\'' +
               ", superName='" + superName + '\'' +
               ", interfaces=" + Arrays.toString(interfaces) +
               ", access=" + access +
               ", invokers=" + invokers +
               '}';
    }

}
