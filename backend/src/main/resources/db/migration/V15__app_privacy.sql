-- 13.1 app reputation v2: category (what the app is for) and trackers known to be inside it.
ALTER TABLE app_reputation ADD COLUMN category VARCHAR(30);
ALTER TABLE app_reputation ADD COLUMN trackers VARCHAR(1000);

-- 13.13 official app registry: which packages really belong to a brand (+ signing certificate when verified).
CREATE TABLE official_apps (
    id           BIGSERIAL PRIMARY KEY,
    brand        VARCHAR(120) NOT NULL,
    category     VARCHAR(30)  NOT NULL,
    package_name VARCHAR(200) NOT NULL UNIQUE,
    play_title   VARCHAR(200),
    cert_sha256  VARCHAR(400),
    source       VARCHAR(60)  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX official_apps_brand ON official_apps (brand);

-- 13.6 domain categories for the phone (ads / analytics / tracker / malware), as a change log for delta sync.
CREATE TABLE domain_category_changes (
    version    BIGSERIAL PRIMARY KEY,
    domain     VARCHAR(253) NOT NULL,
    category   VARCHAR(12)  NOT NULL CHECK (category IN ('ads', 'analytics', 'tracker', 'malware', 'none')),
    op         VARCHAR(6)   NOT NULL CHECK (op IN ('ADD', 'REMOVE')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX domain_category_changes_domain ON domain_category_changes (domain);
