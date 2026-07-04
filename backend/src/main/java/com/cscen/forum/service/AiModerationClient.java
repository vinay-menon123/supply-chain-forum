package com.cscen.forum.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Optional AI content classifier backed by the Claude API. When
 * ANTHROPIC_API_KEY is unset (or a call fails) it returns empty and callers
 * fall back to the wordlist verdict — posting never breaks because of it.
 */
@Service
public class AiModerationClient {

    private static final Logger log = LoggerFactory.getLogger(AiModerationClient.class);

    private static final String SYSTEM_PROMPT = """
            You are the content moderator for CSCE Nexus, a professional community \
            for the supply chain industry. Classify the user's post. Respond with \
            exactly one word: VIOLATION if the post contains harassment, hate speech, \
            sexual content, threats, personal attacks, doxxing, or spam/scam \
            promotion; otherwise OK. Professional criticism, strong technical \
            disagreement, and frustration with processes or tools are OK.""";

    private final AnthropicClient client;
    private final String model;

    public AiModerationClient(Environment env) {
        String apiKey = env.getProperty("ANTHROPIC_API_KEY", "");
        this.model = env.getProperty("ANTHROPIC_MODEL", "claude-opus-4-8");
        if (apiKey.isBlank()) {
            this.client = null;
            log.info("AI moderation disabled (ANTHROPIC_API_KEY not set); using wordlist only");
        } else {
            this.client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(Duration.ofSeconds(20))
                    .maxRetries(1)
                    .build();
            log.info("AI moderation enabled with model {}", model);
        }
    }

    public boolean isEnabled() {
        return client != null;
    }

    /** Empty when disabled or on error — callers fail open to the wordlist verdict. */
    public Optional<Boolean> isViolating(String text) {
        if (client == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        String sample = text.length() > 4000 ? text.substring(0, 4000) : text;
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(256L)
                    .system(SYSTEM_PROMPT)
                    .addUserMessage(sample)
                    .build();
            Message response = client.messages().create(params);
            String verdict = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text().trim().toUpperCase(Locale.ROOT))
                    .findFirst()
                    .orElse("");
            if (verdict.startsWith("VIOLATION")) return Optional.of(true);
            if (verdict.startsWith("OK")) return Optional.of(false);
            log.warn("AI moderation returned unexpected verdict: {}", verdict);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI moderation unavailable, falling back to wordlist: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
