# Docker окружение

## Роль Docker-контура

Папка `C:\Users\achumachenko\dev-projects\GraduationWork\docker` содержит контейнеры, без которых Java/Python система не работает полноценно:

- XWiki как источник базы знаний;
- PostgreSQL для самой XWiki;
- PostgreSQL + pgvector для индекса Java-сервиса;
- Ollama для LLM и embedding моделей.

На момент анализа Docker daemon был недоступен: `docker ps` не смог подключиться к `dockerDesktopLinuxEngine`. Поэтому ниже описана конфигурация по compose-файлам, а не фактическое состояние запущенных контейнеров.

## Контур XWiki

Основной верхнеуровневый compose:

`C:\Users\achumachenko\dev-projects\GraduationWork\docker\xwiki-app-kb-container\docker-compose.yml`

Сервисы:

- `xwiki_postgres`
  - image: `postgres:15`
  - host port: `5432 -> 5432`
  - db: `knowledge_base`
  - user: `kb_admin`
  - password: `kb_admin_pass`
  - volume: `db_data`
- `xwiki_app`
  - image: `xwiki:14.10-postgres-tomcat`
  - host port: `8060 -> 8080`
  - env DB host внутри docker network: `xwiki_postgres`
  - volume: `xwiki_data`

Команды:

```powershell
cd C:\Users\achumachenko\dev-projects\GraduationWork\docker\xwiki-app-kb-container
docker compose up -d
docker compose logs -f xwiki
docker compose down
```

URL XWiki для Java при этом:

```properties
XWIKI_BASE_URL=http://localhost:8060
```

Важно: в `xwiki-app-kb-container\xwiki-docker-master\16.4\postgres-tomcat\docker-compose.yml` есть альтернативный compose XWiki 16.4.6 с портом `8080 -> 8080` и PostgreSQL 17. В старых evaluation логах XWiki проверялся на `http://localhost:8080`, значит раньше мог использоваться именно этот контур или другой запуск XWiki.

## Контур векторной БД

Compose:

`C:\Users\achumachenko\dev-projects\GraduationWork\docker\xwiki-vector-db\docker-compose.yml`

Сервис:

- container: `xwiki-vector-db`
- image: `pgvector/pgvector:pg17`
- host port: `6432 -> 5432`
- db: `xwiki_vector_db`
- user: `user`
- password: `password`
- volume: `pgdata`

Команды:

```powershell
cd C:\Users\achumachenko\dev-projects\GraduationWork\docker\xwiki-vector-db
docker compose up -d
docker compose logs -f postgres
docker compose down
```

Env для Java:

```properties
DB_HOST=localhost
DB_PORT=6432
DB_NAME=xwiki_vector_db
DB_SCHEMA=public
DB_USERNAME=user
DB_PASSWORD=password
```

Проверка вручную:

```powershell
docker exec -it xwiki-vector-db psql -U user -d xwiki_vector_db
\dx
\dt
```

В базе должно быть расширение `vector`; Liquibase также выполняет `create extension if not exists vector`.

## Контур Ollama

Compose:

`C:\Users\achumachenko\dev-projects\GraduationWork\docker\ollama-container\docker-compose.yml`

Сервис:

- container: `ollama`
- image: `ollama/ollama`
- host port: `11434 -> 11434`
- volume: `ollama_data`
- restart: `no`

Команды:

```powershell
cd C:\Users\achumachenko\dev-projects\GraduationWork\docker\ollama-container
docker compose up -d
docker compose logs -f ollama
```

Модели, нужные Java-конфигурации:

```powershell
docker exec -it ollama ollama pull qllama/multilingual-e5-base
docker exec -it ollama ollama pull bge-m3
docker exec -it ollama ollama pull ilyagusev/saiga_llama3
docker exec -it ollama ollama list
```

Env для Java:

```properties
AI_OLLAMA_BASE_URL=http://localhost:11434
AI_EMBEDDING_ENABLED=true
```

## Рекомендуемый порядок запуска системы

1. Запустить Docker Desktop.
2. Поднять XWiki и дождаться доступности UI.
3. Поднять `xwiki-vector-db`.
4. Поднять `ollama` и убедиться, что нужные модели скачаны.
5. Запустить Java-сервис с корректными env.
6. Вызвать `GET /api/xwiki/processAll` для первичной индексации или после изменений чанкинга/моделей.
7. Проверить поиск через `GET /api/xwiki/search?query=...&limit=5`.
8. Запустить Python evaluation.

## Частые несостыковки

- Порт XWiki: верхнеуровневый compose дает `8060`, evaluation-скрипты в логах использовали `8080`.
- Порт PostgreSQL: XWiki DB занимает `5432`, vector DB проброшена на `6432`, чтобы не конфликтовать.
- Если Java не стартует на Liquibase/JDBC, сначала проверить `xwiki-vector-db`, а не XWiki DB.
- Если embedding падает, проверить доступность Ollama и наличие моделей внутри volume `ollama_data`.
- Если `/api/xwiki/processAll` отработал слишком быстро и ничего не проиндексировал, проверить `XWIKI_BASE_URL`, credentials и фильтр системных пространств.

