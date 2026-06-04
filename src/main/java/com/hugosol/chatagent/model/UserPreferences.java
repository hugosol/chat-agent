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
}
