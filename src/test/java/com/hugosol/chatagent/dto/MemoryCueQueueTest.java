package com.hugosol.chatagent.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryCueQueueTest {

    @Test
    void pushAllNew_toEmpty_sortedByScoreAsc() {
        var queue = new MemoryCueQueue(3);
        var a = cue("a", 0.6);
        var b = cue("b", 0.75);
        var c = cue("c", 0.9);

        queue.push(List.of(a, b, c));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("a", "b", "c");
    }

    @Test
    void pushUnsortedInput_sortsInternally() {
        var queue = new MemoryCueQueue(3);
        var x = cue("x", 0.5);
        var y = cue("y", 0.7);
        var z = cue("z", 0.95);

        queue.push(List.of(z, x, y));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("x", "y", "z");
    }

    @Test
    void pushEmpty_unchanged() {
        var queue = new MemoryCueQueue(3);
        var a = cue("a", 0.6);
        var b = cue("b", 0.75);
        var c = cue("c", 0.9);
        queue.push(List.of(a, b, c));

        queue.push(List.of());

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("a", "b", "c");
    }

    @Test
    void pushEmpty_toEmpty_staysEmpty() {
        var queue = new MemoryCueQueue(3);

        queue.push(List.of());

        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.getEntries()).isEmpty();
    }

    @Test
    void pushFewerThanTopK() {
        var queue = new MemoryCueQueue(3);
        queue.push(List.of(cue("a", 0.6), cue("b", 0.75), cue("c", 0.9)));

        queue.push(List.of(cue("d", 0.8)));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("b", "c", "d");
    }

    @Test
    void pushAllNew_toFull_evictsOldest() {
        var queue = new MemoryCueQueue(3);
        queue.push(List.of(cue("a", 0.6), cue("b", 0.75), cue("c", 0.9)));

        queue.push(List.of(cue("d", 0.7), cue("e", 0.85)));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("c", "d", "e");
    }

    @Test
    void pushMixed_refreshesExisting() {
        var queue = new MemoryCueQueue(3);
        queue.push(List.of(cue("a", 0.6), cue("b", 0.75), cue("c", 0.9)));

        queue.push(List.of(cue("d", 0.7), cue("b", 0.9)));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("c", "d", "b");
    }

    @Test
    void pushAllExisting_refreshOnly_noEviction() {
        var queue = new MemoryCueQueue(3);
        queue.push(List.of(cue("a", 0.6), cue("b", 0.75), cue("c", 0.9)));

        queue.push(List.of(cue("a", 0.6), cue("c", 0.9)));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("b", "a", "c");
    }

    @Test
    void pushFourAllNew_capacityThree_evictsFirst() {
        var queue = new MemoryCueQueue(3);

        queue.push(List.of(
                cue("w", 0.4), cue("x", 0.5), cue("y", 0.6), cue("z", 0.8)));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("x", "y", "z");
    }

    @Test
    void pushTwo_toFull_oneDup_oneNew() {
        var queue = new MemoryCueQueue(3);
        queue.push(List.of(cue("a", 0.6), cue("b", 0.75), cue("c", 0.9)));

        queue.push(List.of(cue("b", 0.7), cue("d", 0.85)));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("c", "b", "d");
    }

    @Test
    void pushScoreTie_stableSort_byCreatedAtDesc() {
        var queue = new MemoryCueQueue(3);
        var a = new CueMatch("a", "t-a", "s-a", 0.8,
                Instant.parse("2025-05-28T10:00:00Z"));
        var b = new CueMatch("b", "t-b", "s-b", 0.8,
                Instant.parse("2025-05-27T10:00:00Z"));
        var c = new CueMatch("c", "t-c", "s-c", 0.8,
                Instant.parse("2025-05-29T10:00:00Z"));

        queue.push(List.of(a, b, c));

        assertThat(queue.getEntries())
                .extracting(CueMatch::cueId)
                .containsExactly("b", "a", "c");
    }

    private static CueMatch cue(String cueId, double score) {
        return new CueMatch(cueId, "topic-" + cueId, "summary-" + cueId, score,
                Instant.parse("2025-05-28T10:00:00Z"));
    }
}
