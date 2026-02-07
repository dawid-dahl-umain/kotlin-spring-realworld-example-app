# Hackathon Project: AI + Cucumber BDD

Hexathon 2026, 4-person team exploring AI-powered BDD test generation. Hackathon mode: get it working first, make it right later.

## What This Repo Is

RealWorld (Medium clone) REST API used as a **target application** for adding Cucumber BDD tests. We did not write this app; it's a pre-existing demo.

## Goal

Phase 1: Manually add Cucumber feature files and step definitions to establish patterns.
Phase 2: Use the Koog AI agent in `../gurkagent/` to auto-generate BDD tests for this repo externally.

## Commands

```bash
just                  # List all commands
just run              # Start the app on localhost:8080
just test             # Run all tests (unit + acceptance)
just test-unit        # Run only unit tests
just test-acceptance  # Run only acceptance tests
```

## Acceptance Test Architecture (Three Layers)

```
src/test/
├── resources/acceptance/
│   └── specifications/           <- Layer 1: Gherkin specs
│       └── *.feature
└── kotlin/acceptance/
    ├── CucumberIT.kt             <- test runner (Failsafe picks up *IT classes)
    ├── SpringIntegrationConfig.kt
    ├── DslConfig.kt              <- scenario-scoped DslContext bean
    ├── GlobalSetupHook.kt        <- @Before(order=0) hook for shared state (e.g. JWT)
    ├── dsl/                      <- Layer 2: DSL (routing + aliasing)
    │   ├── *Dsl.kt
    │   └── utils/                <- test isolation utilities
    │       ├── DslContext.kt     <- alias generation for unique test data
    │       └── Params.kt         <- convenience wrapper for step arguments
    └── driver/
        ├── ProtocolDriver.kt     <- interface (DSL depends on this)
        └── http/                 <- Layer 3: HTTP driver implementation
            └── HttpProtocolDriver.kt
```

## Prerequisites

- Java 21 (Kotlin 1.8.21 doesn't support newer versions; justfile sets JAVA_HOME automatically)

## Conventions

- Gherkin feature files go in `src/test/resources/acceptance/specifications/`
- DSL classes go in `src/test/kotlin/acceptance/dsl/`, named `*Dsl.kt`
- Driver implementations go in `src/test/kotlin/acceptance/driver/`
- DSL layer is pure routing: no assertions, no HTTP, no SUT knowledge
- DSL layer owns test isolation via `DslContext` aliasing
- All verification and HTTP logic live in the driver layer only
- Write Gherkin in business language, not technical details
- `ProtocolDriver` is injected into DSL classes by Spring DI (DSL never sees the HTTP implementation)
- Assertion methods in both DSL and driver use the `confirm` prefix (e.g., `confirmArticlePublished`). DSL `confirm` method names match their driver counterparts 1:1

## Test Isolation Principles

- **Every scenario creates its own data** — no shared fixtures, no dependence on other tests
- **Tests can run in parallel and in any order** — isolation is enforced, not assumed
- **DSL layer owns isolation** — DSL classes use `DslContext` to alias all identity values (usernames, emails, titles, tags) so each scenario gets globally unique data
- **Driver holds only the last response** — a single `lastResponse` field overwritten on every HTTP call, plus `authToken` for session state. No accumulated state across steps
- **`DslContext` is scenario-scoped** — registered as a `@Scope("cucumber-glue")` bean in `DslConfig`, so each Cucumber scenario gets a fresh context with its own alias namespace
- Use `context.alias("name")` for all identity values (idempotent within a scenario, unique across scenarios)

## Shared State Across Scenarios

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

## Database Lifecycle

Currently the SUT uses H2 in-memory (`jdbc:h2:mem`), which starts empty on every test run and is destroyed when the JVM exits. This gives us "clear before all" for free.

**If switching to a persistent database:**
- Use a dedicated database for acceptance tests (not the dev/production DB)
- Clear all tables once at the start of each test run (before any scenarios execute)
- Do NOT clean up after each test or after all tests — aliased data accumulates safely
- Each scenario creates its own uniquely-aliased data, so parallel execution is safe
- Implement the cleanup as a Cucumber `@BeforeAll` hook or Maven Failsafe plugin config

## Test Environment Safety

Acceptance tests are fully isolated and cannot cause side effects:
- **Database:** H2 in-memory — created when the JVM starts, destroyed when it exits. No persistent database is touched
- **Port:** Spring Boot starts on a random port (`WebEnvironment.RANDOM_PORT`) — no conflict with a running dev instance
- **Network:** Only `localhost:{random-port}` — no external services, no outbound network calls
- **Filesystem:** Tests don't write to disk. Only standard Maven `target/` output

Running `just test-acceptance` is always safe.

## Adding a New Acceptance Test

1. Create or extend a `.feature` file in `specifications/`
2. Add a `*Dsl.kt` class in `dsl/` that matches Gherkin steps and calls `ProtocolDriver` methods
3. Alias all identity values (usernames, emails, titles, tags) via `context.alias()` for test isolation
4. Add the new methods to the `ProtocolDriver` interface
5. Implement them in `HttpProtocolDriver` (own DTOs, own assertions, plain `RestTemplate`)
6. If adding a new driver class, register it with `@Import` on `SpringIntegrationConfig`

## Gotchas

- Use plain `RestTemplate`, not `TestRestTemplate` (the app's Jackson config breaks our DTOs)
- Test classes named `*IT` run as acceptance tests (Failsafe), `*Test` run as unit tests (Surefire)
- Driver success = method completes; failure = throw (no custom result types)
- `@Bean` methods cannot live in `@SpringBootTest` classes — use a separate `@Configuration` class (see `DslConfig.kt`)
- Do not use `@ScenarioScope` on beans — it creates CGLIB proxies that break Kotlin classes. Use `@Scope("cucumber-glue")` instead
- The SUT's `@Valid` / `@Size` / `@Pattern` annotations on `Register.kt` do not enforce validation (known bug in this demo app — empty/invalid fields are accepted)
