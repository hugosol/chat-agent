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
    private Security security = new Security();
    private Memory memory = new Memory();

    public int getTokenLimit() { return tokenLimit; }
    public void setTokenLimit(int tokenLimit) { this.tokenLimit = tokenLimit; }

    public double getTokenLimitRatio() { return tokenLimitRatio; }
    public void setTokenLimitRatio(double tokenLimitRatio) { this.tokenLimitRatio = tokenLimitRatio; }

    public String getPromptDirectory() { return promptDirectory; }
    public void setPromptDirectory(String promptDirectory) { this.promptDirectory = promptDirectory; }

    public List<InitialUser> getInitialUsers() { return initialUsers; }
    public void setInitialUsers(List<InitialUser> initialUsers) { this.initialUsers = initialUsers; }

    public Security getSecurity() {
        if (security == null) {
            security = new Security();
        }
        return security;
    }
    public void setSecurity(Security security) { this.security = security; }

    public Memory getMemory() {
        if (memory == null) {
            memory = new Memory();
        }
        return memory;
    }
    public void setMemory(Memory memory) { this.memory = memory; }

    public record InitialUser(String username, String password) {}

    public static class Security {
        private List<String> permitAllPaths = new ArrayList<>(List.of("/login/**"));

        public List<String> getPermitAllPaths() { return permitAllPaths; }
        public void setPermitAllPaths(List<String> permitAllPaths) { this.permitAllPaths = permitAllPaths; }
    }

    public static class Memory {
        private int userMemoryRounds = 3;
        private String storePath = "data/embedding-store.json";
        private Retrieval retrieval = new Retrieval();

        public int getUserMemoryRounds() { return userMemoryRounds; }
        public void setUserMemoryRounds(int userMemoryRounds) { this.userMemoryRounds = userMemoryRounds; }

        public String getStorePath() { return storePath; }
        public void setStorePath(String storePath) { this.storePath = storePath; }

        public Retrieval getRetrieval() {
            if (retrieval == null) {
                retrieval = new Retrieval();
            }
            return retrieval;
        }
        public void setRetrieval(Retrieval retrieval) { this.retrieval = retrieval; }
    }

    public static class Retrieval {
        private int topK = 2;
        private double similarityThreshold = 0.6;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }

        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    }
}
