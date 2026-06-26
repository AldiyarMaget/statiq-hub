package kz.bns.hub.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final SegmentRegistry segmentRegistry;

    @Value("${app.search.max-results:10}")
    private int maxResults;

    @Value("${app.search.candidates:30}")
    private int candidates;

    @Value("${app.search.min-score:0.4}")
    private double minScore;

    @Value("${app.search.query-prefix:query:}")
    private String queryPrefix;

    // =========================================================================
    //  ЗАМЕНА метода findRelevantContext + 2 новых хелпера (isApiQuestion, isSwagger).
    //  Остальной файл (findBySource, keywordFallback, extractKeywords, source...)
    //  НЕ меняется.
    //
    //  Что чинит:
    //   1) Вопросы про показатели/КСП больше не уходят в swagger — принудительно
    //      подмешиваем USER_GUIDE.md (как уже сделано для ROLES.md и BDAP).
    //   2) Фрагменты swagger (meta-swagger.json) штрафуются при ранжировании и
    //      больше не вытесняют реальные инструкции — КРОМЕ случая, когда вопрос
    //      действительно про API (тогда swagger, наоборот, нужен).
    // =========================================================================
    public String findRelevantContext(String question) {
        return findRelevantContext(question, List.of());
    }

    public String findRelevantContext(String question, List<Map<String, String>> history) {
        try {
            String lower = question.toLowerCase();

            // Принудительный поиск ROLES.md при вопросах про роли (только если не API-вопрос)
            if (!isApiQuestion(lower) && (lower.contains("рол") || lower.contains("role") || lower.contains("право") || lower.contains("доступ")
                    || lower.contains("разрешен") || lower.contains("полномочи"))) {
                String rolesContext = findBySource("ROLES.md");
                if (!rolesContext.isBlank()) {
                    log.info("Принудительно добавлен контекст из ROLES.md");
                    return rolesContext;
                }
            }

            // Принудительный поиск USER_GUIDE_BDAP.md при вопросах про БДАП/пакеты (только если не API-вопрос)
            if (!isApiQuestion(lower) && (lower.contains("бдап") || lower.contains("бд ап") || lower.contains("bdap")
                    || lower.contains("пакет") || lower.contains("загрузк"))) {
                String bdapContext = findBySource("USER_GUIDE_BDAP.md");
                if (!bdapContext.isBlank()) {
                    log.info("Принудительно добавлен контекст из USER_GUIDE_BDAP.md");
                    return bdapContext;
                }
            }

            // НОВОЕ — принудительный USER_GUIDE.md при вопросах про показатели/КСП/формы.
            // Срабатывает ТОЛЬКО если это НЕ чисто-API вопрос (для аналитика со словом
            // "api/эндпоинт" пусть работает обычный векторный поиск по swagger).
            if (!isApiQuestion(lower) &&
                    (lower.contains("показател") || lower.contains("ксп")
                            || lower.contains("добавить") || lower.contains("создать")
                            || lower.contains("форм") || lower.contains("единиц"))) {
                String guideContext = findBySource("USER_GUIDE.md");
                if (!guideContext.isBlank()) {
                    log.info("Принудительно добавлен контекст из USER_GUIDE.md (показатели/КСП)");
                    return guideContext;
                }
            }

            // Обогащаем поисковый запрос историей диалога для лучшего понимания местоимений
            String searchQuestion = enrichQueryWithHistory(question, history);
            String prefixedQuestion = queryPrefix.isBlank() ? searchQuestion : queryPrefix.trim() + " " + searchQuestion;
            Embedding questionEmbedding = embeddingModel.embed(prefixedQuestion).content();

            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(questionEmbedding)
                            .maxResults(candidates)
                            .minScore(minScore)
                            .build()
            ).matches();

            boolean apiQuestion = isApiQuestion(lower);

            if (matches.isEmpty()) {
                log.info("Векторный поиск пуст (minScore={}). Пробую keyword-фолбэк.", minScore);
                return keywordFallback(searchQuestion, apiQuestion);
            }

            Set<String> keywords = extractKeywords(searchQuestion);

            List<EmbeddingMatch<TextSegment>> ranked = matches.stream()
                    .sorted((a, b) -> {
                        // 1) swagger вниз, если вопрос не про API, иначе вверх, если про API
                        boolean sa = isSwagger(a);
                        boolean sb = isSwagger(b);
                        if (sa != sb) {
                            return apiQuestion ? (sa ? -1 : 1) : (sa ? 1 : -1);
                        }
                        // 2) больше попаданий ключевых слов — выше
                        long ka = countKeywordHits(a, keywords);
                        long kb = countKeywordHits(b, keywords);
                        if (kb != ka) return Long.compare(kb, ka);
                        // 3) при равенстве — по score
                        return Double.compare(b.score(), a.score());
                    })
                    .limit(maxResults)
                    .toList();

            ranked.forEach(m -> log.info("Фрагмент [{}] score={}",
                    source(m), String.format("%.3f", m.score())));

            return ranked.stream()
                    .map(m -> "Источник: " + source(m) + "\n" + m.embedded().text())
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (Exception e) {
            log.warn("Ошибка поиска контекста: {}", e.getMessage());
            return "";
        }
    }

    /** Вопрос явно про API/эндпоинты — тогда swagger нужен и штрафовать его нельзя. */
    private boolean isApiQuestion(String lower) {
        return lower.contains("api") || lower.contains("апи")
                || lower.contains("эндпоинт") || lower.contains("endpoint")
                || lower.contains("swagger") || lower.contains("url")
                || lower.contains("запрос") || lower.contains("json");
    }

    /** Фрагмент относится к swagger-документации (meta-swagger.json и т.п.). */
    private boolean isSwagger(TextSegment seg) {
        String src = source(seg).toLowerCase();
        return src.endsWith(".json") || src.contains("swagger");
    }

    private boolean isSwagger(EmbeddingMatch<TextSegment> m) {
        return isSwagger(m.embedded());
    }

    /** Принудительно находит все фрагменты из конкретного файла. */
    private String findBySource(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        return segmentRegistry.all().stream()
                .filter(seg -> {
                    Object src = seg.metadata().toMap().get("source");
                    return src != null && src.toString().toLowerCase().contains(lowerFileName);
                })
                .map(seg -> "Источник: " + source(seg) + "\n" + seg.text())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String keywordFallback(String question) {
        return keywordFallback(question, isApiQuestion(question.toLowerCase()));
    }

    private String keywordFallback(String question, boolean apiQuestion) {
        Set<String> keywords = extractKeywords(question);
        if (keywords.isEmpty() || segmentRegistry.all().isEmpty()) {
            return "";
        }

        List<TextSegment> ranked = segmentRegistry.all().stream()
                .map(seg -> Map.entry(seg, countKeywordHits(seg.text(), keywords)))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> {
                    // 1) swagger вниз, если вопрос не про API, иначе вверх, если про API
                    boolean sa = isSwagger(a.getKey());
                    boolean sb = isSwagger(b.getKey());
                    if (sa != sb) {
                        return apiQuestion ? (sa ? -1 : 1) : (sa ? 1 : -1);
                    }
                    // 2) больше попаданий ключевых слов — выше
                    return Long.compare(b.getValue(), a.getValue());
                })
                .limit(maxResults)
                .map(Map.Entry::getKey)
                .toList();

        if (ranked.isEmpty()) {
            log.info("Keyword-фолбэк тоже пуст для вопроса: {}", question);
            return "";
        }

        log.info("Keyword-фолбэк нашёл {} фрагмент(ов)", ranked.size());
        return ranked.stream()
                .map(seg -> "Источник: " + source(seg) + "\n" + seg.text())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String enrichQueryWithHistory(String question, List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return question;
        }

        StringBuilder contextBuilder = new StringBuilder();
        int startIdx = Math.max(0, history.size() - 2);
        for (int i = startIdx; i < history.size(); i++) {
            Map<String, String> msg = history.get(i);
            String content = msg.get("content");
            if (content != null && !content.isBlank()) {
                String cleanContent = content.trim();
                if (cleanContent.length() > 200) {
                    cleanContent = cleanContent.substring(0, 200) + "...";
                }
                contextBuilder.append(" ").append(cleanContent);
            }
        }

        String contextStr = contextBuilder.toString().trim();
        if (contextStr.isEmpty()) {
            return question;
        }
        return question + " [Контекст: " + contextStr + "]";
    }

    private long countKeywordHits(EmbeddingMatch<TextSegment> m, Set<String> keywords) {
        return countKeywordHits(m.embedded().text(), keywords);
    }

    private long countKeywordHits(String text, Set<String> keywords) {
        String lower = text.toLowerCase();
        return keywords.stream().filter(lower::contains).count();
    }

    private Set<String> extractKeywords(String question) {
        Set<String> keywords = new HashSet<>();
        String lower = question.toLowerCase();

        // Базовые слова из вопроса
        for (String w : lower.split("[\\s,?.!()—]+")) {
            if (w.length() > 3) {
                keywords.add(w);
                if (w.length() > 6) keywords.add(w.substring(0, w.length() - 2));
                if (w.length() > 4) keywords.add(w.substring(0, w.length() - 1));
            }
        }

        // Роли META
        if (lower.contains("рол") || lower.contains("право") || lower.contains("доступ")
                || lower.contains("разрешен") || lower.contains("полномочи")) {
            keywords.add("estat");
            keywords.add("estatmeta");
            keywords.add("estatmeta_view");
            keywords.add("estatmeta_edit");
            keywords.add("estatmeta_form");
            keywords.add("estatmeta_agreement");
            keywords.add("краткая сводка");
        }

        // БДАП / пакеты / статусы загрузки
        if (lower.contains("бдап") || lower.contains("бд ап") || lower.contains("bdap")
                || lower.contains("пакет") || lower.contains("загрузк")) {
            keywords.add("010");
            keywords.add("015");
            keywords.add("020");
            keywords.add("035");
            keywords.add("101");
            keywords.add("102");
            keywords.add("103");
            keywords.add("104");
            keywords.add("регистрация");
            keywords.add("утверждён");
            keywords.add("estatbdap");
            keywords.add("user_guide_bdap");
            keywords.add("порядок статусов");
        }

        // Согласование / утверждение
        if (lower.contains("согласован") || lower.contains("утвержден")
                || lower.contains("agreement") || lower.contains("approved")) {
            keywords.add("agreement");
            keywords.add("approved");
            keywords.add("agreed");
            keywords.add("estatmeta_agreement");
            keywords.add("estatmeta_confirmation");
            keywords.add("calendardoc");
        }

        // Ошибка "не утверждённый документ"
        if (lower.contains("ошибк") || lower.contains("не утверждённый")
                || lower.contains("найден не")) {
            keywords.add("найден не утверждённый");
            keywords.add("calendardoc");
            keywords.add("блокирует");
        }

        // Формы / создать форму
        if (lower.contains("форм") || lower.contains("создать") || lower.contains("бланк")) {
            keywords.add("estatmeta_form");
            keywords.add("formindexid");
            keywords.add("версия формы");
            keywords.add("индекс формы");
        }

        // Схемы / Mermaid
        if (lower.contains("схем") || lower.contains("диаграмм") || lower.contains("нарисуй")
                || lower.contains("интеграц")) {
            keywords.add("талдау");
            keywords.add("сбор");
            keywords.add("klazz");
            keywords.add("kafka");
        }

        // API / Swagger
        if (lower.contains("апи") || lower.contains("api") || lower.contains("эндпоинт")
                || lower.contains("swagger")) {
            keywords.add("/meta/form");
            keywords.add("/meta/csi");
            keywords.add("get");
            keywords.add("swagger");
        }

        // КСП / показатели
        if (lower.contains("ксп") || lower.contains("показател")) {
            keywords.add("ксп");
            keywords.add("csiinstance");
            keywords.add("классификационный");
            keywords.add("estatmeta_ksp");
        }

        // Разрезности / акронимы
        if (lower.contains("разрезност") || lower.contains("акроним")) {
            keywords.add("пространство");
            keywords.add("space");
            keywords.add("като");
            keywords.add("окэд");
            keywords.add("mdicacronym");
        }

        return keywords;
    }

    private String source(EmbeddingMatch<TextSegment> m) {
        return source(m.embedded());
    }

    private String source(TextSegment seg) {
        Object src = seg.metadata().toMap().get("source");
        return src != null ? src.toString() : "неизвестен";
    }
}