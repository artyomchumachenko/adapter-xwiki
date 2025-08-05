-- Устанавливаем расширение для работы с векторами (если не установлено)
create extension if not exists vector;

-- Создаем таблицу для хранения эмбеддингов документов
create table document_embeddings (
    id serial primary key,                   -- уникальный идентификатор
    document_id varchar(300) not null,       -- id связанного документа в XWiki
    chunk_index int not null,                -- индекс фрагмента документа
    embedding vector(768) not null,          -- векторное представление (размер 768)
    text_snippet text,                       -- исходный текстовый фрагмент (chunk)
    created_at timestamp default now(),      -- дата создания записи
    updated_at timestamp default now()       -- дата последнего обновления
);

-- Создаем индекс для быстрого поиска по эмбеддингам (hnsw для Approximate Nearest Neighbors)
create index idx_embedding_hnsw on document_embeddings
    using hnsw (embedding vector_l2_ops);
