# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

"家常日记" — a WeChat Mini Program for family household collaboration, focused on shared fridge/pantry inventory management with expiry tracking and reminders. Built from a Tencent WebAR SDK template (legacy AR pages exist but are unused).

## Architecture

Two independent codebases in one repo:

- **`miniprogram/`** — WeChat native Mini Program frontend (WXML/WXSS/JS). Communicates with backend via REST API (`utils/request.js`). Auth tokens stored in wx.Storage.
- **`server/`** — Spring Boot 2.7 (Java 8) REST API backend. MySQL + Redis. MyBatis Plus ORM. Flyway migrations.

Authentication flow: `wx.login()` code → `POST /api/auth/wx-login` → server exchanges code with WeChat, returns Bearer token → token stored in Redis (30-day TTL) and validated by `AuthInterceptor` on every request via `AuthContext` (ThreadLocal).

## Development Setup

### Backend

```bash
# Prerequisites: Java 8, MySQL (127.0.0.1:3306/life), Redis (127.0.0.1:6379)
# Copy and fill in WeChat credentials:
cp server/src/main/resources/application-local.example.yml server/src/main/resources/application-local.yml

cd server
./mvnw spring-boot:run
# Runs on port 8080, context path /api
```

Flyway runs migrations from `server/src/main/resources/db/migration/` on startup.

### Frontend

Open the project in **微信开发者工具** (WeChat DevTools). The `miniprogramRoot` is `miniprogram/`. If npm dependencies change, rebuild npm in DevTools (工具 → 构建 npm).

Frontend API base URL is hardcoded to `http://127.0.0.1:8080/api` in `miniprogram/utils/request.js`.

## API Endpoints

All endpoints under `/api`:

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/auth/wx-login` | WeChat login / debug login |
| POST | `/families` | Create family |
| POST | `/families/join` | Join by invite code |
| GET | `/families/current` | Current family + members |
| GET | `/fridge/items?status=` | List items (ALL/EXPIRING/EXPIRED) |
| POST | `/fridge/items` | Create fridge item |
| PUT | `/fridge/items/{id}` | Update fridge item |
| POST | `/fridge/items/{id}/consume` | Consume (reduce quantity) |
| POST | `/fridge/items/{id}/discard` | Discard item |
| GET | `/fridge/reminders/summary` | Dashboard counts |
| GET | `/ingredients/catalog` | System + family ingredients |
| POST | `/ingredients/family` | Add custom ingredient |
| POST | `/subscriptions/expiry-reminder` | Toggle reminder subscription |

## Key Conventions

- Backend entity IDs use Snowflake algorithm (`util/IdGenerator`). Invite codes are 6-char alphanumeric excluding confusable chars (O/0/I/1).
- Backend request/response DTOs are separated into `dto/request/` and `dto/response/` packages.
- Fridge items track status: ACTIVE → CONSUMED (auto when quantity reaches 0) or DISCARDED. All changes are audit-logged in `fridge_change_logs`.
- Daily 8AM cron job (`ReminderDispatchJob`) sends WeChat subscription messages for items expiring within 3 days, with per-user per-item deduplication via `reminder_delivery_logs`.
- Frontend pages: `index` (login/splash) → `family` (create/join) → `home` (dashboard) → `fridge` (inventory) / `ingredients` (catalog).
- Legacy AR-related pages (`camera`, `export`, `native`, `webview`) and components (`operate-list`, `slider`, `loading2`) are from the original template and not registered in `app.json`.
