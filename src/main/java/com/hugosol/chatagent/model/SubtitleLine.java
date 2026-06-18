package com.hugosol.chatagent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subtitle_lines")
public class SubtitleLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String imdbId;

    @Column(nullable = false)
    private String movieTitle;

    private String startTime;

    private String endTime;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String wordsLower;

    private Integer lineIndex;

    public SubtitleLine() {
    }

    public SubtitleLine(String imdbId, String movieTitle, String startTime, String endTime,
                        String text, String wordsLower, Integer lineIndex) {
        this.imdbId = imdbId;
        this.movieTitle = movieTitle;
        this.startTime = startTime;
        this.endTime = endTime;
        this.text = text;
        this.wordsLower = wordsLower;
        this.lineIndex = lineIndex;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getImdbId() { return imdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }
    public String getMovieTitle() { return movieTitle; }
    public void setMovieTitle(String movieTitle) { this.movieTitle = movieTitle; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getWordsLower() { return wordsLower; }
    public void setWordsLower(String wordsLower) { this.wordsLower = wordsLower; }
    public Integer getLineIndex() { return lineIndex; }
    public void setLineIndex(Integer lineIndex) { this.lineIndex = lineIndex; }
}
