package kz.bns.hub.ai;

import java.util.List;
import java.util.Map;

/**
 * Единый контракт для интеграции с языковыми моделями.
 */
public interface LlmClient {
    /**
     * Генерирует текстовый ответ на основе системных инструкций, вопроса и истории сообщений.
     *
     * @param systemPrompt Системная инструкция (системный промпт)
     * @param userPrompt   Пользовательский запрос (включающий контекст из RAG)
     * @param history      История сообщений (чередующиеся роли user/assistant)
     * @return Текст сгенерированного ответа
     */
    String generateAnswer(String systemPrompt, String userPrompt, List<Map<String, String>> history);
}
