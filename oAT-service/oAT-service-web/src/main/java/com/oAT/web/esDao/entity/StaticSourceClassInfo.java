package com.oAT.web.esDao.entity;

import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Map;

public class StaticSourceClassInfo implements Serializable {
    @Field(type = FieldType.Keyword)
    private String classId;
    @Field(type = FieldType.Keyword)
    private String className;
    @Field(type = FieldType.Object)
    private Map<String, StaticSourceMethodInfo> methodMaps;
    @Field(type = FieldType.Text)
    private String sourceCode;

    public String getClassId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Map<String, StaticSourceMethodInfo> getMethodMaps() {
        return methodMaps;
    }

    public void setMethodMaps(Map<String, StaticSourceMethodInfo> methodMaps) {
        this.methodMaps = methodMaps;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
}
