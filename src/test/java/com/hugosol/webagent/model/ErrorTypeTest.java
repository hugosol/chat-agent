package com.hugosol.webagent.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErrorTypeTest {

    @Test
    void fromStringExactMatch() {
        assertThat(ErrorType.fromString("GRAMMAR")).isEqualTo(ErrorType.GRAMMAR);
        assertThat(ErrorType.fromString("WORD_CHOICE")).isEqualTo(ErrorType.WORD_CHOICE);
        assertThat(ErrorType.fromString("CHINGLISH")).isEqualTo(ErrorType.CHINGLISH);
        assertThat(ErrorType.fromString("PRONUNCIATION")).isEqualTo(ErrorType.PRONUNCIATION);
        assertThat(ErrorType.fromString("FLUENCY")).isEqualTo(ErrorType.FLUENCY);
    }

    @Test
    void fromStringCaseInsensitive() {
        assertThat(ErrorType.fromString("grammar")).isEqualTo(ErrorType.GRAMMAR);
        assertThat(ErrorType.fromString("Grammar")).isEqualTo(ErrorType.GRAMMAR);
        assertThat(ErrorType.fromString("word_choice")).isEqualTo(ErrorType.WORD_CHOICE);
        assertThat(ErrorType.fromString("Chinglish")).isEqualTo(ErrorType.CHINGLISH);
    }

    @Test
    void fromStringInvalidThrowsException() {
        assertThatThrownBy(() -> ErrorType.fromString("INVALID_TYPE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromStringNullThrowsException() {
        assertThatThrownBy(() -> ErrorType.fromString(null))
                .isInstanceOf(NullPointerException.class);
    }
}
