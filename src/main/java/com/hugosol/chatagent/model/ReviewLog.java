package com.hugosol.chatagent.model;

import com.hugosol.chatagent.flashcard.Rating;
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
@Table(name = "review_logs")
public class ReviewLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(length = 36, nullable = false)
    private String cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rating rating;

    private int stateBefore;
    private int stateAfter;
    private double stabilityBefore;
    private double stabilityAfter;
    private double difficultyBefore;
    private double difficultyAfter;
    private int stepBefore;
    private double scheduledDays;
    private double elapsedDays;

    @Column(nullable = false)
    private Instant reviewedAt;

    private boolean firstReview;

    @Column(length = 36)
    private String deckId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public Rating getRating() { return rating; }
    public void setRating(Rating rating) { this.rating = rating; }
    public int getStateBefore() { return stateBefore; }
    public void setStateBefore(int stateBefore) { this.stateBefore = stateBefore; }
    public int getStateAfter() { return stateAfter; }
    public void setStateAfter(int stateAfter) { this.stateAfter = stateAfter; }
    public double getStabilityBefore() { return stabilityBefore; }
    public void setStabilityBefore(double stabilityBefore) { this.stabilityBefore = stabilityBefore; }
    public double getStabilityAfter() { return stabilityAfter; }
    public void setStabilityAfter(double stabilityAfter) { this.stabilityAfter = stabilityAfter; }
    public double getDifficultyBefore() { return difficultyBefore; }
    public void setDifficultyBefore(double difficultyBefore) { this.difficultyBefore = difficultyBefore; }
    public double getDifficultyAfter() { return difficultyAfter; }
    public void setDifficultyAfter(double difficultyAfter) { this.difficultyAfter = difficultyAfter; }
    public int getStepBefore() { return stepBefore; }
    public void setStepBefore(int stepBefore) { this.stepBefore = stepBefore; }
    public double getScheduledDays() { return scheduledDays; }
    public void setScheduledDays(double scheduledDays) { this.scheduledDays = scheduledDays; }
    public double getElapsedDays() { return elapsedDays; }
    public void setElapsedDays(double elapsedDays) { this.elapsedDays = elapsedDays; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
    public boolean isFirstReview() { return firstReview; }
    public void setFirstReview(boolean firstReview) { this.firstReview = firstReview; }
    public String getDeckId() { return deckId; }
    public void setDeckId(String deckId) { this.deckId = deckId; }
}
