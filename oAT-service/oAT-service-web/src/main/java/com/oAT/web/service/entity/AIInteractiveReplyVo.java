package com.oAT.web.service.entity;

import java.io.Serializable;
import java.util.List;

public class AIInteractiveReplyVo implements Serializable {
    private String question;
    private String answer;
    private String topic;
    private List<String> suggestions;
    private List<AIQuickLinkVo> quickLinks;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public List<AIQuickLinkVo> getQuickLinks() {
        return quickLinks;
    }

    public void setQuickLinks(List<AIQuickLinkVo> quickLinks) {
        this.quickLinks = quickLinks;
    }
}
