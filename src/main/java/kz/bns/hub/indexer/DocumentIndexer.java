    package kz.bns.hub.indexer;

    import dev.langchain4j.data.document.Document;
    import dev.langchain4j.data.document.DocumentParser;
    import dev.langchain4j.data.document.DocumentSplitter;
    import dev.langchain4j.data.document.Metadata;
    import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
    import dev.langchain4j.data.document.parser.TextDocumentParser;
    import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
    import dev.langchain4j.data.document.splitter.DocumentSplitters;
    import dev.langchain4j.data.embedding.Embedding;
    import dev.langchain4j.data.segment.TextSegment;
    import dev.langchain4j.model.embedding.EmbeddingModel;
    import dev.langchain4j.store.embedding.EmbeddingStore;
    import kz.bns.hub.service.DocumentStatsService;
    import kz.bns.hub.service.SegmentRegistry;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.boot.ApplicationArguments;
    import org.springframework.boot.ApplicationRunner;
    import org.springframework.stereotype.Component;

    import java.io.IOException;
    import java.io.InputStreamReader;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Properties;

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public class DocumentIndexer implements ApplicationRunner {

        private final EmbeddingModel embeddingModel;
        private final EmbeddingStore<TextSegment> embeddingStore;
        private final DocumentStatsService statsService;
        private final SegmentRegistry segmentRegistry;

        @Value("${app.index.passage-prefix:passage:}")
        private String passagePrefix;

        @Value("${app.docs.path:./documents}")
        private String docsPath;

        // Дефолтные значения (используются как fallback)
        @Value("${app.index.chunk-size:800}")
        private int chunkSize;

        @Value("${app.index.chunk-overlap:150}")
        private int chunkOverlap;

        private static final List<String> DOC_EXTENSIONS = List.of(
                ".pdf", ".txt", ".md", ".pptx"  // убрать .docx и .doc
        );

        private static final List<String> CODE_EXTENSIONS = List.of(
                ".java", ".xml", ".yaml", ".yml", ".sql", ".json", ".properties"
        );

        private static final List<String> IMAGE_EXTENSIONS = List.of(
                ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"
        );

        private static final String META_DIR = "meta";
        private static final String BDAP_DIR = "bdap";
        private static final String IMAGES_DIR = "images";

        // Сплиттеры для разных типов контента
        // ROLES.md — большие таблицы, нужен крупный чанк
        private static final DocumentSplitter ROLES_SPLITTER =
                DocumentSplitters.recursive(2500, 200);
        // Инструкции (USER_GUIDE, METHODOLOGY, BDAP) — средний чанк
        private static final DocumentSplitter GUIDE_SPLITTER =
                DocumentSplitters.recursive(1000, 150);
        // JSON/Swagger — маленький чанк для точного поиска эндпоинтов
        private static final DocumentSplitter JSON_SPLITTER =
                DocumentSplitters.recursive(500, 80);
        // docx файлы — крупный чанк
        private static final DocumentSplitter DOCX_SPLITTER =
                DocumentSplitters.recursive(1200, 200);

        @Override
        public void run(ApplicationArguments args) {
            // Сброс флага на случай повторного запуска индексации.
            statsService.setIndexed(false);

            Path root = Path.of(docsPath);

            if (!Files.exists(root)) {
                try {
                    Files.createDirectories(root);
                    log.warn("Папка '{}' создана. Положи документы и перезапусти.", root.toAbsolutePath());
                } catch (IOException e) {
                    log.error("Не удалось создать папку: {}", e.getMessage());
                }
                // Индекс пуст, но это валидное «готовое» состояние — иначе сервис
                // навсегда останется в статусе «гружусь».
                statsService.setIndexed(true);
                return;
            }

            log.info("Индексирую документы из: {}", root.toAbsolutePath());

            List<Document> allDocs = new ArrayList<>();
            allDocs.addAll(loadDocuments(root));
            allDocs.addAll(loadCodeFiles(root));

            if (allDocs.isEmpty()) {
                log.warn("Документы не найдены в папке: {}", root.toAbsolutePath());
                statsService.setDocumentCount(0);
                statsService.setSegmentCount(0);
                statsService.setIndexed(true);
                return;
            }

            // Нарезаем каждый документ своим сплиттером
            List<TextSegment> segments = new ArrayList<>();
            for (Document doc : allDocs) {
                String src = String.valueOf(doc.metadata().toMap().get("source"));

                // Swagger режем ПО ЭНДПОИНТАМ (каждый эндпоинт = цельный фрагмент),
                // чтобы путь и его описание не оказались в разных кусках.
                if (src.toLowerCase().endsWith(".json") && SwaggerSplitter.isSwagger(doc.text())) {
                    List<TextSegment> apiSegments = SwaggerSplitter.split(doc.text(), src);
                    if (!apiSegments.isEmpty()) {
                        segments.addAll(apiSegments);
                        log.info("Нарезан [{}]: {} фрагментов (сплиттер: SWAGGER-по-эндпоинтам)",
                                src, apiSegments.size());
                        continue; // не применяем обычную нарезку к этому файлу
                    }
                    // если разбор не удался — падаем на обычную нарезку ниже
                }

                DocumentSplitter splitter = chooseSplitter(doc);
                List<TextSegment> docSegments = splitter.split(doc);
                segments.addAll(docSegments);
                log.info("Нарезан [{}]: {} фрагментов (сплиттер: {})",
                        doc.metadata().toMap().get("source"),
                        docSegments.size(),
                        splitterName(doc));
            }

            // Скриншоты
            List<TextSegment> imageSegments = new ArrayList<>();
            imageSegments.addAll(loadImageSegments(root.resolve(META_DIR), META_DIR));
            imageSegments.addAll(loadImageSegments(root.resolve(BDAP_DIR), BDAP_DIR));
            segments.addAll(imageSegments);

            enrichSegmentMetadata(segments);

            String prefix = passagePrefix.isBlank() ? "" : passagePrefix.trim() + " ";
            List<TextSegment> toEmbed = segments.stream()
                    .map(s -> TextSegment.from(prefix + s.text(), s.metadata()))
                    .toList();

            List<Embedding> embeddings = embeddingModel.embedAll(toEmbed).content();
            embeddingStore.addAll(embeddings, segments);

            segmentRegistry.setSegments(segments);

            statsService.setDocumentCount(allDocs.size() + imageSegments.size());
            statsService.setSegmentCount(segments.size());

            log.info("Готово! Документов: {}, скриншотов: {}, фрагментов: {}",
                    allDocs.size(), imageSegments.size(), segments.size());

            // Прогрев эмбеддинг-модели: первый embed() инициализирует ONNX в память
            // (несколько секунд). Без прогрева модель «просыпается» на первом вопросе
            // пользователя — поэтому первый запрос тупит/уходит в пустой контекст,
            // даже если сервис запущен давно. Делаем холостой embed один раз при старте.
            try {
                embeddingModel.embed(prefix + "прогрев");
                log.info("Эмбеддинг-модель прогрета");
            } catch (Exception e) {
                log.warn("Не удалось прогреть эмбеддинг-модель: {}", e.getMessage());
            }

            // Индекс И модель готовы — только теперь /api/chat отвечает полноценно.
            statsService.setIndexed(true);
        }

        /**
         * Выбирает сплиттер в зависимости от типа документа.
         */
        private DocumentSplitter chooseSplitter(Document doc) {
            String source = String.valueOf(doc.metadata().toMap().get("source")).toLowerCase();

            if (source.contains("roles")) {
                return ROLES_SPLITTER;   // таблицы ролей — крупный чанк
            }
            if (source.endsWith(".json")) {
                return JSON_SPLITTER;    // swagger — мелкий чанк
            }
    //        if (source.endsWith(".docx") || source.endsWith(".doc")) {
    //            return DOCX_SPLITTER;   // word документы
    //        }
            return GUIDE_SPLITTER;       // md инструкции — средний чанк
        }

        private String splitterName(Document doc) {
            String source = String.valueOf(doc.metadata().toMap().get("source")).toLowerCase();
            if (source.contains("roles")) return "ROLES(2500/200)";
            if (source.endsWith(".json")) return "JSON(500/80)";
    //        if (source.endsWith(".docx")) return "DOCX(1200/200)";
            return "GUIDE(1000/150)";
        }

        private List<TextSegment> loadImageSegments(Path systemDir, String system) {
            Path imagesDir = systemDir.resolve(IMAGES_DIR);
            if (!Files.isDirectory(imagesDir)) {
                log.info("Папка images не найдена: {}", imagesDir);
                return List.of();
            }

            Properties captions = loadCaptions(imagesDir);
            List<TextSegment> result = new ArrayList<>();

            try (var paths = Files.list(imagesDir)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> hasExtension(p, IMAGE_EXTENSIONS))
                        .sorted()
                        .forEach(path -> {
                            String file = path.getFileName().toString();
                            String nameNoExt = file.replaceAll("\\.[^.]+$", "");
                            String caption = captions.getProperty(file,
                                    captions.getProperty(nameNoExt, "")).trim();
                            String description = caption.isBlank()
                                    ? nameNoExt.replace('_', ' ').trim()
                                    : caption;

                            String urlPath = "/" + system + "/images/" + file;
                            String markdown = "![" + description + "](" + urlPath + ")";

                            String text = "Скриншот: " + nameNoExt + ". "
                                    + "Система: " + system.toUpperCase() + ". "
                                    + "Изображение показывает: " + description + ". "
                                    + "Чтобы показать этот экран пользователю, вставь в ответ Markdown: "
                                    + markdown;

                            Metadata md = Metadata.from("source", system + "/images/" + file)
                                    .add("type", "image")
                                    .add("system", system.toUpperCase())
                                    .add("image", urlPath)
                                    .add("caption", description);

                            result.add(TextSegment.from(text, md));
                            log.info("Проиндексирован скриншот [{}]: {}", system.toUpperCase(), file);
                        });
            } catch (IOException e) {
                log.warn("Не удалось прочитать папку {}/images: {}", system, e.getMessage());
            }
            return result;
        }

        private Properties loadCaptions(Path imagesDir) {
            Properties props = new Properties();
            Path file = imagesDir.resolve("captions.properties");
            if (Files.isRegularFile(file)) {
                try (var in = Files.newInputStream(file)) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    log.info("Загружены подписи: {} шт. из {}", props.size(), file);
                } catch (IOException e) {
                    log.warn("Не удалось прочитать captions.properties: {}", e.getMessage());
                }
            }
            return props;
        }

        private boolean isInImagesDir(Path p) {
            for (Path part : p) {
                if (IMAGES_DIR.equals(part.toString())) return true;
            }
            return false;
        }

        private void enrichSegmentMetadata(List<TextSegment> segments) {
            int idx = 0;
            for (TextSegment seg : segments) {
                seg.metadata().add("chunk", String.valueOf(idx++));
            }
        }

        private List<Document> loadDocuments(Path root) {
            List<Document> result = new ArrayList<>();
            try (var paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> !isInImagesDir(p))
                        .filter(p -> hasExtension(p, DOC_EXTENSIONS))
                        .forEach(path -> {
                            try {
                                String fileName = path.toString().toLowerCase();
                                DocumentParser parser = (fileName.endsWith(".md") || fileName.endsWith(".txt"))
                                        ? new TextDocumentParser()
                                        : new ApacheTikaDocumentParser();

                                Document doc = FileSystemDocumentLoader.loadDocument(path, parser);
                                doc.metadata().add("source", path.getFileName().toString());
                                doc.metadata().add("type", "document");

                                String pathStr = path.toString();
                                if (pathStr.contains("/" + META_DIR + "/") || pathStr.contains("\\" + META_DIR + "\\")) {
                                    doc.metadata().add("system", "META");
                                } else if (pathStr.contains("/" + BDAP_DIR + "/") || pathStr.contains("\\" + BDAP_DIR + "\\")) {
                                    doc.metadata().add("system", "BDAP");
                                }

                                result.add(doc);
                                log.info("Загружен: {} ({} симв.)", path.getFileName(), doc.text().length());
                            } catch (Exception e) {
                                log.warn("Не удалось прочитать {}: {}", path.getFileName(), e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.error("Ошибка чтения папки: {}", e.getMessage());
            }
            return result;
        }

        private List<Document> loadCodeFiles(Path root) {
            List<Document> result = new ArrayList<>();
            try (var paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> !isInImagesDir(p))
                        .filter(p -> hasExtension(p, CODE_EXTENSIONS))
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path);
                                String relativePath = root.relativize(path).toString();
                                Document doc = Document.from(content,
                                        Metadata.from("source", relativePath).add("type", "source-code"));
                                result.add(doc);
                                log.info("Загружен код: {}", relativePath);
                            } catch (IOException e) {
                                log.warn("Не удалось прочитать код {}: {}", path, e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.error("Ошибка чтения кода: {}", e.getMessage());
            }
            return result;
        }

        private boolean hasExtension(Path p, List<String> extensions) {
            String name = p.toString().toLowerCase();
            return extensions.stream().anyMatch(name::endsWith);
        }
    }