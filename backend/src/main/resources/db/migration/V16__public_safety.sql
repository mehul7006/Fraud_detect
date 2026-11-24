-- Phase 15 public-safety extras (backend).

-- LIVE_SCAM is a "state" threat: at most one OPEN per user (no repeated alerts during one scam call).
DROP INDEX uq_threat_open_once;
CREATE UNIQUE INDEX uq_threat_open_once
    ON threat_events (user_id, type)
    WHERE status = 'OPEN'
      AND type IN ('CALL_FORWARDING', 'SMS_FORWARDING', 'WHATSAPP_TAKEOVER', 'TELEGRAM_TAKEOVER', 'CLONE_RISK',
                   'ACCOUNT_TAKEOVER', 'LIVE_SCAM');

-- 15.1 live-scam signals: only the KIND of signal and when. No number, no app name, no text. Kept 24 h at most.
CREATE TABLE scam_signals (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    signal      VARCHAR(24) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scam_signals_user_time ON scam_signals (user_id, created_at);

-- 15.7 crowd reports. The reported value is stored only as a SHA-256 hash of its normalised form.
-- One row per (item, reporter): a second report of the same item by the same user is ignored (de-duplication).
CREATE TABLE crowd_items (
    id           BIGSERIAL PRIMARY KEY,
    item_type    VARCHAR(16)      NOT NULL,           -- LINK | UPI | CARE_NUMBER
    value_hash   VARCHAR(64)      NOT NULL,
    link_host    VARCHAR(253),                        -- LINK only: the public website host (needed to block it); never for UPI / numbers
    report_count INT              NOT NULL DEFAULT 0,
    trust_score  DOUBLE PRECISION NOT NULL DEFAULT 0,
    status       VARCHAR(16)      NOT NULL DEFAULT 'UNVERIFIED', -- UNVERIFIED | SUSPICIOUS | CONFIRMED | CLEARED
    reviewed_at  TIMESTAMPTZ,
    first_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uq_crowd_item UNIQUE (item_type, value_hash)
);
CREATE INDEX idx_crowd_items_queue ON crowd_items (status, reviewed_at);

CREATE TABLE crowd_reports (
    id          BIGSERIAL PRIMARY KEY,
    item_id     BIGINT           NOT NULL REFERENCES crowd_items(id) ON DELETE CASCADE,
    reporter_id BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    weight      DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uq_crowd_vote UNIQUE (item_id, reporter_id)
);
CREATE INDEX idx_crowd_reports_reporter ON crowd_reports (reporter_id, created_at);

-- 15.5 "new / unknown UPI ID": how long FraudShield has known a (hashed) UPI ID. Not linked to any user.
CREATE TABLE upi_seen (
    vpa_hash    VARCHAR(64) PRIMARY KEY,
    first_seen  TIMESTAMPTZ NOT NULL DEFAULT now(),
    checks      INT         NOT NULL DEFAULT 1
);
