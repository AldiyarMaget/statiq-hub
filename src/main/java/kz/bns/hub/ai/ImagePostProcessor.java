package kz.bns.hub.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Детерминированно вставляет в ответ ТОЛЬКО ту картинку, которая реально по теме.
 *
 * История:
 *  - Раньше модель нестабильно вставляла картинки → сделали авто-вставку.
 *  - Но первая версия была слишком "жадной": достаточно было ОДНОГО совпавшего
 *    слова, и в ответ попадал лишний скриншот (например, меню "Модули" цеплялось
 *    за слово "интеграция"). Теперь правила СТРОГИЕ.
 *
 * Когда картинка вставляется (должны выполниться ОБА условия):
 *   1) её подпись пересекается с ВОПРОСОМ (хотя бы 1 значимое слово), И
 *   2) её подпись хорошо пересекается с ТЕКСТОМ ОТВЕТА — минимум 2 совпавших
 *      слова ИЛИ не меньше половины слов подписи.
 * Плюс отсекаются слишком короткие/общие подписи (меню, "интерфейс" и т.п.).
 *
 * Идея: если модель в самом ответе говорит про то, что на картинке — картинка
 * по делу. Если совпало одно случайное слово — это не повод тащить скриншот.
 */
@Slf4j
@Component
public class ImagePostProcessor {

    // ![описание](/что-то/images/файл.png)
    private static final Pattern IMG = Pattern.compile(
            "!\\[([^\\]]*)]\\((/[^)\\s]*?/images/[^)\\s]+)\\)");

    // Подписи-"меню"/общие экраны, которые почти никогда не являются ответом по сути.
    private static final Set<String> MENU_WORDS = Set.of(
            "меню", "модули", "интерфейс", "вкладка", "вкладки", "кнопка", "кнопки",
            "окно", "панель", "раздел", "разделы");

    public String ensureImages(String answer, String context, String question) {
        if (answer == null) answer = "";
        if (context == null || context.isBlank()) return answer;

        // 1) Все уникальные картинки из контекста: путь -> подпись.
        Map<String, String> contextImages = new LinkedHashMap<>();
        Matcher cm = IMG.matcher(context);
        while (cm.find()) {
            contextImages.putIfAbsent(cm.group(2).trim(), cm.group(1).trim());
        }
        if (contextImages.isEmpty()) return answer;

        // 2) Картинки, уже вставленные моделью — повторно не добавляем.
        Set<String> alreadyInAnswer = new HashSet<>();
        Matcher am = IMG.matcher(answer);
        while (am.find()) {
            alreadyInAnswer.add(am.group(2).trim());
        }

        // 3) Отбираем строго релевантные недостающие картинки.
        List<String> toAppend = new ArrayList<>();
        for (Map.Entry<String, String> e : contextImages.entrySet()) {
            String path = e.getKey();
            String descr = e.getValue();
            if (alreadyInAnswer.contains(path)) continue;
            if (isRelevantStrict(descr, path, question, answer)) {
                toAppend.add("![" + descr + "](" + path + ")");
            }
        }

        if (toAppend.isEmpty()) return answer;

        log.info("ImagePostProcessor: добавлено {} релевантн(ая/ых) картинк(а/и)", toAppend.size());

        StringBuilder sb = new StringBuilder(answer.stripTrailing());
        sb.append("\n\n");
        for (String img : toAppend) {
            sb.append(img).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * СТРОГАЯ релевантность: картинка должна совпадать по теме И с вопросом, И с
     * ответом, при этом совпадение с ответом — заметное (>=2 слов или >=50%).
     */
    private boolean isRelevantStrict(String descr, String path, String question, String answer) {
        String imgText = descr + " " + path.replace('/', ' ').replace('_', ' ');
        Set<String> imgWords = words(imgText);

        // слишком короткая/пустая подпись — не вставляем (нечего сопоставлять)
        if (imgWords.size() < 2) return false;

        // подпись похожа на скриншот меню/интерфейса — почти всегда не по сути
        long menuHits = imgWords.stream().filter(MENU_WORDS::contains).count();
        boolean looksLikeMenu = menuHits >= 1 && imgWords.size() <= menuHits + 2;
        if (looksLikeMenu) return false;

        // совпадение с вопросом (хотя бы 1 значимое слово)
        int hitsQ = overlapCount(imgWords, words(question));
        if (hitsQ < 1) return false;

        // совпадение с ОТВЕТОМ — главный фильтр
        Set<String> ansWords = words(answer);
        int hitsA = overlapCount(imgWords, ansWords);
        double fracA = imgWords.isEmpty() ? 0.0 : (double) hitsA / imgWords.size();

        return hitsA >= 2 || fracA >= 0.5;
    }

    /** Сколько слов из a встречается в b (точно или по общему корню, ≥5 симв.). */
    private int overlapCount(Set<String> a, Set<String> b) {
        int hits = 0;
        for (String w : a) {
            if (b.contains(w)) { hits++; continue; }
            for (String x : b) {
                if (w.length() >= 5 && x.length() >= 5
                        && (x.startsWith(w) || w.startsWith(x))) {
                    hits++;
                    break;
                }
            }
        }
        return hits;
    }

    private Set<String> words(String s) {
        Set<String> set = new HashSet<>();
        if (s == null) return set;
        for (String w : s.toLowerCase().split("[^a-zа-яё0-9]+")) {
            if (w.length() > 3) set.add(w);
        }
        return set;
    }
}