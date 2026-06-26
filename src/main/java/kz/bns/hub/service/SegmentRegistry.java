package kz.bns.hub.service;

import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Хранит все проиндексированные фрагменты в исходном виде (без эмбеддинг-префиксов),
 * чтобы {@link RetrievalService} мог сделать keyword-фолбэк, когда векторный поиск
 * ничего не вернул (например, модель плохо «поняла» формулировку вопроса на рус/каз).
 *
 * Заполняется один раз при старте в {@code DocumentIndexer}.
 */
@Component
public class SegmentRegistry {

    private final List<TextSegment> segments = new CopyOnWriteArrayList<>();

    public void setSegments(List<TextSegment> all) {
        segments.clear();
        segments.addAll(all);
    }

    public List<TextSegment> all() {
        return segments;
    }
}
