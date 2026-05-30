package com.hugosol.chatagent.service;

import com.hugosol.chatagent.config.AppProperties;
import com.hugosol.chatagent.dto.CueMatch;
import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MemoryCue;
import com.hugosol.chatagent.model.MemoryCueStatus;
import com.hugosol.chatagent.repository.MemoryCueRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    @TempDir
    Path tempDir;

    private EmbeddingService service;
    private InMemoryEmbeddingStore<TextSegment> store;
    private StubEmbeddingModel embeddingModel;
    private MemoryCueRepository repository;
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        store = new InMemoryEmbeddingStore<>();
        embeddingModel = new StubEmbeddingModel();
        repository = mock(MemoryCueRepository.class);
        appProperties = new AppProperties();
        service = new EmbeddingService(
                embeddingModel, store, repository, appProperties,
                Executors.newSingleThreadExecutor());
    }

    @AfterEach
    void tearDown() {
        service = null;
    }

    @Test
    void indexAsync_addsEntryWithCorrectMetadata() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 5, 28, 10, 0);
        service.indexAsync("cue-1", "Travel Plans", "Discussed Japan trip details",
                AgentMode.WORKPLACE_STANDUP, "user-1", now).get();

        var result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Travel Plans Discussed Japan trip details").content())
                .maxResults(1)
                .build());
        assertThat(result.matches()).hasSize(1);
        var embedded = result.matches().get(0).embedded();
        assertThat(embedded.metadata().getString("cueId")).isEqualTo("cue-1");
        assertThat(embedded.metadata().getString("topic")).isEqualTo("Travel Plans");
        assertThat(embedded.metadata().getString("summary")).isEqualTo("Discussed Japan trip details");
        assertThat(embedded.metadata().getString("mode")).isEqualTo("WORKPLACE_STANDUP");
        assertThat(embedded.metadata().getString("userId")).isEqualTo("user-1");
        assertThat(embedded.metadata().getString("createdAt")).isNotNull();
    }

    @Test
    void search_returnsMatchingResultsAboveThreshold() {
        String summary = "login module progress summary";
        indexEntry("cue-1", "Work Standup", summary, AgentMode.WORKPLACE_STANDUP, "user-1");
        indexEntry("cue-2", "Travel Plans", "Planning trip to Japan", AgentMode.WORKPLACE_STANDUP, "user-1");

        List<CueMatch> results = service.search("Work Standup " + summary, AgentMode.WORKPLACE_STANDUP, "user-1", 2, 0.6);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).cueId()).isEqualTo("cue-1");
        assertThat(results.get(0).topic()).isEqualTo("Work Standup");
        assertThat(results.get(0).summary()).isEqualTo(summary);
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void search_returnsSummaryWithoutTopicDuplication() {
        indexEntry("cue-1", "Work Standup", "Discussed login module",
                AgentMode.WORKPLACE_STANDUP, "user-1");

        List<CueMatch> results = service.search("Work Standup Discussed login module",
                AgentMode.WORKPLACE_STANDUP, "user-1", 2, 0.5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).summary()).isEqualTo("Discussed login module");
        assertThat(results.get(0).summary()).doesNotContain("Work Standup");
    }

    @Test
    void search_returnsEmptyForNonMatchingQuery() {
        indexEntry("cue-1", "Work Standup", "Discussed login module progress",
                AgentMode.WORKPLACE_STANDUP, "user-1");

        List<CueMatch> results = service.search("ZXY completely different topic QWE",
                AgentMode.WORKPLACE_STANDUP, "user-1", 2, 0.6);

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmptyForWrongMode() {
        indexEntry("cue-1", "Work Standup", "Discussed login module",
                AgentMode.WORKPLACE_STANDUP, "user-1");

        List<CueMatch> results = service.search("Discussed login module",
                AgentMode.DAILY_TALK, "user-1", 2, 0.3);

        assertThat(results).isEmpty();
    }

    @Test
    void search_returnsEmptyForWrongUserId() {
        indexEntry("cue-1", "Work Standup", "Discussed login module",
                AgentMode.WORKPLACE_STANDUP, "user-1");

        List<CueMatch> results = service.search("Discussed login module",
                AgentMode.WORKPLACE_STANDUP, "user-2", 2, 0.3);

        assertThat(results).isEmpty();
    }

    @Test
    void init_buildsFromH2WhenNoDiskFile() throws Exception {
        Path storePath = tempDir.resolve("nonexistent-store.json");
        Files.deleteIfExists(storePath);

        EmbeddingService svc = new EmbeddingService(
                embeddingModel, store, repository, appProperties,
                Executors.newSingleThreadExecutor()) {
            @Override
            protected Path getStorePath() {
                return storePath;
            }
        };

        when(repository.findAllByStatus(MemoryCueStatus.COMPLETED)).thenReturn(List.of(
                cue("cue-1", "Work", "login summary", AgentMode.WORKPLACE_STANDUP, "user-1"),
                cue("cue-2", "Travel", "japan summary", AgentMode.DAILY_TALK, "user-1")
        ));

        svc.init();

        assertThat(svc.search("Work login summary", AgentMode.WORKPLACE_STANDUP, "user-1", 5, 0.0)).hasSize(1);
        assertThat(svc.search("Travel japan summary", AgentMode.DAILY_TALK, "user-1", 5, 0.0)).hasSize(1);
    }

    @Test
    void init_loadsFromExistingDiskFile() throws Exception {
        indexEntry("cue-existing", "Existing", "existing summary text",
                AgentMode.WORKPLACE_STANDUP, "user-1");
        Path filePath = tempDir.resolve("existing-store.json");
        store.serializeToFile(filePath);
        Files.writeString(tempDir.resolve("existing-store.json.version"), "1");

        InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();
        EmbeddingService newService = new EmbeddingService(
                embeddingModel, newStore, repository, appProperties,
                Executors.newSingleThreadExecutor()) {
            @Override
            protected Path getStorePath() {
                return filePath;
            }
        };

        when(repository.findAllByStatus(MemoryCueStatus.COMPLETED)).thenReturn(List.of());
        newService.init();

        var results = newService.getStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Existing existing summary text").content())
                .maxResults(5)
                .build());
        assertThat(results.matches()).hasSize(1);
    }

    @Test
    void init_migratesFromOldStoreWithoutVersionFile() throws Exception {
        indexEntry("cue-old", "Old Topic", "old summary text",
                AgentMode.WORKPLACE_STANDUP, "user-1");
        Path filePath = tempDir.resolve("old-store.json");
        store.serializeToFile(filePath);

        InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();
        EmbeddingService newService = new EmbeddingService(
                embeddingModel, newStore, repository, appProperties,
                Executors.newSingleThreadExecutor()) {
            @Override
            protected Path getStorePath() {
                return filePath;
            }
        };

        when(repository.findAllByStatus(MemoryCueStatus.COMPLETED)).thenReturn(List.of(
                cue("cue-h2", "H2 Topic", "h2 summary text", AgentMode.WORKPLACE_STANDUP, "user-1")
        ));
        newService.init();

        assertThat(newService.search("H2 Topic h2 summary text",
                AgentMode.WORKPLACE_STANDUP, "user-1", 5, 0.0)).hasSize(1);
        assertThat(Files.exists(tempDir.resolve("old-store.json.version"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("old-store.json.version")).trim()).isEqualTo("1");
    }

    @Test
    void init_rebuildsFromH2WhenDiskFileCorrupted() throws Exception {
        Path filePath = tempDir.resolve("corrupted-store.json");
        Files.writeString(filePath, "not valid json {{{");

        InMemoryEmbeddingStore<TextSegment> newStore = new InMemoryEmbeddingStore<>();
        EmbeddingService newService = new EmbeddingService(
                embeddingModel, newStore, repository, appProperties,
                Executors.newSingleThreadExecutor()) {
            @Override
            protected Path getStorePath() {
                return filePath;
            }
        };

        when(repository.findAllByStatus(MemoryCueStatus.COMPLETED)).thenReturn(List.of(
                cue("cue-3", "Topic", "summary text", AgentMode.WORKPLACE_STANDUP, "user-1")
        ));
        newService.init();

        var results = newService.getStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Topic summary text").content())
                .maxResults(5)
                .build());
        assertThat(results.matches()).hasSize(1);
    }

    private void indexEntry(String cueId, String topic, String summary, AgentMode mode, String userId) {
        String text = topic + " " + summary;
        var embedded = embeddingModel.embed(text).content();
        var segment = TextSegment.from(text);
        segment.metadata().add("cueId", cueId);
        segment.metadata().add("topic", topic);
        segment.metadata().add("summary", summary);
        segment.metadata().add("mode", mode.name());
        segment.metadata().add("userId", userId);
        store.add(embedded, segment);
    }

    private MemoryCue cue(String id, String topic, String summary, AgentMode mode, String userId) {
        MemoryCue cue = new MemoryCue("s-1", userId, mode, 0, topic, summary, MemoryCueStatus.COMPLETED);
        cue.setId(id);
        return cue;
    }

    private static class StubEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<Embedding> embed(String text) {
            float[] vector = new float[384];
            int hash = text.hashCode();
            for (int i = 0; i < 384; i++) {
                vector[i] = (float) Math.sin(hash * (i + 1) * 0.01);
            }
            return Response.from(new Embedding(vector));
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream()
                    .map(s -> embed(s.text()).content())
                    .toList());
        }
    }
}
