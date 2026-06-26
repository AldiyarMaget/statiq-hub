package kz.bns.hub.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.e5smallv2q.E5SmallV2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Конфигурация слоя эмбеддингов и векторного хранилища.
 *
 * Вызов Claude вынесен в {@link kz.bns.hub.ai.ClaudeClient} (через RestTemplate +
 * корпоративный прокси), поэтому langchain4j здесь отвечает ТОЛЬКО за
 * векторизацию документов и поиск. Раньше тут жил дублирующий chat-путь
 * (AnthropicChatModel + AiServices), который нигде не использовался — он удалён.
 */
@Configuration
public class AiConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        // E5-small-v2 (quantized, in-process). ВАЖНО: эта модель из семейства E5,
        // поэтому документы эмбеддятся с префиксом "passage: ", а запросы — "query: "
        // (см. DocumentIndexer / RetrievalService). Без префиксов качество на рус/каз
        // заметно хуже — именно из-за этого раньше не находились документы.
        //
        // E5-small англоцентрична. Для лучшего качества на рус/каз — перейти на
        // multilingual-e5. В langchain4j 0.36 НЕТ готового in-process артефакта
        // multilingual-e5, поэтому варианты миграции:
        //   1) Скачать ONNX intfloat/multilingual-e5-small + tokenizer.json и поднять
        //      через OnnxEmbeddingModel("model.onnx", "tokenizer.json").
        //      Префиксы query:/passage: остаются те же — менять остальной код не нужно.
        //   2) Удалённое embedding-API (Voyage/Cohere multilingual) через langchain4j.
        // При смене модели обнули префиксы (app.index.passage-prefix=, app.search.query-prefix=),
        // если новая модель их не требует.
        return new E5SmallV2QuantizedEmbeddingModel();
    }

    // InMemoryEmbeddingStore удален, так как теперь используется PgvectorEmbeddingStore
    // в VectorStoreConfiguration.

    /**
     * Выбирает активный LlmClient (Gemini или Claude) в зависимости от настройки ai.provider.
     */
    @Bean
    @Primary
    public LlmClient llmClient(
            @Value("${ai.provider:claude}") String provider,
            ClaudeClient claudeClient,
            GeminiClient geminiClient) {
        if ("gemini".equalsIgnoreCase(provider)) {
            return geminiClient;
        }
        return claudeClient;
    }
}
