# Java-сервис XWiki adapter

## Роль сервиса

`ru.cbgr.adapter:xwiki` - Spring Boot сервис, который индексирует XWiki в pgvector и предоставляет REST API семантического поиска. Это основная production-часть проекта.

## Входные точки

- `src/main/java/ru/cbgr/adapter/xwiki/XwikiApplication.java` - запуск Spring Boot.
- `XWikiController`:
  - `GET /api/xwiki/processAll` - запускает обход XWiki и переиндексацию страниц.
  - `GET /api/xwiki/search?query=...&limit=5` - возвращает список `SearchResultDto`.
  - `GET /api/xwiki/search/answer?query=...` - берет лучший найденный chunk и генерирует ответ через LLM.
- `LLMController` и `EmbeddingController` - простые model endpoints для прямой проверки chat/embedding клиентов.

## Основной pipeline индексации

1. `XWikiService.processAllSpacesAndPages()` получает пространства через `XWikiClient.getSpaces()`.
2. Системные пространства фильтруются по префиксам `xwiki:Help`, `xwiki:Main`, `xwiki:Sandbox`, `xwiki:XWiki`.
3. `XWikiSpaceWalker.walk()` рекурсивно обходит пространства и страницы по REST links.
4. `PageProcessor.processPage()`:
   - пропускает страницу, если версия уже есть в таблице `pages`;
   - сохраняет/обновляет страницу через `PageUpsertService`;
   - загружает контент через `PageContentService`;
   - при обновлении удаляет старые chunks/embeddings;
   - сохраняет новые chunks и embeddings через `EmbeddingIngestionService`.
5. `PageContentService` получает `PageDetails.content`, нормализует HTML/wiki content и передает его в `CombinedContentChunker`.
6. `CombinedContentChunker` разделяет обычный текст и wiki-таблицы, затем применяет hard-limit, чтобы не превышать контекст embedding-модели.
7. `EmbeddingIngestionService` для каждого чанка строит embeddings по всем моделям из `models.embedding-models` и сохраняет в таблицу `embeddings`.

## Поиск

Основной controller вызывает `XWikiService.search()`, который сейчас делегирует в `EmbeddingSearchService`. В проекте также есть `EnhancedSemanticSearchService` с более сложной логикой:

- строит embedding запроса по нескольким моделям;
- берет лучший chunk на страницу через SQL `ROW_NUMBER() OVER (PARTITION BY p.id ...)`;
- фильтрует кандидатов по margin/percentile;
- переводит distance в similarity с учетом `vector_l2_ops` или `vector_cosine_ops`;
- сливает сигналы моделей через softmax;
- дедуплицирует результаты по странице.

При изменениях ранжирования сначала проверить, какой сервис реально используется в `EmbeddingSearchService`, и только потом менять алгоритм.

## Данные и схема

Liquibase changelog: `src/main/resources/db/changelog/db.changelog-master.xml`, включает SQL из `sql`.

Основные таблицы:

- `pages`: XWiki page id, version, absolute URL, title.
- `chunks`: chunk index и нормализованный `text_snippet`, связан с `pages`.
- `embeddings`: одна строка на chunk, embedding-колонки добавляются динамически по моделям.
- `document_embeddings`: старый/параллельный вариант хранения embedding размерности 768, судя по текущему коду не является главным путем ingestion.

`EmbeddingColumnsInitializer` должен добавлять колонки вида `<model>_embedding`, например:

- `qllama_multilingual_e5_base_embedding vector(768)`;
- `bge_m3_embedding vector(1024)`.

Важно: класс `EmbeddingColumnsInitializer` не помечен `@Component` в просмотренном коде. Если колонки не создаются автоматически, проверить регистрацию этого runner в конфигурации.

## Конфигурация

`src/main/resources/application.yml` полностью завязан на env-переменные:

```properties
SERVER_PORT=8081
DB_HOST=localhost
DB_PORT=6432
DB_NAME=xwiki_vector_db
DB_SCHEMA=public
DB_USERNAME=user
DB_PASSWORD=password
AI_OLLAMA_BASE_URL=http://localhost:11434
AI_EMBEDDING_ENABLED=true
XWIKI_BASE_URL=http://localhost:8060
XWIKI_USERNAME=<логин XWiki>
XWIKI_PASSWORD=<пароль XWiki>
XWIKI_MODIFICATIONS_ENABLED=false
```

Если используется compose из `xwiki-docker-master/16.4/postgres-tomcat`, XWiki будет на `http://localhost:8080`, а не `8060`.

Модели из `application.yml`:

- embedding: `qllama/multilingual-e5-base`, размер 768, `vector_l2_ops`;
- embedding: `bge-m3`, размер 1024, `vector_cosine_ops`;
- LLM: `ilyagusev/saiga_llama3`.

## Риски и технический долг

- В `AGENTS.md` есть устаревшие правила Maven для другого пути `c:\Projects\ZAGS-2\...`; для текущего проекта их нельзя применять буквально без уточнения.
- В `pom.xml` есть mojibake в одном комментарии, функционально не влияет.
- `GET /api/xwiki/processAll` запускает тяжелую операцию через GET; для production лучше POST/job endpoint.
- Нет явной авторизации на controller endpoints.
- Индексация синхронная; при большой XWiki базе запрос может быть долгим.
- При ошибке embedding одной модели сервис логирует ошибку и продолжает, что удобно для устойчивости, но может приводить к неполному индексу.
- Нужно внимательно различать старую таблицу `document_embeddings` и актуальные `pages/chunks/embeddings`.

## Как работать с изменениями

- Для изменений качества поиска сначала читать `EmbeddingSearchService`, `EnhancedSemanticSearchService`, `EmbeddingIngestionService`, `ContentNormalizationService`, `CombinedContentChunker`.
- Для изменений интеграции с XWiki читать `XWikiClient`, DTO в `dto/xwiki`, `XWikiSpaceWalker`, `PageProcessor`.
- Для изменений схемы БД добавлять SQL changelog и учитывать pgvector indexes.
- После изменений ранжирования прогонять Python evaluation из `XWikiTools/testing` и обновлять `docs/tech/40-business-and-quality-context.md`.

