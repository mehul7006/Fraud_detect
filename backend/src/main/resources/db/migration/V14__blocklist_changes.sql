-- 12.5: change log of blocked DOMAINS for the phone's VPN filter. The phone only ever gets SHA-256 hashes and asks
-- "what changed since version N" (version = this table's sequence).
CREATE TABLE blocklist_changes (
    version     BIGSERIAL PRIMARY KEY,
    domain_hash VARCHAR(64) NOT NULL,
    op          VARCHAR(6)  NOT NULL CHECK (op IN ('ADD', 'REMOVE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX blocklist_changes_hash ON blocklist_changes (domain_hash);

-- domains already blocked before this migration
INSERT INTO blocklist_changes (domain_hash, op)
SELECT encode(sha256(convert_to(lower(value), 'UTF8')), 'hex'), 'ADD' FROM blocklist WHERE kind = 'DOMAIN' ORDER BY id;
