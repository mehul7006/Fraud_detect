-- 3.1: at most ONE open threat per user for "state" threats (forwarding, takeover, clone risk).
-- Enforced by the database so concurrent reports from the phone cannot create duplicates.
CREATE UNIQUE INDEX uq_threat_open_once
    ON threat_events (user_id, type)
    WHERE status = 'OPEN'
      AND type IN ('CALL_FORWARDING', 'SMS_FORWARDING', 'WHATSAPP_TAKEOVER', 'TELEGRAM_TAKEOVER', 'CLONE_RISK');

-- 3.2: carrier response patterns, served to the app so they can be corrected without an app release.
CREATE TABLE carrier_patterns (
    id         BIGSERIAL PRIMARY KEY,
    country    VARCHAR(8)   NOT NULL DEFAULT '*',   -- ISO code or '*' for any
    carrier    VARCHAR(60)  NOT NULL DEFAULT '*',   -- e.g. 'Jio', or '*' for any
    kind       VARCHAR(10)  NOT NULL CHECK (kind IN ('ACTIVE', 'INACTIVE')),
    regex      VARCHAR(200) NOT NULL,
    priority   INT          NOT NULL,               -- lower runs first, first match wins
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Generic seed rules. These are NOT captured from real carriers; real texts must be collected on devices
-- (roadmap 3.4) and added per carrier. INACTIVE rules run first so "not forwarded to ..." is never read as ACTIVE.
INSERT INTO carrier_patterns (kind, regex, priority) VALUES
 ('INACTIVE', '(?i)not\s+(forwarded|active|activated|registered|provisioned)', 10),
 ('INACTIVE', '(?i)\b(inactive|deactivated|disabled|cancelled|erased)\b',      11),
 ('INACTIVE', '(?i)(forwarding|diversion|divert)\s+(is\s+)?(off|not set)',     12),
 ('ACTIVE',   '(?i)forwarded\s+to\s+\+?\d[\d\s-]{6,}',                          20),
 ('ACTIVE',   '(?i)\b(forward(ing|ed)?|divert(ed)?)\b.*\b(active|enabled|registered)\b', 21),
 ('ACTIVE',   '\+?\d{10,15}',                                                   30);
