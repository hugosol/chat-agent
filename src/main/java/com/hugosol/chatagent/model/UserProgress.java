package com.hugosol.chatagent.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_progress")
public class UserProgress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private Integer totalSessions = 0;

    @Column(nullable = false)
    private Long totalMinutes = 0L;

    @Column(length = 5000)
    private String errorStats;

    public UserProgress() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Integer getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Integer totalSessions) { this.totalSessions = totalSessions; }

    public Long getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(Long totalMinutes) { this.totalMinutes = totalMinutes; }

    public String getErrorStats() { return errorStats; }
    public void setErrorStats(String errorStats) { this.errorStats = errorStats; }
}
