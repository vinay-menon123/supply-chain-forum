package com.cscen.forum.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * General-purpose LLM completion for the AI-agent control tower. Picks a backend
 * at startup: <b>Anthropic</b> ({@code ANTHROPIC_API_KEY}) if present, else
 * <b>Gemini</b> ({@code GEMINI_API_KEY}, reusing the news-sync key). If neither is
 * set (or a call fails) it returns empty and the orchestrator falls back to
 * templated reasoning — the feature always works, richer with a key.
 */
@Service
public class AgentAiClient {

    private static final Logger log = LoggerFactory.getLogger(AgentAiClient.class);

    private final AnthropicClient anthropic;   // null unless ANTHROPIC_API_KEY set
    private final String anthropicModel;
    private final String geminiKey;            // "" unless GEMINI_API_KEY set
    private final String geminiModel;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public AgentAiClient(Environment env, ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

        String anthKey = env.getProperty("ANTHROPIC_API_KEY", "").trim();
        this.anthropicModel = env.getProperty("ANTHROPIC_MODEL", "claude-opus-4-8");
        this.geminiKey = env.getProperty("GEMINI_API_KEY", "").trim();
        this.geminiModel = env.getProperty("GEMINI_API_MODEL", "gemini-2.5-flash");

        if (!anthKey.isBlank()) {
            this.anthropic = AnthropicOkHttpClient.builder()
                    .apiKey(anthKey).timeout(Duration.ofSeconds(40)).maxRetries(1).build();
            log.info("Agent AI backend: Anthropic ({})", anthropicModel);
        } else {
            this.anthropic = null;
            if (!geminiKey.isBlank()) {
                log.info("Agent AI backend: Gemini ({})", geminiModel);
            } else {
                log.info("Agent AI disabled (no ANTHROPIC_API_KEY / GEMINI_API_KEY); agents use templated reasoning");
            }
        }
    }

    public boolean isEnabled() {
        return anthropic != null || !geminiKey.isBlank();
    }

    /** "anthropic" | "gemini" | "none" — for showing the mode in the UI. */
    public String provider() {
        return anthropic != null ? "anthropic" : (!geminiKey.isBlank() ? "gemini" : "none");
    }

    /** Model's text output, or empty when disabled / on any error. */
    public Optional<String> complete(String system, String user, long maxTokens) {
        if (user == null || user.isBlank()) {
            return Optional.empty();
        }
        if (anthropic != null) {
            return completeAnthropic(system, user, maxTokens);
        }
        if (!geminiKey.isBlank()) {
            return completeGemini(system, user, maxTokens);
        }
        return Optional.empty();
    }

    private Optional<String> completeAnthropic(String system, String user, long maxTokens) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(anthropicModel)
                    .maxTokens(maxTokens)
                    .system(system)
                    .addUserMessage(user)
                    .build();
            Message response = anthropic.messages().create(params);
            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining("\n"))
                    .trim();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("Anthropic agent completion failed, falling back to templated: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> completeGemini(String system, String user, long maxTokens) {
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + geminiModel + ":generateContent?key=" + geminiKey;

            Map<String, Object> textPart = Map.of("text", system + "\n\n" + user);
            Map<String, Object> generationConfig = new LinkedHashMap<>();
            // Gemini 2.5 models "think" by default, and those tokens count against the
            // output budget — which truncated our JSON. We only need prose here, so
            // disable thinking (thinkingBudget:0) and give the JSON comfortable headroom.
            generationConfig.put("maxOutputTokens", (int) Math.min(Math.max(maxTokens, 2048), 8192));
            generationConfig.put("temperature", 0.4);
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(textPart))));
            body.put("generationConfig", generationConfig);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String b = response.body();
                log.warn("Gemini agent call failed ({}): {}", response.statusCode(),
                        b.length() > 300 ? b.substring(0, 300) : b);
                return Optional.empty();
            }
            JsonNode root = mapper.readTree(response.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0)
                    .path("text").asText("").trim();
            return text.isBlank() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            log.warn("Gemini agent completion failed, falling back to templated: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
