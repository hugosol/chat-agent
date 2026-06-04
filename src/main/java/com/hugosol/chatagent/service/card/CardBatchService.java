package com.hugosol.chatagent.service.card;

import com.hugosol.chatagent.dto.ImportError;
import com.hugosol.chatagent.dto.ImportResult;
import com.hugosol.chatagent.flashcard.CardState;
import com.hugosol.chatagent.flashcard.FsrsScheduler;
import com.hugosol.chatagent.model.BatchOperationLog;
import com.hugosol.chatagent.model.BatchOperationStatus;
import com.hugosol.chatagent.model.BatchOperationType;
import com.hugosol.chatagent.model.Card;
import com.hugosol.chatagent.model.Tag;
import com.hugosol.chatagent.repository.BatchOperationLogRepository;
import com.hugosol.chatagent.repository.CardRepository;
import com.hugosol.chatagent.repository.TagRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CardBatchService {

    private static final Pattern STARTLINE_PATTERN = Pattern.compile("\\(startline (\\d+)\\)");

    private final CardRepository cardRepository;
    private final TagRepository tagRepository;
    private final BatchOperationLogRepository batchOperationLogRepository;
    private final CardCsvParser cardCsvParser;

    public CardBatchService(CardRepository cardRepository, TagRepository tagRepository,
                            BatchOperationLogRepository batchOperationLogRepository,
                            CardCsvParser cardCsvParser) {
        this.cardRepository = cardRepository;
        this.tagRepository = tagRepository;
        this.batchOperationLogRepository = batchOperationLogRepository;
        this.cardCsvParser = cardCsvParser;
    }

    public ImportResult importCards(byte[] fileBytes, String originalFilename, String tagId, String userId) {
        Tag tag = validateTag(tagId, userId);

        List<CardCsvParser.ParsedCardRow> rows;
        try {
            rows = cardCsvParser.parse(new java.io.ByteArrayInputStream(fileBytes));
        } catch (Exception e) {
            int row = extractStartLine(e.getMessage());
            var error = new ImportError(row, "", "CSV解析失败: " + e.getMessage());
            saveLog(userId, null, tag, originalFilename, 0, 0, 0,
                    List.of(error), BatchOperationStatus.FAILED);
            return new ImportResult(0, 0, List.of(error));
        }

        List<ImportError> errors = validateAll(rows, userId);

        if (!errors.isEmpty()) {
            saveLog(userId, null, tag, originalFilename, rows.size(), 0, errors.size(),
                    errors, BatchOperationStatus.FAILED);
            return new ImportResult(rows.size(), 0, errors);
        }

        List<Card> cards = doImport(rows, tag, userId);

        saveLog(userId, cards.size(), tag, originalFilename, rows.size(), cards.size(), 0,
                List.of(), BatchOperationStatus.SUCCESS);

        return new ImportResult(rows.size(), cards.size(), List.of());
    }

    public ExportData exportCards(String tagId, String userId) {
        Tag tag = validateTag(tagId, userId);

        List<Card> cards = cardRepository.findAllByTagsContaining(tag);
        cards = cards.stream()
                .filter(c -> c.getUserId().equals(userId))
                .toList();

        byte[] csvBytes = generateCsv(cards);

        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = tag.getName() + "_" + timestamp + ".csv";

        BatchOperationLog log = new BatchOperationLog();
        log.setUserId(userId);
        log.setOperationType(BatchOperationType.EXPORT);
        log.setTagId(tag.getId());
        log.setTagName(tag.getName());
        log.setFileName(fileName);
        log.setTotalRows(cards.size());
        log.setStatus(BatchOperationStatus.SUCCESS);
        batchOperationLogRepository.save(log);

        return new ExportData(csvBytes, fileName);
    }

    private byte[] generateCsv(List<Card> cards) {
        var csvFormat = org.apache.commons.csv.CSVFormat.DEFAULT.builder()
                .setHeader("front", "back", "stability", "difficulty", "cardState",
                        "due", "reps", "lapses", "lastReview", "firstReviewDate")
                .build();

        var sw = new java.io.StringWriter();
        try (var printer = new org.apache.commons.csv.CSVPrinter(sw, csvFormat)) {
            for (Card card : cards) {
                printer.print(escapeNewlines(card.getFront()));
                printer.print(escapeNewlines(card.getBack()));
                printer.print(formatDouble(card.getStability()));
                printer.print(formatDouble(card.getDifficulty()));
                printer.print(mapCardStateToText(card.getCardState()));
                printer.print(card.getDue() != null ? card.getDue().toString() : "");
                printer.print(card.getReps());
                printer.print(card.getLapses());
                printer.print(card.getLastReview() != null ? card.getLastReview().toString() : "");
                printer.print(card.getFirstReviewDate() != null ? formatInstantDate(card.getFirstReviewDate()) : "");
                printer.println();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("CSV生成失败", e);
        }

        var bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        var csvBytes = sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var result = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(csvBytes, 0, result, bom.length, csvBytes.length);
        return result;
    }

    private String escapeNewlines(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private String formatDouble(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String formatInstantDate(Instant instant) {
        return java.time.LocalDate.ofInstant(instant, java.time.ZoneOffset.UTC).toString();
    }

    private String mapCardStateToText(int state) {
        return switch (state) {
            case 0 -> "New";
            case 1 -> "Learning";
            case 2 -> "Review";
            case 3 -> "Relearning";
            default -> String.valueOf(state);
        };
    }

    public record ExportData(byte[] csvBytes, String fileName) {
    }

    private Tag validateTag(String tagId, String userId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不存在"));
        if (!tag.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "标签不存在");
        }
        if (!"deck".equals(tag.getType())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "只能导入到牌组标签");
        }
        return tag;
    }

    List<ImportError> validateAll(List<CardCsvParser.ParsedCardRow> rows, String userId) {
        List<ImportError> errors = new ArrayList<>();

        Set<String> seenFronts = new HashSet<>();

        for (CardCsvParser.ParsedCardRow row : rows) {
            if (row.front() == null || row.front().isBlank()) {
                errors.add(new ImportError(row.rowNumber(), "", "卡片正面不能为空"));
                continue;
            }
            if (row.back() == null || row.back().isBlank()) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "卡片背面不能为空"));
                continue;
            }

            var fsrs = row.fsrs();
            if (fsrs.stability() != null && fsrs.stability() <= 0) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "stability必须大于0"));
                continue;
            }
            if (fsrs.difficulty() != null && (fsrs.difficulty() < 0 || fsrs.difficulty() > 1)) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "difficulty必须在0到1之间"));
                continue;
            }
            if (fsrs.cardState() != null && fsrs.cardState() == null) {
                continue;
            }
            if (fsrs.cardState() != null && (fsrs.cardState() < 0 || fsrs.cardState() > 3)) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "cardState无效"));
                continue;
            }
            if (fsrs.reps() != null && fsrs.reps() < 0) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "reps不能为负数"));
                continue;
            }
            if (fsrs.lapses() != null && fsrs.lapses() < 0) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "lapses不能为负数"));
                continue;
            }
            if (fsrs.due() != null) {
                try {
                    Instant.parse(fsrs.due());
                } catch (DateTimeParseException e) {
                    errors.add(new ImportError(row.rowNumber(), row.front(), "due格式无效"));
                    continue;
                }
            }
            if (fsrs.lastReview() != null) {
                try {
                    Instant.parse(fsrs.lastReview());
                } catch (DateTimeParseException e) {
                    errors.add(new ImportError(row.rowNumber(), row.front(), "lastReview格式无效"));
                    continue;
                }
            }
            if (fsrs.firstReviewDate() != null && !fsrs.firstReviewDate().isBlank()) {
                try {
                    parseInstantDate(fsrs.firstReviewDate());
                } catch (DateTimeParseException e) {
                    errors.add(new ImportError(row.rowNumber(), row.front(), "firstReviewDate格式无效"));
                    continue;
                }
            }

            String lower = row.front().toLowerCase();
            if (!seenFronts.add(lower)) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "CSV文件内front重复"));
                continue;
            }
        }

        if (!errors.isEmpty()) {
            return errors;
        }

        List<String> fronts = rows.stream().map(r -> r.front().toLowerCase()).distinct().toList();
        Set<String> existingFronts = new HashSet<>(cardRepository.findExistingFronts(fronts, userId));

        for (CardCsvParser.ParsedCardRow row : rows) {
            if (existingFronts.contains(row.front().toLowerCase())) {
                errors.add(new ImportError(row.rowNumber(), row.front(), "卡片已存在"));
            }
        }

        return errors;
    }

    @Transactional
    List<Card> doImport(List<CardCsvParser.ParsedCardRow> rows, Tag tag, String userId) {
        CardState defaults = FsrsScheduler.createInitState(Instant.now());
        List<Card> cards = new ArrayList<>();

        for (CardCsvParser.ParsedCardRow row : rows) {
            Card card = new Card(userId, row.front(), row.back());
            var fsrs = row.fsrs();

            card.setStability(fsrs.stability() != null ? fsrs.stability() : defaults.stability());
            card.setDifficulty(fsrs.difficulty() != null ? fsrs.difficulty() : defaults.difficulty());
            card.setCardState(fsrs.cardState() != null ? fsrs.cardState() : defaults.state());
            card.setReps(fsrs.reps() != null ? fsrs.reps() : defaults.reps());
            card.setLapses(fsrs.lapses() != null ? fsrs.lapses() : defaults.lapses());
            card.setDue(fsrs.due() != null ? Instant.parse(fsrs.due()) : defaults.due());
            card.setLastReview(fsrs.lastReview() != null ? Instant.parse(fsrs.lastReview()) : defaults.lastReview());

            Instant firstReviewDate = null;
            if (fsrs.firstReviewDate() != null && !fsrs.firstReviewDate().isBlank()) {
                firstReviewDate = parseInstantDate(fsrs.firstReviewDate());
            }
            if (firstReviewDate == null && fsrs.cardState() != null && fsrs.cardState() != 0) {
                firstReviewDate = Instant.now();
            }
            card.setFirstReviewDate(firstReviewDate);

            card.setTags(Set.of(tag));
            cards.add(card);
        }

        return cardRepository.saveAll(cards);
    }

    private void saveLog(String userId, Integer successCount, Tag tag, String fileName,
                         int totalRows, int success, int skip,
                         List<ImportError> errors, BatchOperationStatus status) {
        BatchOperationLog log = new BatchOperationLog();
        log.setUserId(userId);
        log.setOperationType(BatchOperationType.IMPORT);
        log.setTagId(tag.getId());
        log.setTagName(tag.getName());
        log.setFileName(fileName);
        log.setTotalRows(totalRows);
        log.setSuccessCount(successCount);
        log.setSkipCount(skip);
        log.setStatus(status);

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) sb.append(",");
                var e = errors.get(i);
                sb.append("{\"row\":").append(e.row())
                        .append(",\"front\":\"").append(escapeJson(e.front()))
                        .append("\",\"reason\":\"").append(escapeJson(e.reason()))
                        .append("\"}");
            }
            sb.append("]");
            log.setErrorDetails(sb.toString());
        }

        batchOperationLogRepository.save(log);
    }

    private Instant parseInstantDate(String value) {
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(value);
            return date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return Instant.parse(value);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int extractStartLine(String message) {
        if (message == null) return 0;
        Matcher m = STARTLINE_PATTERN.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
