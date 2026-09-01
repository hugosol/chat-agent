package com.hugosol.chatagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    @Value("${langchain4j.openai.chat-model.timeout:30s}")
    private Duration timeout;

    private final AppProperties appProperties;

    public LangChain4jConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Primary
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(appProperties.getLlm().getMaxOutputTokens().getDefaultValue())
                .timeout(timeout)
                .build();
    }

    @Bean
    public ChatLanguageModel reportChatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(appProperties.getLlm().getMaxOutputTokens().getReport())
                .timeout(timeout)
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(appProperties.getLlm().getMaxOutputTokens().getConversation())
                .timeout(timeout)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public InMemoryEmbeddingStore<TextSegment> assertionEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}
