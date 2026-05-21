package com.hugosol.webagent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
public class Session extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScenarioType scenario;

    @Column(nullable = false)
    private String persona;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    public Session() {}

    public Session(ScenarioType scenario, String persona) {
        this.scenario = scenario;
        this.persona = persona;
        this.startTime = LocalDateTime.now();
        this.status = SessionStatus.ACTIVE;
    }

    public void complete() {
        this.status = SessionStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public ScenarioType getScenario() { return scenario; }
    public void setScenario(ScenarioType scenario) { this.scenario = scenario; }

    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
}
