package com.hugosol.webagent.config;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Llm llm = new Llm();

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

    public Llm getLlm() {
        if (llm == null) {
            llm = new Llm();
        }
        return llm;
    }
    public void setLlm(Llm llm) { this.llm = llm; }

    public record InitialUser(String username, String password) {}

    public static class Security {
        private List<String> permitAllPaths = new ArrayList<>(List.of("/login/**"));

        public List<String> getPermitAllPaths() { return permitAllPaths; }
        public void setPermitAllPaths(List<String> permitAllPaths) { this.permitAllPaths = permitAllPaths; }
    }

    public static class Memory {
        private String storePath = "data/embedding-store.json";
        private int profileMaxLength = 400;
        private int cueTopicMaxWords = 7;
        private int cueSummaryMaxSentences = 4;
        private Retrieval retrieval = new Retrieval();

        public String getStorePath() { return storePath; }
        public void setStorePath(String storePath) { this.storePath = storePath; }

        public int getProfileMaxLength() { return profileMaxLength; }
        public void setProfileMaxLength(int profileMaxLength) { this.profileMaxLength = profileMaxLength; }

        public int getCueTopicMaxWords() { return cueTopicMaxWords; }
        public void setCueTopicMaxWords(int cueTopicMaxWords) { this.cueTopicMaxWords = cueTopicMaxWords; }

        public int getCueSummaryMaxSentences() { return cueSummaryMaxSentences; }
        public void setCueSummaryMaxSentences(int cueSummaryMaxSentences) { this.cueSummaryMaxSentences = cueSummaryMaxSentences; }

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

    public static class Llm {
        private MaxOutputTokens maxOutputTokens = new MaxOutputTokens();

        public MaxOutputTokens getMaxOutputTokens() {
            if (maxOutputTokens == null) {
                maxOutputTokens = new MaxOutputTokens();
            }
            return maxOutputTokens;
        }
        public void setMaxOutputTokens(MaxOutputTokens maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    }

    public static class MaxOutputTokens {
        @JsonProperty("default")
        private int defaultValue = 2048;
        private int report;

        public int getDefaultValue() { return defaultValue; }
        public void setDefaultValue(int defaultValue) { this.defaultValue = defaultValue; }

        public int getReport() { return report > 0 ? report : getDefaultValue(); }
        public void setReport(int report) { this.report = report; }

        public int getConversation() { return getDefaultValue(); }
        public int getCorrection() { return getDefaultValue(); }
        public int getMemoryCue() { return getDefaultValue(); }
        public int getLearning() { return getDefaultValue(); }
    }
}
