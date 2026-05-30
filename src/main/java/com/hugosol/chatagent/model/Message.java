package com.hugosol.chatagent.model;

import jakarta.persistence.*;

@Entity
@Table(name = "messages")
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "message_id")
    private Integer messageId;

    private Integer tokenCount;

    public Message() {}

    public Message(String sessionId, MessageRole role, String content, Integer messageId, Integer tokenCount) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.messageId = messageId;
        this.tokenCount = tokenCount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
}
