package com.oAT.web.api.snapshot;

public class DeleteSnapshotCommentRequest {
    private String content;
    private String dateTime;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDateTime() { return dateTime; }
    public void setDateTime(String dateTime) { this.dateTime = dateTime; }
}
