package com.hugosol.chatagent.model;

public enum AgentMode {
    WORKPLACE_STANDUP("Standup", "workplace_standup"),
    DAILY_TALK("Daily", "daily_talk"),
    JAPANESE_BUSINESS("ビジネス日本語", "japanese_business");

    private final String displayName;
    private final String templatePath;

    AgentMode(String displayName, String templatePath) {
        this.displayName = displayName;
        this.templatePath = templatePath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTemplatePath() {
        return templatePath;
    }
}
