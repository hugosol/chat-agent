package com.hugosol.chatagent.service;

import com.hugosol.chatagent.flashcard.FsrsSchedulerConfig;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.FsrsOptimizeLog;
import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.FsrsRescheduleLog;
import com.hugosol.chatagent.model.OptimizeStatus;
import com.hugosol.chatagent.model.RescheduleStatus;
import com.hugosol.chatagent.model.ReviewLog;
import com.hugosol.chatagent.model.TriggerType;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.FsrsOptimizeLogRepository;
import com.hugosol.chatagent.repository.FsrsParametersRepository;
import com.hugosol.chatagent.repository.FsrsRescheduleLogRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;
import com.hugosol.chatagent.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FsrsOptimizeServiceTest {

    private static final Instant BASE_TIME = Instant.parse("2024-01-01T00:00:00Z");

    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private FsrsParametersRepository paramsRepository;
    @Mock private CardRepository cardRepository;
    @Mock private UserPreferencesService preferencesService;
    @Mock private FsrsConfigService fsrsConfigService;
    @Mock private FsrsParametersService fsrsParametersService;
    @Mock private ExecutorService optimizerExecutor;
    @Mock private UserRepository userRepository;
    @Mock private FsrsOptimizeLogRepository optimizeLogRepository;
    @Mock private FsrsRescheduleLogRepository rescheduleLogRepository;

    private FsrsOptimizeService service;

    @BeforeEach
    void setUp() {
        service = new FsrsOptimizeService(
                reviewLogRepository, paramsRepository, cardRepository,
                preferencesService, fsrsConfigService, fsrsParametersService,
                optimizerExecutor,
                optimizeLogRepository, rescheduleLogRepository,
                userRepository);
        // Return the saved entity so optLog.getId() is available for rescheduleCards
        lenient().when(optimizeLogRepository.save(any(FsrsOptimizeLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void skippedDueToInsufficientTotalLogs_writesOptimizeLog() {
        when(reviewLogRepository.findByUserIdOrderByReviewedAtAsc("user1"))
                .thenReturn(Collections.emptyList());

        service.executeOptimize("user1", UUID.randomUUID().toString(), TriggerType.MANUAL);

        ArgumentCaptor<FsrsOptimizeLog> captor = ArgumentCaptor.forClass(FsrsOptimizeLog.class);
        verify(optimizeLogRepository).save(captor.capture());
        FsrsOptimizeLog log = captor.getValue();
        assertThat(log.getStatus()).isEqualTo(OptimizeStatus.SKIPPED);
        assertThat(log.getTriggerType()).isEqualTo(TriggerType.MANUAL);
        assertThat(log.getErrorMessage()).contains("insufficient_data");
        assertThat(log.getUserId()).isEqualTo("user1");
    }

    @Test
    void triggerTypeManual_isRecordedCorrectly() {
        when(reviewLogRepository.findByUserIdOrderByReviewedAtAsc("user1"))
                .thenReturn(Collections.emptyList());

        service.executeOptimize("user1", UUID.randomUUID().toString(), TriggerType.MANUAL);

        ArgumentCaptor<FsrsOptimizeLog> captor = ArgumentCaptor.forClass(FsrsOptimizeLog.class);
        verify(optimizeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggerType()).isEqualTo(TriggerType.MANUAL);
    }

    @Test
    void triggerTypeScheduled_isRecordedCorrectly() {
        when(reviewLogRepository.findByUserIdOrderByReviewedAtAsc("user1"))
                .thenReturn(Collections.emptyList());

        service.executeOptimize("user1", UUID.randomUUID().toString(), TriggerType.SCHEDULED);

        ArgumentCaptor<FsrsOptimizeLog> captor = ArgumentCaptor.forClass(FsrsOptimizeLog.class);
        verify(optimizeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggerType()).isEqualTo(TriggerType.SCHEDULED);
    }

    @Test
    void successfulOptimization_writesOptimizeAndRescheduleLogs() {
        List<ReviewLog> logs = generateReviewLogs(600);
        List<String> cardIds = logs.stream().map(ReviewLog::getCardId).distinct().toList();
        List<Card> cards = cardIds.stream().map(cid -> {
            Card c = new Card();
            c.setId(cid);
            c.setUserId("user1");
            c.setFront("front");
            c.setBack("back");
            c.setStability(2.5);
            c.setDifficulty(0.0);
            c.setCardState(0);
            c.setDue(Instant.now());
            return c;
        }).toList();

        when(reviewLogRepository.findByUserIdOrderByReviewedAtAsc("user1")).thenReturn(logs);
        when(paramsRepository.findByUserId("user1"))
                .thenReturn(Optional.of(FsrsParameters.defaults("user1")));
        when(preferencesService.get("user1")).thenReturn(new com.hugosol.chatagent.model.UserPreferences());
        when(reviewLogRepository.findDistinctCardIdsByUserId("user1")).thenReturn(cardIds);
        when(cardRepository.findAllById(cardIds)).thenReturn(cards);
        when(reviewLogRepository.findByUserIdAndCardIdOrderByReviewedAtAsc(anyString(), anyString()))
                .thenAnswer(inv -> {
                    String cardId = inv.getArgument(1);
                    return logs.stream().filter(l -> l.getCardId().equals(cardId)).toList();
                });

        service.executeOptimize("user1", UUID.randomUUID().toString(), TriggerType.MANUAL);

        ArgumentCaptor<FsrsOptimizeLog> optCaptor = ArgumentCaptor.forClass(FsrsOptimizeLog.class);
        verify(optimizeLogRepository).save(optCaptor.capture());
        FsrsOptimizeLog optLog = optCaptor.getValue();
        assertThat(optLog.getStatus()).isEqualTo(OptimizeStatus.SUCCESS);
        assertThat(optLog.isParamsUpdated()).isTrue();
        assertThat(optLog.getUserId()).isEqualTo("user1");
        assertThat(optLog.getId()).isNull(); // Not manually set; Hibernate generates on persist

        ArgumentCaptor<FsrsRescheduleLog> resCaptor = ArgumentCaptor.forClass(FsrsRescheduleLog.class);
        verify(rescheduleLogRepository).save(resCaptor.capture());
        FsrsRescheduleLog resLog = resCaptor.getValue();
        assertThat(resLog.getStatus()).isEqualTo(RescheduleStatus.SUCCESS);
        assertThat(resLog.getTotalCardsWithHistory()).isGreaterThan(0);
        assertThat(resLog.getRescheduledCards()).isGreaterThan(0);
    }

    @Test
    void emptyReschedule_stillWritesRescheduleLog() {
        List<ReviewLog> logs = generateReviewLogs(600);
        List<String> cardIds = logs.stream().map(ReviewLog::getCardId).distinct().toList();
        List<Card> cards = cardIds.stream().map(cid -> {
            Card c = new Card();
            c.setId(cid);
            c.setUserId("user1");
            c.setFront("front");
            c.setBack("back");
            c.setStability(2.5);
            c.setDifficulty(0.0);
            c.setCardState(0);
            c.setDue(Instant.now());
            return c;
        }).toList();

        when(reviewLogRepository.findByUserIdOrderByReviewedAtAsc("user1")).thenReturn(logs);
        when(paramsRepository.findByUserId("user1"))
                .thenReturn(Optional.of(FsrsParameters.defaults("user1")));
        when(preferencesService.get("user1")).thenReturn(new com.hugosol.chatagent.model.UserPreferences());
        when(reviewLogRepository.findDistinctCardIdsByUserId("user1")).thenReturn(cardIds);
        when(cardRepository.findAllById(cardIds)).thenReturn(cards);
        when(reviewLogRepository.findByUserIdAndCardIdOrderByReviewedAtAsc(anyString(), anyString()))
                .thenAnswer(inv -> {
                    String cardId = inv.getArgument(1);
                    return logs.stream().filter(l -> l.getCardId().equals(cardId)).toList();
                });

        service.executeOptimize("user1", UUID.randomUUID().toString(), TriggerType.MANUAL);

        verify(rescheduleLogRepository).save(any(FsrsRescheduleLog.class));
    }

    private List<ReviewLog> generateReviewLogs(int count) {
        return generateUniformRatingLogs(count / 10, 10, null);
    }

    private List<ReviewLog> generateUniformRatingLogs(int numCards, int reviewsPerCard, com.hugosol.chatagent.flashcard.Rating fixedRating) {
        List<ReviewLog> logs = new ArrayList<>();
        Random rng = new Random(42);
        Instant base = BASE_TIME;

        for (int card = 0; card < numCards; card++) {
            String cardId = "card_" + card;
            for (int r = 0; r < reviewsPerCard; r++) {
                com.hugosol.chatagent.flashcard.Rating rating = (fixedRating != null)
                        ? fixedRating
                        : com.hugosol.chatagent.flashcard.Rating.values()[rng.nextInt(4)];
                Instant t = base.plus(Duration.ofDays(card * 30L + r * 7L + rng.nextInt(3)));
                ReviewLog log = new ReviewLog();
                log.setId(UUID.randomUUID().toString());
                log.setCardId(cardId);
                log.setUserId("user1");
                log.setRating(rating);
                log.setReviewedAt(t);
                logs.add(log);
            }
        }
        return logs;
    }
}
