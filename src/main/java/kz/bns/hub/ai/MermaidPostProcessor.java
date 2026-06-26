package kz.bns.hub.ai;

import org.springframework.stereotype.Component;

@Component
public class MermaidPostProcessor {

    private static final String MARKER = "```mermaid";
    private static final String END_MARKER = "```";

    public String fix(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            int start = text.indexOf(MARKER, i);
            if (start == -1) {
                result.append(text.substring(i));
                break;
            }
            result.append(text, i, start);

            int codeStart = start + MARKER.length();
            if (codeStart < text.length() && text.charAt(codeStart) == '\n') codeStart++;

            int end = text.indexOf(END_MARKER, codeStart);
            if (end == -1) {
                result.append(text.substring(start));
                break;
            }

            String mermaidCode = text.substring(codeStart, end);
            result.append(MARKER).append("\n").append(fixCode(mermaidCode)).append(END_MARKER);
            i = end + END_MARKER.length();
        }
        return result.toString();
    }

    private String fixCode(String code) {
        // 1. Декодируем HTML entities (LLM иногда экранирует стрелки и кавычки).
        code = code.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");

        // 2. Убираем мусор, который модель иногда роняет ВНУТРЬ блока:
        //    вложенные ```mermaid/``` и строки-заголовки **...**.
        code = code.replace("```mermaid", "").replace("```", "");
        code = code.replaceAll("(?m)^\\s*\\*\\*[^\\n]*\\*\\*\\s*$", "");

        // 3. Перенос после направления графа, если к нему приклеен первый узел.
        //    Пример: "graph TD A[Старт]" -> "graph TD\n    A[Старт]"
        code = code.replaceAll("((?:graph|flowchart)\\s+(?:TD|TB|BT|RL|LR))\\s+(?=\\S)", "$1\n    ");

        // 4. Фикс слипшихся служебных слов.
        code = code.replace("endsubgraph", "end\nsubgraph");
        code = code.replace("]subgraph", "]\nsubgraph");
        code = code.replace("}subgraph", "}\nsubgraph");

        // 5. Главный фикс — после ] или } сразу идёт следующий узел (слипшиеся связи).
        //    Пример: }Main --> или ]Auth -->|Нет|. \\s* съедает уже существующий
        //    перенос/пробел и ставит ровно один с отступом.
        code = code.replaceAll("([\\]\\}])\\s*([A-Za-z])", "$1\n    $2");

        // 6. Фикс реальных \n внутри ["..."] — заменяем на <br/>.
        code = replaceNewlinesInQuotedNodes(code);

        // 7. Схлопываем лишние пустые строки.
        code = code.replaceAll("\\n{3,}", "\n\n").trim() + "\n";

        return code;
    }

    private String replaceNewlinesInQuotedNodes(String code) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < code.length()) {
            if (i + 1 < code.length() && code.charAt(i) == '[' && code.charAt(i + 1) == '"') {
                int end = code.indexOf("\"]", i);
                if (end != -1) {
                    String inside = code.substring(i + 2, end);
                    inside = inside.replace("\n", "<br/>");
                    result.append("[\"").append(inside).append("\"]");
                    i = end + 2;
                    continue;
                }
            }
            result.append(code.charAt(i));
            i++;
        }
        return result.toString();
    }
}