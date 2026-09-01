package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class MemoryAssertionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MemoryAssertionRepository assertionRepository;

    @Autowired
    private AssertionLineageRepository lineageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AssertionGroup errorPatternGroup;

    @BeforeEach
    void setUp() {
        errorPatternGroup = entityManager.persistFlushFind(
                new AssertionGroup("error-pattern", "Grammar and word choice error patterns recurring in the user's conversations"));
    }

    @Test
    void saveAndFindById_persistsCorrectly() {
        MemoryAssertion assertion = new MemoryAssertion(errorPatternGroup, "ses-1", "user-1",
                AgentMode.WORKPLACE_STANDUP, "past tense", "User struggles with irregular past tense verbs");
        entityManager.persistFlushFind(assertion);

        var found = assertionRepository.findById(assertion.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTopic()).isEqualTo("past tense");
        assertThat(found.get().getState()).isEqualTo("User struggles with irregular past tense verbs");
        assertThat(found.get().isEnabled()).isTrue();
        assertThat(found.get().getGroup().getName()).isEqualTo("error-pattern");
    }

    @Test
    void findByEnabled_true_excludesDisabled() {
        MemoryAssertion enabled = new MemoryAssertion(errorPatternGroup, "ses-1", "user-1",
                AgentMode.WORKPLACE_STANDUP, "past tense", "struggles with past tense");
        MemoryAssertion disabled = new MemoryAssertion(errorPatternGroup, "ses-2", "user-1",
                AgentMode.WORKPLACE_STANDUP, "past tense", "merged: past tense improving");
        disabled.setEnabled(false);

        entityManager.persistFlushFind(enabled);
        entityManager.persistFlushFind(disabled);

        List<MemoryAssertion> results = assertionRepository.findByEnabled(true);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(enabled.getId());
    }

    @Test
    void findByUserIdAndMode_isolatesCorrectly() {
        entityManager.persistFlushFind(new MemoryAssertion(errorPatternGroup, "ses-1", "user-A",
                AgentMode.WORKPLACE_STANDUP, "topic-a", "state a"));
        entityManager.persistFlushFind(new MemoryAssertion(errorPatternGroup, "ses-2", "user-A",
                AgentMode.DAILY_TALK, "topic-b", "state b"));
        entityManager.persistFlushFind(new MemoryAssertion(errorPatternGroup, "ses-3", "user-B",
                AgentMode.WORKPLACE_STANDUP, "topic-c", "state c"));

        List<MemoryAssertion> userA_workplace = assertionRepository
                .findByUserIdAndMode("user-A", AgentMode.WORKPLACE_STANDUP);
        assertThat(userA_workplace).hasSize(1);
        assertThat(userA_workplace.get(0).getTopic()).isEqualTo("topic-a");

        List<MemoryAssertion> userB_workplace = assertionRepository
                .findByUserIdAndMode("user-B", AgentMode.WORKPLACE_STANDUP);
        assertThat(userB_workplace).hasSize(1);
        assertThat(userB_workplace.get(0).getTopic()).isEqualTo("topic-c");
    }

    @Test
    void lineage_recursiveCte_tracesFullAncestry() {
        MemoryAssertion root = entityManager.persistFlushFind(
                new MemoryAssertion(errorPatternGroup, "ses-1", "user-1",
                        AgentMode.WORKPLACE_STANDUP, "past tense", "v1: struggles with past tense"));
        MemoryAssertion mid = entityManager.persistFlushFind(
                new MemoryAssertion(errorPatternGroup, "ses-2", "user-1",
                        AgentMode.WORKPLACE_STANDUP, "past tense", "v2: past tense improving"));
        MemoryAssertion leaf = entityManager.persistFlushFind(
                new MemoryAssertion(errorPatternGroup, "ses-3", "user-1",
                        AgentMode.WORKPLACE_STANDUP, "past tense", "v3: past tense mostly correct"));

        entityManager.persistFlushFind(new AssertionLineage(root.getId(), mid.getId(), "MERGE"));
        entityManager.persistFlushFind(new AssertionLineage(mid.getId(), leaf.getId(), "MERGE"));

        List<String> ancestors = lineageRepository.findAncestorIds(leaf.getId());
        assertThat(ancestors).containsExactlyInAnyOrder(root.getId(), mid.getId());
    }

    @Test
    void lineage_recursiveCte_noAncestors_returnsEmpty() {
        MemoryAssertion orphan = entityManager.persistFlushFind(
                new MemoryAssertion(errorPatternGroup, "ses-1", "user-1",
                        AgentMode.WORKPLACE_STANDUP, "topic", "state"));
        List<String> ancestors = lineageRepository.findAncestorIds(orphan.getId());
        assertThat(ancestors).isEmpty();
    }
}
