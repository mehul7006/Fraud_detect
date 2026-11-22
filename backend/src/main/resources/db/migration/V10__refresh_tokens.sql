-- 8.2: short-lived access tokens + rotating refresh tokens, and logout that really invalidates old tokens.
-- Only a SHA-256 of each refresh token is stored. A "family" is one login (or one extra session, e.g. the background
-- scanner); every refresh replaces the token inside its family. Re-use of an already replaced token = theft ->
-- the whole family is revoked.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family      VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    client      VARCHAR(20)  NOT NULL DEFAULT 'app',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_family ON refresh_tokens (family);

-- logout: access tokens issued before this moment are rejected even though their signature is still valid
ALTER TABLE users ADD COLUMN tokens_valid_after TIMESTAMPTZ;
