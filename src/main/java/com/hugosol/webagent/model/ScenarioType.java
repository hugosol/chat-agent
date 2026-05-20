package com.hugosol.webagent.model;

public enum ScenarioType {
    WORKPLACE_STANDUP("Standup Meeting",
            "a daily standup meeting where team members share progress and blockers"),
    WORKPLACE_ONE_ON_ONE("1-on-1 Meeting",
            "a 1-on-1 meeting with your manager to discuss progress and growth");

    private final String displayName;
    private final String description;

    ScenarioType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
