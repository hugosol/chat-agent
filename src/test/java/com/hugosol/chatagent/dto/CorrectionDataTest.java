package com.hugosol.chatagent.dto;

import com.hugosol.chatagent.model.ErrorType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorrectionDataTest {

    @Test
    void constructorSetsFields() {
        CorrectionData cd = new CorrectionData(ErrorType.GRAMMAR, "he go", "he goes", "缺少第三人称单数");

        assertThat(cd.getType()).isEqualTo(ErrorType.GRAMMAR);
        assertThat(cd.getOriginal()).isEqualTo("he go");
        assertThat(cd.getCorrected()).isEqualTo("he goes");
        assertThat(cd.getExplanation()).isEqualTo("缺少第三人称单数");
    }

    @Test
    void messageIdDefaultsToZero() {
        CorrectionData cd = new CorrectionData(ErrorType.CHINGLISH, "a", "b", "c");
        assertThat(cd.getMessageId()).isZero();
    }

    @Test
    void defaultConstructorAllowsSetters() {
        CorrectionData cd = new CorrectionData();

        cd.setType(ErrorType.WORD_CHOICE);
        cd.setOriginal("big problem");
        cd.setCorrected("major issue");
        cd.setExplanation("用词不当");
        cd.setMessageId(3);

        assertThat(cd.getType()).isEqualTo(ErrorType.WORD_CHOICE);
        assertThat(cd.getOriginal()).isEqualTo("big problem");
        assertThat(cd.getCorrected()).isEqualTo("major issue");
        assertThat(cd.getExplanation()).isEqualTo("用词不当");
        assertThat(cd.getMessageId()).isEqualTo(3);
    }

    @Test
    void allErrorTypesWork() {
        for (ErrorType type : ErrorType.values()) {
            CorrectionData cd = new CorrectionData(type, "orig", "corr", "expl");
            assertThat(cd.getType()).isEqualTo(type);
        }
    }
}
