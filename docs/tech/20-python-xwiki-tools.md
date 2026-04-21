# Python-проект XWikiTools

## Роль проекта

`C:\Users\achumachenko\PycharmProjects\XWikiTools` - вспомогательный набор скриптов вокруг XWiki и Java search service. Это не основной backend, а инструменты для наполнения базы знаний, экспорта страниц, reranking и измерения качества поиска.

## Структура

- `habr/` - импорт/экспорт контента между Habr и XWiki.
- `rerank/` - отдельный FastAPI сервис reranking на модели BGE.
- `testing/` - генерация датасета и evaluation скрипты.

## Habr/XWiki инструменты

`habr/habr_to_xwiki.py`:

- принимает URL статьи Habr;
- парсит статью через `requests` + `BeautifulSoup`;
- конвертирует контент в XWiki-compatible страницу;
- создает/обновляет страницу через XWiki REST API;
- поддерживает spaces/page naming, например статьи под `AI XWiki/backend/Habr_...`.

`habr/export_xwiki_page.py`:

- принимает либо `--page-url`, либо набор `--xwiki-base-url + --spaces + --page`;
- получает source content страницы через XWiki REST;
- сохраняет текст в `.txt`.

Эти скрипты полезны для подготовки корпуса знаний и отладки того, какой именно исходный контент видит Java indexer.

## Reranker service

`rerank/reranker_service.py` - FastAPI endpoint:

- модель: `BAAI/bge-reranker-v2-m3`;
- endpoint: `POST /rerank`;
- request: `query`, `candidates`, опционально `top_n`, `max_length`;
- response: отсортированные кандидаты с `index`, `text`, `score`;
- CPU-only, число torch threads уменьшается примерно вдвое.

Пример запуска из папки `rerank`:

```powershell
pip install fastapi uvicorn torch transformers pydantic
uvicorn reranker_service:app --host 0.0.0.0 --port 8090
```

На момент анализа Java-сервис не выглядит явно интегрированным с этим FastAPI endpoint. Reranker используется как экспериментальный внешний компонент; перед внедрением в основной поиск нужно добавить Java HTTP client и измерить latency/качество.

## Testing и evaluation

`testing/xwiki_queries_dataset.py`:

- генерирует CSV с поисковыми запросами по страницам XWiki;
- использует XWiki REST и Ollama LLM;
- поля датасета: `page_url`, `page_name`, `section`, `ground_truth_paragraph`, `q1`, `q2`, `q3`, `llm_model`.

Пример из README:

```powershell
cd C:\Users\achumachenko\PycharmProjects\XWikiTools\testing
python xwiki_queries_dataset.py --count 50 --root-space "AI XWiki" --ollama-url http://localhost:11434 --ollama-model ilyagusev/saiga_llama3 --out eval_dataset.csv
```

`testing/evaluate_search.py`:

- проверяет Java search service;
- вызывает `GET {base_url}/api/xwiki/search?query=...&limit=...`;
- сравнивает `pageUrl` результатов с `page_url` из CSV;
- считает positional accuracy/MRR, Hit@K, nDCG@K, linear score;
- пишет `out/*_per_query.csv`, `out/*_summary.json`, `logs/*_not_top1.log`.

Пример:

```powershell
cd C:\Users\achumachenko\PycharmProjects\XWikiTools\testing
python evaluate_search.py --csv .\eval_dataset.csv --base-url http://localhost:8081 --k 5 --sleep 0.05 --out-prefix eval_top5
```

`testing/evaluate_xwiki_solr.py`:

- baseline для стандартного поиска XWiki через REST `/query`;
- требует `--xwiki-base-url`, `--wiki`, `--username`, `--password`;
- нужен для сравнения с семантическим поиском Java-сервиса.

Пример:

```powershell
python evaluate_xwiki_solr.py --csv .\eval_dataset.csv --xwiki-base-url http://localhost:8080 --wiki xwiki --username <user> --password <password> --k 5 --sleep 0.05 --out-prefix xwiki_solr_top5 --out-dir out --log-dir logs
```

## Практические правила

- Evaluation запускать из папки `testing`, иначе относительные пути к CSV/out/logs легко ломаются.
- Не хранить новые пароли в документации и git; в старых shell logs уже встречаются credentials, их не дублировать.
- Для честного сравнения фиксировать: датасет, K, модель embeddings, включен ли rerank, дата/время прогона, число errors.
- При изменениях чанкинга или нормализации нужно переиндексировать XWiki через `/api/xwiki/processAll`, иначе метрики будут измерять старый индекс.

