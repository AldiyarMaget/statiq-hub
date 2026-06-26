# StatKnowledgeHub — AI бэкенд для анализа документов БНС

## Структура проекта

```
stat-knowledge-hub/
├── documents/                          ← СЮДА кладёшь документы
│   ├── postanovlenie_001.pdf
│   ├── instruktsiya_oked.docx
│   └── ...
├── pom.xml
└── src/main/java/kz/bns/hub/
    ├── StatKnowledgeHubApplication.java
    ├── ai/
    │   └── AiConfig.java               ← RAG + Claude API
    ├── controller/
    │   └── ChatController.java         ← POST /api/chat
    ├── indexer/
    │   └── DocumentIndexer.java        ← читает папку documents/
    └── service/
        └── DocumentStatsService.java   ← счётчики документов
```

## Запуск

### 1. Получи API ключ Claude
https://console.anthropic.com → API Keys → Create Key

### 2. Положи документы в папку documents/
```bash
mkdir documents
# скопируй PDF, DOCX, TXT постановлений сюда
```

### 3. Запусти сервис

**Windows:**
```cmd
set CLAUDE_API_KEY=sk-ant-...
mvn spring-boot:run
```

**Linux/Mac:**
```bash
export CLAUDE_API_KEY=sk-ant-...
mvn spring-boot:run
```

**IntelliJ IDEA:**
Run → Edit Configurations → Environment variables → `CLAUDE_API_KEY=sk-ant-...`

### 4. Проверь что работает
```
http://localhost:8090/api/health   → OK
http://localhost:8090/api/status   → {"documentsLoaded": 5, "segmentsIndexed": 120}
```

## API

### POST /api/chat
```json
Запрос:
{
  "question": "Что говорит постановление о сроках сдачи отчётности?"
}

Ответ:
{
  "answer": "Согласно пункту 4.2 постановления...\n\nИсточник: postanovlenie_001.pdf, п.4.2"
}
```

## Поддерживаемые форматы документов
- PDF (.pdf)
- Word (.docx, .doc)
- Текст (.txt, .md)
- Код (.java, .sql, .xml, .yaml, .json)

## Требования
- Java 17+
- Maven 3.8+
- Интернет (для Claude API)
