package com.hugosol.chatagent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "card_enhancements")
public class CardEnhancement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnhancementType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnhancementStatus status;

    @Column(columnDefinition = "TEXT")
    private String data;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(columnDefinition = "TEXT")
    private String requestUrl;

    public CardEnhancement() {
    }

    public CardEnhancement(String cardId, EnhancementType type, EnhancementStatus status,
                           String data, String error, String requestUrl) {
        this.cardId = cardId;
        this.type = type;
        this.status = status;
        this.data = data;
        this.error = error;
        this.requestUrl = requestUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public EnhancementType getType() { return type; }
    public void setType(EnhancementType type) { this.type = type; }
    public EnhancementStatus getStatus() { return status; }
    public void setStatus(EnhancementStatus status) { this.status = status; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getRequestUrl() { return requestUrl; }
    public void setRequestUrl(String requestUrl) { this.requestUrl = requestUrl; }
}
