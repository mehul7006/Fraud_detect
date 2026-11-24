-- 10.1: brand registry for the look-alike website engine. Lists are '|' separated (small, read once into memory).
CREATE TABLE brands (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(120) NOT NULL UNIQUE,
    category         VARCHAR(30)  NOT NULL,
    official_domains TEXT         NOT NULL,
    keywords         TEXT         NOT NULL,
    favicon_hash     VARCHAR(64),             -- 10.8: hash of the real site's favicon (filled later)
    source           VARCHAR(60)  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
