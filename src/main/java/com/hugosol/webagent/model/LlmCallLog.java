package com.hugosol.webagent.model;

import jakarta.persistence.*;

@Entity
@Table(name = "llm_call_logs")
public class LlmCallLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "agent_type")
    private String agentType;

    @Column
    private String mode;

    @Column(length = 100)
    private String model;

    @Column(name = "request_prompt", columnDefinition = "CLOB")
    private String requestPrompt;

    @Column(name = "system_prompt", columnDefinition = "CLOB")
    private String systemPrompt;

    @Column(name = "chat_history", columnDefinition = "CLOB")
    private String chatHistory;

    @Column(name = "response_text", columnDefinition = "CLOB")
    private String responseText;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(length = 20)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getRequestPrompt() { return requestPrompt; }
    public void setRequestPrompt(String requestPrompt) { this.requestPrompt = requestPrompt; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getChatHistory() { return chatHistory; }
    public void setChatHistory(String chatHistory) { this.chatHistory = chatHistory; }

    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }

    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }

    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
