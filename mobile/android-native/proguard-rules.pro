# 8.9: R8 rules for FraudShield's own code. Flutter and its plugins ship their own consumer rules.
# Services / activities named in the manifest are kept by R8 automatically; these cover what is found by name at runtime.

# Notification listener + main activity (bound by the system by class name)
-keep class com.fraudshield.app.AdNotificationListener { *; }
-keep class com.fraudshield.app.MainActivity { *; }

# Keystore-backed background session: keep the key spec / cipher code paths readable in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# org.json is part of Android; nothing to keep. No reflection-based JSON in our code.
