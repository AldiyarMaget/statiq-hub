package kz.bns.hub.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalIntentRouter {

    private final EmbeddingModel embeddingModel;

    @Value("${app.search.query-prefix:query:}")
    private String queryPrefix;

    @Value("${app.router.threshold:0.75}")
    private double routingThreshold;

    private final Map<UserIntent, List<Embedding>> cachedAnchorVectors = new EnumMap<>(UserIntent.class);

    // TODO: После миграции на ONNX multilingual-e5-small заменить анкоры на лаконичные английские / мультиязычные эквиваленты
    @PostConstruct
    public void init() {
        log.info("Инициализация локального семантического роутера интентов...");
        
        Map<UserIntent, List<String>> anchors = new EnumMap<>(UserIntent.class);

        anchors.put(UserIntent.API_REQUEST, List.of(
            "как вызвать метод api эндпоинт",
            "спецификация swagger json схема параметров",
            "get post url запросы интерфейса интеграции",
            "какие параметры передавать в апи метод"
        ));

        anchors.put(UserIntent.ROLES_AND_ACCESS, List.of(
            "какие у меня роли права доступа полномочия",
            "ошибка доступа не хватает разрешений estatmeta",
            "матрица распределения прав пользователей роли",
            "как получить доступ к редактированию или согласованию"
        ));

        anchors.put(UserIntent.BDAP_PACKAGES, List.of(
            "ошибка загрузки пакета в бдап bdap",
            "порядок статусов пакетов 010 015 020 101",
            "документ не утвержден блокирует пакет бдап",
            "регистрация и отправка отчетности в бд ап"
        ));

        anchors.put(UserIntent.METHODOLOGY_KSP, List.of(
            "как добавить новый показатель ксп",
            "создание новой статистической формы бланка",
            "классификационный разрез единица измерения показатели",
            "методология ведения csiinstance и индексов форм"
        ));

        // Предварительно векторизуем эталонные фразы с префиксом "query: " (требование E5)
        anchors.forEach((intent, phrases) -> {
            List<Embedding> embeddings = new ArrayList<>();
            for (String phrase : phrases) {
                String prefix = queryPrefix.isBlank() ? "" : queryPrefix.trim() + " ";
                Embedding emb = embeddingModel.embed(prefix + phrase).content();
                embeddings.add(emb);
            }
            cachedAnchorVectors.put(intent, embeddings);
        });
        
        log.info("Локальный семантический роутер успешно инициализирован. Закешировано кластеров: {}", cachedAnchorVectors.size());
    }

    public UserIntent route(String question) {
        if (question == null || question.isBlank()) {
            return UserIntent.GENERAL_SEARCH;
        }

        String prefix = queryPrefix.isBlank() ? "" : queryPrefix.trim() + " ";
        Embedding questionEmbedding = embeddingModel.embed(prefix + question.toLowerCase()).content();

        UserIntent bestIntent = UserIntent.GENERAL_SEARCH;
        double maxSimilarity = -1.0;

        for (Map.Entry<UserIntent, List<Embedding>> entry : cachedAnchorVectors.entrySet()) {
            for (Embedding anchor : entry.getValue()) {
                double similarity = cosineSimilarity(questionEmbedding.vector(), anchor.vector());
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                    if (similarity >= routingThreshold) {
                        bestIntent = entry.getKey();
                    }
                }
            }
        }

        log.info("Семантический роутер: определен интент [{}] с макс. косинусным сходством {}", bestIntent, String.format("%.3f", maxSimilarity));
        return bestIntent;
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
