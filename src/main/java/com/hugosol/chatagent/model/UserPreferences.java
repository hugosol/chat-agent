package com.hugosol.chatagent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_preferences")
public class UserPreferences extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private int newCardDailyLimit = 20;

    @Column(nullable = false)
    private int dayStartHour = 6;

    @Column
    private String timezone;

    @Column
    private String lastDeckId;

    @Column
    private String lastMode;

    @Column
    private String learningSteps;

    @Column
    private String relearningSteps;

    @Column
    private Double desiredRetention;

    @Column
    private Integer maximumInterval;

    @Column
    private Boolean enableFuzz;

    @Column
    private Boolean shuffleDueCards;

    public UserPreferences() {
    }

    public UserPreferences(String userId) {
        this.userId = userId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getNewCardDailyLimit() { return newCardDailyLimit; }
    public void setNewCardDailyLimit(int newCardDailyLimit) { this.newCardDailyLimit = newCardDailyLimit; }
    public int getDayStartHour() { return dayStartHour; }
    public void setDayStartHour(int dayStartHour) { this.dayStartHour = dayStartHour; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getLastDeckId() { return lastDeckId; }
    public void setLastDeckId(String lastDeckId) { this.lastDeckId = lastDeckId; }
    public String getLastMode() { return lastMode; }
    public void setLastMode(String lastMode) { this.lastMode = lastMode; }
    public String getLearningSteps() { return learningSteps; }
    public void setLearningSteps(String learningSteps) { this.learningSteps = learningSteps; }
    public String getRelearningSteps() { return relearningSteps; }
    public void setRelearningSteps(String relearningSteps) { this.relearningSteps = relearningSteps; }
    public Double getDesiredRetention() { return desiredRetention; }
    public void setDesiredRetention(Double desiredRetention) { this.desiredRetention = desiredRetention; }
    public Integer getMaximumInterval() { return maximumInterval; }
    public void setMaximumInterval(Integer maximumInterval) { this.maximumInterval = maximumInterval; }
    public Boolean getEnableFuzz() { return enableFuzz; }
    public void setEnableFuzz(Boolean enableFuzz) { this.enableFuzz = enableFuzz; }
    public Boolean getShuffleDueCards() { return shuffleDueCards; }
    public void setShuffleDueCards(Boolean shuffleDueCards) { this.shuffleDueCards = shuffleDueCards; }
}
