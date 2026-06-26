package kz.bns.hub.tools;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Одноразовая CLI-утилита: вытаскивает скриншоты из .docx (Apache POI) в папку
 * <система>/images/img_N.<ext> и собирает <система>/USER_GUIDE_<SYS>.md, где текст
 * параграфов перемежается ссылками на скриншоты ![описание](/<система>/images/img_N.png).
 *
 * Запуск (один раз, после правок):
 *   java -cp "target/classes:<classpath POI>" kz.bns.hub.tools.DocxImageExtractor \
 *        documents/bdap bdap BDAP
 *   java -cp ... kz.bns.hub.tools.DocxImageExtractor documents/meta meta META
 *
 * Аргументы: <папка с docx> <url-сегмент для картинок> <ярлык системы для заголовка>.
 * Если docx в папке нет — выходит молча (для meta, где только .md/.json).
 */
public class DocxImageExtractor {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Использование: DocxImageExtractor <папка> <url-сегмент> [ярлык]");
            return;
        }
        Path dir = Path.of(args[0]);
        String urlSegment = args[1];               // напр. "bdap" → /bdap/images/...
        String label = args.length > 2 ? args[2] : urlSegment.toUpperCase();

        List<Path> docs;
        try (var paths = Files.list(dir)) {
            docs = paths.filter(p -> p.toString().toLowerCase().endsWith(".docx"))
                        .filter(p -> !p.getFileName().toString().startsWith("~$")) // временные файлы Word
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
        }
        if (docs.isEmpty()) {
            System.out.println("[" + label + "] .docx не найдены в " + dir.toAbsolutePath() + " — пропускаю.");
            return;
        }

        Path imagesDir = dir.resolve("images");
        Files.createDirectories(imagesDir);

        StringBuilder md = new StringBuilder();
        md.append("# Руководство пользователя ").append(label).append("\n\n");
        md.append("> Автоматически собрано из .docx (").append(docs.size())
          .append(" шт.). Скриншоты лежат в `").append(urlSegment).append("/images/`.\n\n");

        int[] imgCounter = {0}; // сквозная нумерация img_N по всем docx системы

        for (Path doc : docs) {
            String docName = stripExt(doc.getFileName().toString());
            md.append("\n---\n\n## ").append(docName).append("\n\n");
            try (InputStream in = Files.newInputStream(doc);
                 XWPFDocument xwpf = new XWPFDocument(in)) {
                renderBody(xwpf, imagesDir, urlSegment, md, imgCounter);
            } catch (Exception e) {
                System.err.println("[" + label + "] не смог разобрать " + docName + ": " + e.getMessage());
            }
        }

        Path guide = dir.resolve("USER_GUIDE_" + label + ".md");
        Files.writeString(guide, md.toString(), StandardCharsets.UTF_8);
        System.out.println("[" + label + "] готово: " + imgCounter[0] + " скриншот(ов) → "
                + imagesDir + ", руководство → " + guide);
    }

    /** Идём по телу документа по порядку: параграфы (с картинками внутри) и таблицы. */
    private static void renderBody(XWPFDocument xwpf, Path imagesDir, String urlSegment,
                                   StringBuilder md, int[] imgCounter) throws IOException {
        for (IBodyElement element : xwpf.getBodyElements()) {
            if (element instanceof XWPFParagraph p) {
                renderParagraph(p, imagesDir, urlSegment, md, imgCounter);
            } else if (element instanceof XWPFTable t) {
                renderTable(t, md);
            }
        }
    }

    private static void renderParagraph(XWPFParagraph p, Path imagesDir, String urlSegment,
                                        StringBuilder md, int[] imgCounter) throws IOException {
        String text = p.getText() == null ? "" : p.getText().trim();
        if (!text.isEmpty()) {
            // Заголовки docx → markdown-подзаголовки, остальное — обычный абзац.
            String style = p.getStyle() == null ? "" : p.getStyle().toLowerCase();
            if (style.contains("heading") || style.contains("title") || style.startsWith("заг")) {
                md.append("### ").append(text).append("\n\n");
            } else {
                md.append(text).append("\n\n");
            }
        }

        // Картинки внутри параграфа — извлекаем по порядку и сразу ставим ссылку.
        for (XWPFRun run : p.getRuns()) {
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                writePicture(pic, imagesDir, urlSegment, md, imgCounter, text);
            }
        }
    }

    private static void writePicture(XWPFPicture pic, Path imagesDir, String urlSegment,
                                     StringBuilder md, int[] imgCounter, String nearbyText) throws IOException {
        XWPFPictureData data = pic.getPictureData();
        if (data == null || data.getData() == null || data.getData().length == 0) {
            return;
        }
        String ext = normalizeExt(data.suggestFileExtension());
        int n = ++imgCounter[0];
        String fileName = "img_" + n + "." + ext;
        Files.write(imagesDir.resolve(fileName), data.getData());

        // Описание: подпись из docx, иначе ближайший текст, иначе номер.
        String descr = firstNonBlank(pic.getDescription(), nearbyText, "Скриншот " + n);
        descr = descr.replace("\n", " ").replace("]", " ").replace("[", " ").trim();
        if (descr.length() > 120) descr = descr.substring(0, 120).trim();

        md.append("![").append(descr).append("](/")
          .append(urlSegment).append("/images/").append(fileName).append(")\n\n");
        System.out.println("  + " + fileName + " — " + descr);
    }

    private static void renderTable(XWPFTable table, StringBuilder md) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return;
        for (int r = 0; r < rows.size(); r++) {
            List<XWPFTableCell> cells = rows.get(r).getTableCells();
            StringBuilder line = new StringBuilder("|");
            for (XWPFTableCell cell : cells) {
                String cellText = cell.getText() == null ? "" : cell.getText().replace("\n", " ").replace("|", "\\|").trim();
                line.append(' ').append(cellText).append(" |");
            }
            md.append(line).append("\n");
            if (r == 0) { // строка-разделитель markdown-таблицы после шапки
                StringBuilder sep = new StringBuilder("|");
                for (int c = 0; c < cells.size(); c++) sep.append(" --- |");
                md.append(sep).append("\n");
            }
        }
        md.append("\n");
    }

    private static String normalizeExt(String ext) {
        if (ext == null || ext.isBlank()) return "png";
        ext = ext.toLowerCase();
        if (ext.equals("jpeg")) return "jpg";
        return ext;
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }
}
