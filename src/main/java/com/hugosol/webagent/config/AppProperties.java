package com.hugosol.webagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private int tokenLimit = 128000;
    private double tokenLimitRatio = 0.8;
    private String promptDirectory = "classpath:prompts";
    private List<InitialUser> initialUsers = new ArrayList<>();

    public int getTokenLimit() { return tokenLimit; }
    public void setTokenLimit(int tokenLimit) { this.tokenLimit = tokenLimit; }

    public double getTokenLimitRatio() { return tokenLimitRatio; }
    public void setTokenLimitRatio(double tokenLimitRatio) { this.tokenLimitRatio = tokenLimitRatio; }

    public String getPromptDirectory() { return promptDirectory; }
    public void setPromptDirectory(String promptDirectory) { this.promptDirectory = promptDirectory; }

    public List<InitialUser> getInitialUsers() { return initialUsers; }
    public void setInitialUsers(List<InitialUser> initialUsers) { this.initialUsers = initialUsers; }

    public record InitialUser(String username, String password) {}
}
