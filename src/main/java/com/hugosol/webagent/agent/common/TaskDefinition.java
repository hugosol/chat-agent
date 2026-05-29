package com.hugosol.webagent.agent.common;

import java.util.Map;
import java.util.function.Function;

public class TaskDefinition<P, R> {

    private final String template;
    private final Function<P, Map<String, String>> paramBuilder;
    private final Function<String, R> parser;
    private final ErrorStrategy errorStrategy;

    private TaskDefinition(Builder<P, R> builder) {
        this.template = builder.template;
        this.paramBuilder = builder.paramBuilder;
        this.parser = builder.parser;
        this.errorStrategy = builder.errorStrategy;
    }

    public String template() { return template; }
    public Function<P, Map<String, String>> paramBuilder() { return paramBuilder; }
    public Function<String, R> parser() { return parser; }
    public ErrorStrategy errorStrategy() { return errorStrategy; }

    public static <P, R> Builder<P, R> builder() {
        return new Builder<>();
    }

    public static class Builder<P, R> {
        private String template;
        private Function<P, Map<String, String>> paramBuilder;
        private Function<String, R> parser;
        private ErrorStrategy errorStrategy;

        public Builder<P, R> template(String template) { this.template = template; return this; }
        public Builder<P, R> paramBuilder(Function<P, Map<String, String>> paramBuilder) { this.paramBuilder = paramBuilder; return this; }
        public Builder<P, R> parser(Function<String, R> parser) { this.parser = parser; return this; }
        public Builder<P, R> errorStrategy(ErrorStrategy errorStrategy) { this.errorStrategy = errorStrategy; return this; }

        public TaskDefinition<P, R> build() {
            if (template == null) throw new IllegalStateException("template is required");
            if (paramBuilder == null) throw new IllegalStateException("paramBuilder is required");
            if (parser == null) throw new IllegalStateException("parser is required");
            if (errorStrategy == null) throw new IllegalStateException("errorStrategy is required");
            return new TaskDefinition<>(this);
        }
    }
}
