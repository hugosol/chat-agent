package com.hugosol.chatagent.model;

import jakarta.persistence.*;

@Entity
@Table(name = "memory_assertions")
public class MemoryAssertion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private AssertionGroup group;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentMode mode;

    @Column
    private String topic;

    @Column(columnDefinition = "CLOB")
    private String state;

    @Column(nullable = false)
    private boolean enabled = true;

    public MemoryAssertion() {}

    public MemoryAssertion(AssertionGroup group, String sessionId, String userId, AgentMode mode,
                           String topic, String state) {
        this.group = group;
        this.sessionId = sessionId;
        this.userId = userId;
        this.mode = mode;
        this.topic = topic;
        this.state = state;
        this.enabled = true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public AssertionGroup getGroup() { return group; }
    public void setGroup(AssertionGroup group) { this.group = group; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public AgentMode getMode() { return mode; }
    public void setMode(AgentMode mode) { this.mode = mode; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
