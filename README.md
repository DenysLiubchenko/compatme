# CompatMe

Backend for an intelligent dating/compatibility-matching chatbot, built as a professional/applied
master's thesis project. Determines candidate compatibility from free-text profile descriptions
using NLP (Gemini embeddings + chat model), with reciprocal (mutual-interest) matching rather than
one-directional similarity, and lets users refine their search criteria through natural-language
dialogue.

Primary user-facing language is **Ukrainian**; the system is UTF-8 throughout and makes no
assumption that profile text is English/ASCII.

## Architecture: hexagonal (ports & adapters)

This backend is deliberately **not** structured as a conventional Spring MVC
controller/service/repository stack. It follows hexagonal architecture:

```
domain/                     Plain Java. Entities, value objects, the reciprocal-matching
                             aggregation strategies. ZERO Spring / MongoDB / Gemini SDK
                             dependencies — fully unit-testable with no framework context.

application/                Use case orchestration. Framework-light: only @Service/@Component
  port/in/                  stereotype annotations for wiring, no framework types in signatures.
  port/out/                 Inbound use cases (driven by adapters) and outbound dependencies
                             (driven adapters implement these) — both are plain Java interfaces.

adapter/in/web/             Inbound adapter: Spring @RestControllers. Depend on port.in use case
                             interfaces only — never on the persistence or Gemini adapters.

adapter/out/persistence/    Outbound adapter: Spring Data MongoDB. Implements port.out.
                             ProfileRepositoryPort. MongoDB @Document classes live ONLY here;
                             the domain Profile aggregate is never a @Document.

adapter/out/gemini/         Outbound adapter: Google Gen AI SDK (com.google.genai). Implements
                             port.out.EmbeddingProviderPort and port.out.ChatCompletionPort.

adapter/telegram/           Thin Telegram bot client. Talks to this backend's own REST API over
                             HTTP; contains no domain/business logic itself.

config/                     Spring @Configuration classes wiring adapters to ports (the only
                             place dependency injection crosses the hexagon's boundary).

bootstrap/                  CommandLineRunner that seeds MongoDB with sample profiles.
```

**Why this matters for the thesis**: the domain layer (`domain/service/CompatibilityScorer` and
the `CompatibilityAggregationStrategy` implementations) can be unit-tested with zero Spring
context — see `src/test/java/.../domain/service/`. Swapping MongoDB for another database, or the
Gemini API for a different embedding provider, only requires writing a new class that implements
`ProfileRepositoryPort` / `EmbeddingProviderPort` / `ChatCompletionPort` and rewiring one bean in
`config/` — nothing in `domain` or `application` needs to change, because those layers only depend
on the port interfaces, never on the concrete adapters.

## Core domain logic

- Each profile has two **separately embedded** free-text fields: `selfDescription` ("who I am")
  and `preferenceDescription` ("who I'm looking for"). They are never merged into one embedding.
- Directional compatibility scores:
  ```
  scoreAtoB = cosineSimilarity(embedding(preferenceDescription_A), embedding(selfDescription_B))
  scoreBtoA = cosineSimilarity(embedding(preferenceDescription_B), embedding(selfDescription_A))
  ```
- Three selectable aggregation strategies (`domain/service/`), for the thesis's A/B evaluation:
  - `SIMPLE_AVERAGE` — plain average of `scoreAtoB` and `scoreBtoA`
  - `SIMPLE_SELF_SIMILARITY` — symmetric similarity between the two self-descriptions only
  - `RECIPROCAL_HARMONIC` — harmonic mean: `2 * scoreAtoB * scoreBtoA / (scoreAtoB + scoreBtoA)`,
    which collapses toward zero whenever either side's interest is weak (the thesis's core
    contribution: a match only ranks highly when both sides are plausibly interested)
- Embeddings are computed once and cached in MongoDB, tagged with the model name/version,
  dimensionality, and a SHA-256 hash of the source text — an embedding is only recomputed if the
  underlying text changed or the model changed (`application/service/EmbeddingGenerationService`).
- Preference refinement (`application/service/PreferenceRefinementService`): a natural-language
  message (e.g. *"хочу когось спокійнішого"*) is interpreted by the Gemini chat model into a
  rewritten `preferenceDescription` (as structured JSON, not free text), only that field's
  embedding is recomputed, and the candidate pool is re-ranked without touching any other profile's
  embeddings.
- Candidate pre-filtering: recommendations apply cheap hard filters (age range, mutual
  gender/seeking-gender match) before any NLP scoring runs, then score the remaining candidates
  in-memory in Java using cached embeddings (sufficient at the hundreds-to-thousands-of-profiles
  scale this prototype targets; MongoDB Atlas Vector Search was intentionally not required).

## REST API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/profiles` | Create a profile |
| `PUT` | `/api/v1/profiles/{id}` | Update a profile |
| `GET` | `/api/v1/profiles/{id}` | Get a profile by id |
| `GET` | `/api/v1/profiles/by-telegram/{telegramUserId}` | Get a profile by Telegram user id |
| `GET` | `/api/v1/profiles` | List all profiles |
| `DELETE` | `/api/v1/profiles/{id}` | Delete a profile |
| `POST` | `/api/v1/profiles/{id}/embeddings` | Generate (cached) embeddings for a profile |
| `GET` | `/api/v1/profiles/{id}/recommendations?strategy=&topN=` | Top-N recommendations |
| `POST` | `/api/v1/profiles/{id}/preference-refinements` | Submit a natural-language refinement |

`strategy` accepts `SIMPLE_AVERAGE`, `SIMPLE_SELF_SIMILARITY`, or `RECIPROCAL_HARMONIC` (default).

## Running locally

### Fastest path: Docker Compose

```bash
cp .env.example .env   # then edit .env and set GEMINI_API_KEY
docker compose up --build
```

This starts MongoDB, the backend, and an optional MongoDB web UI (http://localhost:8081) with
zero local Java/Maven install required. Full instructions, all environment variables, and
troubleshooting: see **[STARTUP.md](STARTUP.md)**.

### Manual setup

### Prerequisites

- Java 17+
- Maven (or use the bundled `./mvnw`)
- A MongoDB instance — either local (`docker run -p 27017:27017 mongo:7`) or MongoDB Atlas
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

### Environment variables

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `MONGODB_URI` | no | `mongodb://localhost:27017/compatme` | MongoDB connection string (local or Atlas SRV URI) |
| `GEMINI_API_KEY` | **yes** | — | Gemini Developer API key |
| `GEMINI_EMBEDDING_MODEL` | no | `gemini-embedding-001` | Embedding model name |
| `GEMINI_EMBEDDING_DIMENSIONALITY` | no | `768` | Requested embedding output dimensionality |
| `GEMINI_CHAT_MODEL` | no | `gemini-2.5-flash` | Chat/generation model name |
| `GEMINI_MAX_RETRY_ATTEMPTS` | no | `4` | Max attempts (incl. first) for retried Gemini calls |
| `GEMINI_RETRY_INITIAL_BACKOFF_MILLIS` | no | `1000` | Initial retry backoff delay |
| `GEMINI_RETRY_BACKOFF_MULTIPLIER` | no | `2.0` | Exponential backoff multiplier |
| `TELEGRAM_BOT_ENABLED` | no | `false` | Set `true` to start the Telegram bot client |
| `TELEGRAM_BOT_TOKEN` | only if bot enabled | — | Token issued by @BotFather |
| `TELEGRAM_BOT_USERNAME` | only if bot enabled | — | Bot username registered with @BotFather |
| `TELEGRAM_BACKEND_BASE_URL` | no | `http://localhost:8080` | Base URL the bot uses to call this backend |
| `SEED_DATA_ENABLED` | no | `false` | Set `true` to seed sample profiles at startup |

### Run

```bash
export GEMINI_API_KEY=your-api-key
export MONGODB_URI=mongodb://localhost:27017/compatme   # or an Atlas SRV URI
./mvnw spring-boot:run
```

To seed a handful of sample Ukrainian-language profiles (and generate their embeddings) at
startup, add `-DSEED_DATA_ENABLED=true` or export `SEED_DATA_ENABLED=true` before running.

### Pointing at MongoDB Atlas

Set `MONGODB_URI` to your Atlas SRV connection string, e.g.:

```bash
export MONGODB_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/compatme?retryWrites=true&w=majority"
```

No code changes are required — the connection string is the only thing that differs between
local and Atlas.

### Tests

```bash
./mvnw test
```

Domain-layer tests (`src/test/java/.../domain/service/`) run with **zero Spring context** and
verify the aggregation strategies directly, per the hexagonal architecture requirement that this
logic be testable in complete isolation.

## Notes on scope

- Authentication/authorization is intentionally out of scope; endpoints take a profile id
  explicitly rather than deriving it from a session.
- User account and dating-profile data are modeled as a single combined entity (`Profile`).
- Candidate scoring is done in-memory in Java rather than via MongoDB Atlas Vector Search, which
  keeps the prototype runnable on a free-tier/local MongoDB instance; this is a deliberate,
  documented tradeoff appropriate for a thesis prototype's expected data scale (hundreds to a few
  thousand profiles).
