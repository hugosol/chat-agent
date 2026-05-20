package com.hugosol.webagent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "session_reports")
public class SessionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String sessionId;

    @Column(length = 3000)
    private String summary;

    private Integer fluencyScore;

    @Column(length = 2000)
    private String vocabularySuggestions;

    @Column(length = 1000)
    private String keyTakeaway;

    @Column(length = 5000)
    private String errorSummary;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public SessionReport() {}

    public SessionReport(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getFluencyScore() { return fluencyScore; }
    public void setFluencyScore(Integer fluencyScore) { this.fluencyScore = fluencyScore; }

    public String getVocabularySuggestions() { return vocabularySuggestions; }
    public void setVocabularySuggestions(String vocabularySuggestions) { this.vocabularySuggestions = vocabularySuggestions; }

    public String getKeyTakeaway() { return keyTakeaway; }
    public void setKeyTakeaway(String keyTakeaway) { this.keyTakeaway = keyTakeaway; }

    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
