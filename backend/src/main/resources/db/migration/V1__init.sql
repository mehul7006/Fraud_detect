CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id VARCHAR(100) NOT NULL,
  model VARCHAR(100),
  os_version VARCHAR(50),
  sim_hash VARCHAR(100),
  fingerprint VARCHAR(100),
  clone_risk_score INT NOT NULL DEFAULT 0,
  last_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, device_id)
);

CREATE TABLE threat_events (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(40) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  title VARCHAR(200) NOT NULL,
  detail VARCHAR(1000),
  source_app VARCHAR(200),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_threat_user_status ON threat_events(user_id, status);

CREATE TABLE blocklist (
  id BIGSERIAL PRIMARY KEY,
  kind VARCHAR(10) NOT NULL,       -- DOMAIN | SHA256
  value VARCHAR(255) NOT NULL UNIQUE,
  reason VARCHAR(200)
);

INSERT INTO blocklist(kind, value, reason) VALUES
 ('DOMAIN','example-phish.test','seed sample'),
 ('SHA256','275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f','EICAR test file');
