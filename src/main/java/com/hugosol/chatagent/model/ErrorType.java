package com.hugosol.chatagent.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ErrorType {
    GRAMMAR,
    WORD_CHOICE,
    CHINGLISH,
    PRONUNCIATION,
    FLUENCY;

    @JsonCreator
    public static ErrorType fromString(String value) {
        return valueOf(value.toUpperCase());
    }
}
