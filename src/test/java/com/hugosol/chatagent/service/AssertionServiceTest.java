package com.hugosol.chatagent.service;

import com.hugosol.chatagent.agent.MemoryCueAgent;
import com.hugosol.chatagent.agent.common.TaskContext;
import com.hugosol.chatagent.agent.common.TaskName;
import com.hugosol.chatagent.agent.common.TaskRunner;
import com.hugosol.chatagent.dto.MessageData;
import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.AssertionGroupRepository;
import com.hugosol.chatagent.repository.AssertionLineageRepository;
import com.hugosol.chatagent.repository.MemoryAssertionRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssertionServiceTest {

    @Mock
    private TaskRunner runner;

    @Mock
    private MemoryCueAgent memoryCueAgent;

    @Mock
    private MemoryAssertionRepository assertionRepository;

    @Mock
    private AssertionGroupRepository groupRepository;

    @Mock
    private AssertionLineageRepository lineageRepository;

    @Mock
    private InMemoryEmbeddingStore<TextSegment> assertionEmbeddingStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ExecutorService executor;

    private AssertionService service;

    private AssertionGroup errorPatternGroup;

    private static final String SESSION_ID = "ses-1";
    private static final String USER_ID = "user-1";
    private static final AgentMode MODE = AgentMode.WORKPLACE_STANDUP;

    @BeforeEach
    void setUp() {
        errorPatternGroup = new AssertionGroup("error-pattern", "Grammar and word choice error patterns recurring in the user's conversations");

        // Make executor run tasks synchronously for tests
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        service = new AssertionService(runner, memoryCueAgent, assertionRepository,
                groupRepository, lineageRepository, assertionEmbeddingStore, embeddingModel,
                executor,
                null); // promptLoader not needed — tasks are already registered in real app
    }

    // ── Extractor tests ─────────────────────────────────────────

    @Test
    void extract_emptyConversation_producesNoAssertions() {
        List<MessageData> messages = List.of();
        when(memoryCueAgent.detectSwitches(eq(messages), eq(MODE), any(TaskContext.class)))
                .thenReturn(Collections.emptyList());

        List<MemoryAssertion> result = service.extract(SESSION_ID, USER_ID, MODE, messages, errorPatternGroup);

        assertThat(result).isEmpty();
        verify(runner, never()).requestModel(any(TaskName.class), any(), any(TaskContext.class));
        verify(assertionRepository, never()).save(any());
    }

    @Test
    void extract_singleSegmentSingleTopic_savesOneAssertion() {
        List<MessageData> messages = List.of(
                new MessageData(MessageRole.USER, "I go to store yesterday", 0));
        when(memoryCueAgent.detectSwitches(eq(messages), eq(MODE), any(TaskContext.class)))
                .thenReturn(List.of(0)); // one switch at message 0 = one segment

        when(runner.requestModel(eq(TaskName.EXTRACT_TOPICS), any(), any(TaskContext.class)))
                .thenReturn(List.of("past tense"));
        when(runner.requestModel(eq(TaskName.EXTRACT_STATE), any(), any(TaskContext.class)))
                .thenReturn("User struggles with irregular past tense verbs");

        MemoryAssertion savedAssertion = new MemoryAssertion(errorPatternGroup, SESSION_ID, USER_ID, MODE,
                "past tense", "User struggles with irregular past tense verbs");
        when(assertionRepository.save(any(MemoryAssertion.class))).thenReturn(savedAssertion);

        List<MemoryAssertion> result = service.extract(SESSION_ID, USER_ID, MODE, messages, errorPatternGroup);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTopic()).isEqualTo("past tense");
        assertThat(result.get(0).getState()).isEqualTo("User struggles with irregular past tense verbs");
        assertThat(result.get(0).isEnabled()).isTrue();

        ArgumentCaptor<MemoryAssertion> captor = ArgumentCaptor.forClass(MemoryAssertion.class);
        verify(assertionRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("past tense");
    }

    @Test
    void extract_multipleTopics_savesMultipleAssertions() {
        List<MessageData> messages = List.of(
                new MessageData(MessageRole.USER, "I have many sheeps", 0));
        when(memoryCueAgent.detectSwitches(eq(messages), eq(MODE), any(TaskContext.class)))
                .thenReturn(Collections.emptyList());

        when(runner.requestModel(eq(TaskName.EXTRACT_TOPICS), any(), any(TaskContext.class)))
                .thenReturn(List.of("plural nouns", "article usage"));
        when(runner.requestModel(eq(TaskName.EXTRACT_STATE), any(), any(TaskContext.class)))
                .thenReturn("state text");

        when(assertionRepository.save(any(MemoryAssertion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<MemoryAssertion> result = service.extract(SESSION_ID, USER_ID, MODE, messages, errorPatternGroup);

        assertThat(result).hasSize(2);
        verify(assertionRepository, times(2)).save(any(MemoryAssertion.class));
    }

    @Test
    void extract_llmFailure_propagatesException() {
        List<MessageData> messages = List.of(
                new MessageData(MessageRole.USER, "test", 0));
        when(memoryCueAgent.detectSwitches(eq(messages), eq(MODE), any(TaskContext.class)))
                .thenReturn(Collections.emptyList());

        when(runner.requestModel(eq(TaskName.EXTRACT_TOPICS), any(), any(TaskContext.class)))
                .thenThrow(new RuntimeException("LLM error"));

        assertThatThrownBy(() -> service.extract(SESSION_ID, USER_ID, MODE, messages, errorPatternGroup))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("LLM error");

        // Step2 should never be called after Step1 failure
        verify(runner, never()).requestModel(eq(TaskName.EXTRACT_STATE), any(), any(TaskContext.class));
        verify(assertionRepository, never()).save(any());
    }

    // ── Manager tests ───────────────────────────────────────────

    @Test
    void manage_noSemanticMatches_noMerges() {
        MemoryAssertion newAssertion = createAssertion("new-1", "past tense", "User has improved past tense usage");

        // Even if search returned candidates, they'd be filtered by DB check
        // We rely on the embedding store search being empty
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(new float[384])));
        when(assertionEmbeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(Collections.emptyList()));

        service.manage(SESSION_ID, USER_ID, MODE, List.of(newAssertion),
                new TaskContext(SESSION_ID, USER_ID, MODE.name()));

        verify(runner, never()).requestModel(eq(TaskName.JUDGE_SAME), any(), any());
        verify(runner, never()).requestModel(eq(TaskName.MERGE_ASSERTION), any(), any());
        verify(lineageRepository, never()).save(any());
    }

    @Test
    void manage_oneYesMatch_performsMerge() {
        MemoryAssertion newAssertion = createAssertion("new-1", "past tense", "User has improved past tense usage");
        MemoryAssertion oldAssertion = createAssertion("old-1", "past tense", "User struggled with past tense");
        // old assertion is from a different session
        oldAssertion.setSessionId("old-session");

        // Mock search to return the old assertion
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(new float[384])));
        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
        metadata.add("assertionId", oldAssertion.getId());
        TextSegment matchSegment = TextSegment.from(oldAssertion.getState(), metadata);
        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(
                List.of(new dev.langchain4j.store.embedding.EmbeddingMatch<>(0.85, "id", new Embedding(new float[384]), matchSegment)));
        when(assertionEmbeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);

        when(assertionRepository.findById(oldAssertion.getId())).thenReturn(Optional.of(oldAssertion));

        // Judge returns YES
        when(runner.requestModel(eq(TaskName.JUDGE_SAME), any(), any(TaskContext.class)))
                .thenReturn(true);
        // Merge returns combined state
        when(runner.requestModel(eq(TaskName.MERGE_ASSERTION), any(), any(TaskContext.class)))
                .thenReturn("User struggled with past tense but has shown improvement");

        when(assertionRepository.save(any(MemoryAssertion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.manage(SESSION_ID, USER_ID, MODE, List.of(newAssertion),
                new TaskContext(SESSION_ID, USER_ID, MODE.name()));

        // Old assertion should be disabled
        verify(assertionRepository).save(argThat(a -> !a.isEnabled() && a.getId().equals(oldAssertion.getId())));

        // New merged assertion should be saved
        verify(assertionRepository, atLeastOnce()).save(argThat(a ->
                a.isEnabled() && "User struggled with past tense but has shown improvement".equals(a.getState())));

        // Lineage edge should be recorded
        verify(lineageRepository).save(argThat(l ->
                l.getParentId().equals(oldAssertion.getId()) && "MERGE".equals(l.getOperation())));
    }

    @Test
    void manage_judgeReturnsNo_skipsMerge() {
        MemoryAssertion newAssertion = createAssertion("new-2", "past tense", "User improved");
        MemoryAssertion oldAssertion = createAssertion("old-2", "articles", "User struggles with articles");
        oldAssertion.setSessionId("old-session");

        when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(new float[384])));
        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
        metadata.add("assertionId", oldAssertion.getId());
        TextSegment matchSegment = TextSegment.from(oldAssertion.getState(), metadata);
        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(
                List.of(new dev.langchain4j.store.embedding.EmbeddingMatch<>(0.8, "id", new Embedding(new float[384]), matchSegment)));
        when(assertionEmbeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
        when(assertionRepository.findById(oldAssertion.getId())).thenReturn(Optional.of(oldAssertion));

        // Judge returns NO
        when(runner.requestModel(eq(TaskName.JUDGE_SAME), any(), any(TaskContext.class)))
                .thenReturn(false);

        service.manage(SESSION_ID, USER_ID, MODE, List.of(newAssertion),
                new TaskContext(SESSION_ID, USER_ID, MODE.name()));

        // Merge should NOT be called
        verify(runner, never()).requestModel(eq(TaskName.MERGE_ASSERTION), any(), any());
        verify(lineageRepository, never()).save(any());
        // Old assertion should NOT be disabled
        verify(assertionRepository, never()).save(argThat(a -> !a.isEnabled()));
    }

    @Test
    void manage_sameSessionCandidate_skipsMerge() {
        // Two assertions from the same session — should NOT merge with each other
        MemoryAssertion newAssertion = createAssertion("new-1", "past tense", "User struggles with past tense");
        MemoryAssertion sameSessionOld = createAssertion("new-2", "articles", "User omits articles");
        // sameSessionOld is from the same session (SESSION_ID = "ses-1")

        when(embeddingModel.embed(anyString())).thenReturn(Response.from(new Embedding(new float[384])));
        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
        metadata.add("assertionId", sameSessionOld.getId());
        TextSegment matchSegment = TextSegment.from(sameSessionOld.getState(), metadata);
        EmbeddingSearchResult<TextSegment> searchResult = new EmbeddingSearchResult<>(
                List.of(new dev.langchain4j.store.embedding.EmbeddingMatch<>(0.85, "id", new Embedding(new float[384]), matchSegment)));
        when(assertionEmbeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(searchResult);
        when(assertionRepository.findById(sameSessionOld.getId())).thenReturn(Optional.of(sameSessionOld));

        service.manage(SESSION_ID, USER_ID, MODE, List.of(newAssertion),
                new TaskContext(SESSION_ID, USER_ID, MODE.name()));

        // Same session → Judge should NOT be called, no merge
        verify(runner, never()).requestModel(eq(TaskName.JUDGE_SAME), any(), any());
        verify(runner, never()).requestModel(eq(TaskName.MERGE_ASSERTION), any(), any());
        verify(lineageRepository, never()).save(any());
    }

    // ── Helpers ────────────────────────────────────────────────

    private MemoryAssertion createAssertion(String id, String topic, String state) {
        MemoryAssertion a = new MemoryAssertion(errorPatternGroup, SESSION_ID, USER_ID, MODE, topic, state);
        a.setId(id);
        return a;
    }
}
