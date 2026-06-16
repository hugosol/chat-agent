package com.hugosol.chatagent.model;

import com.hugosol.chatagent.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaConfig.class)
class CardEnhancementAuditingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldSetCreateTimeAndUpdateTime() {
        CardEnhancement enhancement = new CardEnhancement("card-1", EnhancementType.SUBTITLE,
                EnhancementStatus.PENDING, null, null, null);

        CardEnhancement saved = entityManager.persistFlushFind(enhancement);

        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
    }
}
