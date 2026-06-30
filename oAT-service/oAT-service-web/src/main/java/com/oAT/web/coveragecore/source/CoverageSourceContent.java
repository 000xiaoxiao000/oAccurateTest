package com.oAT.web.coveragecore.source;

import java.io.Serializable;

public class CoverageSourceContent implements Serializable {
    private String sourcePath;
    private String content;
    private String message;

    public static CoverageSourceContent content(String sourcePath, String content) {
        CoverageSourceContent result = new CoverageSourceContent();
        result.setSourcePath(sourcePath);
        result.setContent(content);
        return result;
    }

    public static CoverageSourceContent message(String message) {
        CoverageSourceContent result = new CoverageSourceContent();
        result.setMessage(message);
        return result;
    }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
