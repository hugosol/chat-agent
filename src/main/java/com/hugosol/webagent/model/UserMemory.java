package com.hugosol.webagent.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_memory")
public class UserMemory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MemoryType type;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AgentMode mode;

    @Column(length = 36)
    private String sessionId;

    public UserMemory() {}

    public UserMemory(String userId, MemoryType type, String content, Integer version) {
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.version = version;
    }

    public UserMemory(String userId, MemoryType type, String content, Integer version, AgentMode mode) {
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.version = version;
        this.mode = mode;
    }

    public UserMemory(String userId, MemoryType type, String content, Integer version, AgentMode mode, String sessionId) {
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.version = version;
        this.mode = mode;
        this.sessionId = sessionId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public MemoryType getType() { return type; }
    public void setType(MemoryType type) { this.type = type; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public AgentMode getMode() { return mode; }
    public void setMode(AgentMode mode) { this.mode = mode; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
