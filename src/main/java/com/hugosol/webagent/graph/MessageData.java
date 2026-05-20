package com.hugosol.webagent.graph;

import com.hugosol.webagent.model.MessageRole;

import java.io.Serializable;

public class MessageData implements Serializable {
    private MessageRole role;
    private String content;
    private long timestamp;

    public MessageData() {}

    public MessageData(MessageRole role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
