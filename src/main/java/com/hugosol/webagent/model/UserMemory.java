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

    public UserMemory() {}

    public UserMemory(String userId, MemoryType type, String content, Integer version) {
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.version = version;
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
}
