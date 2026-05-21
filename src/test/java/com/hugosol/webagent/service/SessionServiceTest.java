package com.hugosol.webagent.service;

import com.hugosol.webagent.dto.CorrectionData;
import com.hugosol.webagent.dto.MessageData;
import com.hugosol.webagent.model.AgentType;
import com.hugosol.webagent.model.ErrorType;
import com.hugosol.webagent.model.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionServiceTest {

    private SessionService service;
    private TokenTracker tokenTracker;

    @BeforeEach
    void setUp() {
        tokenTracker = new TokenTracker(128000, 0.8);
        service = new SessionService(tokenTracker);
    }

    @Test
    void initCreatesSession() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        assertThat(service.exists("s1")).isTrue();
    }

    @Test
    void existsReturnsFalseForUnknownSession() {
        assertThat(service.exists("unknown")).isFalse();
    }

    @Test
    void existsReturnsFalseAfterRemove() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.remove("s1");
        assertThat(service.exists("s1")).isFalse();
    }

    @Test
    void initInitializesTokenTracker() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.recordTokens("s1", AgentType.CONVERSATION, 500);
        assertThat(service.getUsageRatio("s1")).isGreaterThan(0);
    }

    @Test
    void removeClearsTokenTracker() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.recordTokens("s1", AgentType.CONVERSATION, 500);
        service.remove("s1");
        assertThat(service.getUsageRatio("s1")).isZero();
    }

    @Test
    void bindAndGetSessionId() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        assertThat(service.getSessionId("ws1")).isEqualTo("s1");
    }

    @Test
    void unbindKeepsStateAlive() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.unbind("ws1");
        assertThat(service.getSessionId("ws1")).isNull();
        assertThat(service.exists("s1")).isTrue();
    }

    @Test
    void rebindAfterUnbind() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.unbind("ws1");
        service.bind("ws2", "s1");
        assertThat(service.getSessionId("ws2")).isEqualTo("s1");
        assertThat(service.getSessionId("ws1")).isNull();
    }

    @Test
    void getSessionIdReturnsNullForUnknown() {
        assertThat(service.getSessionId("unknown")).isNull();
    }

    @Test
    void addMessageStoresCorrectly() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.addMessage("s1", MessageRole.USER, "Hello", 1, null);

        List<MessageData> messages = service.getMessages("s1");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getRole()).isEqualTo(MessageRole.USER);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello");
        assertThat(messages.get(0).getMessageId()).isEqualTo(1);
    }

    @Test
    void addMessageStoresTokenCount() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.addMessage("s1", MessageRole.AGENT, "Reply", 1, 520);

        List<MessageData> messages = service.getMessages("s1");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getTokenCount()).isEqualTo(520);
    }

    @Test
    void addMessageAllowsNullTokenCount() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.addMessage("s1", MessageRole.USER, "Hello", 1, null);

        List<MessageData> messages = service.getMessages("s1");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getTokenCount()).isNull();
    }

    @Test
    void addMessageDoesNotThrowForNonexistentSession() {
        service.addMessage("unknown", MessageRole.USER, "test", 1, null);
    }

    @Test
    void getMessagesReturnsDefensiveCopy() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.addMessage("s1", MessageRole.USER, "Hello", 1, null);

        List<MessageData> messages = service.getMessages("s1");
        messages.clear();

        assertThat(service.getMessages("s1")).hasSize(1);
    }

    @Test
    void getMessagesReturnsEmptyListForNonexistentSession() {
        assertThat(service.getMessages("unknown")).isEmpty();
    }

    @Test
    void addCorrectionsStoresCorrectly() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "orig", "corr", "expl");
        service.addCorrections("s1", List.of(cd));

        List<CorrectionData> corrections = service.getCorrections("s1");
        assertThat(corrections).hasSize(1);
        assertThat(corrections.get(0).getType()).isEqualTo(ErrorType.GRAMMAR);
    }

    @Test
    void addCorrectionsAccumulates() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.addCorrections("s1", List.of(
                new CorrectionData(ErrorType.GRAMMAR, "a", "b", "c")
        ));
        service.addCorrections("s1", List.of(
                new CorrectionData(ErrorType.CHINGLISH, "d", "e", "f")
        ));

        assertThat(service.getCorrections("s1")).hasSize(2);
    }

    @Test
    void getCorrectionsReturnsDefensiveCopy() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.addCorrections("s1", List.of(
                new CorrectionData(ErrorType.GRAMMAR, "a", "b", "c")
        ));

        List<CorrectionData> corrections = service.getCorrections("s1");
        corrections.clear();

        assertThat(service.getCorrections("s1")).hasSize(1);
    }

    @Test
    void getCorrectionsReturnsEmptyListForNonexistentSession() {
        assertThat(service.getCorrections("unknown")).isEmpty();
    }

    @Test
    void getCorrectionCountReturnsSize() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        assertThat(service.getCorrectionCount("s1")).isZero();

        service.addCorrections("s1", List.of(
                new CorrectionData(ErrorType.GRAMMAR, "a", "b", "c"),
                new CorrectionData(ErrorType.CHINGLISH, "d", "e", "f")
        ));
        assertThat(service.getCorrectionCount("s1")).isEqualTo(2);
    }

    @Test
    void getCorrectionCountReturnsZeroForNonexistentSession() {
        assertThat(service.getCorrectionCount("unknown")).isZero();
    }

    @Test
    void getScenarioAndPersonaReturnStoredValues() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        assertThat(service.getScenario("s1")).isEqualTo("WORKPLACE_STANDUP");
        assertThat(service.getPersona("s1")).isEqualTo("TEAM_COLLEAGUE");
    }

    @Test
    void getScenarioAndPersonaReturnEmptyForNonexistent() {
        assertThat(service.getScenario("unknown")).isEmpty();
        assertThat(service.getPersona("unknown")).isEmpty();
    }

    @Test
    void recordTokensDelegatesToTokenTracker() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        service.recordTokens("s1", AgentType.CONVERSATION, 1000);

        double ratio = service.getUsageRatio("s1");
        assertThat(ratio).isGreaterThan(0);
    }

    @Test
    void isTokenWarningDelegatesToTokenTracker() {
        service.init("s1", "WORKPLACE_STANDUP", "TEAM_COLLEAGUE", "ws1");
        assertThat(service.isTokenWarning("s1")).isFalse();

        service.recordTokens("s1", AgentType.CONVERSATION, 120000);
        assertThat(service.isTokenWarning("s1")).isTrue();
    }
}
