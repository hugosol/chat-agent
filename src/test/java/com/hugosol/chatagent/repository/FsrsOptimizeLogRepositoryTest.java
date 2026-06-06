package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.FsrsOptimizeLog;
import com.hugosol.chatagent.model.OptimizeStatus;
import com.hugosol.chatagent.model.TriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class FsrsOptimizeLogRepositoryTest {

    @Autowired
    private FsrsOptimizeLogRepository repository;

    @Test
    void persistAndRetrieveByUserId() {
        FsrsOptimizeLog log = new FsrsOptimizeLog();
        log.setUserId("user1");
        log.setTriggerType(TriggerType.MANUAL);
        log.setStatus(OptimizeStatus.SUCCESS);
        log.setTotalReviewLogs(600);
        log.setNonSameDayReviews(550);
        log.setCardSequences(120);
        log.setEpochs(5);
        log.setIterations(250);
        log.setFinalLoss(0.32);
        log.setDefaultLoss(0.45);
        log.setLossImprovement(0.13);
        log.setParamsUpdated(true);
        log.setWeightsBefore("[0.5,0.6,0.7]");
        log.setWeightsAfter("[0.4,0.5,0.6]");
        log.setStartTime(Instant.now());
        log.setEndTime(Instant.now().plusMillis(5000));
        log.setDurationMs(5000);

        repository.save(log);

        var page = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 4));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo("user1");
        assertThat(page.getContent().get(0).getTriggerType()).isEqualTo(TriggerType.MANUAL);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(OptimizeStatus.SUCCESS);
        assertThat(page.getContent().get(0).getFinalLoss()).isCloseTo(0.32, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(page.getContent().get(0).isParamsUpdated()).isTrue();
    }

    @Test
    void paginationReturnsCorrectSlice() {
        for (int i = 0; i < 6; i++) {
            FsrsOptimizeLog log = new FsrsOptimizeLog();
            log.setUserId("user1");
            log.setTriggerType(TriggerType.SCHEDULED);
            log.setStatus(OptimizeStatus.SKIPPED);
            log.setStartTime(Instant.now().minusSeconds(i * 3600L));
            log.setEndTime(Instant.now().minusSeconds(i * 3600L));
            repository.save(log);
        }

        var page1 = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 4));
        assertThat(page1.getContent()).hasSize(4);
        assertThat(page1.getTotalElements()).isEqualTo(6);
        assertThat(page1.getTotalPages()).isEqualTo(2);

        var page2 = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(1, 4));
        assertThat(page2.getContent()).hasSize(2);
    }

    @Test
    void userIsolation() {
        FsrsOptimizeLog log1 = new FsrsOptimizeLog();
        log1.setUserId("user1");
        log1.setTriggerType(TriggerType.MANUAL);
        log1.setStatus(OptimizeStatus.SUCCESS);
        log1.setStartTime(Instant.now());
        log1.setEndTime(Instant.now());
        repository.save(log1);

        FsrsOptimizeLog log2 = new FsrsOptimizeLog();
        log2.setUserId("user2");
        log2.setTriggerType(TriggerType.MANUAL);
        log2.setStatus(OptimizeStatus.FAILED);
        log2.setStartTime(Instant.now());
        log2.setEndTime(Instant.now());
        repository.save(log2);

        var page = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo("user1");
    }

    @Test
    void recordsSkippedStatus() {
        FsrsOptimizeLog log = new FsrsOptimizeLog();
        log.setUserId("user1");
        log.setTriggerType(TriggerType.SCHEDULED);
        log.setStatus(OptimizeStatus.SKIPPED);
        log.setTotalReviewLogs(200);
        log.setErrorMessage("insufficient_data: 200 < 512");
        log.setStartTime(Instant.now());
        log.setEndTime(Instant.now());
        repository.save(log);

        var page = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 4));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(OptimizeStatus.SKIPPED);
        assertThat(page.getContent().get(0).getErrorMessage()).contains("insufficient_data");
    }
}
