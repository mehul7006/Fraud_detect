-- 6.1: real labelled data for tuning the ad-abuse weights. Opt-in on the phone ("Help improve risky-app
-- detection"). Written when the user decides about a flagged app: Uninstall = ABUSIVE, Ignore = BENIGN.
-- Only the numeric/boolean signals the score uses are kept - no app list, no message text, no device id.
CREATE TABLE app_labels (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_name      VARCHAR(200) NOT NULL,
    label             VARCHAR(10)  NOT NULL CHECK (label IN ('ABUSIVE', 'BENIGN')),
    notif_per_hour    INT          NOT NULL,
    overlay           BOOLEAN      NOT NULL,
    sms               BOOLEAN      NOT NULL,
    accessibility     BOOLEAN      NOT NULL,
    listener          BOOLEAN      NOT NULL,
    full_screen       BOOLEAN      NOT NULL,
    self_launch       INT          NOT NULL,
    score_shown       INT          NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, package_name)
);
