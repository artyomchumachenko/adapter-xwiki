-- Создаем таблицу pages
create table if not exists pages (
    id serial primary key,                       -- уникальный идентификатор страницы
    version int not null default 1,              -- версия записи
    sys_create_date timestamp default now(),     -- дата создания записи
    sys_update_date timestamp default now(),     -- дата последнего обновления записи
    xwiki_id varchar(300) not null,              -- идентификатор в XWiki
    xwiki_version varchar(10) not null,         -- версия документа в XWiki
    xwiki_absolute_url text                      -- абсолютный URL документа в XWiki
    );

-- Создаем таблицу chunks
create table if not exists chunks (
    id serial primary key,                       -- уникальный идентификатор фрагмента
    version int not null default 1,              -- версия записи
    sys_create_date timestamp default now(),     -- дата создания записи
    sys_update_date timestamp default now(),     -- дата последнего обновления записи
    page_id int not null,                        -- внешний ключ к pages (странице)
    chunk_index int not null,                    -- порядковый номер (индекс) фрагмента в документе
    text_snippet text,                           -- текстовый фрагмент (chunk)
    constraint fk_chunks_pages foreign key (page_id) references pages (id)
    );

-- Создаем таблицу embeddings
create table if not exists embeddings (
    id serial primary key,                       -- уникальный идентификатор эмбеддинга
    version int not null default 1,              -- версия записи
    sys_create_date timestamp default now(),     -- дата создания записи
    sys_update_date timestamp default now(),     -- дата последнего обновления записи
    chunk_id int not null,                       -- внешний ключ к chunks (фрагменту)
    constraint fk_embeddings_chunks foreign key (chunk_id) references chunks (id)
    );
