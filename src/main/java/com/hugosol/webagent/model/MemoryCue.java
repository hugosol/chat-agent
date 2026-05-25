package com.hugosol.webagent.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "memory_cues")
public class MemoryCue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentMode mode;

    @Column(name = "segment_index", nullable = false)
    private int segmentIndex;

    @Column
    private String topic;

    @Column(columnDefinition = "CLOB")
    private String summary;

    @Column
    @Convert(converter = StringListConverter.class)
    private List<String> tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoryCueStatus status;

    public MemoryCue() {}

    public MemoryCue(String sessionId, String userId, AgentMode mode, int segmentIndex,
                     String topic, String summary, List<String> tags, MemoryCueStatus status) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.mode = mode;
        this.segmentIndex = segmentIndex;
        this.topic = topic;
        this.summary = summary;
        this.tags = tags;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public AgentMode getMode() { return mode; }
    public void setMode(AgentMode mode) { this.mode = mode; }

    public int getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(int segmentIndex) { this.segmentIndex = segmentIndex; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public MemoryCueStatus getStatus() { return status; }
    public void setStatus(MemoryCueStatus status) { this.status = status; }
}
