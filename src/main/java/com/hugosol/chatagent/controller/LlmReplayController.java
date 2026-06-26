package com.hugosol.chatagent.controller;

import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.LlmCallLog;
import com.hugosol.chatagent.model.Message;
import com.hugosol.chatagent.model.Session;
import com.hugosol.chatagent.repository.LlmCallLogRepository;
import com.hugosol.chatagent.repository.MessageRepository;
import com.hugosol.chatagent.repository.SessionRepository;
import com.hugosol.chatagent.service.AssertionService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
@Profile("local")
public class LlmReplayController {

    private static final Logger log = LoggerFactory.getLogger(LlmReplayController.class);

    private final LlmCallLogRepository logRepository;
    private final ChatLanguageModel chatModel;
    private final AssertionService assertionService;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    public LlmReplayController(LlmCallLogRepository logRepository,
                               @Qualifier("chatLanguageModel") ChatLanguageModel chatModel,
                               AssertionService assertionService,
                               SessionRepository sessionRepository,
                               MessageRepository messageRepository) {
        this.logRepository = logRepository;
        this.chatModel = chatModel;
        this.assertionService = assertionService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @GetMapping(value = "/llm-replay", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> replay(@RequestParam String id) {
        LlmCallLog logEntry = logRepository.findById(id).orElse(null);
        if (logEntry == null) {
            return ResponseEntity.status(404).body("Log not found: " + id);
        }

        if ("CONVERSATION".equals(logEntry.getAgentType())) {
            return ResponseEntity.badRequest().body("不支持流式日志回放（agent_type=CONVERSATION），请使用同步 Agent 日志（CORRECTION/REPORT/LEARNING/MEMORY_CUE）");
        }

        String prompt = logEntry.getRequestPrompt();
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest().body("日志记录无 request_prompt，无法回放（id=" + id + ", agent_type=" + logEntry.getAgentType() + "）");
        }

        log.info("Replaying LLM call: id={}, agent_type={}, model={}", id, logEntry.getAgentType(), logEntry.getModel());

        try {
            String response = chatModel.chat(prompt);
            System.out.println("=== LLM Replay Response (id=" + id + ") ===");
            System.out.println(response);
            System.out.println("=== End ===");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("LLM replay failed: id={}", id, e);
            return ResponseEntity.internalServerError().body("Replay failed: " + e.getMessage());
        }
    }

    @GetMapping(value = "/rerun-assertions", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> rerunAssertions(@RequestParam String sessionId) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.status(404).body("Session not found: " + sessionId);
        }

        AgentMode mode = session.getMode();
        if (mode == AgentMode.JAPANESE_BUSINESS) {
            return ResponseEntity.badRequest().body("JAPANESE_BUSINESS 模式不生成 assertions（与正常流程一致）");
        }

        String userId = session.getUserId();
        List<Message> dbMessages = messageRepository.findBySessionIdOrderByCreateTimeAsc(sessionId);
        if (dbMessages.isEmpty()) {
            return ResponseEntity.badRequest().body("该会话无已持久化消息（sessionId=" + sessionId + "），请确保会话已正常结束");
        }

        List<MessageData> messages = dbMessages.stream().map(m -> {
            MessageData md = new MessageData(m.getRole(), m.getContent(),
                    m.getMessageId() != null ? m.getMessageId() : 0);
            md.setTokenCount(m.getTokenCount());
            return md;
        }).toList();

        log.info("Rerunning assertions: sessionId={}, userId={}, mode={}, messageCount={}",
                sessionId, userId, mode, messages.size());

        long startTime = System.currentTimeMillis();
        try {
            List<List<MessageData>> segments = List.of(messages);
            assertionService.generateAssertionsAsync(sessionId, userId, mode, segments).join();
            long elapsed = System.currentTimeMillis() - startTime;
            String result = "Assertion pipeline completed: sessionId=" + sessionId
                    + ", userId=" + userId + ", mode=" + mode
                    + ", messageCount=" + messages.size()
                    + ", elapsed=" + elapsed + "ms";
            System.out.println("=== " + result + " ===");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Assertion replay failed: sessionId={}, elapsed={}ms", sessionId, elapsed, e);
            return ResponseEntity.internalServerError()
                    .body("Assertion replay failed after " + elapsed + "ms: " + e.getMessage());
        }
    }
}
