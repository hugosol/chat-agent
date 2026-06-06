package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.model.FsrsRescheduleLog;
import com.hugosol.chatagent.model.RescheduleStatus;
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
class FsrsRescheduleLogRepositoryTest {

    @Autowired
    private FsrsRescheduleLogRepository repository;

    @Test
    void persistAndRetrieveByUserId() {
        FsrsRescheduleLog log = new FsrsRescheduleLog();
        log.setUserId("user1");
        log.setOptimizeLogId("opt-uuid-1");
        log.setTriggerType(TriggerType.MANUAL);
        log.setStatus(RescheduleStatus.SUCCESS);
        log.setTotalCardsWithHistory(150);
        log.setRescheduledCards(150);
        log.setStartTime(Instant.now());
        log.setEndTime(Instant.now().plusMillis(2000));
        log.setDurationMs(2000);

        repository.save(log);

        var page = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 4));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo("user1");
        assertThat(page.getContent().get(0).getOptimizeLogId()).isEqualTo("opt-uuid-1");
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(RescheduleStatus.SUCCESS);
        assertThat(page.getContent().get(0).getRescheduledCards()).isEqualTo(150);
    }

    @Test
    void recordsEmptyReschedule() {
        FsrsRescheduleLog log = new FsrsRescheduleLog();
        log.setUserId("user1");
        log.setTriggerType(TriggerType.SCHEDULED);
        log.setStatus(RescheduleStatus.SUCCESS);
        log.setTotalCardsWithHistory(0);
        log.setRescheduledCards(0);
        log.setStartTime(Instant.now());
        log.setEndTime(Instant.now());
        repository.save(log);

        var page = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 4));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getRescheduledCards()).isEqualTo(0);
        assertThat(page.getContent().get(0).getTotalCardsWithHistory()).isEqualTo(0);
    }

    @Test
    void paginationAndOrdering() {
        for (int i = 0; i < 6; i++) {
            FsrsRescheduleLog log = new FsrsRescheduleLog();
            log.setUserId("user1");
            log.setTriggerType(TriggerType.SCHEDULED);
            log.setStatus(RescheduleStatus.SUCCESS);
            log.setStartTime(Instant.now().minusSeconds(i * 3600L));
            log.setEndTime(Instant.now().minusSeconds(i * 3600L));
            repository.save(log);
        }

        var page1 = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 4));
        assertThat(page1.getContent()).hasSize(4);
        assertThat(page1.getTotalElements()).isEqualTo(6);

        var page2 = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(1, 4));
        assertThat(page2.getContent()).hasSize(2);

        assertThat(page1.getContent().get(0).getStartTime())
                .isAfter(page2.getContent().get(0).getStartTime());
    }

    @Test
    void userIsolation() {
        FsrsRescheduleLog log1 = new FsrsRescheduleLog();
        log1.setUserId("user1");
        log1.setTriggerType(TriggerType.MANUAL);
        log1.setStatus(RescheduleStatus.SUCCESS);
        log1.setStartTime(Instant.now());
        log1.setEndTime(Instant.now());
        repository.save(log1);

        FsrsRescheduleLog log2 = new FsrsRescheduleLog();
        log2.setUserId("user2");
        log2.setTriggerType(TriggerType.MANUAL);
        log2.setStatus(RescheduleStatus.FAILED);
        log2.setStartTime(Instant.now());
        log2.setEndTime(Instant.now());
        repository.save(log2);

        var page = repository.findByUserIdOrderByStartTimeDesc("user1", PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo("user1");
    }
}
