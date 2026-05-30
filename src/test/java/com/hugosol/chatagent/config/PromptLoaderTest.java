package com.hugosol.chatagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptLoaderTest {

    private final PromptLoader loader = new PromptLoader(new DefaultResourceLoader());

    @Test
    void loadsExistingFile() {
        String content = loader.load("correction.txt");
        assertThat(content).isNotEmpty();
        assertThat(content).contains("{userInput}");
    }

    @Test
    void loadsReportTemplate() {
        String content = loader.load("report.txt");
        assertThat(content).isNotEmpty();
        assertThat(content).contains("{fullConversation}");
    }

    @Test
    void fileNotFoundThrowsRuntimeException() {
        assertThatThrownBy(() -> loader.load("nonexistent.txt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nonexistent.txt");
    }

    @Test
    void loadedContentIsTrimmedCorrectly() {
        String content = loader.load("correction.txt");
        assertThat(content).doesNotContain("\r");
    }
}
