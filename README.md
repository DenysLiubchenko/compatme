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
- **`photoUrns`** (optional, max 5): user-entered URL/URN/key references stored as strings only.
  No image bytes are stored and the backend never fetches, renders, moderates, analyzes, or sends
  them to a vision API. Legacy `photoUrl` is retained for existing records/API clients but is also
  a stored-only reference and is not fetched or analyzed.

## OkCupid CSV import

`src/main/resources/okcupid_profiles.csv` is the active test-data source. It contains 10,000
eligible profiles. Preprocessing skipped 3,038 source rows with a blank `essay0` or `essay9` before
retaining 10,000 valid rows. `essay0` maps to `selfDescription`, `essay9` maps to
`preferenceDescription`, and `essay1` through `essay8` were discarded completely.

The source `location` column was replaced by `country` and `city`. Source values use a US
`city, state` format; generated rows use `country: "United States"` and the city portion as
`city` (for example, `san francisco, california` becomes `San Francisco`, `United States`). State
is not misrepresented as a country. The CSV has no photo data, so imported `photoUrns` is empty.

Import is disabled by default. To seed the data without calling Gemini:

```bash
export OKCUPID_IMPORT_ENABLED=true
export GEMINI_API_KEY=your-key
export MONGODB_URI=mongodb://localhost:27017/compatme
./mvnw spring-boot:run
```

The importer logs imported and skipped counts. CSV optional attributes are populated from their
source columns and the importer deliberately does not call the Gemini attribute extractor or
embedding generator. Imported profiles therefore have no embeddings and are excluded from
recommendations until embeddings are generated separately. Profile ids are deterministic per
retained CSV row, making reruns idempotent.

## Profile schema and extraction

Required profile fields are name, age, sex, orientation, country, city, self-description, and
partner preference. Orientation is `STRAIGHT`, `GAY`, `BISEXUAL`, or `OTHER`; unknown dataset
values map to `OTHER`. Optional attributes map directly from corresponding CSV columns when
present. For profiles created through the API or Telegram, Gemini may extract optional attributes
from the two descriptions only when directly and unambiguously stated. It must not infer sensitive
traits or guess; missing/ambiguous values stay null or empty, and extraction failure does not
block profile creation. Only `selfDescription` and `preferenceDescription` are embedded or scored.

`photoUrns` is an optional list of plain URN/key references. The backend never fetches, analyzes,
moderates, or processes referenced photos and makes no vision API calls.

## Telegram user ids

`telegramUserId` is optional and unique when present (`@Indexed(unique = true, sparse = true)` on
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
4. **Sex** — inline keyboard, single choice (Male / Female / Non-binary)
5. **Orientation** — inline keyboard, single choice (Straight / Gay / Bisexual / Other)
6. **Seeking genders** — inline keyboard, multi-select (tapping toggles a ✅ marker by editing the
   same message, not sending a new one); "Continue" requires at least one selection
7. **Location** — inline choice "📍 Share My Location" or "✍️ Enter Manually":
   - *Share*: switches to a reply keyboard with Telegram's native location-request button; the
     resulting coordinates are reverse-geocoded (see below) into a country/city, shown as an
     inline "is this correct?" confirmation before anything is written as final; on failure, falls
     back to manual entry with an explanation
   - *Manual*: free-text country, then free-text city
8. **About me** / **About you** — free text, minimum 10 characters. Prompts suggest useful topics
   (lifestyle, education/work, pets, languages, family plans, habits/preferences). Optional
   attributes are only extracted when directly stated; Gemini is instructed not to infer and
   leaves unstated/ambiguous values empty.
9. **Photo URL (optional, up to 5)** — inline choice "🔗 Add Photo URL" or "⏭ Skip for now". The
   user enters an HTTP(S) URL; only the reference string is stored. The backend never requests or
   analyzes the URL or image.
10. **Review & confirm** — formatted summary with a "✅ Looks good, save it!" button plus separate
   "✏️ Edit ..." buttons per field group (Name, Age, Sex, Orientation, Location, Descriptions,
   Photos). Editing a field group jumps back to its first step and, on completion, returns directly
   to Review — every other already-collected field is left untouched. "✏️ Edit Photos" opens a
   small management view showing the current URL references, "🗑 Remove URL N" per reference,
   "➕ Add URL" (re-enters the same free-text URL step), and "✅ Done" back to Review.
11. **Save** — maps the collected fields onto the existing `POST /api/v1/profiles` +
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
  it as text, with name,
  age, location, self-description, and preference-description. "✏️ Edit My Profile" re-enters
  Review, prefilled from the saved profile; "⬅️ Back to Menu" returns here.
- **💘 My Matches** — fetches top candidates via the existing recommendation use case (no scoring
  logic duplicated) and presents them one at a time, Tinder-style: name/age/
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

**`photoUrns`** (`List<String>`, optional, max 5): plain URL/URN/key references entered by the
user. The backend only stores and returns these strings. It does not dereference URLs, download
bytes, render remote images, moderate or analyze photos, or call any vision API. Telegram shows a
count of references on text cards; it does not send media.

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

`POST /api/v1/profiles` and `PUT /api/v1/profiles/{id}` require `displayName`, `age`, `gender`,
`orientation`, `country`, `city`, `selfDescription`, and `preferenceDescription`. Optional
attributes may be supplied in `optionalFields`; otherwise Gemini extracts only explicitly stated
details from the two descriptions. Example:

```json
{
  "displayName": "Alex",
  "age": 28,
  "gender": "MALE",
  "orientation": "STRAIGHT",
  "country": "United States",
  "city": "San Francisco",
  "seekingGenders": ["FEMALE"],
  "selfDescription": "About me: I work as a teacher, speak English and Spanish, and have a dog.",
  "preferenceDescription": "About you: I am looking for a kind woman who enjoys the outdoors.",
  "optionalFields": {
    "job": "teacher",
    "speaks": ["English", "Spanish"],
    "pets": "has a dog"
  },
  "photoUrns": []
}
```

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
| `OKCUPID_IMPORT_ENABLED` | no | `false` | Set `true` to import up to 10,000 valid OkCupid profiles; no Gemini calls |

### Run

```bash
export GEMINI_API_KEY=your-api-key
export MONGODB_URI=mongodb://localhost:27017/compatme   # or an Atlas SRV URI
./mvnw spring-boot:run
```

To import the bundled OkCupid profiles at startup, export `OKCUPID_IMPORT_ENABLED=true` before
running. The import does not call Gemini. Imported profiles have no embeddings; generate those
later through the existing embeddings endpoint if needed for recommendations.

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
verify reciprocal harmonic aggregation directly, per the hexagonal architecture requirement
that this logic be testable in complete isolation.

## Notes on scope

- Authentication/authorization is intentionally out of scope; endpoints take a profile id
  explicitly rather than deriving it from a session.
- User account and dating-profile data are modeled as a single combined entity (`Profile`).
- Candidate scoring is done in-memory in Java rather than via MongoDB Atlas Vector Search, which
  keeps the prototype runnable on a free-tier/local MongoDB instance; this is a deliberate,
  documented tradeoff appropriate for a thesis prototype's expected data scale (hundreds to a few
  thousand profiles).
