# Recommendation test scenario (`test_users.csv`)

Run the app with the `test` profile so `OkCupidProfileDataLoader` imports
`src/main/resources/test_users.csv` and generates embeddings for every row:

```bash
SPRING_PROFILES_ACTIVE=test ./mvnw spring-boot:run      # Mongo + GEMINI key required
python3 scenario/run_scenario.py                         # BASE_URL defaults to http://localhost:8082
```

Use a clean DB (`TEST_MONGODB_URI` → empty `test` database) so no other profiles leak into results.

## Cast

| Profile | Age | Sex/orient. | Location | Scope | Wants age | Purpose |
|---|---|---|---|---|---|---|
| Alice_City / _Country / _World | 27 | f / straight | Kyiv, UA | CITY / COUNTRY / WORLDWIDE | 25–33 | Requesters: introvert, wants a confident, caring lead |
| Oleh_Caring | 30 | m / straight | Kyiv, UA | CITY | 24–34 | **Ideal match** (outgoing, wants a quiet person to open up) |
| Sam_Social | 29 | m / straight | Kyiv, UA | CITY | 24–35 | One-directional: fits what Alice wants, but he wants a party-goer |
| Taras_NoMatch | 28 | m / straight | Kyiv, UA | CITY | 25–32 | Passes filters, semantically unrelated (family values) |
| Petro_TooOld | 52 | m / straight | Kyiv, UA | CITY | 40–55 | Hard filter: outside Alice's age range |
| Yuri_NarrowPref | 29 | m / straight | Kyiv, UA | CITY | 35–45 | Hard filter: Alice (27) outside **his** age range |
| Denys_WrongOrientation | 29 | m / gay | Kyiv, UA | CITY | 24–34 | Hard filter: does not seek women |
| Olena_WrongGender | 28 | f / straight | Kyiv, UA | CITY | 25–33 | Hard filter: same gender as Alice |
| Serhii_Lviv | 31 | m / straight | Lviv, UA | CITY | 25–35 | Oleh-like text, other city → only COUNTRY / WORLDWIDE |
| Mark_Canada | 30 | m / straight | Vancouver, CA | WORLDWIDE | 24–36 | Oleh-like text, other country → only WORLDWIDE |

## Expectations

| Requester | Must contain | Must NOT contain | Ranking |
|---|---|---|---|
| Alice_City | Oleh, Sam, Taras | Petro, Yuri, Denys, Olena, Serhii, Mark, other Alices | Oleh #1; Oleh > Sam > Taras |
| Alice_Country | + Serhii | Mark (+ all hard-filtered) | Oleh/Serhii in top 2; both > Sam > Taras |
| Alice_World | + Serhii, Mark | hard-filtered ones | Oleh/Serhii/Mark in top 3; all > Sam > Taras |
| Alice_City `?scope=WORLDWIDE` | same as Alice_World | — | query scope overrides stored scope |
| Oleh_Caring | Alice_City/Country/World, Olena | Sam, Taras, … (men) | the three Alices tie on score (identical text) |

Score checks (per item): all scores in [0,1]; for Sam, `scoreAtoB` (Alice→Sam: Sam fits Alice's wish)
should be noticeably higher than `scoreBtoA` (Sam→Alice); `aggregatedScore` ≤ max(directional scores)
(reciprocal harmonic penalises one-sided fit).

## Known gap this scenario catches

`RecommendationService.ageFilterCenteredOn` only applies the **requester's** age range. The candidate's own
range is never checked, so Yuri_NarrowPref (wants 35–45) is still recommended to 27-year-old Alice.
The `Yuri` check is expected to FAIL until age preference is made mutual.
