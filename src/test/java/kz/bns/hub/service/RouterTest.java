package kz.bns.hub.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class RouterTest {

    public static void main(String[] args) {
        System.out.println("Инициализация локальной модели E5SmallV2QuantizedEmbeddingModel...");
        EmbeddingModel embeddingModel = new E5SmallV2QuantizedEmbeddingModel();
        String queryPrefix = "query: ";

        Map<UserIntent, List<String>> anchors = new EnumMap<>(UserIntent.class);

        anchors.put(UserIntent.API_REQUEST, List.of(
            "api endpoint swagger json request",
            "http method url web service api url",
            "get post query parameters api integration"
        ));

        anchors.put(UserIntent.ROLES_AND_ACCESS, List.of(
            "user roles permissions access denied auth",
            "security tokens access rights security matrix",
            "administration forbidden block unauthorized permissions"
        ));

        anchors.put(UserIntent.BDAP_PACKAGES, List.of(
            "upload package status document error",
            "processing package load data file system",
            "archive transfer file status upload error"
        ));

        anchors.put(UserIntent.METHODOLOGY_KSP, List.of(
            "methodology statistical forms indicators metrics",
            "create new form design documents structure",
            "classification measurements handbook indicators metadata"
        ));

        Map<UserIntent, List<Embedding>> cachedAnchorVectors = new EnumMap<>(UserIntent.class);

        anchors.forEach((intent, phrases) -> {
            List<Embedding> embeddings = new ArrayList<>();
            for (String phrase : phrases) {
                Embedding emb = embeddingModel.embed(queryPrefix + phrase).content();
                embeddings.add(emb);
            }
            cachedAnchorVectors.put(intent, embeddings);
        });

        System.out.println("Модель загружена, анкоры векторизованы.\n");

        List<String> testPhrases = List.of(
            "Забыл пароль, не могу зайти, пишет заблокировано",
            "Как отправить отчет и почему висит статус 010",
            "Где посмотреть документацию по эндпоинтам получения форм"
        );

        for (String testPhrase : testPhrases) {
            System.out.println("--------------------------------------------------");
            System.out.println("Вопрос: \"" + testPhrase + "\"");
            
            Embedding questionEmbedding = embeddingModel.embed(queryPrefix + testPhrase.toLowerCase()).content();
            
            UserIntent bestIntent = UserIntent.GENERAL_SEARCH;
            double maxSimilarity = -1.0;

            for (Map.Entry<UserIntent, List<Embedding>> entry : cachedAnchorVectors.entrySet()) {
                for (int i = 0; i < entry.getValue().size(); i++) {
                    Embedding anchor = entry.getValue().get(i);
                    String anchorText = anchors.get(entry.getKey()).get(i);
                    double similarity = cosineSimilarity(questionEmbedding.vector(), anchor.vector());
                    
                    // Раскомментируйте, чтобы увидеть сходство со всеми анкорами
                    // System.out.printf("  [%s] %s -> %.4f%n", entry.getKey(), anchorText, similarity);
                    
                    if (similarity > maxSimilarity) {
                        maxSimilarity = similarity;
                        bestIntent = entry.getKey();
                    }
                }
            }

            System.out.printf("Лучший интент: %s (score: %.4f)%n", bestIntent, maxSimilarity);
        }
    }

    private static double cosineSimilarity(float[] vectorA, float[] vectorB) {
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
