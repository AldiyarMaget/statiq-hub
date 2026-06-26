package kz.bns.hub.controller;

import kz.bns.hub.ai.LlmClient;
import kz.bns.hub.ai.MermaidPostProcessor;
import kz.bns.hub.ai.ImagePostProcessor;
import kz.bns.hub.ai.PromptBuilder;
import kz.bns.hub.service.DocumentStatsService;
import kz.bns.hub.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins:*}")
public class ChatController {

    private final DocumentStatsService statsService;
    private final RetrievalService retrievalService;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final MermaidPostProcessor mermaidPostProcessor;
    private final ImagePostProcessor imagePostProcessor;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody Map<String, Object> body) {
        String question = asString(body.get("question"));
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Вопрос пустой"));
        }

        List<Map<String, String>> history = extractHistory(body);
        String mode = normalizeMode(asString(body.get("mode")));
        log.info("Вопрос: {} (режим: {})", question, mode);

        try {
            String context = retrievalService.findRelevantContext(question, history);
            String userPrompt = promptBuilder.userPrompt(question, context, mode);
            String answer = llmClient.generateAnswer(promptBuilder.systemPrompt(), userPrompt, history);
            answer = mermaidPostProcessor.fix(answer);
            // Гарантируем показ релевантных скриншотов, даже если модель их не вставила.
//            answer = imagePostProcessor.ensureImages(answer, context, question);

            log.info("Ответ получен, длина: {}", answer.length());
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (Exception e) {
            log.error("Ошибка обработки вопроса", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "ready",
                "documentsLoaded", statsService.getDocumentCount(),
                "segmentsIndexed", statsService.getSegmentCount()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    private String asString(Object o) {
        return o instanceof String s ? s : null;
    }

    /** Фронт шлёт 'user' (методолог, простой язык) или 'analyst' (с API).
     *  Всё неизвестное/пустое трактуем как методолога — безопасный дефолт. */
    private String normalizeMode(String mode) {
        return "analyst".equals(mode) ? "analyst" : "user";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> extractHistory(Map<String, Object> body) {
        Object messages = body.getOrDefault("messages", List.of());
        if (messages instanceof List<?> list) {
            return (List<Map<String, String>>) list;
        }
        return List.of();
    }
}