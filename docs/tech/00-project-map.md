# Карта проекта XWiki Search

## Назначение

Проект решает задачу семантического поиска по базе знаний XWiki. В бизнес-смысле это надстройка над корпоративной wiki: пользователь задает вопрос или поисковую фразу, а система возвращает релевантные страницы и фрагменты текста лучше, чем стандартный полнотекстовый поиск XWiki/Solr.

Основной сценарий:

1. XWiki хранит статьи базы знаний.
2. Java-сервис обходит XWiki REST API, забирает страницы, чистит контент, режет его на чанки.
3. Для чанков строятся embedding-векторы через Ollama.
4. Векторы сохраняются в PostgreSQL с расширением pgvector.
5. REST endpoint `/api/xwiki/search` принимает запрос, строит embedding запроса и возвращает top-N страниц.
6. Python-проект используется для загрузки/выгрузки контента, генерации датасетов и измерения качества поиска.

## Основные репозитории и папки

- `C:\Users\achumachenko\dev-projects\GraduationWork\java\xwiki` - основной Java/Spring Boot адаптер XWiki.
- `C:\Users\achumachenko\PycharmProjects\XWikiTools` - вспомогательные Python-инструменты для Habr/XWiki, rerank и evaluation.
- `C:\Users\achumachenko\dev-projects\GraduationWork\docker` - docker-compose окружение: XWiki, PostgreSQL, pgvector, Ollama.
- `docs/tech` - техническая память агента по проекту. При существенных изменениях архитектуры, запуска, контейнеров или метрик обновлять эти файлы.

## Технологический стек

- Java 21, Spring Boot 3.3.0, Maven.
- Spring AI + Ollama для LLM и embeddings.
- PostgreSQL + pgvector для векторного поиска.
- Liquibase для первичной схемы БД.
- XWiki REST API как источник страниц.
- Python 3, requests, BeautifulSoup, FastAPI, transformers/torch для вспомогательных задач и rerank.
- Docker Compose для локального окружения.

## Ключевые runtime-сервисы

- XWiki UI/API: обычно `http://localhost:8060` по верхнеуровневому compose или `http://localhost:8080` по compose из `xwiki-docker-master`.
- Java search service: ожидаемый порт из переменной `SERVER_PORT`, в оценочных скриптах использовался `http://localhost:8081`.
- Vector DB: `localhost:6432 -> container:5432`, база `xwiki_vector_db`, пользователь `user`, пароль `password`.
- Ollama: `http://localhost:11434`.
- Python reranker: отдельный FastAPI сервис, если запускается вручную, endpoint `/rerank`.

## Главные связи между частями

- Java `application.yml` требует env-переменные для БД, XWiki, Ollama и порта сервиса.
- Docker `xwiki-vector-db` должен быть поднят до запуска Java-сервиса, иначе Liquibase/JDBC не подключатся.
- Docker `ollama` должен содержать модели, указанные в `models.embedding-models` и `models.llm-model`.
- Python `testing/evaluate_search.py` проверяет Java endpoint `/api/xwiki/search`.
- Python `testing/evaluate_xwiki_solr.py` проверяет стандартный XWiki Solr REST search как baseline.
- Python `habr/habr_to_xwiki.py` помогает наполнить XWiki статьями, а `testing/xwiki_queries_dataset.py` строит датасет запросов.

## Текущая рабочая гипотеза архитектуры

Система ориентирована на дипломную/исследовательскую демонстрацию улучшения поиска по XWiki. Главная ценность - не просто интеграция с XWiki, а измеримый выигрыш качества поиска относительно встроенного XWiki Solr. Поэтому любые изменения в ранжировании, чанкинге, нормализации или моделях нужно сопровождать прогоном evaluation-скриптов и фиксацией метрик.

