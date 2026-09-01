package com.hugosol.chatagent.agent.common;

import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.MessageRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralized utility for conversation-to-XML conversion and few-shot template parsing.
 *
 * <p>All {@code <turn>} XML passes through this class, guaranteeing that few-shot
 * examples and real conversation data use the identical format.</p>
 */
public final class ExampleMsgFormatter {

    private ExampleMsgFormatter() {
        // static utility — no instances
    }

    // ── XML conversion ──────────────────────────────────────────

    /**
     * Converts a full conversation segment to {@code <turn>} XML.
     * Both user and agent messages are included.
     */
    public static String toXml(List<MessageData> messages) {
        return toXml(messages, false);
    }

    /**
     * Converts only user messages to {@code <turn>} XML.
     * Agent messages are skipped.
     */
    public static String toXmlUserOnly(List<MessageData> messages) {
        return toXml(messages, true);
    }

    private static String toXml(List<MessageData> messages, boolean userOnly) {
        StringBuilder xml = new StringBuilder();
        for (MessageData msg : messages) {
            if (userOnly && msg.getRole() != MessageRole.USER) {
                continue;
            }
            String role = msg.getRole() == MessageRole.USER ? "user" : "assistant";
            xml.append("<turn role=\"").append(role).append("\">")
                    .append(escapeXml(msg.getContent()))
                    .append("</turn>\n");
        }
        return xml.toString();
    }

    // ── Few-shot parsing ────────────────────────────────────────

    /**
     * Parses a human-readable few-shot template into LangChain4j {@link ChatMessage} list.
     *
     * <p>Template format — blocks separated by {@code ---}:</p>
     * <pre>
     * User: first turn
     * Assistant: reply
     * ---
     * ["expected output"]
     * ---
     * User: another turn
     * ...
     * </pre>
     *
     * <p>Odd blocks (0, 2, 4…) are conversation blocks → parsed to {@link MessageData} →
     * {@code toXml()} → {@link UserMessage}.</p>
     * <p>Even blocks (1, 3, 5…) are AI expected output → {@link AiMessage}.</p>
     *
     * @param content  the full template text
     * @param userOnly if true, conversation blocks use {@link #toXmlUserOnly} instead of {@link #toXml}
     * @return alternating {@link UserMessage} / {@link AiMessage} list
     */
    public static List<ChatMessage> parseFewShot(String content, boolean userOnly) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String[] blocks = content.split("\n---\n");
        List<ChatMessage> messages = new ArrayList<>();

        for (int i = 0; i < blocks.length; i++) {
            String block = blocks[i].trim();
            if (block.isEmpty()) {
                continue;
            }

            if (i % 2 == 0) {
                // Conversation block → parse into MessageData → toXml → UserMessage
                // Falls back to plain text if block doesn't use User:/Assistant: format
                List<MessageData> turns = parseConversationBlock(block);
                if (turns.isEmpty()) {
                    messages.add(UserMessage.from(block));
                } else {
                    String xml = userOnly ? toXmlUserOnly(turns) : toXml(turns);
                    messages.add(UserMessage.from(xml));
                }
            } else {
                // AI expected output → AiMessage
                messages.add(AiMessage.from(block));
            }
        }

        return messages;
    }

    // ── Private helpers ─────────────────────────────────────────

    /**
     * Parses a conversation block (lines prefixed with {@code User:} / {@code Assistant:})
     * into a list of {@link MessageData}.
     */
    static List<MessageData> parseConversationBlock(String block) {
        List<MessageData> messages = new ArrayList<>();
        int msgId = 0;

        for (String line : block.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("User: ")) {
                messages.add(new MessageData(MessageRole.USER, line.substring(6), msgId++));
            } else if (line.startsWith("Assistant: ")) {
                messages.add(new MessageData(MessageRole.AGENT, line.substring(11), msgId++));
            }
            // Lines without a recognized prefix are silently skipped
        }

        return messages;
    }

    static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
