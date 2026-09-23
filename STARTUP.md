# Running CompatMe locally (Docker Compose + IDE)

By default, `docker-compose.yml` only starts the **infrastructure** (MongoDB + an optional MongoDB
web UI). The Spring Boot backend itself is commented out, so you can run/debug it directly from
your IDE (IntelliJ, VS Code, etc.) with breakpoints, hot reload, etc.

## 1. Prerequisites

- Docker + Docker Compose v2 (`docker compose version`)
- Java 21 (matches `pom.xml`'s `java.version`)
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

## 2. Start infrastructure (MongoDB + Mongo Express)

```bash
docker compose up -d
```

This starts:

| Service | URL | Purpose |
|---|---|---|
| `mongodb` | `localhost:27017` | Database, reachable from your host machine (IDE, Compass, etc.) |
| `mongo-express` | http://localhost:8081 | Optional web UI to browse MongoDB data (login: `admin` / `MONGO_EXPRESS_PASSWORD`, default password `admin`) |

## 3. Run the backend from your IDE

Set these environment variables in your IDE's run configuration (or export them in the shell you
launch the IDE from):

| Variable | Required? | Value to use | Notes |
|---|---|---|---|
| `MONGODB_URI` | **Yes** | `mongodb://localhost:27017/compatme` | Points at the Dockerized MongoDB via its published host port. |
| `GEMINI_API_KEY` | **Yes** | your real key | From Google AI Studio. |
| `GEMINI_EMBEDDING_MODEL` | No | `gemini-embedding-001` (default) | Override only if testing a different embedding model. |
| `GEMINI_EMBEDDING_DIMENSIONALITY` | No | `768` (default) | Must match what the embedding model supports. |
| `GEMINI_CHAT_MODEL` | No | `gemini-2.5-flash` (default) | Override if the model name changes/deprecates. |
| `GEMINI_MAX_RETRY_ATTEMPTS` | No | `4` (default) | Retry attempts on Gemini 429/5xx errors. |
| `GEMINI_RETRY_INITIAL_BACKOFF_MILLIS` | No | `1000` (default) | Initial backoff delay. |
| `GEMINI_RETRY_BACKOFF_MULTIPLIER` | No | `2.0` (default) | Exponential backoff multiplier. |
| `TELEGRAM_BOT_ENABLED` | No | `false` (default) | Set `true` to run the Telegram bot in-process alongside the backend. |
| `TELEGRAM_BOT_TOKEN` | Only if bot enabled | — | Must set if `TELEGRAM_BOT_ENABLED=true`. |
| `TELEGRAM_BOT_USERNAME` | Only if bot enabled | — | Must set if `TELEGRAM_BOT_ENABLED=true`. |
| `SEED_DATA_ENABLED` | No | `false` (default) | Set `true` to seed ~6 sample Ukrainian profiles + embeddings on startup. |
| `SERVER_PORT` | No | `8080` (default) | Local port the backend listens on. |

**IntelliJ IDEA**: Run/Debug Configurations → your `CompatmeApplication` config → Environment
variables → add the two required ones (at minimum):
```
MONGODB_URI=mongodb://localhost:27017/compatme;GEMINI_API_KEY=your-real-gemini-api-key
```

**Command line equivalent** (if you'd rather not use the IDE's run button):
```bash
export MONGODB_URI=mongodb://localhost:27017/compatme
export GEMINI_API_KEY=your-real-gemini-api-key
./mvnw spring-boot:run
```

Then run/debug `CompatmeApplication` as usual.

## 4. Verify it's running

```bash
curl http://localhost:8080/api/v1/profiles
```

Should return `[]` (or your seeded sample profiles, if `SEED_DATA_ENABLED=true`).

## 5. Seed sample data (optional)

Set `SEED_DATA_ENABLED=true` in your IDE run configuration (or export it before `./mvnw
spring-boot:run`) and restart the app. Seeding is idempotent — it matches existing sample profiles
by `telegramUserId`, so re-running it won't create duplicates.

## 6. Stopping / resetting infrastructure

```bash
docker compose down            # stop containers, keep MongoDB data volume
docker compose down -v         # stop containers AND delete MongoDB data
```

## Running the backend as a container instead (optional)

`docker-compose.yml` still contains a full `compatme-backend` service definition — it's just
commented out. Uncomment it if you'd rather run everything in Docker (e.g. to reproduce a
teammate's environment or test the production Dockerfile build):

```bash
cp .env.example .env   # then edit .env and set GEMINI_API_KEY
docker compose up --build
```

## Troubleshooting

- **Backend can't reach MongoDB from the IDE** — make sure you used
  `mongodb://localhost:27017/compatme` (not the in-container hostname `mongodb`), and that
  `docker compose ps` shows `mongodb` as `healthy`.
- **`GEMINI_API_KEY must be set in .env`** (only applies if you uncommented the backend service in
  compose) — copy `.env.example` to `.env` and fill in `GEMINI_API_KEY`.
- **Telegram bot doesn't start** — check `TELEGRAM_BOT_ENABLED=true` and both
  `TELEGRAM_BOT_TOKEN`/`TELEGRAM_BOT_USERNAME` are set.
- **Port already in use** — change `SERVER_PORT` (IDE env var) or stop whatever else is bound to
  `8080`/`27017`/`8081`.
