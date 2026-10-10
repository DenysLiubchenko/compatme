# CompatMe — End-to-End Test Scenario

Covers the full flow: profile CRUD + validation → embeddings → hard filters (age, gender/orientation,
location scope) → NLP ranking (reciprocal harmonic mean) → preference refinement → likes / mutual match
→ photos → error handling.

## 0. Setup

```bash
docker compose up -d                      # Mongo + MinIO
SPRING_PROFILES_ACTIVE=default ./mvnw spring-boot:run   # app.okcupid.enabled=false, EMPTY database
export API=http://localhost:8082/api/v1/profiles
```

Rules for this scenario:

- Start from an **empty** database — any extra profile can appear in recommendations and break the expected order.
- Create every profile via `POST $API` and save the returned `id` (`ANNA`, `MAX`, …).
- **Always send `seekingGenders` explicitly.** The REST API does not derive it from `orientation`;
  an empty set means "matches everyone" (`Profile.matchesSeekingGender`).
- Scores come from real Gemini embeddings. Absolute values vary; the **relative order** and the
  **directional asymmetries** below are what must hold. Where two candidates are intentionally close,
  the order is given as a band (`≈`) and only the band boundaries are asserted.

Scoring reminder (`CompatibilityScorer`):

```
A→B  = cos(pref_A, self_B)     "does B look like what A wants?"
B→A  = cos(pref_B, self_A)     "does A look like what B wants?"
aggregated = 2·A→B·B→A / (A→B + B→A)   (0 if either ≤ 0)
```

---

## 1. Cast

Main requester is **Anna**. Everyone else is designed to land at a specific place in her list, or to
be removed by a specific filter.

| Key | Name | Age | Gender | Orientation | seekingGenders | Country / City | Scope | Pref. age | Role |
|---|---|---|---|---|---|---|---|---|---|
| ANNA | Anna | 28 | FEMALE | STRAIGHT | [MALE] | Ukraine / Kyiv | CITY | 25–34 | **Requester** |
| MAX | Max | 30 | MALE | STRAIGHT | [FEMALE] | Ukraine / Kyiv | CITY | 24–32 | Perfect mutual match |
| IVAN | Ivan | 31 | MALE | STRAIGHT | [FEMALE] | Ukraine / Kyiv | CITY | 25–33 | Good mutual match, less specific |
| ANDRII | Andrii | 29 | MALE | BISEXUAL | [MALE, FEMALE] | Ukraine / Kyiv | CITY | 24–35 | Bisexual — must pass gender filter, moderate fit |
| DMYTRO | Dmytro | 27 | MALE | STRAIGHT | [FEMALE] | Ukraine / Kyiv | CITY | 22–30 | One-sided: Anna would like him, he wants a party girl |
| BOHDAN | Bohdan | 32 | MALE | STRAIGHT | [FEMALE] | Ukraine / Kyiv | CITY | 25–35 | One-sided: he wants someone like Anna, he is not what she wants |
| KOSTYA | Kostiantyn | 30 | MALE | STRAIGHT | [FEMALE] | Ukraine / Kyiv | CITY | 22–32 | Unrelated both ways — bottom |
| NAZAR | Nazar | 29 | MALE | STRAIGHT | [FEMALE] | Ukraine / Lviv | CITY | 25–33 | Max-quality match, other city |
| LIAM | Liam | 31 | MALE | STRAIGHT | [FEMALE] | Germany / Berlin | WORLDWIDE | 25–35 | Strong match, other country |
| VIKTOR | Viktor | 45 | MALE | STRAIGHT | [FEMALE] | Ukraine / Kyiv | CITY | 25–40 | ✗ outside Anna's age range |
| ROMAN | Roman | 30 | MALE | STRAIGHT | [FEMALE] | Ukraine / Kyiv | CITY | 20–24 | ✗ Anna outside **his** age range |
| ARTEM | Artem | 29 | MALE | GAY | [MALE] | Ukraine / Kyiv | CITY | 24–34 | ✗ does not seek women |
| SOFIA | Sofia | 27 | FEMALE | STRAIGHT | [MALE] | Ukraine / Kyiv | CITY | 25–35 | ✗ for Anna (gender); candidate for Max |

### 1.1 About me / About you

**Anna** (requester)
- *About me:* I'm a children's book illustrator, so most of my days are quiet and spent drawing with a cup of tea next to me. I'm a bit of an introvert — I recharge with long walks in the forest, film photography and slow Sunday mornings. I love museums, used bookstores and cooking something new on weekends. With people I'm warm but take time to open up.
- *About you:* I'm looking for a calm, curious and kind man who enjoys nature and hiking, reads a lot and likes deep conversations more than loud parties. Someone patient and emotionally mature, who would rather plan a weekend trip to the mountains than a night at a club.

**Max** — perfect mutual
- *About me:* I'm a calm and curious software engineer who spends every free weekend hiking in the Carpathians. I read a lot — mostly history and science fiction — and I'd much rather have a long, deep conversation over dinner than go to a loud party. Friends say I'm patient, kind and a good listener.
- *About you:* I'd love to meet a gentle, creative woman — maybe an artist or someone who draws or takes photos — who is a little introverted, enjoys quiet mornings, forest walks, museums and bookstores, and opens up slowly but warmly.

**Ivan** — good, but generic
- *About me:* I'm a high school biology teacher. I'm easygoing and kind, I enjoy reading in the evenings, cycling around the city and an occasional trip out of town. I like honest conversations and a calm pace of life.
- *About you:* Looking for a kind and thoughtful woman who enjoys simple things — books, walks, cooking together — and values honesty and a calm home life.

**Andrii** — bisexual, moderate fit
- *About me:* I work as a barista and play bass in a small indie band. I'm friendly, a bit chaotic, love vinyl records, street food festivals and late-night talks about music. I like going to concerts a couple of times a month.
- *About you:* Someone creative and open-minded who appreciates music and art, and won't mind spending some evenings at small gigs and some at home listening to records.

**Dmytro** — Anna likes him, he doesn't want her
- *About me:* I'm a calm, well-read guy who loves hiking and mountains, I do trail running and spend a lot of time in nature. Patient, kind, I enjoy thoughtful conversations.
- *About you:* I want a super outgoing, energetic girl who loves nightlife, clubbing, festivals and big parties every weekend, the louder the better, and who is always the center of attention.

**Bohdan** — he wants her, she doesn't want him
- *About me:* I'm a nightclub promoter and DJ. I live for loud parties, festivals, clubbing until sunrise and huge crowds. I'm always on the move, I get bored with quiet evenings and I rarely read.
- *About you:* I'm looking for a quiet, introverted, artistic woman who draws or takes photos, loves forest walks, museums, bookstores and slow mornings with tea — someone to balance my crazy lifestyle.

**Kostiantyn** — unrelated
- *About me:* I'm a crypto trader and gym enthusiast. Most of my day is charts, protein shakes and heavy lifting. I drive a sports car and I'm focused on making money fast.
- *About you:* Looking for a fitness-oriented woman who trains at the gym every day, follows a strict diet and is interested in investing and luxury cars.

**Nazar** (Lviv) — top-tier, other city
- *About me:* I'm a calm, curious architect who reads constantly and goes hiking in the mountains almost every weekend. I prefer deep conversations to noisy parties, and people describe me as patient, gentle and emotionally mature.
- *About you:* I hope to meet a creative, slightly introverted woman — an illustrator or a photographer — who loves forest walks, museums, old bookstores, cooking and quiet Sunday mornings.

**Liam** (Berlin) — strong, other country
- *About me:* I'm a quiet and curious museum curator. On weekends I go hiking in the Alps or wander around second-hand bookstores. I'm kind, patient, and I like slow, meaningful conversations.
- *About you:* Looking for an artistic, gentle woman who enjoys nature, books, film photography and calm weekends rather than nightlife.

**Viktor** (45) — age filter
- *About me:* Calm, well-read, love hiking and long conversations. Patient and kind.
- *About you:* A gentle, creative, introverted woman who loves nature, books and museums.

**Roman** — his age range excludes Anna
- *About me:* Calm and curious, I hike a lot, read every evening and prefer deep talks to parties.
- *About you:* A creative, quiet woman who loves forest walks, museums and bookstores.

**Artem** (gay) — orientation filter
- *About me:* Calm graphic designer, I love hiking, museums and reading.
- *About you:* A kind, curious man who enjoys nature and long conversations.

**Sofia** — gender filter for Anna; candidate for Max
- *About me:* I'm a marketing manager, very social, I love parties, rooftop bars, travelling with big groups of friends and trying every new restaurant in town.
- *About you:* Looking for an outgoing, ambitious man who loves nightlife, travel and spontaneous adventures.

> Viktor / Roman / Artem deliberately reuse Max-like text: if any of them shows up, it is the **filter**
> that failed, not the NLP.

---

## 2. Profile management & validation

| # | Step | Expected |
|---|---|---|
| 2.1 | `POST $API` for all 13 profiles | `201/200`, body has `id`, `hasSelfEmbedding=false`, `hasPreferenceEmbedding=false` |
| 2.2 | `GET $API/{ANNA}` | fields equal the request; `seekingGenders=[MALE]`, `searchScope=CITY`, `minPreferredAge=25`, `maxPreferredAge=34` |
| 2.3 | `GET $API` | exactly 13 profiles |
| 2.4 | `POST $API` with `age: 17` | `400`, error mentions `age must be at least 18` |
| 2.5 | `POST $API` without `country` | `400`, `country must not be blank` |
| 2.6 | `POST $API` without `gender` | `400`, `sex is required` |
| 2.7 | `POST $API` with blank `selfDescription` | `400` |
| 2.8 | `POST $API` with `minPreferredAge: 40, maxPreferredAge: 30` | `400` (invalid range) |
| 2.9 | `GET $API/00000000-0000-0000-0000-000000000000` | `404` |
| 2.10 | `GET $API/{ANNA}/recommendations` **before** embeddings | `409` — "has no embeddings yet" |

## 3. Embeddings

| # | Step | Expected |
|---|---|---|
| 3.1 | `POST $API/{id}/embeddings` for all 13 | `200`; then `GET` shows `hasSelfEmbedding=true`, `hasPreferenceEmbedding=true` |
| 3.2 | `POST` embeddings again for ANNA | succeeds, idempotent (no change in later scores) |
| 3.3 | `PUT $API/{IVAN}` changing only `city` | embeddings **kept** (text unchanged) |
| 3.4 | `PUT $API/{IVAN}` changing `selfDescription` (add one sentence), then revert it back to the original text and regenerate | after the change: `hasSelfEmbedding=false`, `hasPreferenceEmbedding=true`; Ivan disappears from Anna's list until regenerated; after revert + regenerate he is back |

## 4. Recommendations for Anna — filters and order

Call `GET $API/{ANNA}/recommendations?topN=20`.

### 4.1 Scope CITY (stored default)

**Expected candidates & order**

| Rank | Candidate | Why | A→B | B→A |
|---|---|---|---|---|
| 1 | **Max** | both texts mirror each other | high | high |
| 2 | **Ivan** | generic but aligned both ways | mid-high | mid-high |
| 3–5 | **Andrii ≈ Dmytro ≈ Bohdan** | Andrii: moderate both ways; Dmytro & Bohdan: one-sided, harmonic mean pulls them down | — | — |
| 6 | **Kostiantyn** | unrelated both ways | low | low |

**Must NOT appear:** Viktor (age 45 > 34), Roman (Anna 28 not in his 20–24 — *see §9*), Artem (seeks MALE),
Sofia (FEMALE, Anna seeks MALE), Nazar (Lviv), Liam (Germany).

**Directional assertions**
- Dmytro: `scoreAtoB` **>** `scoreBtoA` (he fits Anna's wish; she doesn't fit his).
- Bohdan: `scoreBtoA` **>** `scoreAtoB` (Anna fits his wish; he doesn't fit hers).
- Max: `aggregatedScore` > Dmytro's and Bohdan's `aggregatedScore`, even though Dmytro's `scoreAtoB` may be close to Max's.
- For every item: `aggregatedScore ≤ max(scoreAtoB, scoreBtoA)` and `aggregatedScore ≥ min(...)`.
- Kostiantyn has the lowest `aggregatedScore`.

### 4.2 Scope COUNTRY — `?scope=COUNTRY`

| Rank | Candidate |
|---|---|
| 1–2 | **Max ≈ Nazar** |
| 3 | Ivan |
| 4–6 | Andrii ≈ Dmytro ≈ Bohdan |
| 7 | Kostiantyn |

Must NOT appear: Liam + all hard-filtered.

### 4.3 Scope WORLDWIDE — `?scope=WORLDWIDE`

| Rank | Candidate |
|---|---|
| 1–3 | **Max ≈ Nazar ≈ Liam** |
| 4 | Ivan |
| 5–7 | Andrii ≈ Dmytro ≈ Bohdan |
| 8 | Kostiantyn |

Must NOT appear: Viktor, Roman, Artem, Sofia.

### 4.4 Stored scope + topN

| # | Step | Expected |
|---|---|---|
| 4.4.1 | `PUT $API/{ANNA}` with `searchScope: WORLDWIDE` (texts unchanged), then call without `scope` param | same list as 4.3; embeddings kept |
| 4.4.2 | same call with `?scope=CITY` | same list as 4.1 (query param overrides stored scope) |
| 4.4.3 | `?topN=2&scope=WORLDWIDE` | exactly 2 items, both from {Max, Nazar, Liam} |
| 4.4.4 | Restore Anna to `searchScope: CITY` | — |

## 5. Recommendations from the other side

| Requester | Expected list (order) | Must NOT appear |
|---|---|---|
| **Max** (seeks F, 24–32, CITY) | 1. **Anna** 2. **Sofia** | all men; Anna's score here equals Max's score in Anna's list with A→B/B→A **swapped** |
| **Andrii** (bi, seeks M+F, 24–35) | Anna, Sofia, and men who also seek MALE: **Artem** only | straight men (they don't seek MALE) |
| **Artem** (gay) | **Andrii** only | everyone else |
| **Sofia** (seeks M, 25–35, CITY) | Dmytro (wants a party girl, highest B→A for her) near top; **Bohdan** high A→B (party DJ) — expected top 2: **Bohdan ≈ Dmytro**; Max, Ivan, Kostiantyn, Andrii lower | Viktor (45), Roman (her 27 ∉ 20–24 — *§9*), Artem, Anna, Nazar, Liam |

Symmetry check: for pair (Anna, Max), `Anna→Max.scoreAtoB == Max→Anna.scoreBtoA` and vice versa
(same embeddings), and `aggregatedScore` is identical.

## 6. Preference refinement

`POST $API/{ANNA}/preference-refinements`

```json
{ "message": "Actually I want someone much more outgoing and adventurous, who loves festivals and going out, not a homebody.", "topN": 20 }
```

| # | Expected |
|---|---|
| 6.1 | `200`; `updatedPreferenceDescription` mentions outgoing / festivals / going out; `changeSummary` non-empty |
| 6.2 | `GET $API/{ANNA}` → `preferenceDescription` equals `updatedPreferenceDescription`; both embeddings still present |
| 6.3 | For every candidate, **`scoreBtoA` is unchanged** vs §4.1 (Anna's self text and candidates' embeddings were not touched) |
| 6.4 | `scoreAtoB` **rises** for Bohdan and Andrii, **falls** for Max, Ivan, Dmytro |
| 6.5 | Bohdan moves up (expected rank ≤ 3); Max stays in the list but drops below his §4.1 score |
| 6.6 | Filters unchanged: same set of candidates as §4.1 |
| 6.7 | `message: ""` → `400`; `topN: 0` → `400` |
| 6.8 | Reset: `PUT $API/{ANNA}` with the original *About you*, `POST .../embeddings` → §4.1 order is restored |

## 7. Likes & mutual match

| # | Step | Expected |
|---|---|---|
| 7.1 | `POST $API/{ANNA}/likes` `{"likedProfileId": MAX}` | `{"mutualMatch": false}`; Max gets "new like" notification (if Telegram id set) |
| 7.2 | `GET $API/{MAX}/liked-by` | `[Anna]` |
| 7.3 | `GET $API/{ANNA}/liked-by` | `[]` |
| 7.4 | `POST $API/{MAX}/likes` `{"likedProfileId": ANNA}` | `{"mutualMatch": true}`; both get "mutual match" notification |
| 7.5 | Repeat 7.4 | `{"mutualMatch": true}`, **no** duplicate like, **no** second notification |
| 7.6 | `POST $API/{KOSTYA}/likes` → ANNA | `mutualMatch=false`; `GET $API/{ANNA}/liked-by` = {Max, Kostiantyn} |
| 7.7 | `POST $API/{ANNA}/likes` → unknown id | Expected `404`. **Current code** (`LikeService.recordLike`) does not check the profile exists: it stores the like and returns `mutualMatch=false` — known gap |
| 7.8 | Likes do **not** affect scores: Anna's recommendations identical to §4.1 |

## 8. Photos

| # | Step | Expected |
|---|---|---|
| 8.1 | `POST $API/{ANNA}/photos` `-F file=@anna.jpg` (< 5 MB, image/jpeg) | `200/201`, returns URN; `GET $API/{ANNA}` → `photoUrns` contains it |
| 8.2 | `GET $API/{ANNA}/photos/{urn}` | `200`, bytes equal uploaded file, `Content-Type: image/jpeg` |
| 8.3 | Anna appears in Max's recommendations with `photoUrns` populated |
| 8.4 | Upload `.gif` / `text/plain` | `415` |
| 8.5 | Upload 6 MB image | `413` |
| 8.6 | Upload 7th photo (limit 6) | `409` |
| 8.7 | `DELETE $API/{ANNA}/photos/{urn}` then `GET` it | `204`, then `404`; URN removed from profile |

## 9. Deal-breaker checks

Set Anna's `dealBreakers.smokingStatuses` to `["YES", "SOMETIMES", "WHEN_DRINKING"]`, set Dmytro's
`optionalFields.smokes` to `YES`, regenerate neither embedding (structured attributes do not affect embeddings),
then request Anna's recommendations. Dmytro must be absent. Clear the deal-breaker and he must return at his
previous relative position. Repeat in reverse: configure a candidate's deal-breaker and verify Anna is excluded.

An unset candidate value remains eligible: a smoking deal-breaker must not exclude a candidate whose `smokes` is
`null`. This follows the selected policy that unknown values are allowed.

## 10. Resolved — mutual age preference

`RecommendationService.ageFilterCenteredOn` filters candidates only by the **requester's** preferred age
range. The candidate's range is now also checked, so **Roman** (wants 20–24) is excluded from Anna's and
Sofia's lists.
gender). Until it's fixed, record §4/§5 "Roman must NOT appear" as a known FAIL.

## 11. Telegram bot (manual, optional)

1. `/start` with a fresh account → onboarding asks name, age, gender, orientation, location, scope, age range, *about me*, *about you*, photo.
2. Enter Anna's data → profile created with real `telegramUserId`; `GET $API/by-telegram/{chatId}` returns it; embeddings generated.
3. Browse → cards follow §4.1 order (Max first). Like Max.
4. Log in as Max's Telegram account (or set Max's `telegramUserId`) → receives "new like" notification; browse → Anna first; like → both receive "mutual match".
5. Preferences menu → send the §6 message → updated description shown, browsing order changes per §6.5.
6. Settings → change scope to WORLDWIDE → Nazar and Liam appear near the top.

## 12. Pass criteria

- All hard-filter "must NOT appear" checks pass.
- Rank-1 for Anna (CITY) is Max; Kostiantyn is last; one-sided candidates (Dmytro, Bohdan) never outrank Max or Ivan.
- Directional asymmetries in §4.1 and score invariants in §6.3 hold.
- Likes, photos, validation and error codes behave as listed.
