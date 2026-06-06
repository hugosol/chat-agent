package com.hugosol.chatagent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "fsrs_reschedule_logs")
public class FsrsRescheduleLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    private String optimizeLogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RescheduleStatus status;

    private int totalCardsWithHistory;
    private int rescheduledCards;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    private long durationMs;

    public FsrsRescheduleLog() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getOptimizeLogId() { return optimizeLogId; }
    public void setOptimizeLogId(String optimizeLogId) { this.optimizeLogId = optimizeLogId; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public RescheduleStatus getStatus() { return status; }
    public void setStatus(RescheduleStatus status) { this.status = status; }
    public int getTotalCardsWithHistory() { return totalCardsWithHistory; }
    public void setTotalCardsWithHistory(int totalCardsWithHistory) { this.totalCardsWithHistory = totalCardsWithHistory; }
    public int getRescheduledCards() { return rescheduledCards; }
    public void setRescheduledCards(int rescheduledCards) { this.rescheduledCards = rescheduledCards; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
