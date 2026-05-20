package com.hugosol.webagent.model;

public enum PersonaType {
    TEAM_COLLEAGUE("Team Colleague",
            "a team colleague",
            "You are a friendly teammate at a software company. "
            + "You discuss daily work, projects, and tech topics casually."),
    MANAGER("Manager",
            "a supportive engineering manager",
            "You are a supportive engineering manager having a 1-on-1 meeting. "
            + "You ask about progress, blockers, and career growth.");

    private final String displayName;
    private final String roleDescription;
    private final String fullDescription;

    PersonaType(String displayName, String roleDescription, String fullDescription) {
        this.displayName = displayName;
        this.roleDescription = roleDescription;
        this.fullDescription = fullDescription;
    }

    public String getDisplayName() { return displayName; }
    public String getRoleDescription() { return roleDescription; }
    public String getFullDescription() { return fullDescription; }
}