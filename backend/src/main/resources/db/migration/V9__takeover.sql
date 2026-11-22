-- 7.2: account-takeover correlation needs to know when a device first appeared and when its SIM last changed.
ALTER TABLE devices ADD COLUMN first_seen TIMESTAMPTZ NOT NULL DEFAULT now();
UPDATE devices SET first_seen = last_seen WHERE first_seen > last_seen;
ALTER TABLE devices ADD COLUMN sim_changed_at TIMESTAMPTZ;

-- ACCOUNT_TAKEOVER is a "state" threat too: at most one OPEN per user.
DROP INDEX uq_threat_open_once;
CREATE UNIQUE INDEX uq_threat_open_once
    ON threat_events (user_id, type)
    WHERE status = 'OPEN'
      AND type IN ('CALL_FORWARDING', 'SMS_FORWARDING', 'WHATSAPP_TAKEOVER', 'TELEGRAM_TAKEOVER', 'CLONE_RISK',
                   'ACCOUNT_TAKEOVER');

CREATE INDEX idx_threat_user_created ON threat_events (user_id, created_at);
