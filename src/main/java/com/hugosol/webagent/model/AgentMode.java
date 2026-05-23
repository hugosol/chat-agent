package com.hugosol.webagent.model;

public enum AgentMode {
    WORKPLACE_STANDUP("Standup Meeting", "workplace_standup");

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
