# RealWorld API + Cucumber BDD

A Medium-clone REST API (Kotlin + Spring Boot) used as a target application for Cucumber BDD acceptance testing. Part of the Hexathon 2026 project exploring AI-powered BDD test generation.

## The App (System Under Test)

A pre-existing [RealWorld](https://github.com/gothinkster/realworld) demo API. We didn't write it, we test it. Forked from [gothinkster/kotlin-spring-realworld-example-app](https://github.com/gothinkster/kotlin-spring-realworld-example-app).

- REST API on `localhost:8080`
- H2 in-memory database
- JWT authentication
- Endpoints: users, profiles, articles, tags, comments

## Prerequisites

- Java 21 (Kotlin 1.8.21 doesn't support newer versions)
- [just](https://github.com/casey/just) command runner (`brew install just`)

## Commands

```bash
just                  # List all commands
just run              # Start the app on localhost:8080
just test             # Run all tests (unit + acceptance)
just test-unit        # Run only unit tests
just test-acceptance  # Run only acceptance tests
just lint             # Lint all Kotlin files
just format           # Auto-format all Kotlin files
just check            # Run all tests, then lint and format
```

The justfile sets `JAVA_HOME` to Java 21 automatically.

## Manual Testing

Start the app with `just run`, then hit the API with curl, Postman, or any HTTP client:

```bash
# Get all tags
curl http://localhost:8080/api/tags

# Register a user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"user": {"username": "jake", "email": "jake@example.com", "password": "password"}}'

# Login
curl -X POST http://localhost:8080/api/users/login \
  -H "Content-Type: application/json" \
  -d '{"user": {"email": "jake@example.com", "password": "password"}}'
```

The full API spec is at [RealWorld API Spec](https://realworld-docs.netlify.app/specifications/backend/endpoints/).

## Two Types of Tests

### Unit Tests (`just test-unit`)

Standard JUnit 5 tests under `src/test/kotlin/io/realworld/`. They test internal logic directly. Run by Maven **Surefire** during the `test` phase.

### Acceptance Tests (`just test-acceptance`)

Cucumber BDD tests that treat the app as a black box: boot it up, hit it over HTTP, verify the responses. Run by Maven **Failsafe** during the `verify` phase.

## Unit Test Architecture

Pure JUnit 5 tests. No Spring context, no HTTP, no database. Run by Maven **Surefire** during the `test` phase.

## Acceptance Test Architecture

Three layers, each with a clear responsibility:

```
src/test/
├── resources/acceptance/
│   └── specifications/           <- Layer 1: Gherkin
│       └── *.feature
└── kotlin/acceptance/
    ├── CucumberIT.kt             <- test runner (Failsafe picks up *IT classes)
    ├── SpringIntegrationConfig.kt
    ├── DslConfig.kt              <- scenario-scoped DslContext bean
    ├── GlobalSetupHook.kt        <- @Before(order=0) hook for shared state (e.g. JWT)
    ├── dsl/                      <- Layer 2: DSL (routing + aliasing)
    │   ├── *Dsl.kt
    │   └── utils/                <- test isolation utilities
    │       ├── DslContext.kt
    │       └── Params.kt
    └── driver/
        ├── ProtocolDriver.kt     <- interface (DSL depends on this)
        └── http/                 <- Layer 3: HTTP implementation
            └── HttpProtocolDriver.kt
```

### Layer 1: Specifications

Business-readable Gherkin scenarios. No code, no technical details.

```gherkin
Feature: Tags
  Scenario: Published tags appear in the tag list
    Given a registered user is logged in
    And the author has published an article titled "Tag Test" with tag "cucumber"
    When a client requests the list of tags
    Then the tag list includes "cucumber"
```

### Layer 2: DSL

Routes Gherkin steps to driver methods. Pure routing with no assertions, no HTTP, and no SUT knowledge. Only depends on the `ProtocolDriver` interface (injected by Spring DI).

The DSL layer also owns **test isolation** via utilities in `dsl/utils/`:

- **`DslContext`** — generates unique aliases for identity values (usernames, emails, titles, tags). `context.alias("alice")` returns `"alice1"` in the first scenario and `"alice2"` in the next. Idempotent within a scenario, globally unique across scenarios. Registered as a scenario-scoped bean so each Cucumber scenario gets a fresh instance.
- **`Params`** — convenience wrapper for extracting aliased, optional, or sequenced values from step arguments.

### Layer 3: Driver

The only layer that talks to the app. Makes HTTP calls, owns its own DTOs, does all verification. If a driver method completes without throwing, the step passes. If it throws, the step fails.

The driver holds only a single **last response** (status code + body), overwritten on every HTTP call. No accumulated state across steps. The SUT is the source of truth for state, not the test layers.

The driver sits behind a `ProtocolDriver` interface. The current implementation is HTTP (`HttpProtocolDriver`), but the same specs and DSL could run against a CLI or web UI driver without changes.

### Test Isolation

Every scenario is **functionally isolated**: it creates its own data and has no dependence on other tests. Tests can run in parallel and in any order.

The DSL layer enforces this by aliasing all identity values (usernames, emails, article titles, tags) through `DslContext`, so no two scenarios share data — even when the Gherkin uses the same readable names.

### Shared State Across Scenarios

Some setup (like user registration) is too expensive to repeat per scenario. We do it once and reuse the result.

**How the shared state gets created:**
- `GlobalSetupHook` has a `@Before(order=0)` Cucumber hook. `order=0` means it runs before any other hooks or steps in every scenario
- On the very first scenario, it registers a user via the driver and stores the JWT in a `companion object` field (`sharedToken`). It then sets `initialized = true`
- On every subsequent scenario, the hook fires again but sees `initialized == true` and returns immediately — no HTTP call, no duplicate registration
- Because this runs `@Before` every scenario, the shared state is guaranteed to exist before any step that needs it

**How a scenario uses it:**
- Gherkin says `Given a registered user is logged in` — business language, no mention of sharing
- The DSL step reads `GlobalSetupHook.sharedToken` and calls `driver.loginWithToken(token)`
- The driver sets `authToken` directly (no HTTP call) — all subsequent requests in that scenario are authenticated

**Why `@Volatile` and `companion object`:**
- `companion object` makes the field survive across scenarios (Cucumber creates a new hook instance per scenario, but companion fields are static — they persist for the whole test run)
- `@Volatile` ensures that if scenarios ever run on different threads, they always see the latest value — without it, a thread could see stale/empty data

**Adding new shared state:** follow the same pattern — add it to `GlobalSetupHook`'s companion object, guard it with the `initialized` flag, read it in the DSL, pass it to the driver.

**What NOT to share:** test data (articles, tags, comments). Every scenario creates its own data via `DslContext` aliasing. Only share expensive, idempotent setup like authentication tokens.

### Database Lifecycle

Currently the SUT uses H2 in-memory (`jdbc:h2:mem`), which starts empty on every test run and is destroyed when the JVM exits. Data accumulates during the run but each scenario's data is uniquely aliased, so there are no collisions.

**If switching to a persistent database:**
- Use a **dedicated database** for acceptance tests (not the dev/production DB)
- Clear all tables **once at the start** of each test run (before any scenarios execute)
- Do NOT clean up after each test or after all tests — aliased data accumulates safely
- Implement the cleanup as a Cucumber `@BeforeAll` hook or Maven Failsafe plugin config

### Layer Responsibilities

| Layer | Imports SUT? | Knows HTTP? | Has verification? | Owns isolation? | Holds state? |
|-------|---|---|---|---|---|
| `specifications/` (Gherkin) | No | No | No | No | No |
| `dsl/` (DSL) | No | No | No | Yes (aliasing) | Pending values within a scenario |
| `driver/http/` | No | Yes | Yes | No | Last response only |
| `SpringIntegrationConfig` | Only `ApiApplication::class` | No | No | No | No |

To swap the protocol, change the `@Import` in `SpringIntegrationConfig`. The specs and DSL stay untouched.

### Test Environment Safety

Acceptance tests are fully isolated and cannot cause side effects:
- **Database:** H2 in-memory — created when the JVM starts, destroyed when it exits. No persistent database is touched
- **Port:** Spring Boot starts on a random port (`WebEnvironment.RANDOM_PORT`) — no conflict with a running dev instance
- **Network:** Only `localhost:{random-port}` — no external services, no outbound network calls
- **Filesystem:** Tests don't write to disk. Only standard Maven `target/` output

Running `just test-acceptance` is always safe.

## Adding a New Acceptance Test

1. Create or extend a `.feature` file in `src/test/resources/acceptance/specifications/`
2. Add a `*Dsl.kt` class in `dsl/` that matches Gherkin steps and calls `ProtocolDriver` methods
3. Alias all identity values (usernames, emails, titles, tags) via `DslContext` for test isolation
4. Add the new methods to the `ProtocolDriver` interface
5. Implement them in `HttpProtocolDriver` (own DTOs, own assertions, plain `RestTemplate`)
6. If adding a new driver class, register it with `@Import` on `SpringIntegrationConfig`

Run `just test-acceptance` to verify.
