package com.hugosol.chatagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private final PromptLoader loader = new PromptLoader(new DefaultResourceLoader());

    @Test
    void loadsExistingFile() {
        String content = loader.load("correction/system.txt");
        assertThat(content).isNotEmpty();
        assertThat(content).contains("Correction prompt:");
    }

    @Test
    void loadsReportTemplate() {
        String content = loader.load("report/system.txt");
        assertThat(content).isNotEmpty();
        assertThat(content).contains("Report prompt.");
    }

    @Test
    void fileNotFoundThrowsRuntimeException() {
        assertThatThrownBy(() -> loader.load("nonexistent.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nonexistent.txt");
    }

    @Test
    void loadedContentIsTrimmedCorrectly() {
        String content = loader.load("correction/system.txt");
        assertThat(content).doesNotContain("\r");
    }
}
