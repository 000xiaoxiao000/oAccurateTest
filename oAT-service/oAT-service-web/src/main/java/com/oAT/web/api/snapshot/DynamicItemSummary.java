package com.oAT.web.api.snapshot;

import java.util.Date;

public class DynamicItemSummary {
    private String time;
    private String title;
    private String type;
    private String describe;
    private Date date;

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDescribe() { return describe; }
    public void setDescribe(String describe) { this.describe = describe; }
    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }
}
