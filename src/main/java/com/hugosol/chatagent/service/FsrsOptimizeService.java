package com.hugosol.chatagent.service;

import com.hugosol.chatagent.dto.OptimizeProgress;
import com.hugosol.chatagent.dto.OptimizeResult;
import com.hugosol.chatagent.flashcard.CardState;
import com.hugosol.chatagent.flashcard.FsrsOptimizer;
import com.hugosol.chatagent.flashcard.FsrsScheduler;
import com.hugosol.chatagent.flashcard.FsrsSchedulerConfig;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.FsrsOptimizeLog;
import com.hugosol.chatagent.model.FsrsRescheduleLog;
import com.hugosol.chatagent.model.OptimizeStatus;
import com.hugosol.chatagent.model.RescheduleStatus;
import com.hugosol.chatagent.model.ReviewLog;
import com.hugosol.chatagent.model.TriggerType;
import com.hugosol.chatagent.model.UserPreferences;
import com.hugosol.chatagent.model.User;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.FsrsOptimizeLogRepository;
import com.hugosol.chatagent.repository.FsrsParametersRepository;
import com.hugosol.chatagent.repository.FsrsRescheduleLogRepository;
import com.hugosol.chatagent.repository.ReviewLogRepository;
import com.hugosol.chatagent.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Service
public class FsrsOptimizeService {

    private static final Logger log = LoggerFactory.getLogger(FsrsOptimizeService.class);
    private static final int MIN_REVIEWS = 512;

    private final ReviewLogRepository reviewLogRepository;
    private final FsrsParametersRepository paramsRepository;
    private final CardRepository cardRepository;
    private final UserPreferencesService preferencesService;
    private final FsrsConfigService fsrsConfigService;
    private final CacheManager cacheManager;
    private final ExecutorService optimizerExecutor;
    private final FsrsOptimizeLogRepository optimizeLogRepository;
    private final FsrsRescheduleLogRepository rescheduleLogRepository;
    private final UserRepository userRepository;

    private final ConcurrentHashMap<String, OptimizeProgress> progressMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userTaskMap = new ConcurrentHashMap<>();

    public FsrsOptimizeService(ReviewLogRepository reviewLogRepository,
                               FsrsParametersRepository paramsRepository,
                               CardRepository cardRepository,
                               UserPreferencesService preferencesService,
                               FsrsConfigService fsrsConfigService,
                               CacheManager cacheManager,
                               @Qualifier("optimizerExecutor") ExecutorService optimizerExecutor,
                               FsrsOptimizeLogRepository optimizeLogRepository,
                               FsrsRescheduleLogRepository rescheduleLogRepository,
                               UserRepository userRepository) {
        this.reviewLogRepository = reviewLogRepository;
        this.paramsRepository = paramsRepository;
        this.cardRepository = cardRepository;
        this.preferencesService = preferencesService;
        this.fsrsConfigService = fsrsConfigService;
        this.cacheManager = cacheManager;
        this.optimizerExecutor = optimizerExecutor;
        this.optimizeLogRepository = optimizeLogRepository;
        this.rescheduleLogRepository = rescheduleLogRepository;
        this.userRepository = userRepository;
    }

    public OptimizeProgress getProgress(String taskId) {
        return progressMap.get(taskId);
    }

    public String getRunningTaskId(String userId) {
        return userTaskMap.get(userId);
    }

    public String startOptimize(String userId) {
        return startOptimize(userId, TriggerType.MANUAL);
    }

    public String startOptimize(String userId, TriggerType triggerType) {
        String runningTaskId = userTaskMap.get(userId);
        if (runningTaskId != null && isRunning(runningTaskId)) {
            return runningTaskId;
        }

        String taskId = UUID.randomUUID().toString();
        progressMap.put(taskId, OptimizeProgress.pending());
        userTaskMap.put(userId, taskId);
        executeOptimize(userId, taskId, triggerType);
        return taskId;
    }

    private boolean isRunning(String taskId) {
        OptimizeProgress progress = progressMap.get(taskId);
        return progress != null && ("RUNNING".equals(progress.status()) || "PENDING".equals(progress.status()));
    }

    @Async("optimizerExecutor")
    public CompletableFuture<OptimizeResult> executeOptimize(String userId, String taskId, TriggerType triggerType) {
        long startTime = System.currentTimeMillis();
        try {
            List<ReviewLog> logs = reviewLogRepository.findByUserIdOrderByReviewedAtAsc(userId);
            int totalLogs = logs.size();

            if (totalLogs < MIN_REVIEWS) {
                String reason = "insufficient_data: " + totalLogs + " < " + MIN_REVIEWS;
                progressMap.put(taskId, OptimizeProgress.skipped(reason));
                log.info("FSRS optimize skipped for user {}: {}", userId, reason);

                FsrsOptimizeLog optLog = new FsrsOptimizeLog();
                optLog.setUserId(userId);
                optLog.setTriggerType(triggerType);
                optLog.setStatus(OptimizeStatus.SKIPPED);
                optLog.setTotalReviewLogs(totalLogs);
                optLog.setErrorMessage(reason);
                optLog.setStartTime(Instant.ofEpochMilli(startTime));
                optLog.setEndTime(Instant.now());
                optLog.setDurationMs(System.currentTimeMillis() - startTime);
                optimizeLogRepository.save(optLog);

                userTaskMap.remove(userId);
                return CompletableFuture.completedFuture(null);
            }

            FsrsParameters params = paramsRepository.findByUserId(userId).orElse(null);
            UserPreferences prefs = preferencesService.get(userId);
            FsrsSchedulerConfig config = FsrsSchedulerConfig.merge(params, prefs);

            FsrsOptimizer optimizer = new FsrsOptimizer(logs, config);

            progressMap.put(taskId, OptimizeProgress.running(0, 0, 1, 0.0));

            OptimizeResult result = optimizer.optimize((epoch, batch, totalBatches, currentLoss) -> {
                progressMap.put(taskId, OptimizeProgress.running(epoch, batch, totalBatches, currentLoss));
            });

            if (result.iterations() == 0) {
                String reason = "insufficient_data: " + optimizer.totalNonSameDayReviews() + " non-same-day reviews < " + MIN_REVIEWS;
                progressMap.put(taskId, OptimizeProgress.skipped(reason));

                FsrsOptimizeLog optLog = new FsrsOptimizeLog();
                optLog.setUserId(userId);
                optLog.setTriggerType(triggerType);
                optLog.setStatus(OptimizeStatus.SKIPPED);
                optLog.setTotalReviewLogs(totalLogs);
                optLog.setNonSameDayReviews(optimizer.totalNonSameDayReviews());
                optLog.setErrorMessage(reason);
                optLog.setStartTime(Instant.ofEpochMilli(startTime));
                optLog.setEndTime(Instant.now());
                optLog.setDurationMs(System.currentTimeMillis() - startTime);
                optimizeLogRepository.save(optLog);

                userTaskMap.remove(userId);
                return CompletableFuture.completedFuture(null);
            }

            FsrsSchedulerConfig defaultConfig = FsrsSchedulerConfig.defaults();
            FsrsOptimizer defaultOptimizer = new FsrsOptimizer(logs, defaultConfig);
            double defaultLoss = defaultOptimizer.computeLoss(defaultConfig.weights());

            String weightsBeforeJson = Arrays.toString(config.weights());

            if (result.finalLoss() >= defaultLoss) {
                log.warn("FSRS optimize for user {}: optimized loss {} >= default loss {}, keeping old params",
                        userId, result.finalLoss(), defaultLoss);
                progressMap.put(taskId, OptimizeProgress.completed(result));

                FsrsOptimizeLog optLog = new FsrsOptimizeLog();
                optLog.setUserId(userId);
                optLog.setTriggerType(triggerType);
                optLog.setStatus(OptimizeStatus.SUCCESS);
                optLog.setTotalReviewLogs(totalLogs);
                optLog.setNonSameDayReviews(optimizer.totalNonSameDayReviews());
                optLog.setEpochs(5);
                optLog.setIterations(result.iterations());
                optLog.setFinalLoss(result.finalLoss());
                optLog.setDefaultLoss(defaultLoss);
                optLog.setLossImprovement(defaultLoss - result.finalLoss());
                optLog.setParamsUpdated(false);
                optLog.setWeightsBefore(weightsBeforeJson);
                optLog.setStartTime(Instant.ofEpochMilli(startTime));
                optLog.setEndTime(Instant.now());
                optLog.setDurationMs(System.currentTimeMillis() - startTime);
                optimizeLogRepository.save(optLog);

                userTaskMap.remove(userId);
                return CompletableFuture.completedFuture(result);
            }

            saveParameters(userId, params, result.weights());

            cacheManager.getCache("fsrsConfig").evict(userId);

            String optimizeLogId = UUID.randomUUID().toString();
            rescheduleCards(userId, result.weights(), config, optimizeLogId, triggerType);

            progressMap.put(taskId, OptimizeProgress.completed(result));

            String weightsAfterJson = Arrays.toString(result.weights());

            FsrsOptimizeLog optLog = new FsrsOptimizeLog();
            optLog.setId(optimizeLogId);
            optLog.setUserId(userId);
            optLog.setTriggerType(triggerType);
            optLog.setStatus(OptimizeStatus.SUCCESS);
            optLog.setTotalReviewLogs(totalLogs);
            optLog.setNonSameDayReviews(optimizer.totalNonSameDayReviews());
            optLog.setEpochs(5);
            optLog.setIterations(result.iterations());
            optLog.setFinalLoss(result.finalLoss());
            optLog.setDefaultLoss(defaultLoss);
            optLog.setLossImprovement(defaultLoss - result.finalLoss());
            optLog.setParamsUpdated(true);
            optLog.setWeightsBefore(weightsBeforeJson);
            optLog.setWeightsAfter(weightsAfterJson);
            optLog.setStartTime(Instant.ofEpochMilli(startTime));
            optLog.setEndTime(Instant.now());
            optLog.setDurationMs(System.currentTimeMillis() - startTime);
            optimizeLogRepository.save(optLog);

            userTaskMap.remove(userId);

            log.info("FSRS optimize completed for user {}: loss={} (default={}), iterations={}, durationMs={}",
                    userId, result.finalLoss(), defaultLoss, result.iterations(), result.durationMs());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("FSRS optimize failed for user {}", userId, e);
            progressMap.put(taskId, OptimizeProgress.failed(e.getMessage()));

            FsrsOptimizeLog optLog = new FsrsOptimizeLog();
            optLog.setUserId(userId);
            optLog.setTriggerType(triggerType);
            optLog.setStatus(OptimizeStatus.FAILED);
            optLog.setErrorMessage(e.getMessage());
            optLog.setStartTime(Instant.ofEpochMilli(startTime));
            optLog.setEndTime(Instant.now());
            optLog.setDurationMs(System.currentTimeMillis() - startTime);
            optimizeLogRepository.save(optLog);

            userTaskMap.remove(userId);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Scheduled(cron = "0 0 3 * * SUN")
    public void scheduledOptimize() {
        log.info("Starting scheduled FSRS optimize for all users");
        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                String userId = user.getUsername();
                int reviewCount = reviewLogRepository.countByUserId(userId);
                if (reviewCount < MIN_REVIEWS) {
                    log.debug("Skipping scheduled optimize for user {} ({} reviews < {})",
                            userId, reviewCount, MIN_REVIEWS);
                    continue;
                }
                startOptimize(userId, TriggerType.SCHEDULED);
                log.info("Scheduled FSRS optimize started for user {}", userId);
            } catch (Exception e) {
                log.error("Scheduled FSRS optimize failed for user {}", user.getUsername(), e);
            }
        }
        log.info("Finished scheduled FSRS optimize for all users");
    }

    @Transactional
    void saveParameters(String userId, FsrsParameters existingParams, double[] weights) {
        FsrsParameters params = (existingParams != null)
                ? existingParams
                : FsrsParameters.defaults(userId);
        params.setUserId(userId);
        params.setWeights(weights);
        paramsRepository.save(params);
    }

    @Transactional
    void rescheduleCards(String userId, double[] weights, FsrsSchedulerConfig baseConfig,
                         String optimizeLogId, TriggerType triggerType) {
        long startTime = System.currentTimeMillis();
        List<String> cardIds = reviewLogRepository.findDistinctCardIdsByUserId(userId);
        int totalCardsWithHistory = cardIds.size();

        try {
            if (cardIds.isEmpty()) {
                FsrsRescheduleLog resLog = new FsrsRescheduleLog();
                resLog.setUserId(userId);
                resLog.setOptimizeLogId(optimizeLogId);
                resLog.setTriggerType(triggerType);
                resLog.setStatus(RescheduleStatus.SUCCESS);
                resLog.setTotalCardsWithHistory(0);
                resLog.setRescheduledCards(0);
                resLog.setStartTime(Instant.ofEpochMilli(startTime));
                resLog.setEndTime(Instant.now());
                resLog.setDurationMs(System.currentTimeMillis() - startTime);
                rescheduleLogRepository.save(resLog);
                return;
            }

            FsrsSchedulerConfig config = new FsrsSchedulerConfig(
                    weights,
                    baseConfig.desiredRetention(),
                    baseConfig.learningSteps(),
                    baseConfig.relearningSteps(),
                    baseConfig.maximumInterval(),
                    baseConfig.enableFuzz(),
                    baseConfig.enableShortTerm()
            );

            FsrsScheduler scheduler = new FsrsScheduler(config);
            List<Card> cards = cardRepository.findAllById(cardIds);
            List<Card> updatedCards = new ArrayList<>();
            Instant now = Instant.now();

            for (Card card : cards) {
                List<ReviewLog> logs = reviewLogRepository.findByUserIdAndCardIdOrderByReviewedAtAsc(userId, card.getId());
                if (logs.isEmpty()) {
                    continue;
                }
                CardState result = scheduler.reschedule(logs, now);

                card.setStability(result.stability());
                card.setDifficulty(result.difficulty());
                card.setCardState(result.state());
                card.setDue(result.due());
                card.setReps(result.reps());
                card.setLapses(result.lapses());
                card.setStep(result.step());
                card.setLastReview(result.lastReview());

                updatedCards.add(card);
            }

            if (!updatedCards.isEmpty()) {
                cardRepository.saveAll(updatedCards);
            }

            FsrsRescheduleLog resLog = new FsrsRescheduleLog();
            resLog.setUserId(userId);
            resLog.setOptimizeLogId(optimizeLogId);
            resLog.setTriggerType(triggerType);
            resLog.setStatus(RescheduleStatus.SUCCESS);
            resLog.setTotalCardsWithHistory(totalCardsWithHistory);
            resLog.setRescheduledCards(updatedCards.size());
            resLog.setStartTime(Instant.ofEpochMilli(startTime));
            resLog.setEndTime(Instant.now());
            resLog.setDurationMs(System.currentTimeMillis() - startTime);
            rescheduleLogRepository.save(resLog);

            log.info("FSRS optimize rescheduled {} cards for user {}", updatedCards.size(), userId);

        } catch (Exception e) {
            log.error("FSRS reschedule failed for user {}", userId, e);

            FsrsRescheduleLog resLog = new FsrsRescheduleLog();
            resLog.setUserId(userId);
            resLog.setOptimizeLogId(optimizeLogId);
            resLog.setTriggerType(triggerType);
            resLog.setStatus(RescheduleStatus.FAILED);
            resLog.setTotalCardsWithHistory(totalCardsWithHistory);
            resLog.setErrorMessage(e.getMessage());
            resLog.setStartTime(Instant.ofEpochMilli(startTime));
            resLog.setEndTime(Instant.now());
            resLog.setDurationMs(System.currentTimeMillis() - startTime);
            rescheduleLogRepository.save(resLog);
        }
    }
}
