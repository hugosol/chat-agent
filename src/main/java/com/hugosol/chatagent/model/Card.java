package com.hugosol.chatagent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cards")
public class Card extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String front;

    @Column(nullable = false)
    private String back;

    private double stability;

    private double difficulty;

    private int cardState;

    @Column
    private Instant due;

    private int reps;

    private int lapses;

    @Column
    private Instant lastReview;

    @Column
    private Instant firstReviewDate;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "card_tags",
            joinColumns = @JoinColumn(name = "card_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    public Card() {
    }

    public Card(String userId, String front, String back) {
        this.userId = userId;
        this.front = front;
        this.back = back;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFront() { return front; }
    public void setFront(String front) { this.front = front; }
    public String getBack() { return back; }
    public void setBack(String back) { this.back = back; }
    public double getStability() { return stability; }
    public void setStability(double stability) { this.stability = stability; }
    public double getDifficulty() { return difficulty; }
    public void setDifficulty(double difficulty) { this.difficulty = difficulty; }
    public int getCardState() { return cardState; }
    public void setCardState(int cardState) { this.cardState = cardState; }
    public Instant getDue() { return due; }
    public void setDue(Instant due) { this.due = due; }
    public int getReps() { return reps; }
    public void setReps(int reps) { this.reps = reps; }
    public int getLapses() { return lapses; }
    public void setLapses(int lapses) { this.lapses = lapses; }
    public Instant getLastReview() { return lastReview; }
    public void setLastReview(Instant lastReview) { this.lastReview = lastReview; }
    public Instant getFirstReviewDate() { return firstReviewDate; }
    public void setFirstReviewDate(Instant firstReviewDate) { this.firstReviewDate = firstReviewDate; }
    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }
}
