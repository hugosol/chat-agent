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
@Table(name = "fsrs_optimize_logs")
public class FsrsOptimizeLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptimizeStatus status;

    private int totalReviewLogs;
    private int nonSameDayReviews;
    private int cardSequences;
    private int epochs;
    private int iterations;
    private double finalLoss;
    private double defaultLoss;
    private double lossImprovement;
    private boolean paramsUpdated;

    @Column(length = 4000)
    private String weightsBefore;

    @Column(length = 4000)
    private String weightsAfter;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    private long durationMs;

    public FsrsOptimizeLog() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public OptimizeStatus getStatus() { return status; }
    public void setStatus(OptimizeStatus status) { this.status = status; }
    public int getTotalReviewLogs() { return totalReviewLogs; }
    public void setTotalReviewLogs(int totalReviewLogs) { this.totalReviewLogs = totalReviewLogs; }
    public int getNonSameDayReviews() { return nonSameDayReviews; }
    public void setNonSameDayReviews(int nonSameDayReviews) { this.nonSameDayReviews = nonSameDayReviews; }
    public int getCardSequences() { return cardSequences; }
    public void setCardSequences(int cardSequences) { this.cardSequences = cardSequences; }
    public int getEpochs() { return epochs; }
    public void setEpochs(int epochs) { this.epochs = epochs; }
    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
    public double getFinalLoss() { return finalLoss; }
    public void setFinalLoss(double finalLoss) { this.finalLoss = finalLoss; }
    public double getDefaultLoss() { return defaultLoss; }
    public void setDefaultLoss(double defaultLoss) { this.defaultLoss = defaultLoss; }
    public double getLossImprovement() { return lossImprovement; }
    public void setLossImprovement(double lossImprovement) { this.lossImprovement = lossImprovement; }
    public boolean isParamsUpdated() { return paramsUpdated; }
    public void setParamsUpdated(boolean paramsUpdated) { this.paramsUpdated = paramsUpdated; }
    public String getWeightsBefore() { return weightsBefore; }
    public void setWeightsBefore(String weightsBefore) { this.weightsBefore = weightsBefore; }
    public String getWeightsAfter() { return weightsAfter; }
    public void setWeightsAfter(String weightsAfter) { this.weightsAfter = weightsAfter; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
