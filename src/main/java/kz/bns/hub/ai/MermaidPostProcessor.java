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

        // 1b. Авто-замена запрещенных спецсимволов внутри блока mermaid:
        code = code.replace("«", "'")
                .replace("»", "'")
                .replace("→", "->")
                .replace("&", " и ");

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

        // 6. Фикс реальных \n внутри ["..."] и {"..."} — заменяем на &lt;br/&gt;.
        code = replaceNewlinesInQuotedNodes(code);

        // 6b. Заменяем любые оставшиеся теги <br> или <br/> на их безопасную HTML-сущность &lt;br/&gt;.
        code = code.replaceAll("(?i)<br\\s*/?>", "&lt;br/&gt;");

        // 7. Валидатор кавычек (Парсер-гард)
        String[] lines = code.split("\n");
        for (int j = 0; j < lines.length; j++) {
            String line = lines[j];
            String indent = "";
            int k = 0;
            while (k < line.length() && Character.isWhitespace(line.charAt(k))) {
                indent += line.charAt(k);
                k++;
            }
            String trimmedLine = line.trim();
            
            int lastBracketQuote = trimmedLine.lastIndexOf("[\"");
            int lastBraceQuote = trimmedLine.lastIndexOf("{\"");
            int lastParenQuote = trimmedLine.lastIndexOf("(\"");
            
            int max = Math.max(lastBracketQuote, Math.max(lastBraceQuote, lastParenQuote));
            
            if (max != -1) {
                if (max == lastBracketQuote) {
                    String sub = trimmedLine.substring(lastBracketQuote);
                    if (!sub.contains("\"]")) {
                        String inner = trimmedLine.substring(lastBracketQuote + 2);
                        if (inner.endsWith("\"")) inner = inner.substring(0, inner.length() - 1);
                        inner = inner.replace("\"", "'");
                        trimmedLine = trimmedLine.substring(0, lastBracketQuote + 2) + inner + "\"]";
                    }
                } else if (max == lastBraceQuote) {
                    String sub = trimmedLine.substring(lastBraceQuote);
                    if (!sub.contains("\"}")) {
                        String inner = trimmedLine.substring(lastBraceQuote + 2);
                        if (inner.endsWith("\"")) inner = inner.substring(0, inner.length() - 1);
                        inner = inner.replace("\"", "'");
                        trimmedLine = trimmedLine.substring(0, lastBraceQuote + 2) + inner + "\"}";
                    }
                } else if (max == lastParenQuote) {
                    String sub = trimmedLine.substring(lastParenQuote);
                    if (!sub.contains("\")")) {
                        String inner = trimmedLine.substring(lastParenQuote + 2);
                        if (inner.endsWith("\"")) inner = inner.substring(0, inner.length() - 1);
                        inner = inner.replace("\"", "'");
                        trimmedLine = trimmedLine.substring(0, lastParenQuote + 2) + inner + "\")";
                    }
                }
            }
            lines[j] = indent + trimmedLine;
        }
        code = String.join("\n", lines);

        // 8. Схлопываем лишние пустые строки.
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
                    inside = inside.replace("\n", "&lt;br/&gt;").replace("\r", "");
                    inside = inside.replace("\"", "'");
                    inside = inside.replace("<", "&lt;").replace(">", "&gt;");
                    result.append("[\"").append(inside).append("\"]");
                    i = end + 2;
                    continue;
                }
            }
            if (i + 1 < code.length() && code.charAt(i) == '{' && code.charAt(i + 1) == '"') {
                int end = code.indexOf("\"}", i);
                if (end != -1) {
                    String inside = code.substring(i + 2, end);
                    inside = inside.replace("\n", "&lt;br/&gt;").replace("\r", "");
                    inside = inside.replace("\"", "'");
                    inside = inside.replace("<", "&lt;").replace(">", "&gt;");
                    result.append("{\"").append(inside).append("\"}");
                    i = end + 2;
                    continue;
                }
            }
            if (i + 1 < code.length() && code.charAt(i) == '(' && code.charAt(i + 1) == '"') {
                int end = code.indexOf("\")", i);
                if (end != -1) {
                    String inside = code.substring(i + 2, end);
                    inside = inside.replace("\n", "&lt;br/&gt;").replace("\r", "");
                    inside = inside.replace("\"", "'");
                    inside = inside.replace("<", "&lt;").replace(">", "&gt;");
                    result.append("(\"").append(inside).append("\")");
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