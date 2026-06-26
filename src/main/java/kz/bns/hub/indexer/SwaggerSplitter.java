package kz.bns.hub.indexer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Нарезает Swagger/OpenAPI JSON ПО ЭНДПОИНТАМ — каждый эндпоинт = один фрагмент.
 *
 * Зачем: раньше swagger резался тупо по 500 символов, и один эндпоинт рвался
 * между кусками (путь в одном фрагменте, его описание summary — в другом).
 * Из-за этого поиск не находил эндпоинт целиком: например запрос
 * "Получить года из таблицы периодов" не находил /meta/period/years,
 * потому что путь и его summary оказывались в разных фрагментах.
 *
 * Теперь каждый фрагмент — это цельный, человекочитаемый блок про ОДИН эндпоинт:
 *   Эндпоинт: GET /meta/period/years
 *   Описание: Получить года из таблицы периодов
 *   Параметры: ...
 * Так и название, и описание, и параметры всегда вместе — поиск находит точно.
 */
@Slf4j
public class SwaggerSplitter {

    private static final Gson GSON = new Gson();

    /** true, если документ похож на swagger/openapi json. */
    public static boolean isSwagger(String text) {
        if (text == null) return false;
        String head = text.stripLeading();
        if (!head.startsWith("{")) return false;
        return head.contains("\"swagger\"") || head.contains("\"openapi\"") || head.contains("\"paths\"");
    }

    /**
     * Режет swagger по эндпоинтам. Если структура неожиданная — возвращает пустой
     * список, и вызывающий код может откатиться на обычную нарезку.
     */
    public static List<TextSegment> split(String json, String source) {
        List<TextSegment> result = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("paths") || !root.get("paths").isJsonObject()) {
                return result;
            }
            JsonObject paths = root.getAsJsonObject("paths");

            for (Map.Entry<String, JsonElement> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                if (!pathEntry.getValue().isJsonObject()) continue;
                JsonObject methods = pathEntry.getValue().getAsJsonObject();

                for (Map.Entry<String, JsonElement> methodEntry : methods.entrySet()) {
                    String method = methodEntry.getKey().toUpperCase(); // GET/POST/...
                    if (!methodEntry.getValue().isJsonObject()) continue;
                    JsonObject op = methodEntry.getValue().getAsJsonObject();

                    String summary = optString(op, "summary");
                    String description = optString(op, "description");
                    String operationId = optString(op, "operationId");
                    String tags = "";
                    if (op.has("tags") && op.get("tags").isJsonArray()) {
                        List<String> t = new ArrayList<>();
                        op.getAsJsonArray("tags").forEach(e -> t.add(e.getAsString()));
                        tags = String.join(", ", t);
                    }

                    // параметры
                    List<String> params = new ArrayList<>();
                    if (op.has("parameters") && op.get("parameters").isJsonArray()) {
                        op.getAsJsonArray("parameters").forEach(pe -> {
                            if (pe.isJsonObject()) {
                                JsonObject po = pe.getAsJsonObject();
                                String pname = optString(po, "name");
                                String pin = optString(po, "in");
                                String pdesc = optString(po, "description");
                                StringBuilder pb = new StringBuilder(pname);
                                if (!pin.isBlank()) pb.append(" (").append(pin).append(")");
                                if (!pdesc.isBlank()) pb.append(" — ").append(pdesc);
                                if (!pname.isBlank()) params.add(pb.toString());
                            }
                        });
                    }

                    // человекочитаемый текст фрагмента
                    StringBuilder text = new StringBuilder();
                    text.append("API эндпоинт: ").append(method).append(" ").append(path).append("\n");
                    if (!summary.isBlank())     text.append("Описание: ").append(summary).append("\n");
                    if (!description.isBlank() && !description.equals(summary))
                        text.append("Подробно: ").append(description).append("\n");
                    if (!tags.isBlank())        text.append("Группа: ").append(tags).append("\n");
                    if (!operationId.isBlank()) text.append("operationId: ").append(operationId).append("\n");
                    if (!params.isEmpty())      text.append("Параметры: ").append(String.join("; ", params)).append("\n");

                    Metadata md = Metadata.from("source", source)
                            .add("type", "api-endpoint")
                            .add("path", path)
                            .add("method", method);

                    result.add(TextSegment.from(text.toString().trim(), md));
                }
            }
            log.info("Swagger нарезан по эндпоинтам: {} фрагментов (по 1 на эндпоинт)", result.size());
        } catch (Exception e) {
            log.warn("Не удалось разобрать swagger по эндпоинтам ({}), откат на обычную нарезку: {}",
                    source, e.getMessage());
            return new ArrayList<>(); // пусто -> вызывающий откатится
        }
        return result;
    }

    private static String optString(JsonObject o, String key) {
        if (o != null && o.has(key) && o.get(key).isJsonPrimitive()) {
            return o.get(key).getAsString().trim();
        }
        return "";
    }
}