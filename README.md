# PR Sense

An AI-powered GitHub App that reviews pull requests and posts inline code-review comments directly on the diff.

[**Install on GitHub →**](https://github.com/apps/pr-sense) · [Live service](https://pr-sense.onrender.com/health)

---

## What it does

When a pull request is opened or updated, PR Sense fetches the diff, asks Gemini to review it, and posts the findings as inline comments on the exact lines that need attention — plus a summary table on the PR.

Any GitHub account can install it. There is no per-user setup, no OAuth flow, and no configuration required.

### Example

On a PR that introduces a SQL injection vulnerability, the review comment appears inline:

> **High** · `Security`
>
> Directly concatenating string variables into SQL statements creates a SQL injection vulnerability. Use parameterized queries instead.

And the PR receives a summary:

> The PR introduces a SQL injection vulnerability in `UserService.java`. This should be resolved before merging.
>
> **Findings** — 2 total · 2 high
>
> | # | Severity | Category | Location | Issue |
> |---|:--------:|:--------:|----------|-------|
> | 1 | High | Security | `UserService.java:11` | Directly concatenating string variables into SQL statements creates a SQL injection vulnerability. |
> | 2 | High | Security | `UserService.java:16` | `String.hashCode()` is non-cryptographic and insecure for passwords. Use bcrypt, Argon2, or PBKDF2. |

---

## How it works

```
GitHub ──webhook──▶ /webhook
                       │
                       ├─ 1. Verify HMAC-SHA256 signature over raw bytes
                       ├─ 2. Return 202 immediately (GitHub times out at 10s)
                       │
                       ▼  (async, on reviewExecutor thread pool)
                       ├─ 3. Mint a short-lived JWT signed with the App's private key
                       ├─ 4. Exchange JWT for a per-installation access token (cached)
                       ├─ 5. Fetch the PR diff
                       ├─ 6. Parse the diff for commentable new-side line numbers
                       ├─ 7. Send the diff to Gemini with a JSON response schema
                       ├─ 8. Validate and snap each returned line number
                       └─ 9. POST the review with inline comments
```

---

## Key design decisions

**Insert-first idempotency.** Every review is recorded by `(repo, PR, commit SHA)` in a PostgreSQL table with a unique constraint. The insert happens *before* the LLM call. If two webhooks race, the loser hits `DataIntegrityViolationException` and exits. No application-level locking, no in-memory state.

**Diff-aware line validation.** GitHub rejects the entire review with HTTP 422 if even one commented line falls outside the diff. A unified-diff parser walks hunk headers and tracks the new-side line counter — added (`+`) and context (` `) lines advance it, removed (`-`) lines do not. Each LLM-returned line number is validated against this set and snapped to the nearest valid line. If GitHub still rejects the batch, it falls back to a single summary comment.

**Constrained LLM output.** Gemini is invoked with a `responseSchema` that forces structured JSON — `{ summary, comments: [{ path, line, category, severity, comment }] }` — instead of free-form text. This removes an entire class of parsing failures.

**Two-stage authentication.** GitHub Apps authenticate as the App (RS256 JWT, 10-minute expiry) to obtain a per-installation token (1-hour expiry, scoped to that installation's repos). Tokens are cached in memory and refreshed at 55 minutes to stay within GitHub's app-level token-creation throttling.

**Async pipeline.** The webhook returns `202 Accepted` immediately, before any LLM work begins. Review generation runs on a dedicated thread pool. Without this, GitHub's 10-second delivery timeout would fire on every slow Gemini response.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 |
| AI | Google Gemini 3.6 Flash (`generateContent` with response schema) |
| Auth | GitHub App JWT (`jjwt`), BouncyCastle for PEM parsing |
| Database | PostgreSQL (Aiven) via Spring Data JPA / Hibernate |
| HTTP | Spring `RestTemplate` on JDK `HttpClient` |
| Build | Maven |
| Container | Docker (two-stage build) |
| Hosting | Render (free tier) |

---

## Installation

1. Go to [github.com/apps/pr-sense](https://github.com/apps/pr-sense)
2. Click **Install**
3. Choose the account or organization, and either grant access to all repositories or select specific ones
4. Open a pull request — the review appears automatically

No signup, no API key, no configuration.

---

## Local development

### Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 16+
- A GitHub App ([create one](https://docs.github.com/en/apps/creating-github-apps))
- A Gemini API key ([get one](https://aistudio.google.com/app/apikey))

### Setup

```bash
git clone https://github.com/SaiRamNamana/pr-sense.git
cd pr-sense
```

Create a database:

```bash
createdb prsense
```

Set the following environment variables. For local development you can put them in your IDE's run configuration, or export them in your shell:

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/prsense"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="yourpassword"
export GEMINI_API_KEY="your-gemini-key"
export GITHUB_APP_ID="your-app-id"
export GITHUB_WEBHOOK_SECRET="your-webhook-secret"
export GITHUB_APP_PRIVATE_KEY="/absolute/path/to/private-key.pem"
```

`GITHUB_APP_PRIVATE_KEY` accepts either a filesystem path or an inline PEM with `\n` escapes — the same code handles both, so production can use inline values while local dev uses a file.

Run:

```bash
./mvnw spring-boot:run
```

The service listens on `http://localhost:8080`. Health check at `/health`.

### Exposing your local instance to GitHub

GitHub needs a public URL to deliver webhooks. For local development:

```bash
ngrok http 8080
```

Then set your GitHub App's **Webhook URL** to `https://<your-ngrok-id>.ngrok-free.app/webhook`. Note that ngrok's free tier rotates the URL on restart, so you'll update this each session.

---

## Configuration reference

| Environment variable | Required | Description |
|---|:---:|---|
| `SPRING_DATASOURCE_URL` | Yes | JDBC URL. Must start with `jdbc:` |
| `SPRING_DATASOURCE_USERNAME` | Yes | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Database password |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | No | Defaults to `update`. Set to `validate` once migrations are in place |
| `GEMINI_API_KEY` | Yes | Google AI Studio API key |
| `GEMINI_MODEL` | No | Defaults to `gemini-3.6-flash` |
| `GITHUB_APP_ID` | Yes | Numeric App ID from GitHub App settings |
| `GITHUB_WEBHOOK_SECRET` | Yes | Must match the secret configured in GitHub App settings |
| `GITHUB_APP_PRIVATE_KEY` | Yes | PEM contents (inline with `\n`) or absolute file path |

---

## Deployment

The service is containerized with a two-stage Docker build:

```bash
docker build -t pr-sense .
docker run --rm -p 8080:8080 --env-file .env pr-sense
```

Production runs on Render's free tier with PostgreSQL hosted on Aiven. The health check endpoint at `/health` is used by Render to determine readiness.

**Note on cold starts.** Render's free tier suspends idle services after 15 minutes. The first webhook after a suspension takes 20–60 seconds to wake the container, which exceeds GitHub's 10-second delivery timeout. GitHub retries automatically, so reviews still post — just delayed. A free uptime monitor pinging `/health` every 5 minutes keeps the service warm within the free tier's monthly instance-hour allowance.

---

## Project structure

```
src/main/java/com/sairam/pr_sense/
├── Controllers/
│   └── WebhookController.java          # Signature verification, event routing, /health
├── Services/
│   ├── GithubAuthService.java          # JWT signing, installation token exchange
│   ├── InstallationTokenCache.java     # In-memory token cache with expiry
│   ├── ReviewService.java              # Gemini prompt, response schema, validation
│   ├── GithubCommentService.java       # Review posting with 422 fallback
│   └── PullRequestReviewOrchestrator.java  # Async pipeline coordination
├── DTO/
│   ├── PullRequestEvent.java           # Webhook payload
│   ├── InstallationEvent.java          # App installation webhook
│   ├── ReviewResult.java               # Parsed Gemini response
│   └── ReviewComment.java              # Single comment record
├── model/
│   └── ReviewedCommit.java             # Idempotency entity
├── repository/
│   └── ReviewedCommitRepository.java   # Spring Data JPA repository
├── util/
│   └── DiffParser.java                 # Unified-diff line number mapping
└── Config/
    └── HttpConfig.java                 # RestTemplate and async executor beans
```

---

## Limitations

- **Single-instance deployment.** Token caching and async execution are in-process. Horizontal scaling would need a shared cache (Redis) and a distributed lock or queue for idempotency — the database constraint already handles the latter safely.
- **Sequential reviews.** Large PRs are truncated at 60,000 characters before being sent to Gemini. Files matching common generated-code patterns are not currently excluded.
- **No repository-level configuration.** Every installation gets the same review behavior. Per-organization settings (severity thresholds, ignored paths, custom prompts) are not implemented.
- **Gemini quota.** On the free tier, requests are rate-limited. A busy repository can exhaust the daily quota.

---

## License

MIT
