-- 3.4: real Hindi wording captured from a Vi India SIM (Android 15, phone service in Hindi, 2026-11-22):
-- "Voice: अग्रेषित नहीं किया गया" = not forwarded. Mirrored in mobile/lib/core/carrier_classifier.dart builtInRules.
-- Classification is now per line (CarrierClassifier), so one forwarded service among "not forwarded" ones is ACTIVE.
INSERT INTO carrier_patterns (kind, regex, priority) VALUES ('INACTIVE', 'अग्रेषित\s*नहीं', 14);
