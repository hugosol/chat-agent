package com.hugosol.webagent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_progress")
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private Integer totalSessions = 0;

    @Column(nullable = false)
    private Long totalMinutes = 0L;

    @Column(length = 5000)
    private String errorStats;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public UserProgress() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Integer totalSessions) { this.totalSessions = totalSessions; }

    public Long getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(Long totalMinutes) { this.totalMinutes = totalMinutes; }

    public String getErrorStats() { return errorStats; }
    public void setErrorStats(String errorStats) { this.errorStats = errorStats; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
