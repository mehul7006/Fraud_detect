# FraudShield – Android Anti-Fraud App

Backend: **Java 21 + Spring Boot 3** | Frontend: **Flutter + Kotlin native bridge** | DB: **PostgreSQL**

## Folder layout
```
docs/        feasibility, architecture, roadmap, API spec
jira/        jira-import.csv  (Epics + Stories + Tasks, import via Jira > System > External System Import > CSV)
backend/     Spring Boot REST API (scanning engine, threat store, auth)
mobile/      Flutter app + Android native (Kotlin) code
```
## Read first
1. `docs/01-FEASIBILITY.md`  – what Android allows and what it does NOT (important!)
2. `docs/02-ARCHITECTURE.md` – full flow
3. `docs/03-ROADMAP.md`      – step-by-step plan
4. `jira/jira-import.csv`    – tickets

## Run backend
```
cd backend
docker compose up -d db
mvn spring-boot:run
```
Swagger-less quick test: `curl -X POST localhost:8080/api/v1/scan/url -H "Authorization: Bearer <jwt>" -H "Content-Type: application/json" -d "{\"url\":\"http://bit.ly.login-verify.xyz/whatsapp\"}"`

## Run mobile
```
cd mobile
flutter create . --platforms=android   # generates android/ shell; then copy the files from mobile/android-native/ over it (see docs/02)
flutter pub get
flutter run
```
