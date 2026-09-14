# Running CompatMe locally (Docker Compose)

This starts the full local environment: MongoDB, an optional MongoDB web UI, and the CompatMe
backend itself, all wired together — no local Java/Maven install required (Docker builds the jar
inside a container).

## 1. Prerequisites

- Docker + Docker Compose v2 (`docker compose version`)
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

## 2. Configure environment variables

Copy the example env file and fill in your Gemini API key:

```bash
cp .env.example .env
```

Edit `.env` and set at minimum:

```dotenv
GEMINI_API_KEY=your-real-gemini-api-key
```

Everything else in `.env.example` is optional and has a working default (see the table below).
`.env` is gitignored — never commit it.

### Environment variables reference

| Variable | Required? | Default | Notes |
|---|---|---|---|
| `GEMINI_API_KEY` | **Yes** | — | Must override. Get it from Google AI Studio. |
| `GEMINI_EMBEDDING_MODEL` | No | `gemini-embedding-001` | Override only if testing a different embedding model. |
| `GEMINI_EMBEDDING_DIMENSIONALITY` | No | `768` | Must match what the embedding model supports. |
| `GEMINI_CHAT_MODEL` | No | `gemini-2.5-flash` | Override if the model name changes/deprecates. |
| `GEMINI_MAX_RETRY_ATTEMPTS` | No | `4` | Retry attempts on Gemini 429/5xx errors. |
| `GEMINI_RETRY_INITIAL_BACKOFF_MILLIS` | No | `1000` | Initial backoff delay. |
| `GEMINI_RETRY_BACKOFF_MULTIPLIER` | No | `2.0` | Exponential backoff multiplier. |
| `TELEGRAM_BOT_ENABLED` | No | `false` | Set `true` to run the Telegram bot alongside the backend. |
| `TELEGRAM_BOT_TOKEN` | Only if bot enabled | — | Must override if `TELEGRAM_BOT_ENABLED=true`. |
| `TELEGRAM_BOT_USERNAME` | Only if bot enabled | — | Must override if `TELEGRAM_BOT_ENABLED=true`. |
| `SEED_DATA_ENABLED` | No | `false` | Set `true` to seed ~6 sample Ukrainian profiles + embeddings on startup. |
| `SERVER_PORT` | No | `8080` | Host port the backend is published on. |
| `MONGO_EXPRESS_PASSWORD` | No | `admin` | Password for the optional Mongo web UI (`admin`/this password). |

`MONGODB_URI` is **not** in `.env` — docker-compose.yml wires it automatically to the `mongodb`
service (`mongodb://mongodb:27017/compatme`) so containers can reach each other by service name.

## 3. Start everything

```bash
docker compose up --build
```

This builds the backend image (multi-stage Maven build, no local JDK needed) and starts:

| Service | URL | Purpose |
|---|---|---|
| `compatme-backend` | http://localhost:8080 | The REST API |
| `mongodb` | localhost:27017 | Database (also reachable from your host machine, e.g. via Compass) |
| `mongo-express` | http://localhost:8081 | Optional web UI to browse MongoDB data (login: `admin` / `MONGO_EXPRESS_PASSWORD`) |

Run in the background instead:

```bash
docker compose up --build -d
```

## 4. Verify it's running

```bash
curl http://localhost:8080/api/v1/profiles
```

Should return `[]` (or your seeded sample profiles, if `SEED_DATA_ENABLED=true`).

## 5. Seed sample data (optional)

Either set `SEED_DATA_ENABLED=true` in `.env` before the first `docker compose up`, or run it
as a one-off against an already-running stack:

```bash
docker compose stop compatme-backend
SEED_DATA_ENABLED=true docker compose up -d compatme-backend
```

Seeding is idempotent — it matches existing sample profiles by `telegramUserId`, so re-running it
won't create duplicates.

## 6. Stopping / resetting

```bash
docker compose down            # stop containers, keep MongoDB data volume
docker compose down -v         # stop containers AND delete MongoDB data
```

## 7. Rebuilding after code changes

```bash
docker compose up --build compatme-backend
```

## Running without Docker (backend only, against a locally running MongoDB)

If you'd rather run just MongoDB in Docker and the Spring Boot app directly on your machine
(faster iteration while developing):

```bash
docker compose up -d mongodb
export GEMINI_API_KEY=your-real-gemini-api-key
export MONGODB_URI=mongodb://localhost:27017/compatme
./mvnw spring-boot:run
```

## Troubleshooting

- **`GEMINI_API_KEY must be set in .env`** — you forgot to copy `.env.example` to `.env` and/or
  fill in `GEMINI_API_KEY`.
- **Backend can't reach MongoDB** — make sure the `mongodb` service is healthy first
  (`docker compose ps` should show it as `healthy`); the backend's `depends_on` condition waits
  for this automatically, but a first-time image pull can take a minute.
- **Telegram bot doesn't start** — check `TELEGRAM_BOT_ENABLED=true` and both
  `TELEGRAM_BOT_TOKEN`/`TELEGRAM_BOT_USERNAME` are set; check `docker compose logs compatme-backend`.
- **Port already in use** — override `SERVER_PORT` in `.env` (e.g. `SERVER_PORT=8090`).
