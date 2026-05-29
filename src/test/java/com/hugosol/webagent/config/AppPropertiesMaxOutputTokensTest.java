package com.hugosol.webagent.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesMaxOutputTokensTest {

    @Test
    void defaultMaxOutputTokensIs2048() {
        AppProperties.Llm llm = new AppProperties.Llm();
        assertThat(llm.getMaxOutputTokens().getDefaultValue()).isEqualTo(2048);
    }

    @Test
    void reportMaxOutputTokensIs4096() {
        AppProperties.Llm llm = new AppProperties.Llm();
        llm.getMaxOutputTokens().setReport(4096);
        AppProperties.MaxOutputTokens maxTokens = llm.getMaxOutputTokens();
        assertThat(maxTokens.getReport()).isEqualTo(4096);
    }

    @Test
    void reportNotConfiguredFallsBackToDefault() {
        AppProperties.Llm llm = new AppProperties.Llm();
        AppProperties.MaxOutputTokens maxTokens = llm.getMaxOutputTokens();
        assertThat(maxTokens.getReport()).isEqualTo(maxTokens.getDefaultValue());
    }

    @Test
    void unconfiguredAgentFallsBackToDefault() {
        AppProperties.Llm llm = new AppProperties.Llm();
        AppProperties.MaxOutputTokens maxTokens = llm.getMaxOutputTokens();
        var defaultValue = maxTokens.getDefaultValue();
        assertThat(maxTokens.getCorrection()).isEqualTo(defaultValue);
        assertThat(maxTokens.getConversation()).isEqualTo(defaultValue);
        assertThat(maxTokens.getLearning()).isEqualTo(defaultValue);
        assertThat(maxTokens.getMemoryCue()).isEqualTo(defaultValue);
    }

    @Test
    void maxOutputTokensBelongsToLlmConfig() {
        AppProperties props = new AppProperties();
        AppProperties.Llm llm = props.getLlm();
        assertThat(llm).isNotNull();
        assertThat(llm.getMaxOutputTokens()).isNotNull();
    }
}
