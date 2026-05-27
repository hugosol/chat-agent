package com.hugosol.webagent.repository;

import com.hugosol.webagent.config.JpaConfig;
import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryCue;
import com.hugosol.webagent.model.MemoryCueStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class MemoryCueRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MemoryCueRepository repository;

    @Test
    void saveAndFindById_persistsCorrectly() {
        MemoryCue cue = new MemoryCue("ses-1", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "Travel plans", "Talked about Japan trip",
                MemoryCueStatus.COMPLETED);
        entityManager.persistFlushFind(cue);

        var found = repository.findById(cue.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTopic()).isEqualTo("Travel plans");
        assertThat(found.get().getStatus()).isEqualTo(MemoryCueStatus.COMPLETED);
    }

    @Test
    void findBySessionId_returnsCorrectRecords() {
        entityManager.persistFlushFind(new MemoryCue("s-abc", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "Topic A", "summary A", MemoryCueStatus.COMPLETED));
        entityManager.persistFlushFind(new MemoryCue("s-abc", "user-1", AgentMode.WORKPLACE_STANDUP, 1,
                "Topic B", "summary B", MemoryCueStatus.COMPLETED));
        entityManager.persistFlushFind(new MemoryCue("s-xyz", "user-1", AgentMode.WORKPLACE_STANDUP, 0,
                "Other", "other summary", MemoryCueStatus.COMPLETED));

        List<MemoryCue> results = repository.findBySessionId("s-abc");
        assertThat(results).hasSize(2);
        assertThat(results).extracting(MemoryCue::getSegmentIndex).containsExactly(0, 1);
    }

    @Test
    void findBySessionId_returnsEmptyForUnknownSession() {
        List<MemoryCue> results = repository.findBySessionId("nonexistent");
        assertThat(results).isEmpty();
    }
}
