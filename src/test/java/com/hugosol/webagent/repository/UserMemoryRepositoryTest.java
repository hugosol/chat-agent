package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.MemoryType;
import com.hugosol.webagent.model.UserMemory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.hugosol.webagent.config.JpaConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class UserMemoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserMemoryRepository repository;

    @Test
    void findTopByUserIdAndTypeOrderByVersionDesc_returnsLatestVersion() {
        entityManager.persistFlushFind(new UserMemory("user-1", MemoryType.TOPIC_SUMMARY, "v1 content", 1));
        entityManager.persistFlushFind(new UserMemory("user-1", MemoryType.TOPIC_SUMMARY, "v3 content", 3));
        entityManager.persistFlushFind(new UserMemory("user-1", MemoryType.TOPIC_SUMMARY, "v2 content", 2));

        Optional<UserMemory> result = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "user-1", MemoryType.TOPIC_SUMMARY, null);

        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo("v3 content");
        assertThat(result.get().getVersion()).isEqualTo(3);
    }

    @Test
    void findTopByUserIdAndTypeOrderByVersionDesc_separatesByType() {
        entityManager.persistFlushFind(new UserMemory("user-1", MemoryType.TOPIC_SUMMARY, "topic v1", 1));
        entityManager.persistFlushFind(new UserMemory("user-1", MemoryType.LEARNING_PROFILE, "profile v1", 1));

        Optional<UserMemory> topicResult = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "user-1", MemoryType.TOPIC_SUMMARY, null);
        Optional<UserMemory> profileResult = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "user-1", MemoryType.LEARNING_PROFILE, null);

        assertThat(topicResult).isPresent();
        assertThat(topicResult.get().getContent()).isEqualTo("topic v1");
        assertThat(profileResult).isPresent();
        assertThat(profileResult.get().getContent()).isEqualTo("profile v1");
    }

    @Test
    void findTopByUserIdAndTypeOrderByVersionDesc_returnsEmptyWhenNoMatches() {
        Optional<UserMemory> result = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "nonexistent", MemoryType.TOPIC_SUMMARY, null);

        assertThat(result).isEmpty();
    }
}
