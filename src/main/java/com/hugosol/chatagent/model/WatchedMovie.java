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
@Table(name = "watched_movies")
public class WatchedMovie extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String imdbId;

    @Column(nullable = false)
    private String title;

    private Integer releaseYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubtitleStatus subtitleStatus;

    private Integer subtitleLineCount;

    @Column(columnDefinition = "TEXT")
    private String subtitleError;

    public WatchedMovie() {
    }

    public WatchedMovie(String userId, String imdbId, String title, Integer releaseYear,
                        SubtitleStatus subtitleStatus) {
        this.userId = userId;
        this.imdbId = imdbId;
        this.title = title;
        this.releaseYear = releaseYear;
        this.subtitleStatus = subtitleStatus;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getImdbId() { return imdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getReleaseYear() { return releaseYear; }
    public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }
    public SubtitleStatus getSubtitleStatus() { return subtitleStatus; }
    public void setSubtitleStatus(SubtitleStatus subtitleStatus) { this.subtitleStatus = subtitleStatus; }
    public Integer getSubtitleLineCount() { return subtitleLineCount; }
    public void setSubtitleLineCount(Integer subtitleLineCount) { this.subtitleLineCount = subtitleLineCount; }
    public String getSubtitleError() { return subtitleError; }
    public void setSubtitleError(String subtitleError) { this.subtitleError = subtitleError; }
}
