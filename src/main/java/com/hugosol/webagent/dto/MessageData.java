package com.hugosol.webagent.dto;

import com.hugosol.webagent.model.MessageRole;

import java.io.Serializable;

public class MessageData implements Serializable {
    private int messageId;
    private MessageRole role;
    private String content;
    private long timestamp;

    public MessageData() {}

    public MessageData(MessageRole role, String content, int messageId) {
        this.role = role;
        this.content = content;
        this.messageId = messageId;
        this.timestamp = System.currentTimeMillis();
    }

    public int getMessageId() { return messageId; }
    public void setMessageId(int messageId) { this.messageId = messageId; }

    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
