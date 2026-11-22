-- 6.2: a known-good app is trusted only if it is the REAL app: signed with the developer's certificate,
-- or (while the certificate is not known yet) installed from Google Play, which never lets a different
-- developer publish under the same package name. A sideloaded copy using "com.whatsapp" is scored normally,
-- and one with a known name but the wrong certificate is flagged as a fake.
ALTER TABLE app_reputation ADD COLUMN cert_sha256 VARCHAR(400);
