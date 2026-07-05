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
import java.util.stream.Collectors;

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

    private static final String VERIFY_PROMPT = """
            CSCE Nexus is a members-only community strictly for people working in or \
            studying the supply chain, logistics, procurement, manufacturing, \
            warehousing, transportation or operations field. You are given a new \
            member's professional headline (and possibly a LinkedIn URL). Decide \
            whether this person plausibly belongs to the supply chain / logistics \
            ecosystem. Be inclusive of adjacent roles (operations, trade, freight, \
            inventory, planning, sourcing, e-commerce fulfilment, supply chain \
            academia/students). Respond with exactly one word: RELEVANT if they \
            plausibly belong, otherwise UNRELATED.""";

    private final AnthropicClient client;
    private final String model;
    private final String generativeModel;

    public AiModerationClient(Environment env) {
        String apiKey = env.getProperty("ANTHROPIC_API_KEY", "");
        this.model = env.getProperty("ANTHROPIC_MODEL", "claude-opus-4-8");
        this.generativeModel = env.getProperty("GMAIL_SYNC_MODEL", "claude-3-5-haiku-20241022");
        if (apiKey.isBlank()) {
            this.client = null;
            log.info("AI moderation disabled (ANTHROPIC_API_KEY not set); using wordlist only");
        } else {
            this.client = AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .timeout(Duration.ofSeconds(20))
                    .maxRetries(1)
                    .build();
            log.info("AI moderation enabled with model {} and generative model {}", model, generativeModel);
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

    /**
     * Judges whether a member's professional headline belongs to the supply
     * chain field. Empty when disabled or on error — callers then leave the
     * member PENDING for manual admin review (never a hard block).
     */
    public Optional<Boolean> isSupplyChainRelevant(String headline, String linkedinUrl) {
        if (client == null || headline == null || headline.isBlank()) {
            return Optional.empty();
        }
        String sample = "Headline: " + headline
                + (linkedinUrl == null || linkedinUrl.isBlank() ? "" : "\nLinkedIn: " + linkedinUrl);
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16L)
                    .system(VERIFY_PROMPT)
                    .addUserMessage(sample)
                    .build();
            Message response = client.messages().create(params);
            String verdict = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text().trim().toUpperCase(Locale.ROOT))
                    .findFirst()
                    .orElse("");
            if (verdict.startsWith("RELEVANT")) return Optional.of(true);
            if (verdict.startsWith("UNRELATED")) return Optional.of(false);
            log.warn("AI verification returned unexpected verdict: {}", verdict);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("AI verification unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Generates a list of Q&A items from a supply chain news digest newsletter using Claude Haiku.
     */
    public Optional<String> generateQuestionsAndAnswers(String newsletterText) {
        if (client == null || newsletterText == null || newsletterText.isBlank()) {
            return Optional.empty();
        }
        String systemPrompt = """
                You are an expert supply chain and AI consultant. Your task is to analyze the provided supply chain and AI newsletter.
                Extract the main topics or trends discussed.
                Generate exactly 2 to 3 professional, engaging community Q&A items based on the newsletter content.
                
                For each Q&A item, you must generate:
                1. An engaging, realistic question title (at least 8 characters, maximum 200 characters) about a key trend or challenge in the digest.
                2. A natural-sounding, detailed question body (as if written by a real practitioner seeking advice, discussing challenges, or sharing experiences, maximum 2000 characters).
                3. A relevant topic tag selected ONLY from these exact uppercase strings:
                   GENERAL, DEMAND_PLANNING, PROCUREMENT, MANUFACTURING, LOGISTICS, WAREHOUSING, INVENTORY, SUSTAINABILITY, DIGITAL_AI, RISK, CAREERS.
                4. A realistic, high-quality response/answer (comment body) from another member that answers the question in a human, practical, and experienced way (maximum 2000 characters).
                
                Return the output ONLY as a valid JSON array of objects. Do not include markdown code block formatting (like ```json) or any conversational text. Use exactly this JSON structure:
                [
                  {
                    "title": "Question title here...",
                    "body": "Detailed question body here...",
                    "tag": "DIGITAL_AI",
                    "answer": "Detailed helpful answer here..."
                  }
                ]
                """;
        try {
            String sample = newsletterText.length() > 25000 ? newsletterText.substring(0, 25000) : newsletterText;
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(generativeModel)
                    .maxTokens(4000L)
                    .system(systemPrompt)
                    .addUserMessage(sample)
                    .build();
            Message response = client.messages().create(params);
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining("\n"));
            return Optional.of(text);
        } catch (Exception e) {
            log.error("AI question generation failed: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
}
