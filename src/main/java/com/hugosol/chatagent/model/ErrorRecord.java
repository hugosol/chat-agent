package com.hugosol.chatagent.model;

import jakarta.persistence.*;

@Entity
@Table(name = "error_records")
public class ErrorRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false, name = "message_db_id")
    private String messageDbId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ErrorType type;

    @Column(nullable = false, length = 2000)
    private String originalText;

    @Column(nullable = false, length = 2000)
    private String correctedText;

    @Column(length = 1000)
    private String explanation;

    public ErrorRecord() {}

    public ErrorRecord(String sessionId, String messageDbId, ErrorType type,
                       String originalText, String correctedText, String explanation) {
        this.sessionId = sessionId;
        this.messageDbId = messageDbId;
        this.type = type;
        this.originalText = originalText;
        this.correctedText = correctedText;
        this.explanation = explanation;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getMessageDbId() { return messageDbId; }
    public void setMessageDbId(String messageDbId) { this.messageDbId = messageDbId; }

    public ErrorType getType() { return type; }
    public void setType(ErrorType type) { this.type = type; }

    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }

    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
}
