package com.hugosol.webagent.graph;

import java.io.Serializable;

public class MessageData implements Serializable {
    private String role;
    private String content;
    private long timestamp;

    public MessageData() {}

    public MessageData(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
