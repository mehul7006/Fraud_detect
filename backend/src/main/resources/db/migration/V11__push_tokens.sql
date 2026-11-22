-- 7.3: Firebase Cloud Messaging token of each device, sent by the app with its hourly check-in.
ALTER TABLE devices ADD COLUMN push_token VARCHAR(512);
