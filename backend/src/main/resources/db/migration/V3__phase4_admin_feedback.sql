-- 4.3: role on users (USER | ADMIN). Admins are promoted only at start-up via ADMIN_EMAIL (no public endpoint).
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 4.4: false-positive / false-negative reports. Host only, never the full URL or query string.
CREATE TABLE url_feedback (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    host       VARCHAR(255) NOT NULL,
    kind       VARCHAR(20)  NOT NULL CHECK (kind IN ('FALSE_POSITIVE', 'FALSE_NEGATIVE')),
    note       VARCHAR(300),
    status     VARCHAR(10)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
    resolution VARCHAR(20),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_feedback_open ON url_feedback (user_id, host) WHERE status = 'OPEN';
