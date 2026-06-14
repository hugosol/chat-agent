package com.hugosol.chatagent.service;

import com.hugosol.chatagent.config.JpaConfig;
import com.hugosol.chatagent.flashcard.Rating;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.FsrsOptimizeLog;
import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.FsrsRescheduleLog;
import com.hugosol.chatagent.model.OptimizeStatus;
import com.hugosol.chatagent.model.RescheduleStatus;
import com.hugosol.chatagent.model.ReviewLog;
import com.hugosol.chatagent.model.TriggerType;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.FsrsOptimizeLogRepository;
import com.hugosol.chatagent.repository.FsrsParametersRepository;
import com.hugosol.chatagent.repository.FsrsRescheduleLogRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;
import com.hugosol.chatagent.repository.UserPreferencesRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that exercises {@link FsrsOptimizeService#executeOptimize}
 * with a real H2 database, verifying that both optimize and reschedule logs
 * are persisted correctly.
 *
 * <p>This test catches the Hibernate 6 StaleObjectStateException that occurs
 * when saving an entity with a manually-set {@code @GeneratedValue} UUID ID.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
@Import({JpaConfig.class, FsrsOptimizeService.class, FsrsParametersService.class,
        FsrsConfigService.class, UserPreferencesService.class, TestOptimizerExecutorConfig.class})
@ActiveProfiles("test")
class FsrsOptimizeServiceIT {

    private static final Instant BASE_TIME = Instant.parse("2025-01-01T00:00:00Z");
    private static final int NUM_CARDS = 60;
    private static final int REVIEWS_PER_CARD = 11; // 660 total > 512 MIN_REVIEWS

    @Autowired
    private ReviewLogRepository reviewLogRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private FsrsParametersRepository paramsRepository;

    @Autowired
    private UserPreferencesRepository preferencesRepository;

    @Autowired
    private FsrsOptimizeLogRepository optimizeLogRepository;

    @Autowired
    private FsrsRescheduleLogRepository rescheduleLogRepository;

    @Autowired
    private FsrsOptimizeService service;

    private final String userId = "integration-test-user";

    @BeforeEach
    void setUp() {
        // Create default parameters
        FsrsParameters params = FsrsParameters.defaults(userId);
        paramsRepository.save(params);

        // Create default preferences
        UserPreferences prefs = new UserPreferences(userId);
        preferencesRepository.save(prefs);

        // Create cards and review logs
        Random rng = new Random(42);
        List<Card> cards = new ArrayList<>();
        List<ReviewLog> allLogs = new ArrayList<>();

        // Build cards without IDs (let Hibernate generate UUIDs)
        // and accumulate review logs keyed by index, then wire after save.
        List<List<ReviewLog>> logsByCard = new ArrayList<>();
        for (int i = 0; i < NUM_CARDS; i++) {
            Card card = new Card();
            card.setUserId(userId);
            card.setFront("front " + i);
            card.setBack("back " + i);
            card.setStability(2.5);
            card.setDifficulty(0.0);
            card.setCardState(0); // New
            card.setDue(Instant.now());
            card.setReps(0);
            card.setLapses(0);
            card.setStep(0);
            cards.add(card);

            List<ReviewLog> perCard = new ArrayList<>();
            for (int r = 0; r < REVIEWS_PER_CARD; r++) {
                Rating rating = Rating.values()[rng.nextInt(4)];
                Instant reviewedAt = BASE_TIME
                        .plus(Duration.ofDays(i * 30L + r * 3L + rng.nextInt(3)));
                ReviewLog log = new ReviewLog();
                // Let Hibernate generate ReviewLog ID too
                log.setUserId(userId);
                log.setRating(rating);
                log.setReviewedAt(reviewedAt);
                log.setScheduledDays(1.0);
                log.setElapsedDays(1.0);
                perCard.add(log);
            }
            logsByCard.add(perCard);
        }
        List<Card> savedCards = cardRepository.saveAll(cards);

        // Wire card IDs into review logs after save
        for (int i = 0; i < NUM_CARDS; i++) {
            String cardId = savedCards.get(i).getId();
            for (ReviewLog log : logsByCard.get(i)) {
                log.setCardId(cardId);
                allLogs.add(log);
            }
        }
        reviewLogRepository.saveAll(allLogs);
    }

    @Test
    void executeOptimize_persistsOptimizeLogAndRescheduleLog() {
        String taskId = UUID.randomUUID().toString();
        var future = service.executeOptimize(userId, taskId, TriggerType.MANUAL);

        // The @Async method returns CompletableFuture; with synchronous executor
        // it completes immediately
        var result = future.join();

        assertThat(result).isNotNull();
        assertThat(result.finalLoss()).isGreaterThan(0.0);

        // Verify optimize log was persisted
        List<FsrsOptimizeLog> optLogs = optimizeLogRepository.findAll();
        assertThat(optLogs).hasSize(1);
        FsrsOptimizeLog optLog = optLogs.get(0);
        assertThat(optLog.getUserId()).isEqualTo(userId);
        assertThat(optLog.getStatus()).isEqualTo(OptimizeStatus.SUCCESS);
        assertThat(optLog.isParamsUpdated()).isTrue();
        assertThat(optLog.getId()).isNotNull().isNotEmpty();

        // Verify reschedule log was persisted and references optimize log
        List<FsrsRescheduleLog> resLogs = rescheduleLogRepository.findAll();
        assertThat(resLogs).hasSize(1);
        FsrsRescheduleLog resLog = resLogs.get(0);
        assertThat(resLog.getUserId()).isEqualTo(userId);
        assertThat(resLog.getStatus()).isEqualTo(RescheduleStatus.SUCCESS);
        assertThat(resLog.getRescheduledCards()).isGreaterThan(0);
        assertThat(resLog.getOptimizeLogId()).isEqualTo(optLog.getId());
    }
}
