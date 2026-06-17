package com.hugosol.chatagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hugosol.chatagent.model.*;
import com.hugosol.chatagent.repository.CardEnhancementRepository;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.SubtitleLineRepository;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class CardEnhanceService {

    private static final Logger log = LoggerFactory.getLogger(CardEnhanceService.class);

    private final CardRepository cardRepository;
    private final CardEnhancementRepository cardEnhancementRepository;
    private final SubtitleLineRepository subtitleLineRepository;
    private final WatchedMovieRepository watchedMovieRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final WiktionaryClient wiktionaryClient;
    private final ObjectMapper objectMapper;

    public CardEnhanceService(CardRepository cardRepository,
                              CardEnhancementRepository cardEnhancementRepository,
                              SubtitleLineRepository subtitleLineRepository,
                              WatchedMovieRepository watchedMovieRepository,
                              ChatLanguageModel chatLanguageModel,
                              WiktionaryClient wiktionaryClient) {
        this.cardRepository = cardRepository;
        this.cardEnhancementRepository = cardEnhancementRepository;
        this.subtitleLineRepository = subtitleLineRepository;
        this.watchedMovieRepository = watchedMovieRepository;
        this.chatLanguageModel = chatLanguageModel;
        this.wiktionaryClient = wiktionaryClient;
        this.objectMapper = new ObjectMapper();
    }

    public record EnhanceResult(MovieQuote movieQuote, String sceneSummary, String etymology) {}
    public record MovieQuote(String movieTitle, String imdbId, String quote, String timestamp) {}
    private record SubtitleSearchResult(MovieQuote movieQuote, String sceneSummary) {}

    @Transactional
    public EnhanceResult enhance(String cardId, String userId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found: " + cardId));

        List<CardEnhancement> existing = cardEnhancementRepository.findByCardId(cardId);
        boolean hasSubtitle = existing.stream().anyMatch(e ->
                e.getType() == EnhancementType.SUBTITLE && e.getStatus() == EnhancementStatus.SUCCESS);
        boolean hasEtymology = existing.stream().anyMatch(e ->
                e.getType() == EnhancementType.ETYMOLOGY && e.getStatus() == EnhancementStatus.SUCCESS);

        if (hasSubtitle && hasEtymology) {
            return buildResult(existing);
        }

        String word = card.getFront().trim().toLowerCase();

        MovieQuote freshMovieQuote = null;
        String freshSceneSummary = null;

        if (!hasSubtitle) {
            try {
                var result = searchSubtitle(word, userId, cardId);
                if (result != null) {
                    freshMovieQuote = result.movieQuote();
                    freshSceneSummary = result.sceneSummary();
                }
            } catch (Exception e) {
                log.warn("Subtitle enhancement failed for card {}: {}", cardId, e.getMessage());
                saveEnhancement(cardId, EnhancementType.SUBTITLE, EnhancementStatus.FAILED,
                        null, e.getMessage(), null);
            }
        }

        String etymology = null;
        if (!hasEtymology) {
            try {
                etymology = fetchEtymology(word, cardId);
            } catch (Exception e) {
                log.warn("Etymology fetch failed for card {}: {}", cardId, e.getMessage());
                saveEnhancement(cardId, EnhancementType.ETYMOLOGY, EnhancementStatus.FAILED,
                        null, e.getMessage(), null);
            }
        }
        // For cached etymology, extract from existing
        if (hasEtymology && etymology == null) {
            etymology = existing.stream()
                    .filter(e -> e.getType() == EnhancementType.ETYMOLOGY && e.getStatus() == EnhancementStatus.SUCCESS)
                    .map(CardEnhancement::getData)
                    .findFirst().orElse(null);
        }

        // For cached subtitle (when etymology-only retry), extract from existing
        if (hasSubtitle && freshMovieQuote == null) {
            try {
                var sub = existing.stream()
                        .filter(e -> e.getType() == EnhancementType.SUBTITLE && e.getStatus() == EnhancementStatus.SUCCESS)
                        .findFirst();
                if (sub.isPresent()) {
                    var node = objectMapper.readTree(sub.get().getData());
                    if (node.has("quote")) {
                        freshMovieQuote = new MovieQuote(
                                node.get("movieTitle").asText(),
                                node.get("imdbId").asText(),
                                node.get("quote").asText(),
                                node.get("timestamp").asText());
                    }
                    if (node.has("sceneSummary")) {
                        freshSceneSummary = node.get("sceneSummary").asText();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract cached subtitle for card {}: {}", cardId, e.getMessage());
            }
        }

        return new EnhanceResult(freshMovieQuote, freshSceneSummary, etymology);
    }

    private SubtitleSearchResult searchSubtitle(String word, String userId, String cardId) {
        log.info("searchSubtitle: word='{}' userId='{}' cardId={}", word, userId, cardId);
        List<WatchedMovie> movies = watchedMovieRepository.findByUserId(userId);
        if (movies.isEmpty()) {
            log.info("searchSubtitle: no watched movies for userId={}", userId);
            saveEnhancement(cardId, EnhancementType.SUBTITLE, EnhancementStatus.SUCCESS,
                    "{\"found\":false}", null, null);
            return null;
        }

        List<String> imdbIds = movies.stream().map(WatchedMovie::getImdbId).toList();
        // Use substring match on wordsLower (space-delimited) — handles word at
        // start/end of line and adjacent punctuation like "valhalla."
        String pattern = "%" + word + "%";
        List<SubtitleLine> matches = subtitleLineRepository.findByImdbIdInAndWordsLowerLike(imdbIds, pattern);

        if (matches.isEmpty()) {
            log.info("searchSubtitle: no subtitle match for word='{}' imdbIds={}", word, imdbIds);
            return null;
        }

        SubtitleLine match = matches.get(0);
        String movieTitle = match.getMovieTitle();
        String imdbId = match.getImdbId();

        int start = Math.max(1, match.getLineIndex() - 2);
        int end = match.getLineIndex() + 2;
        List<SubtitleLine> context = subtitleLineRepository
                .findByImdbIdAndLineIndexBetween(imdbId, start, end);

        String sceneSummary = generateSceneSummary(movieTitle, context, word);

        MovieQuote quote = new MovieQuote(movieTitle, imdbId, match.getText(), match.getStartTime());

        try {
            Map<String, Object> dataMap = new java.util.HashMap<>();
            dataMap.put("movieTitle", movieTitle);
            dataMap.put("imdbId", imdbId);
            dataMap.put("quote", match.getText());
            dataMap.put("timestamp", match.getStartTime());
            if (sceneSummary != null) {
                dataMap.put("sceneSummary", sceneSummary);
            }
            String data = objectMapper.writeValueAsString(dataMap);
            saveEnhancement(cardId, EnhancementType.SUBTITLE, EnhancementStatus.SUCCESS,
                    data, null, null);
            log.info("Subtitle enhancement saved for card {}: {} [{}]", cardId, movieTitle, match.getStartTime());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize subtitle enhancement data", e);
        }

        return new SubtitleSearchResult(quote, sceneSummary);
    }

    private String generateSceneSummary(String movieTitle, List<SubtitleLine> context, String targetWord) {
        StringBuilder contextText = new StringBuilder();
        for (SubtitleLine line : context) {
            String marker = line.getWordsLower().contains(targetWord) ? "  ← 目标词" : "";
            contextText.append("- ").append(line.getText()).append(marker).append("\n");
        }

        String prompt = """
                你是电影台词分析助手。以下是电影《%s》中一段包含单词"%s"的台词及前后文。请用一两句中文描述这段台词发生的情境（谁在什么情况下对谁说了什么）。

                台词上下文：
%s

                请直接给出场景摘要，不要解释，不要引述原文：""".formatted(movieTitle, targetWord, contextText.toString());

        try {
            String result = chatLanguageModel.chat(prompt).trim();
            log.info("generateSceneSummary for '{}': LLM returned {} chars: {}",
                    targetWord, result.length(),
                    result.length() > 100 ? result.substring(0, 100) + "..." : result);
            if (result.isEmpty()) {
                log.warn("generateSceneSummary for '{}': LLM returned empty string", targetWord);
                return "(场景摘要生成失败：模型返回空)";
            }
            return result;
        } catch (Exception e) {
            log.warn("Scene summary generation failed: {}", e.getMessage());
            return "(场景摘要生成失败)";
        }
    }

    private String fetchEtymology(String word, String cardId) {
        String etymology = wiktionaryClient.fetchEtymology(word);
        if (etymology != null && !etymology.isBlank()) {
            saveEnhancement(cardId, EnhancementType.ETYMOLOGY, EnhancementStatus.SUCCESS,
                    etymology, null, null);
            return etymology;
        } else {
            saveEnhancement(cardId, EnhancementType.ETYMOLOGY, EnhancementStatus.FAILED,
                    null, "No etymology found", null);
            return null;
        }
    }

    private void saveEnhancement(String cardId, EnhancementType type, EnhancementStatus status,
                                  String data, String error, String requestUrl) {
        CardEnhancement enhancement = new CardEnhancement(cardId, type, status, data, error, requestUrl);
        cardEnhancementRepository.save(enhancement);
    }

    private EnhanceResult buildResult(List<CardEnhancement> enhancements) {
        MovieQuote movieQuote = null;
        String sceneSummary = null;
        String etymology = null;

        for (CardEnhancement e : enhancements) {
            if (e.getType() == EnhancementType.SUBTITLE && e.getStatus() == EnhancementStatus.SUCCESS) {
                try {
                    var node = objectMapper.readTree(e.getData());
                    if (node.has("quote")) {
                        movieQuote = new MovieQuote(
                                node.get("movieTitle").asText(),
                                node.get("imdbId").asText(),
                                node.get("quote").asText(),
                                node.get("timestamp").asText()
                        );
                    }
                    if (node.has("sceneSummary")) {
                        sceneSummary = node.get("sceneSummary").asText();
                    }
                } catch (JsonProcessingException ex) {
                    log.warn("Failed to parse subtitle enhancement data", ex);
                }
            } else if (e.getType() == EnhancementType.ETYMOLOGY && e.getStatus() == EnhancementStatus.SUCCESS) {
                etymology = e.getData();
            }
        }

        return new EnhanceResult(movieQuote, sceneSummary, etymology);
    }
}
