# Логика микросервиса XWiki Adapter

Документ описывает ключевую логику микросервиса и предназначен для использования ИИ агентом.

## Основные процессы

### 1) Полная обработка контента (/api/xwiki/processAll)

Цель: синхронизировать пространства и страницы XWiki, сохранить метаданные, разбить контент на части и проиндексировать эмбеддинги.

1. Запрос на обработку поступает в контроллер.
   - @XWikiController.processAllSpacesAndPages
2. Сервис получает список пространств XWiki.
   - @XWikiService.processAllSpacesAndPages
   - @XWikiClient.getSpaces
3. Из списка исключаются системные пространства.
   - @XWikiService.isBusinessSpace
4. Для каждого пространства выполняется рекурсивный обход страниц и вложенных пространств.
   - @XWikiSpaceWalker.walk
   - @XWikiLinkResolver.getHref (получение ссылки на страницы)
   - @XWikiClient.getPages
5. Каждая страница проходит обработку.
   - @PageProcessor.processPage
   - Фильтрация по версии и/или белому списку названий:
     - @PageProcessor.isSkipProcess
   - Upsert метаданных страницы:
     - @PageUpsertService.upsert
6. Контент страницы загружается и разбивается на части.
   - @PageContentService.loadAndChunkContent
   - @XWikiClient.getPageDetails
   - @ContentNormalizationService.normalizeForChunking
   - @CombinedContentChunker.chunkContent
7. Если страница уже существовала, удаляются старые части и эмбеддинги.
   - @EmbeddingIngestionService.cleanupOldData
8. Для каждой части сохраняется текст и строятся эмбеддинги по всем моделям.
   - @EmbeddingIngestionService.persistChunksAndEmbeddings
   - @LlamaAiClient.getEmbeddings
   - @VectorNormalizationService.normalizeVector
   - @EmbeddingJdbcRepository.updateEmbeddingVector

Результат: база содержит актуальные метаданные страниц, чанки текста и их эмбеддинги по всем моделям.

### 2) Поиск (/api/xwiki/search)

Цель: найти релевантные страницы по пользовательскому запросу и вернуть ссылку + сниппет.

1. Запрос на поиск поступает в контроллер.
   - @XWikiController.search
2. Выполняется семантический поиск по эмбеддингам.
   - @XWikiService.search
   - @EmbeddingSearchService.search
3. Запрос нормализуется и по каждой модели строится вектор запроса.
   - @ContentNormalizationService.normalize
   - @EmbeddingSearchService.buildQueryVectors
   - @LlamaAiClient.getEmbeddings
   - @VectorNormalizationService.normalizeVector
4. Для каждой модели извлекаются кандидаты (лучший чанк на страницу) из БД.
   - @EmbeddingSearchService.fetchAllModels
   - SQL ранжирует расстояние и оставляет 1 лучший чанк на страницу
5. Результаты разных моделей объединяются через RRF-ранжирование.
   - @EmbeddingSearchService.search (fusedScores + bestDtoByPage)
6. Итоговый список ограничивается limit и преобразуется в DTO.
   - @XWikiService.getLinkResults
   - @PageRepository.findByXwikiId

Результат: список DTO с заголовком страницы, абсолютной ссылкой и релевантным сниппетом.

## Ключевые сущности и хранилища

- Page: метаданные страницы XWiki (id, title, url, version).
  - @PageUpsertService.createMeta
- Chunk: часть нормализованного контента страницы.
  - @EmbeddingIngestionService.persistChunksAndEmbeddings
- Embedding: векторные представления частей контента по нескольким моделям.
  - @EmbeddingJdbcRepository.updateEmbeddingVector

## Внешние зависимости

- XWiki REST API:
  - Получение пространств: @XWikiClient.getSpaces
  - Получение страниц пространства: @XWikiClient.getPages
  - Получение содержимого страницы: @XWikiClient.getPageDetails
- AI сервис эмбеддингов:
  - @LlamaAiClient.getEmbeddings

## Важные правила и допущения

- Системные пространства XWiki не обрабатываются.
  - @XWikiService.isBusinessSpace
- Если версия страницы не изменилась, обработка пропускается.
  - @PageProcessor.isSkipProcess
- Для поиска используется объединение результатов разных моделей (RRF) и дедупликация по странице.
  - @EmbeddingSearchService.search
- Метаданные страницы обязательны для возврата результата поиска.
  - @XWikiService.getLinkResults

## Точки расширения

- Добавление новых моделей эмбеддингов:
  - @ModelsProperties.embeddingModels
  - @EmbeddingColumnNameResolver.toEmbeddingColumn
- Изменение логики нормализации контента:
  - @ContentNormalizationService.normalize
  - @ContentNormalizationService.normalizeForChunking
- Изменение алгоритма ранжирования поиска:
  - @EmbeddingSearchService.search
