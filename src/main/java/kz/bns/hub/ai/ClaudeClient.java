package kz.bns.hub.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Тонкая обёртка над Anthropic Messages API.
 *
 * Идёт через корпоративный прокси БНС (вызов langchain4j прокси не поддерживал —
 * поэтому используем RestTemplate). Здесь же — безопасный разбор ответа и
 * валидация ролей истории, чтобы кривой вход не валил сервис.
 */
@Slf4j
@Component
public class ClaudeClient implements LlmClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final Set<String> ALLOWED_ROLES = Set.of("user", "assistant");

    // Ключ берётся из claude.api.key (значение лежит в application.properties).
    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.model:claude-sonnet-4-5}")
    private String model;

    @Value("${claude.max-tokens:4096}")
    private int maxTokens;

    @Value("${proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${proxy.host:}")
    private String proxyHost;

    @Value("${proxy.port:3128}")
    private int proxyPort;

    @Value("${proxy.connect-timeout-ms:30000}")
    private int connectTimeoutMs;

    @Value("${proxy.read-timeout-ms:120000}")
    private int readTimeoutMs;

    /**
     * @param systemPrompt системная инструкция
     * @param prompt       пользовательский промпт (с контекстом из документов)
     * @param history      история диалога (роли user/assistant); невалидные элементы отбрасываются
     * @return текст ответа модели
     */
    @Override
    public String generateAnswer(String systemPrompt, String prompt, List<Map<String, String>> history) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, String> h : history) {
                String role = h.get("role");
                String content = h.get("content");
                if (ALLOWED_ROLES.contains(role) && content != null && !content.isBlank()) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
//                "temperature", 0,        // ← добавь эту строку
                "system", systemPrompt,
                "messages", messages

        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate().postForEntity(API_URL, entity, Map.class);

        return extractText(response.getBody());
    }

    /** Достаёт текст из ответа Claude, не падая на неожиданном формате. */
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalStateException("Пустой ответ от Claude API");
        }
        Object contentObj = body.get("content");
        if (!(contentObj instanceof List<?> content) || content.isEmpty()) {
            log.warn("Неожиданный ответ Claude: {}", body);
            throw new IllegalStateException("Ответ Claude не содержит content");
        }
        // Берём первый text-блок, а не слепо content[0] (там может быть tool_use и т.п.).
        for (Object block : content) {
            if (block instanceof Map<?, ?> map
                    && "text".equals(map.get("type"))
                    && map.get("text") instanceof String text) {
                return text;
            }
        }
        throw new IllegalStateException("В ответе Claude нет текстового блока");
    }

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        if (proxyEnabled && !proxyHost.isBlank()) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }
        return new RestTemplate(factory);
    }
}
