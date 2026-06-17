package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.CardEnhancement;
import com.hugosol.chatagent.model.EnhancementStatus;
import com.hugosol.chatagent.model.EnhancementType;
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
class CardEnhancementRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CardEnhancementRepository repository;

    @Test
    void saveAndFindById_persistsCorrectly() {
        CardEnhancement enhancement = new CardEnhancement("card-1", EnhancementType.SUBTITLE,
                EnhancementStatus.SUCCESS,
                "{\"movieTitle\":\"Inception\"}", null, "https://api.subdl.com/tt1375666");
        entityManager.persistFlushFind(enhancement);

        var found = repository.findById(enhancement.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCardId()).isEqualTo("card-1");
        assertThat(found.get().getType()).isEqualTo(EnhancementType.SUBTITLE);
        assertThat(found.get().getStatus()).isEqualTo(EnhancementStatus.SUCCESS);
    }

    @Test
    void findByCardId_returnsMatchingEnhancements() {
        entityManager.persistFlushFind(new CardEnhancement("card-abc", EnhancementType.SUBTITLE,
                EnhancementStatus.SUCCESS, "{}", null, null));
        entityManager.persistFlushFind(new CardEnhancement("card-abc", EnhancementType.ETYMOLOGY,
                EnhancementStatus.SUCCESS, "From Latin...", null, null));
        entityManager.persistFlushFind(new CardEnhancement("card-xyz", EnhancementType.SUBTITLE,
                EnhancementStatus.FAILED, null, "Not found", null));

        List<CardEnhancement> results = repository.findByCardId("card-abc");
        assertThat(results).hasSize(2);
        assertThat(results).extracting(CardEnhancement::getType)
                .containsExactlyInAnyOrder(EnhancementType.SUBTITLE, EnhancementType.ETYMOLOGY);
    }

    @Test
    void findByCardId_returnsEmptyForUnknownCard() {
        List<CardEnhancement> results = repository.findByCardId("nonexistent");
        assertThat(results).isEmpty();
    }

    @Test
    void findById_returnsEmptyForUnknownId() {
        var found = repository.findById("nonexistent");
        assertThat(found).isEmpty();
    }
}
