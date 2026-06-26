package kz.bns.hub.ai;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Клиент для интеграции с Google Gemini API.
 * 
 * Использует официальный Google GenAI SDK.
 */
@Slf4j
@Component
public class GeminiClient implements LlmClient {

    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant");

    @Value("${ai.gemini.api-key}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-2.5-flash}")
    private String model;

    @Override
    public String generateAnswer(String systemPrompt, String userPrompt, List<Map<String, String>> history) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured. Please set 'ai.gemini.api-key' property.");
        }

        // Инициализация официального клиента
        Client client = Client.builder().apiKey(apiKey).build();

        StringBuilder fullContext = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            fullContext.append("SYSTEM INSTRUCTIONS:\n").append(systemPrompt).append("\n\n");
        }

        if (history != null && !history.isEmpty()) {
            fullContext.append("CHAT HISTORY:\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if (ALLOWED_ROLES.contains(role) && content != null && !content.isBlank()) {
                    fullContext.append(role).append(": ").append(content).append("\n");
                }
            }
            fullContext.append("\nCURRENT ANALYST QUESTION:\n").append(userPrompt);
        } else {
            fullContext.append("USER QUESTION:\n").append(userPrompt);
        }

        List<Content> contents = List.of(Content.builder()
                .role("user")
                .parts(List.of(Part.builder().text(fullContext.toString()).build()))
                .build());

        log.info("Sending request to Gemini API (SDK), model: {}", model);

        int maxRetries = 4;
        int defaultWaitMs = 2000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Вызов генерации
                GenerateContentResponse response = client.models.generateContent(model, contents, null);
                return response.text();
            } catch (Exception e) {
                lastException = e;
                String msg = e.getMessage() != null ? e.getMessage() : "";
                boolean isRateLimitOrTransient = msg.contains("429") || msg.contains("503") 
                        || msg.toLowerCase().contains("quota") 
                        || msg.toLowerCase().contains("too many requests")
                        || msg.toLowerCase().contains("service unavailable")
                        || msg.toLowerCase().contains("overloaded");

                if (isRateLimitOrTransient && attempt < maxRetries) {
                    int currentWaitMs = defaultWaitMs;
                    // Пытаемся распарсить точное время ожидания от API
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("Please retry in ([\\d.]+)s").matcher(msg);
                    if (matcher.find()) {
                        try {
                            double seconds = Double.parseDouble(matcher.group(1));
                            currentWaitMs = (int) (seconds * 1000) + 1500; // добавляем 1.5 секунды буфера
                        } catch (Exception ignored) {}
                    }

                    log.warn("Gemini API rate limit or transient error (attempt {}/{}). Retrying in {}ms... Error: {}", 
                            attempt, maxRetries, currentWaitMs, msg);
                    try {
                        Thread.sleep(currentWaitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Request interrupted", ie);
                    }
                    defaultWaitMs *= 2; // Экспоненциальный бэкофф на случай, если парсинг не сработал
                } else {
                    log.error("Fatal error calling Gemini SDK on attempt {}", attempt, e);
                    throw new RuntimeException("Failed to generate answer from Gemini API", e);
                }
            }
        }
        throw new RuntimeException("Failed to generate answer from Gemini API after " + maxRetries + " attempts", lastException);
    }
}
