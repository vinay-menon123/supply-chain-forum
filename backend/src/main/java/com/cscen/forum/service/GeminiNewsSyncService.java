package com.cscen.forum.service;

import com.cscen.forum.model.Comment;
import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.CommentRepository;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GeminiNewsSyncService {

    private static final Logger log = LoggerFactory.getLogger(GeminiNewsSyncService.class);

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final CommentRepository commentRepository;
    private final NotificationService notificationService;
    private final Environment env;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiNewsSyncService(UserRepository userRepository,
                                 QuestionRepository questionRepository,
                                 CommentRepository commentRepository,
                                 NotificationService notificationService,
                                 Environment env) {
        this.userRepository = userRepository;
        this.questionRepository = questionRepository;
        this.commentRepository = commentRepository;
        this.notificationService = notificationService;
        this.env = env;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public boolean isEnabled() {
        String apiKey = env.getProperty("GEMINI_API_KEY", "");
        return !apiKey.isBlank();
    }

    @Scheduled(cron = "${news.sync.cron:0 30 23 * * *}")
    public void scheduleDailySync() {
        log.info("Starting scheduled daily Gemini news sync...");
        String result = syncLatestNews();
        log.info("Scheduled daily Gemini news sync completed: {}", result);
    }

    public String syncLatestNews() {
        if (!isEnabled()) {
            return "skipped — Gemini API key is not configured (set GEMINI_API_KEY)";
        }

        List<User> approvedUsers = userRepository.findByVerifyStatusOrderByCreatedAtDesc("APPROVED")
                .stream()
                .filter(u -> !u.isBanned())
                .toList();

        if (approvedUsers.isEmpty()) {
            return "skipped — no approved users found to author questions/answers";
        }

        String apiKey = env.getProperty("GEMINI_API_KEY");
        String model = env.getProperty("GEMINI_API_MODEL", "gemini-2.5-flash");
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        String prompt = "Search Google for the latest news on Supply Chain and AI in India and globally from the last 24 hours. Based on the findings, generate 2-3 realistic, high-quality community Q&A topics. For each topic, you must generate:\n" +
                "1. An engaging, realistic question title (at least 8 characters, max 200 characters) about a key trend or challenge in the news.\n" +
                "2. A natural-sounding, detailed question body (as if written by a real practitioner seeking advice, discussing challenges, or sharing experiences, max 2000 characters).\n" +
                "3. A relevant topic tag selected ONLY from these exact uppercase strings:\n" +
                "GENERAL, DEMAND_PLANNING, PROCUREMENT, MANUFACTURING, LOGISTICS, WAREHOUSING, INVENTORY, SUSTAINABILITY, DIGITAL_AI, RISK, CAREERS.\n" +
                "4. A realistic, high-quality response/answer (comment body) from another member that answers the question in a human, practical, and experienced way (max 2000 characters).\n" +
                "\n" +
                "Return the output ONLY as a valid JSON array of objects. Do not include markdown code block formatting (like ```json) or any conversational text. Use exactly this JSON structure:\n" +
                "[\n" +
                "  {\n" +
                "    \"title\": \"Question title here...\",\n" +
                "    \"body\": \"Detailed question body here...\",\n" +
                "    \"tag\": \"DIGITAL_AI\",\n" +
                "    \"answer\": \"Detailed helpful answer here...\"\n" +
                "  }\n" +
                "]";

        try {
            // Prepare Request Body
            Map<String, Object> requestBody = new HashMap<>();
            
            Map<String, Object> part = new HashMap<>();
            part.put("text", prompt);
            
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(part));
            
            requestBody.put("contents", List.of(content));
            
            Map<String, Object> googleSearch = new HashMap<>();
            googleSearch.put("google_search", new HashMap<>());
            
            requestBody.put("tools", List.of(googleSearch));

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(45))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Gemini API call failed with status code {}: {}", response.statusCode(), response.body());
                return "error: Gemini API returned status code " + response.statusCode();
            }

            JsonNode root = objectMapper.readTree(response.body());
            String responseText = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText()
                    .trim();

            if (responseText.isEmpty()) {
                log.warn("Gemini API returned empty text candidate");
                return "skipped — empty response from Gemini";
            }

            int created = processGeneratedContent(responseText, approvedUsers);
            return "created " + created + " questions from latest search news";

        } catch (Exception e) {
            log.error("Failed to perform Gemini news sync", e);
            return "error: " + e.getMessage();
        }
    }

    @Transactional
    public int processGeneratedContent(String responseText, List<User> approvedUsers) throws Exception {
        String jsonText = responseText.trim();
        if (jsonText.startsWith("```")) {
            jsonText = jsonText.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
        }

        List<GeneratedItem> items;
        try {
            items = objectMapper.readValue(jsonText, new TypeReference<List<GeneratedItem>>() {});
        } catch (Exception e) {
            log.error("Failed to parse JSON response from Gemini: {}", jsonText, e);
            throw e;
        }

        int created = 0;
        for (GeneratedItem item : items) {
            if (item.title() == null || item.title().trim().length() < 8) {
                log.warn("Generated question title is too short, skipping: {}", item.title());
                continue;
            }

            // Validate tag
            String tag = item.tag() != null ? item.tag().trim().toUpperCase() : "GENERAL";
            if (!QuestionService.TAGS.contains(tag)) {
                tag = "GENERAL";
            }

            // Pick question author randomly from approved users (seeded real names)
            User qAuthor = approvedUsers.get(ThreadLocalRandom.current().nextInt(approvedUsers.size()));

            // Pick different answer author randomly if possible
            User aAuthor = qAuthor;
            if (approvedUsers.size() > 1) {
                while (aAuthor.getId().equals(qAuthor.getId())) {
                    aAuthor = approvedUsers.get(ThreadLocalRandom.current().nextInt(approvedUsers.size()));
                }
            }

            // Create question
            Question q = Question.create();
            q.setTitle(item.title().trim());
            q.setBody(item.body().trim());
            q.setTag(tag);
            q.setAuthorId(qAuthor.getId());
            questionRepository.save(q);

            // Create answer
            Comment c = Comment.create();
            c.setBody(item.answer().trim());
            c.setAuthorId(aAuthor.getId());
            c.setQuestionId(q.getId());
            commentRepository.save(c);

            // Notify followers
            try {
                notificationService.notifyNewQuestion(q, qAuthor, QuestionService.tagLabel(tag));
            } catch (Exception e) {
                log.error("Failed to send notification for new question: {}", q.getId(), e);
            }

            created++;
        }

        return created;
    }

    public record GeneratedItem(String title, String body, String tag, String answer) {}
}
