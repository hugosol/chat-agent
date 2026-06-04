package com.hugosol.chatagent.service.card;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardCsvParserTest {

    private final CardCsvParser parser = new CardCsvParser();

    @Test
    void parse_fullFields_allParsedCorrectly() throws Exception {
        String csv = """
                front,back,stability,difficulty,cardState,due,reps,lapses,lastReview,firstReviewDate
                hello,world,3.0,0.3,Review,2024-06-01T10:00:00Z,5,2,2024-05-01T10:00:00Z,2024-06-01
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.rowNumber()).isEqualTo(1);
        assertThat(row.front()).isEqualTo("hello");
        assertThat(row.back()).isEqualTo("world");
        assertThat(row.fsrs().stability()).isEqualTo(3.0);
        assertThat(row.fsrs().difficulty()).isEqualTo(0.3);
        assertThat(row.fsrs().cardState()).isEqualTo(2);
        assertThat(row.fsrs().due()).isEqualTo("2024-06-01T10:00:00Z");
        assertThat(row.fsrs().reps()).isEqualTo(5);
        assertThat(row.fsrs().lapses()).isEqualTo(2);
        assertThat(row.fsrs().lastReview()).isEqualTo("2024-05-01T10:00:00Z");
        assertThat(row.fsrs().firstReviewDate()).isEqualTo("2024-06-01");
    }

    @Test
    void parse_commaInsideField_preservesComma() throws Exception {
        String csv = """
                front,back
                "hello, world","goodbye, world"
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("hello, world");
        assertThat(rows.get(0).back()).isEqualTo("goodbye, world");
    }

    @Test
    void parse_newlineInsideField_preservesNewline() throws Exception {
        String csv = """
                front,back
                "hello
                world","line1
                line2"
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("hello\nworld");
        assertThat(rows.get(0).back()).isEqualTo("line1\nline2");
    }

    @Test
    void parse_doubleQuoteInsideField_unescaped() throws Exception {
        String csv = "front,back\r\n"
                + "\"she said \"\"hello\"\" to me\",\"hello \"\"world\"\"\"\r\n";

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("she said \"hello\" to me");
        assertThat(rows.get(0).back()).isEqualTo("hello \"world\"");
    }

    @Test
    void parse_cardStateAllValues_correctMapping() throws Exception {
        String csv = """
                front,cardState
                a,New
                b,Learning
                c,Review
                d,Relearning
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).fsrs().cardState()).isEqualTo(0);
        assertThat(rows.get(1).fsrs().cardState()).isEqualTo(1);
        assertThat(rows.get(2).fsrs().cardState()).isEqualTo(2);
        assertThat(rows.get(3).fsrs().cardState()).isEqualTo(3);
    }

    @Test
    void parse_cardStateIllegalValue_mapsToNull() throws Exception {
        String csv = """
                front,cardState
                a,Invalid
                b,unknown
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).fsrs().cardState()).isNull();
        assertThat(rows.get(1).fsrs().cardState()).isNull();
    }

    @Test
    void parse_missingFsrsColumns_fieldsAreNull() throws Exception {
        String csv = """
                front,back
                hello,world
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        var fsrs = rows.get(0).fsrs();
        assertThat(fsrs.stability()).isNull();
        assertThat(fsrs.difficulty()).isNull();
        assertThat(fsrs.cardState()).isNull();
        assertThat(fsrs.due()).isNull();
        assertThat(fsrs.reps()).isNull();
        assertThat(fsrs.lapses()).isNull();
        assertThat(fsrs.lastReview()).isNull();
        assertThat(fsrs.firstReviewDate()).isNull();
    }

    @Test
    void parse_bomHeader_skippedAndHeaderParsed() throws Exception {
        byte[] bytes = new byte[]{
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                'f', 'r', 'o', 'n', 't', ',', 'b', 'a', 'c', 'k', '\n',
                'h', 'e', 'l', 'l', 'o', ',', 'w', 'o', 'r', 'l', 'd', '\n'
        };

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(new ByteArrayInputStream(bytes));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("hello");
        assertThat(rows.get(0).back()).isEqualTo("world");
    }

    @Test
    void parse_extraColumns_ignored() throws Exception {
        String csv = """
                front,back,unknownCol,anotherCol
                hello,world,ignored1,ignored2
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("hello");
        assertThat(rows.get(0).back()).isEqualTo("world");
    }

    @Test
    void parse_headerName_caseInsensitive() throws Exception {
        String csv = """
                Front,BACK,STABILITY,cardState
                hello,world,3.0,Review
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("hello");
        assertThat(rows.get(0).back()).isEqualTo("world");
        assertThat(rows.get(0).fsrs().stability()).isEqualTo(3.0);
        assertThat(rows.get(0).fsrs().cardState()).isEqualTo(2);
    }

    @Test
    void parse_fsrsInvalidFormat_mapsToNull() throws Exception {
        String csv = """
                front,stability,difficulty,reps,lapses
                a,notanumber,0.3,abc,def
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        var fsrs = rows.get(0).fsrs();
        assertThat(fsrs.stability()).isNull();
        assertThat(fsrs.difficulty()).isEqualTo(0.3);
        assertThat(fsrs.reps()).isNull();
        assertThat(fsrs.lapses()).isNull();
    }

    @Test
    void parse_emptyFile_returnsEmptyList() throws Exception {
        String csv = """
                front,back
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).isEmpty();
    }

    @Test
    void parse_multipleRows_correctRowNumbers() throws Exception {
        String csv = """
                front,back
                a,a
                b,b
                c,c
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).rowNumber()).isEqualTo(1);
        assertThat(rows.get(1).rowNumber()).isEqualTo(2);
        assertThat(rows.get(2).rowNumber()).isEqualTo(3);
    }

    @Test
    void parse_fsrsEmptyStrings_mapsToNull() throws Exception {
        String csv = """
                front,stability,difficulty,cardState,due,reps,lapses,lastReview
                a,  ,,     ,    ,, , ,
                """;

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        var fsrs = rows.get(0).fsrs();
        assertThat(fsrs.stability()).isNull();
        assertThat(fsrs.difficulty()).isNull();
        assertThat(fsrs.cardState()).isNull();
        assertThat(fsrs.due()).isNull();
        assertThat(fsrs.reps()).isNull();
        assertThat(fsrs.lapses()).isNull();
        assertThat(fsrs.lastReview()).isNull();
    }

    @Test
    void parse_escapedNewlineInFront_restoresRealNewline() throws Exception {
        String csv = "front,back\n"
                + "line1\\nline2,hello\n";

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("line1\nline2");
    }

    @Test
    void parse_escapedBackslash_restoresSingleBackslash() throws Exception {
        String csv = "front,back\n"
                + "C:\\\\path\\\\to\\\\file,hello\n";

        List<CardCsvParser.ParsedCardRow> rows = parser.parse(toStream(csv));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).front()).isEqualTo("C:\\path\\to\\file");
    }

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
