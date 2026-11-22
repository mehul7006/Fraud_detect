-- 6.2: known-good / known-bad apps by package name. GOOD is never flagged, BAD is always flagged.
CREATE TABLE app_reputation (
    id           BIGSERIAL PRIMARY KEY,
    package_name VARCHAR(200) NOT NULL UNIQUE,
    label        VARCHAR(120),
    verdict      VARCHAR(10)  NOT NULL CHECK (verdict IN ('GOOD', 'BAD')),
    source       VARCHAR(60)  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 6.3: which apps each device had at the last scan, to tell "new since last scan".
CREATE TABLE device_apps (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id    VARCHAR(100) NOT NULL,
    package_name VARCHAR(200) NOT NULL,
    first_seen   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_score   INT          NOT NULL DEFAULT 0,
    last_verdict VARCHAR(12)  NOT NULL DEFAULT 'SAFE',
    UNIQUE (user_id, device_id, package_name)
);
