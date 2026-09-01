package com.hugosol.chatagent.agent.common;

import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.MessageRole;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleMsgFormatterTest {

    // ── toXml ───────────────────────────────────────────────────

    @Test
    void toXmlIncludesAllTurns() {
        List<MessageData> messages = List.of(
                new MessageData(MessageRole.USER, "Hello", 0),
                new MessageData(MessageRole.AGENT, "Hi there!", 1),
                new MessageData(MessageRole.USER, "How are you?", 2)
        );

        String xml = ExampleMsgFormatter.toXml(messages);

        assertThat(xml).contains("<turn role=\"user\">Hello</turn>");
        assertThat(xml).contains("<turn role=\"assistant\">Hi there!</turn>");
        assertThat(xml).contains("<turn role=\"user\">How are you?</turn>");
    }

    @Test
    void toXmlUserOnlySkipsAssistantTurns() {
        List<MessageData> messages = List.of(
                new MessageData(MessageRole.USER, "Hello", 0),
                new MessageData(MessageRole.AGENT, "Hi there!", 1),
                new MessageData(MessageRole.USER, "How are you?", 2)
        );

        String xml = ExampleMsgFormatter.toXmlUserOnly(messages);

        assertThat(xml).contains("<turn role=\"user\">Hello</turn>");
        assertThat(xml).doesNotContain("assistant");
        assertThat(xml).contains("<turn role=\"user\">How are you?</turn>");
    }

    @Test
    void toXmlEmptyListReturnsEmptyString() {
        assertThat(ExampleMsgFormatter.toXml(List.of())).isEmpty();
    }

    @Test
    void toXmlEscapesSpecialCharacters() {
        List<MessageData> messages = List.of(
                new MessageData(MessageRole.USER, "a < b & c > \"d\"", 0)
        );

        String xml = ExampleMsgFormatter.toXml(messages);

        assertThat(xml).contains("&lt;");
        assertThat(xml).contains("&gt;");
        assertThat(xml).contains("&amp;");
        assertThat(xml).contains("&quot;");
        assertThat(xml).doesNotContain("< b");
    }

    @Test
    void toXmlMapsCorrectionRoleToAssistant() {
        List<MessageData> messages = List.of(
                new MessageData(MessageRole.CORRECTION, "correction text", 0)
        );

        String xml = ExampleMsgFormatter.toXml(messages);

        assertThat(xml).contains("<turn role=\"assistant\">correction text</turn>");
    }

    // ── parseFewShot ────────────────────────────────────────────

    @Test
    void parseFewShotSinglePair() {
        String template = """
                User: I go to park yesterday.
                Assistant: You went to the park yesterday?
                ---
                ["past tense"]
                """;

        List<ChatMessage> result = ExampleMsgFormatter.parseFewShot(template, false);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(1)).isInstanceOf(AiMessage.class);

        String userXml = ((UserMessage) result.get(0)).singleText();
        assertThat(userXml).contains("<turn role=\"user\">I go to park yesterday.</turn>");
        assertThat(userXml).contains("<turn role=\"assistant\">You went to the park yesterday?</turn>");

        assertThat(((AiMessage) result.get(1)).text()).isEqualTo("[\"past tense\"]");
    }

    @Test
    void parseFewShotTwoPairs() {
        String template = """
                User: I have a idea.
                Assistant: An idea? What is it?
                ---
                ["articles"]
                ---
                User: She always come late.
                Assistant: She comes late?
                ---
                ["third person -s"]
                """;

        List<ChatMessage> result = ExampleMsgFormatter.parseFewShot(template, false);

        assertThat(result).hasSize(4);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(1)).isInstanceOf(AiMessage.class);
        assertThat(result.get(2)).isInstanceOf(UserMessage.class);
        assertThat(result.get(3)).isInstanceOf(AiMessage.class);

        assertThat(((AiMessage) result.get(1)).text()).isEqualTo("[\"articles\"]");
        assertThat(((AiMessage) result.get(3)).text()).isEqualTo("[\"third person -s\"]");
    }

    @Test
    void parseFewShotEmptyInput() {
        assertThat(ExampleMsgFormatter.parseFewShot(null, false)).isEmpty();
        assertThat(ExampleMsgFormatter.parseFewShot("", false)).isEmpty();
        assertThat(ExampleMsgFormatter.parseFewShot("   ", false)).isEmpty();
    }

    @Test
    void parseFewShotUserOnly() {
        String template = """
                User: Hello
                Assistant: Hi!
                User: How are you?
                ---
                ["greeting"]
                """;

        List<ChatMessage> result = ExampleMsgFormatter.parseFewShot(template, true);

        String userXml = ((UserMessage) result.get(0)).singleText();
        assertThat(userXml).contains("<turn role=\"user\">Hello</turn>");
        assertThat(userXml).contains("<turn role=\"user\">How are you?</turn>");
        assertThat(userXml).doesNotContain("assistant");
    }

    @Test
    void parseFewShotPlainTextBlockFallback() {
        // judge-same style: Statement A / Statement B (no User:/Assistant: prefixes)
        String template = """
                Statement A: The user forgets past tense.
                Statement B: The learner struggles with irregular past tense.
                ---
                YES
                ---
                Statement A: Subject-verb agreement errors.
                Statement B: Confuses a and an before vowels.
                ---
                NO
                """;

        List<ChatMessage> result = ExampleMsgFormatter.parseFewShot(template, false);

        assertThat(result).hasSize(4);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) result.get(0)).singleText())
                .contains("Statement A: The user forgets past tense.");
        assertThat(((AiMessage) result.get(1)).text()).isEqualTo("YES");
        assertThat(((AiMessage) result.get(3)).text()).isEqualTo("NO");
    }

    @Test
    void parseFewShotThreePairsMatchingOriginalAssertionExamples() {
        // These should match the original AssertionService few-shot examples
        String template = """
                User: Yesterday I go to the park.
                Assistant: Ah, you went to the park? Was it crowded?
                User: No, but I forget my key there.
                Assistant: Oh no, you forgot your key? Hope you got it back.
                ---
                ["past tense"]
                ---
                User: I have a idea for the meeting.
                Assistant: An idea? What is it?
                User: She always come late on Monday.
                Assistant: She comes late? That's annoying.
                ---
                ["articles", "third person -s"]
                ---
                User: I had a great weekend. Watched a movie and relaxed.
                Assistant: Sounds lovely! What did you watch?
                User: An old comedy. Nothing special but fun.
                ---
                []
                """;

        List<ChatMessage> result = ExampleMsgFormatter.parseFewShot(template, false);

        assertThat(result).hasSize(6);

        // Example 1: past tense
        String xml1 = ((UserMessage) result.get(0)).singleText();
        assertThat(xml1).contains("<turn role=\"user\">Yesterday I go to the park.</turn>");
        assertThat(xml1).contains("<turn role=\"assistant\">Ah, you went to the park? Was it crowded?</turn>");
        assertThat(((AiMessage) result.get(1)).text()).isEqualTo("[\"past tense\"]");

        // Example 2: articles + third person
        String xml2 = ((UserMessage) result.get(2)).singleText();
        assertThat(xml2).contains("<turn role=\"user\">I have a idea for the meeting.</turn>");
        assertThat(((AiMessage) result.get(3)).text()).isEqualTo("[\"articles\", \"third person -s\"]");

        // Example 3: empty
        String xml3 = ((UserMessage) result.get(4)).singleText();
        assertThat(xml3).contains("<turn role=\"user\">I had a great weekend. Watched a movie and relaxed.</turn>");
        assertThat(((AiMessage) result.get(5)).text()).isEqualTo("[]");
    }

    // ── parseConversationBlock ──────────────────────────────────

    @Test
    void parseConversationBlockIgnoresUnknownPrefixes() {
        String block = """
                User: Hello
                System: ignored
                Assistant: Hi!
                """;

        List<MessageData> messages = ExampleMsgFormatter.parseConversationBlock(block);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(1).getRole()).isEqualTo(MessageRole.AGENT);
    }

    @Test
    void parseConversationBlockHandlesEmptyLines() {
        String block = """
                User: Hello

                Assistant: Hi!
                """;

        List<MessageData> messages = ExampleMsgFormatter.parseConversationBlock(block);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello");
        assertThat(messages.get(1).getContent()).isEqualTo("Hi!");
    }

    // ── escapeXml ───────────────────────────────────────────────

    @Test
    void escapeXmlNullReturnsEmpty() {
        assertThat(ExampleMsgFormatter.escapeXml(null)).isEmpty();
    }

    @Test
    void escapeXmlHandlesAllEntities() {
        String input = "if (a < b && c > d) { \"done\" }";
        String escaped = ExampleMsgFormatter.escapeXml(input);

        assertThat(escaped).isEqualTo("if (a &lt; b &amp;&amp; c &gt; d) { &quot;done&quot; }");
    }
}
