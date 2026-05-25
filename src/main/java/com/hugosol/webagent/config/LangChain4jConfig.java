package com.hugosol.webagent.config;

import com.hugosol.webagent.service.LlmCallLogService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.openai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.openai.chat-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.openai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.openai.chat-model.temperature:0.7}")
    private double temperature;

    @Value("${langchain4j.openai.chat-model.max-tokens:2048}")
    private int maxTokens;

    @Value("${langchain4j.openai.chat-model.timeout:30s}")
    private Duration timeout;

    @Bean
    public ChatLanguageModel chatLanguageModel(LlmCallLogService llmCallLogService) {
        var delegate = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
        return new LoggableChatModel(delegate, llmCallLogService);
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .build();
    }
}
