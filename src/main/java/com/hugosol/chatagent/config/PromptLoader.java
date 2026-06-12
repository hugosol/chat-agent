package com.hugosol.chatagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private final ResourceLoader resourceLoader;

    public PromptLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String load(String promptFile) {
        try {
            Resource resource = resourceLoader.getResource("classpath:prompts/" + promptFile);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Loaded prompt file: {}", promptFile);
            return content;
        } catch (IOException e) {
            log.error("Failed to load prompt file: {}", promptFile, e);
            throw new RuntimeException("Failed to load prompt: " + promptFile, e);
        }
    }

    public String loadIfExists(String promptFile, String fallback) {
        try {
            Resource resource = resourceLoader.getResource("classpath:prompts/" + promptFile);
            if (resource.exists()) {
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                log.debug("Loaded prompt file: {}", promptFile);
                return content;
            }
        } catch (IOException e) {
            log.warn("Failed to load prompt file {}, falling back", promptFile, e);
        }
        return fallback;
    }
}
