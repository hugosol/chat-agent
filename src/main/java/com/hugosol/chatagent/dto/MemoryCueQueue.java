package com.hugosol.chatagent.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MemoryCueQueue implements Serializable {

    private static final int DEFAULT_CAPACITY = 3;

    private final int capacity;
    private final ArrayList<CueMatch> entries;

    public MemoryCueQueue() {
        this(DEFAULT_CAPACITY);
    }

    public MemoryCueQueue(int capacity) {
        this.capacity = capacity;
        this.entries = new ArrayList<>();
    }

    public void push(List<CueMatch> newResults) {
        if (newResults.isEmpty()) {
            return;
        }
        var sorted = new ArrayList<>(newResults);
        sorted.sort(Comparator.comparingDouble(CueMatch::score)
                .thenComparing(CueMatch::createdAt));
        for (var item : sorted) {
            entries.removeIf(e -> e.cueId().equals(item.cueId()));
            entries.add(item);
        }
        while (entries.size() > capacity) {
            entries.remove(0);
        }
    }

    public List<CueMatch> getEntries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
