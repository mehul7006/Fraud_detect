-- Phase 16 call guard. Phone numbers are NEVER stored: only SHA-256 (lower-case hex) of the canonical E.164 text
-- (see com.fraudshield.calls.PhoneNumbers). There is no column for a raw number or a caller's name.

-- 16.1 one row per reported number (hash).
CREATE TABLE number_reputation (
    number_hash  VARCHAR(64)      PRIMARY KEY,
    category     VARCHAR(20)      NOT NULL,
    report_count INT              NOT NULL DEFAULT 0,
    trust_score  DOUBLE PRECISION NOT NULL DEFAULT 0,
    suspicious   BOOLEAN          NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now()
);
-- 16.2 range lookups by the first 5 hex characters
CREATE INDEX number_reputation_prefix ON number_reputation (number_hash varchar_pattern_ops);

-- 16.5 one vote per user per number (re-reporting changes the vote, never adds one). Deleted with the account.
CREATE TABLE number_reports (
    id          BIGSERIAL        PRIMARY KEY,
    user_id     BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    number_hash VARCHAR(64)      NOT NULL,
    category    VARCHAR(20)      NOT NULL,
    weight      DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT number_reports_one_vote UNIQUE (user_id, number_hash)
);
CREATE INDEX number_reports_hash ON number_reports (number_hash);
CREATE INDEX number_reports_user_time ON number_reports (user_id, created_at);

-- 16.4 change log for the phone's signed delta (version = this table's sequence). Only SUSPICIOUS numbers are listed.
CREATE TABLE number_changes (
    version     BIGSERIAL   PRIMARY KEY,
    number_hash VARCHAR(64) NOT NULL,
    category    VARCHAR(20),
    op          VARCHAR(6)  NOT NULL CHECK (op IN ('ADD', 'REMOVE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX number_changes_hash ON number_changes (number_hash);
