# CompatMe

Backend for an intelligent dating/compatibility-matching chatbot, built as a professional/applied
master's thesis project. Determines candidate compatibility from free-text profile descriptions
using NLP (Gemini embeddings + chat model), with reciprocal (mutual-interest) matching rather than
one-directional similarity, and lets users refine their search criteria through natural-language
dialogue.

Primary user-facing language is **English**; the system is UTF-8 throughout and makes no
assumption that profile text is limited to a particular alphabet/script.

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
`CompatibilityAggregationStrategy`) can be unit-tested with zero Spring
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
- **Single, fixed compatibility-scoring method** (`domain/service/CompatibilityScorer` +
  `ReciprocalHarmonicAggregationStrategy`): the harmonic mean of the two directional scores —
  `2 * scoreAtoB * scoreBtoA / (scoreAtoB + scoreBtoA)`, clamped to `0.0` whenever either
  directional score is zero or negative (cosine similarity can be negative for unrelated text,
  and the harmonic mean is only well-behaved for same-signed positive inputs).

  **Why reciprocal harmonic aggregation, not a plain average**: a recommendation is only
  genuinely valuable if the interest is mutual. A simple average lets one very high directional
  score mask a very low one — e.g. `average(0.95, 0.05) = 0.5`, still a "medium" recommendation
  even though one side is barely interested. The harmonic mean collapses toward zero whenever
  *either* direction is weak — `harmonic(0.95, 0.05) ≈ 0.095` — so a candidate only ranks highly
  when both sides are plausibly interested in each other. One-directional infatuation does not
  produce a high score. This is the project's only aggregation method; there is no
  strategy-selection parameter anywhere in the API.

  `CompatibilityAggregationStrategy` remains a separate interface (not inlined into
  `CompatibilityScorer`) purely to keep the aggregation formula independently unit-testable and
  swappable, without implying multiple interchangeable strategies exist — see
  `domain/service/ReciprocalHarmonicAggregationStrategyTest`.
- Embeddings are computed once and cached in MongoDB, tagged with the model name/version,
  dimensionality, and a SHA-256 hash of the source text — an embedding is only recomputed if the
  underlying text changed or the model changed (`application/service/EmbeddingGenerationService`).
- Preference refinement (`application/service/PreferenceRefinementService`): a natural-language
  message (e.g. *"I want someone calmer"*) is interpreted by the Gemini chat model into a
  rewritten `preferenceDescription` (as structured JSON, not free text), only that field's
  embedding is recomputed, and the candidate pool is re-ranked without touching any other profile's
  embeddings.
- Candidate pre-filtering: recommendations apply cheap hard filters (age range, mutual
  gender/seeking-gender match, and location scope — see below) before any NLP scoring runs, then
  score the remaining candidates in-memory in Java using cached embeddings (sufficient at the
  hundreds-to-thousands-of-profiles scale this prototype targets; MongoDB Atlas Vector Search was
  intentionally not required).
  `seekingGenders` is a set end to end (domain, persistence, and both the profile-creation/update
  DTOs and the response DTO), so a profile can seek multiple genders simultaneously; the mutual
  match check (`Profile.mutuallyMatchesSeekingGender`) is a set-membership check on both sides,
  not an exact single-value comparison.
- **Location filtering (`country`/`city`, `LocationScope`)**: each profile optionally carries a
  free-text `country` and `city`. Recommendation requests accept a `scope` query parameter:
  - `GLOBAL` (default) — no location filtering, candidates from anywhere are considered
  - `COUNTRY` — only candidates in the same country as the requester
  - `CITY` — only candidates in the same city (and therefore country) as the requester

  The filter is **relative to the requester's own profile**, not an arbitrary search parameter —
  `GET /api/v1/profiles/{id}/recommendations?scope=CITY` means "find me people in my own city".
  Comparisons are case-insensitive exact-string matches (no geocoding/normalization). If the
  requester hasn't set the field(s) the requested scope needs, the filter is permissive (matches
  everyone) rather than excluding every candidate — consistent with how `seekingGenders` behaves
  when unset.
- **`archetypeIds`** (`List<Integer>`, optional): a thesis-evaluation-only tag on `Profile`
  recording which synthetic personality archetype(s) a profile blends, set by the synthetic
  dataset generator. It is carried through the domain model, MongoDB document, seed-loader JSON,
  and both profile DTOs, but is **never read by `CompatibilityScorer` or
  `CompatibilityAggregationStrategy`** — it exists purely so evaluation results can be
  sliced/inspected by archetype after the fact. Every place it appears in code is commented to
  make this explicit.
- **`photoUrl`** (optional): a URL to the profile's photo — **only the URL is stored**, never
  image bytes (no file upload endpoint exists or is planned). Must start with `http://` or
  `https://` when present; any other scheme (e.g. `javascript:`, `data:`) is rejected both at the
  HTTP boundary (`@Pattern` on `ProfileRequest`) and, authoritatively, by the domain `Profile`
  constructor. Returned in `ProfileResponse` and in each `RecommendationItem` (so recommendation
  results can render a candidate's photo without a follow-up profile lookup).

## Evaluating compatibility scoring against a ground-truth dataset

The thesis's results chapter measures how well reciprocal harmonic aggregation reflects genuine
reciprocal compatibility, using a hand- or synthetically-labeled ground-truth dataset: pairs of
profile ids with an expected label (`MUTUAL_MATCH`, `ONE_SIDED`, or `NO_MATCH`).

- **Import**: set `GROUND_TRUTH_IMPORT_ENABLED=true` and place your dataset at
  `src/main/resources/ground-truth.json` (the bundled file already references the bundled
  `sample-profiles.json` entries — see below) before starting the app. Format — an array of:
  ```json
  [
    { "profileAId": "<id>", "profileBId": "<id>", "label": "MUTUAL_MATCH" }
  ]
  ```
  `label` accepts `MUTUAL_MATCH`, `ONE_SIDED`, or `NO_MATCH`. Each import **replaces** the
  previously stored dataset (it's a write-once import target, not an append log). Both referenced
  profiles must already exist (and, to be scorable, already have embeddings generated) — pairs
  referencing missing profiles or incomplete embeddings are skipped and logged at DEBUG.
- **Report**: `GET /api/v1/evaluation/report` scores every stored pair via the app's single
  `CompatibilityScorer` (reusing the same reciprocal-harmonic scoring logic the live
  recommendation flow uses) and returns the average aggregated score per label, plus how many
  pairs contributed to each label:
  ```json
  {
    "averageScoreByLabel": { "MUTUAL_MATCH": 0.79, "ONE_SIDED": 0.31, "NO_MATCH": 0.09 },
    "pairCounts": { "MUTUAL_MATCH": 18, "ONE_SIDED": 17, "NO_MATCH": 10 }
  }
  ```
  (numbers above are illustrative only). A meaningfully higher average for `MUTUAL_MATCH` than for
  `ONE_SIDED`/`NO_MATCH` is the empirical validation for choosing reciprocal harmonic aggregation
  as the project's scoring method. The same table is also logged at INFO as a plain-text summary
  each time the report is generated, so it can be screenshotted for the thesis without a JSON
  viewer.

## Sample/test data (`sample-profiles.json` + `ground-truth.json`)

Both files live in `src/main/resources/` and are designed to be used together:
`ground-truth.json` references profiles bundled in `sample-profiles.json` by id, so
seeding both (`SEED_DATA_ENABLED=true` + `GROUND_TRUTH_IMPORT_ENABLED=true`) gives you a working
evaluation report with zero manual id lookup. The bundled dataset currently has 476 synthetic
profiles (deduplicated from a generator run) and 66 labeled ground-truth pairs across all three
labels.

- **`telegramUserId` is optional** and expected to be `null` for every entry in
  `sample-profiles.json` — you don't need to create a real Telegram account per synthetic test
  profile. It's only ever non-null for profiles created through the actual Telegram bot (which
  supplies the real chat id). When `null`, `GET /api/v1/profiles/{id}` and friends return a
  readable placeholder string (`"N/A (no Telegram account, sample/evaluation profile)"`) instead
  of an absent/null field.
- **`telegramUserId` is unique when present** (`@Indexed(unique = true, sparse = true)` on
  `ProfileDocument`, `sparse` so any number of profiles can still have it `null`). This index is
  only actually created by MongoDB because `spring.data.mongodb.auto-index-creation: true` is set
  in `application.yml` — Spring Data MongoDB does **not** create `@Indexed` indexes by default,
  so that setting matters. `POST /api/v1/profiles` (no explicit id) looks up an existing profile
  by `telegramUserId` first and updates it in place if one exists, rather than inserting a
  duplicate — this is what makes it safe for the Telegram bot to call it every time someone
  re-completes onboarding via `/start`. If you're upgrading a database that predates this and hit
  a duplicate-key error on startup, find and remove the older duplicate(s) first, e.g.:
  ```js
  db.profiles.aggregate([
    { $group: { _id: "$telegramUserId", ids: { $push: "$_id" }, count: { $sum: 1 } } },
    { $match: { _id: { $ne: null }, count: { $gt: 1 } } }
  ])
  ```
- **`sampleKey`** (required in `sample-profiles.json` only, e.g. `"seed-001"`) is a stable string
  the seed loader (`bootstrap/ProfileDataLoader`) hashes into a deterministic profile id
  (`UUID.nameUUIDFromBytes`, namespaced with `"compatme-sample-profile:"`), so re-running the
  loader against an already-seeded database updates the same profiles instead of duplicating them
  — without needing a `telegramUserId`-based lookup at all. It is never stored or exposed
  anywhere else. If you add your own sample profiles, compute their ids the same way (or just run
  the seed loader once and read the resulting ids back via `GET /api/v1/profiles`) before writing
  matching entries into `ground-truth.json`.
- The bundled profiles have no real display names (the source generator didn't provide any), so
  `displayName` is set to each profile's `sampleKey` (e.g. `"p0001"`) as a readable placeholder.
- `Gender` includes `NON_BINARY` (in addition to `MALE`/`FEMALE`/`OTHER`) because the bundled
  dataset's `seekingGenders` uses it.
- Seeding all bundled profiles calls the Gemini embedding API twice per profile (self +
  preference descriptions) — budget for hundreds of API calls and real wall-clock time on first
  run; re-runs skip recomputation for any profile whose text hasn't changed.

## Telegram bot: button-driven onboarding

The Telegram bot (`adapter/telegram/`) is a thin client: it never touches the domain/application
layers directly, only this backend's own REST API via `BackendApiClient` — see
[Architecture](#architecture-hexagonal-ports--adapters) above. Profile creation is a step-by-step
conversation using Telegram inline keyboards (buttons), a native location-request reply keyboard,
and free text only where buttons don't make sense.

**Flow** (`/start` → ... → profile saved):

1. **Welcome** — inline button "🚀 Create My Profile"
2. **Name** — free text
3. **Age** — free text, validated as an integer in `[18, 99]`; invalid input re-prompts without
   advancing
4. **Gender** — inline keyboard, single choice (Male / Female / Non-binary)
5. **Seeking genders** — inline keyboard, multi-select (tapping toggles a ✅ marker by editing the
   same message, not sending a new one); "Continue" requires at least one selection
6. **Location** — inline choice "📍 Share My Location" or "✍️ Enter Manually":
   - *Share*: switches to a reply keyboard with Telegram's native location-request button; the
     resulting coordinates are reverse-geocoded (see below) into a country/city, shown as an
     inline "is this correct?" confirmation before anything is written as final; on failure, falls
     back to manual entry with an explanation
   - *Manual*: free-text country, then free-text city
7. **Self description** / **8. Preference description** — free text, minimum 10 characters
8. **Photos (optional, up to 5)** — inline choice "📷 Add a Photo" or "⏭ Skip for now". Adding a
   photo appends it (only Telegram's `file_id` is stored — see below) and edits the same
   confirmation into "✅ Photo added (N/5)." with "➕ Add Another" (hidden once 5 is reached) and
   "✅ Done Adding Photos".
9. **Review & confirm** — formatted summary with a "✅ Looks good, save it!" button plus separate
   "✏️ Edit ..." buttons per field group (Name, Age, Gender/Preference, Location, Descriptions,
   Photos). Editing a field group jumps back to its first step and, on completion, returns directly
   to Review — every other already-collected field is left untouched. "✏️ Edit Photos" opens a
   small management view: current photos as a media group, "🗑 Remove Photo N" per photo, "➕ Add
   More" (re-enters the same add-photo flow), "✅ Done" back to Review.
10. **Save** — maps the collected fields onto the existing `POST /api/v1/profiles` +
    `POST /api/v1/profiles/{id}/embeddings` calls (no persistence logic duplicated in the bot
    adapter), shows "🎉 Your profile is live!", then opens the persistent main menu (see below).

Every inline-keyboard step (except the welcome message and free-text steps) has a "⬅️ Back"
button that returns to the previous step without discarding anything already collected.

## Telegram bot: persistent main menu

Reachable via `/menu`, `/start` (for a user who already has a profile — it skips onboarding
entirely), or any "⬅️ Back to Menu" button, all through one reusable method
(`ConversationFlowHandler.sendMainMenu`) so the menu-building logic lives in exactly one place.
Four sections:

- **👤 My Profile** — fetches your own profile via the existing profile-query use case and renders
  it: photos via `sendPhoto`/`sendMediaGroup` if any are attached (else plain text), with name,
  age, location, self-description, and preference-description. "✏️ Edit My Profile" re-enters
  Review, prefilled from the saved profile; "⬅️ Back to Menu" returns here.
- **💘 My Matches** — fetches top candidates via the existing recommendation use case (no scoring
  logic duplicated) and presents them one at a time, Tinder-style: photo(s) if present, name/age/
  location, self-description, with "👍 Like" / "👎 Skip" / "⬅️ Back to Menu". The browsing queue is
  intentionally **session-scoped, in-memory only** (not persisted) — restarting the bot simply
  means the user re-opens My Matches to get a fresh batch, which is an acceptable simplicity
  tradeoff for a browsing session (unlike onboarding, which does need to survive a restart).
- **❤️ Who Liked Me** — same one-at-a-time card UI, sourced from `GetProfilesWhoLikedMeUseCase`,
  with "👍 Like Back" (always produces a mutual match, since a reverse like exists by definition)
  and "➡️ Next" instead of Like/Skip. Shows a friendly empty state if no one has liked you yet.
- **⚙️ Settings** — the same Edit Profile / Pause Matching / Delete Account menu also reachable via
  `/settings`.

### Likes vs. compatibility score

"Liking" a profile is a **new, separate concept** from the existing compatibility score — a score
is a computed prediction (`CompatibilityScorer`), a like is an explicit recorded user action
(`domain.model.Like`). Neither implies the other. A `RecordLikeUseCase` records the like and
detects mutual matches (a reverse like already existing between the two profiles); when "My
Matches" or "Who Liked Me" produces a mutual match, the bot shows a distinct
"🎉 It's a mutual match with {name}!" message. Backed by a dedicated `likes` MongoDB collection
(`LikeDocument`, with a unique compound index on liker+liked to prevent duplicate like records).
Deleting an account cascades to remove every like where that profile is either side, so no
orphaned like records linger.

**`/settings`** is always accessible and offers "✏️ Edit My Profile" (re-enters Review, prefilled
from the saved profile), "🔕 Pause Matching" (placeholder — no pause/deactivate concept exists in
the domain model yet), and "🗑 Delete My Account", which requires an explicit
"⚠️ Are you sure?" confirmation before calling the existing `DELETE /api/v1/profiles/{id}`
endpoint and fully clearing conversation state (so a subsequent `/start` begins completely fresh).
Account deletion also cascades to `Like` records on both sides (see above).

**Conversation state** (`adapter/telegram/state/`) — current step plus every field collected so
far — is stored per Telegram user in its own MongoDB collection
(`telegram_conversation_states`, via `ConversationStateDocument`/`ConversationStateStore`), so an
in-progress conversation survives a bot restart during development. This is a self-contained
`adapter.telegram` concern with no port/adapter indirection — the domain/application layers have
no notion of "conversation steps," only of the final, complete profile once Review is confirmed.
(The My Matches/Who Liked Me browsing queue is deliberately NOT part of this persisted state —
see above.)

**`photoFileIds`** (`List<String>`, optional, max 5): Telegram's own `file_id` references for
uploaded photos — **only these opaque ids are stored, never image bytes**. Telegram hosts the
actual files indefinitely and a `file_id` can be resent via `sendPhoto`/`sendMediaGroup` at any
time, which keeps MongoDB storage minimal (relevant on a free-tier 512MB cluster) and avoids
building a separate media storage/CDN layer. Treated exactly like `archetypeIds` architecturally:
present through the domain model, MongoDB document, and profile DTOs, but **never read by
`CompatibilityScorer` or any `CompatibilityAggregationStrategy`** — purely presentation data.

**Reverse geocoding** (`adapter/out/geocoding/`) implements a new outbound port,
`ReverseGeocodingPort`, via the free [Nominatim](https://nominatim.openstreetmap.org)
(OpenStreetMap) API. Its usage policy requires:
- A descriptive `User-Agent` header identifying this as a university thesis project (set via
  `NOMINATIM_USER_AGENT`, sent on every request)
- At most 1 request/second — enforced with a simple client-side minimum-interval guard
  (`NOMINATIM_MIN_REQUEST_INTERVAL_MILLIS`, default 1100ms); no queue/batching is needed since
  onboarding calls this at most once per user, never in a hot path. Nothing is cached, for the
  same reason.

Network errors or "no result found" responses throw `ReverseGeocodingException`, which the bot
catches to fall back to manual location entry with an explanation — reverse geocoding failure
never blocks onboarding.

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
| `GET` | `/api/v1/profiles/{id}/recommendations?topN=&scope=` | Top-N recommendations |
| `POST` | `/api/v1/profiles/{id}/preference-refinements` | Submit a natural-language refinement |
| `POST` | `/api/v1/profiles/{likerId}/likes` | Record a like; returns `{"mutualMatch": bool}` |
| `GET` | `/api/v1/profiles/{id}/liked-by` | Profiles who have liked this one, most recent first |
| `GET` | `/api/v1/evaluation/report` | Ground-truth evaluation report (average score per label) |

All recommendations/refinements are scored via the app's single compatibility-scoring method
(reciprocal harmonic mean) — there is no `strategy` parameter to select between alternatives.
`scope` accepts `GLOBAL` (default), `COUNTRY`, or `CITY` — see "Location filtering" above.

## Running locally

### Fastest path: Docker infra + run backend from your IDE

```bash
docker compose up -d   # starts MongoDB + optional Mongo Express web UI
```

Then run/debug `CompatmeApplication` from your IDE with `MONGODB_URI=mongodb://localhost:27017/compatme`
and `GEMINI_API_KEY=<your key>` set as environment variables. Full instructions (including how to
run the backend as a container too, if you'd rather): see **[STARTUP.md](STARTUP.md)**.

### Manual setup

### Prerequisites

- Java 21+ (project builds with `java.version=21`)
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
| `NOMINATIM_BASE_URL` | no | `https://nominatim.openstreetmap.org` | Reverse-geocoding endpoint base URL |
| `NOMINATIM_USER_AGENT` | no | see `application.yml` | **Must** identify your app per Nominatim's usage policy |
| `NOMINATIM_MIN_REQUEST_INTERVAL_MILLIS` | no | `1100` | Client-side guard for Nominatim's 1 req/sec limit |
| `SEED_DATA_ENABLED` | no | `false` | Set `true` to seed sample profiles at startup |
| `GROUND_TRUTH_IMPORT_ENABLED` | no | `false` | Set `true` to (re-)import `ground-truth.json` at startup |

### Run

```bash
export GEMINI_API_KEY=your-api-key
export MONGODB_URI=mongodb://localhost:27017/compatme   # or an Atlas SRV URI
./mvnw spring-boot:run
```

To seed a handful of sample English-language profiles (and generate their embeddings) at
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
