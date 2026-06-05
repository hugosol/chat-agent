package com.hugosol.chatagent.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.hugosol.chatagent.repository.FsrsParametersRepository;

import com.hugosol.chatagent.config.JpaConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaConfig.class)
class FsrsParametersRepositoryTest {

    @Autowired
    private FsrsParametersRepository repository;

    @Test
    void defaults_createsDefaultWeights() {
        FsrsParameters params = FsrsParameters.defaults("user1");
        assertThat(params.getUserId()).isEqualTo("user1");
        double[] w = params.getWeights();
        assertThat(w).hasSize(21);
        assertThat(w[0]).isCloseTo(0.212, within(1e-9));
        assertThat(w[20]).isCloseTo(0.1542, within(1e-9));
        assertThat(params.isEnableShortTerm()).isTrue();
    }

    @Test
    void saveAndFindByUserId() {
        FsrsParameters params = FsrsParameters.defaults("user1");
        repository.save(params);

        var found = repository.findByUserId("user1");
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("user1");
        assertThat(found.get().getWeights()).hasSize(21);
    }

    @Test
    void findByUserId_notFound_returnsEmpty() {
        var result = repository.findByUserId("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void getWeights_matchesIndividualGetters() {
        FsrsParameters params = FsrsParameters.defaults("user1");
        double[] w = params.getWeights();
        assertThat(w[0]).isCloseTo(params.getW0(), within(1e-9));
        assertThat(w[1]).isCloseTo(params.getW1(), within(1e-9));
        assertThat(w[20]).isCloseTo(params.getW20(), within(1e-9));
    }
}
