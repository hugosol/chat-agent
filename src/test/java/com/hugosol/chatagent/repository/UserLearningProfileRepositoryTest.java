package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.LearningType;
import com.hugosol.chatagent.model.UserLearningProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.hugosol.chatagent.config.JpaConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class UserLearningProfileRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserLearningProfileRepository repository;

    @Test
    void findTopByUserIdAndTypeOrderByVersionDesc_returnsLatestVersion() {
        entityManager.persistFlushFind(new UserLearningProfile("user-1", LearningType.LEARNING_PROFILE, "v1 content", 1));
        entityManager.persistFlushFind(new UserLearningProfile("user-1", LearningType.LEARNING_PROFILE, "v3 content", 3));
        entityManager.persistFlushFind(new UserLearningProfile("user-1", LearningType.LEARNING_PROFILE, "v2 content", 2));

        Optional<UserLearningProfile> result = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "user-1", LearningType.LEARNING_PROFILE, null);

        assertThat(result).isPresent();
        assertThat(result.get().getContent()).isEqualTo("v3 content");
        assertThat(result.get().getVersion()).isEqualTo(3);
    }

    @Test
    void findTopByUserIdAndTypeOrderByVersionDesc_separatesByType() {
        entityManager.persistFlushFind(new UserLearningProfile("user-1", LearningType.LEARNING_PROFILE, "profile v1", 1));

        Optional<UserLearningProfile> profileResult = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "user-1", LearningType.LEARNING_PROFILE, null);

        assertThat(profileResult).isPresent();
        assertThat(profileResult.get().getContent()).isEqualTo("profile v1");
    }

    @Test
    void findTopByUserIdAndTypeOrderByVersionDesc_returnsEmptyWhenNoMatches() {
        Optional<UserLearningProfile> result = repository.findTopByUserIdAndTypeAndModeOrderByVersionDesc(
                "nonexistent", LearningType.LEARNING_PROFILE, null);

        assertThat(result).isEmpty();
    }
}
