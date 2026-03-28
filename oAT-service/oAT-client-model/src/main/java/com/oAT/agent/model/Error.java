package com.oAT.agent.model;

import java.io.Serializable;

public class Error implements Serializable {
    private static final long serialVersionUID = -7156032079009497957L;

    private String message;
    private String type;
    private String code;
    private String errorStack;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getErrorStack() {
        return errorStack;
    }

    public void setErrorStack(String errorStack) {
        this.errorStack = errorStack;
    }
}
