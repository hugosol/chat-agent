package com.hugosol.chatagent.agent.common;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LlmTaskDefinition<P, R> {

    private final String systemTemplate;
    private final String userTemplate;
    private final List<ChatMessage> exampleMessages;
    private final Function<P, Map<String, String>> paramBuilder;
    private final Function<String, R> parser;
    private final ErrorStrategy errorStrategy;

    private LlmTaskDefinition(Builder<P, R> builder) {
        this.systemTemplate = builder.systemTemplate;
        this.userTemplate = builder.userTemplate;
        this.exampleMessages = builder.exampleMessages;
        this.paramBuilder = builder.paramBuilder;
        this.parser = builder.parser;
        this.errorStrategy = builder.errorStrategy;
    }

    public String systemTemplate() { return systemTemplate; }
    public String userTemplate() { return userTemplate; }
    public List<ChatMessage> exampleMessages() { return exampleMessages; }
    public Function<P, Map<String, String>> paramBuilder() { return paramBuilder; }
    public Function<String, R> parser() { return parser; }
    public ErrorStrategy errorStrategy() { return errorStrategy; }

    public static <P, R> Builder<P, R> builder() {
        return new Builder<>();
    }

    public static class Builder<P, R> {
        private String systemTemplate;
        private String userTemplate;
        private List<ChatMessage> exampleMessages;
        private Function<P, Map<String, String>> paramBuilder;
        private Function<String, R> parser;
        private ErrorStrategy errorStrategy;

        public Builder<P, R> systemTemplate(String systemTemplate) { this.systemTemplate = systemTemplate; return this; }
        public Builder<P, R> userTemplate(String userTemplate) { this.userTemplate = userTemplate; return this; }
        public Builder<P, R> exampleMessages(List<ChatMessage> exampleMessages) { this.exampleMessages = exampleMessages; return this; }
        public Builder<P, R> paramBuilder(Function<P, Map<String, String>> paramBuilder) { this.paramBuilder = paramBuilder; return this; }
        public Builder<P, R> parser(Function<String, R> parser) { this.parser = parser; return this; }
        public Builder<P, R> errorStrategy(ErrorStrategy errorStrategy) { this.errorStrategy = errorStrategy; return this; }

        public LlmTaskDefinition<P, R> build() {
            if (systemTemplate == null) throw new IllegalStateException("systemTemplate is required");
            if (userTemplate == null) throw new IllegalStateException("userTemplate is required");
            if (paramBuilder == null) throw new IllegalStateException("paramBuilder is required");
            if (parser == null) throw new IllegalStateException("parser is required");
            if (errorStrategy == null) throw new IllegalStateException("errorStrategy is required");
            return new LlmTaskDefinition<>(this);
        }
    }
}
