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
just check            # Run all tests, format, then lint
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

### Project Structure

This is a single-module project. All acceptance test code lives under one test directory.

For **multi-module projects (monorepos)**, the structure scales differently — see [Multi-Module Structure](#multi-module-structure) below.

### Single-Module Layout

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

| Layer | Knows SUT? | Has verification? | Owns isolation? | Holds state? |
|-------|---|---|---|---|
| Specifications (Gherkin) | No | No | No | No |
| DSL | No | No | Yes | No |
| Driver | Yes | Yes | No | No* |

\* The SUT is the source of truth for state, not the test layers. A driver implementation may hold transient data (e.g. the HTTP driver keeps the last response), but no layer accumulates state across steps.

Only the driver layer has knowledge of or dependence on the SUT. The specs and DSL are protocol-agnostic — to swap from HTTP to a CLI or UI driver, only the driver implementation changes.

### Test Environment Safety

Acceptance tests are fully isolated and cannot cause side effects:
- **Database:** Tests use a dedicated database, separate from dev/production
- **Port:** The app starts on its own port, isolated from any running instance
- **Network:** No external services or outbound network calls
- **Filesystem:** No writes beyond standard build output

Running `just test-acceptance` is always safe.

### External Dependencies We Do Not Control

This section describes how to handle third-party APIs and other external services that we do not own — payment providers, notification services, partner APIs, etc. This does **not** apply to infrastructure we control (our own database, our own message queue, etc.).

#### Default: Always Mocked

When acceptance tests run normally (`just test-acceptance`), all external dependencies are mocked. The driver layer injects stubs or fakes instead of real service clients. This is the only way to guarantee:

- **Determinism** — tests produce the same result every time, regardless of network conditions or third-party uptime
- **Speed** — no network round-trips to external services
- **Safety** — no mutations in systems we don't control
- **Isolation** — we only test what we are directly responsible for

This is the default and requires no flags or configuration.

#### Live Mode: Opt-In Real Service Testing

Occasionally — typically before a release — it is useful to verify that real external services still behave as our stubs assume. Contracts drift, APIs get versioned, fields get renamed. A passing mocked test does not guarantee the real integration still works.

Rather than maintaining a separate contract test suite, we reuse existing acceptance scenarios. A flag (environment variable, system property, or similar) switches the driver layer to inject the real service client instead of the stub. Only scenarios explicitly tagged as live-safe are executed in this mode.

**Rules:**

1. **No flag = mocked.** The default run always uses stubs. Live mode is never implicit.
2. **Only tagged scenarios run in live mode.** Untagged scenarios are skipped entirely when the live flag is active. In the default mocked mode, all scenarios run regardless of tags.
3. **Tags are per-scenario, not per-feature.** Each scenario is individually assessed and marked. This is a deliberate decision by the developer, not a blanket toggle.
4. **Prefer read-only scenarios.** GET requests and other read-only operations are generally the safest choice. Mutations in external systems require more caution, but are acceptable when the external service provides test/sandbox endpoints or when the team explicitly decides the risk is manageable.
5. **Live failures are informational.** A failure in live mode means the external contract may have changed — it does not necessarily mean our code is broken. Developers investigate and decide how to respond: update the integration, pin an API version, add a workaround, or accept the change. There is no single correct response.

**Pseudocode:**

```
# In the feature file, tag specific scenarios:

@live-safe
Scenario: Fetch available payment methods
  When a client requests the list of payment methods
  Then the response contains at least one payment method

Scenario: Process a payment
  When a client submits a payment of $50
  Then the payment is accepted
  # ^^^ NOT tagged — this mutates external state


# In the test wiring (pseudocode):

if live_flag is set:
    inject RealPaymentClient instead of StubPaymentClient
    run only scenarios tagged @live-safe
else:
    inject StubPaymentClient
    run all scenarios
```

**Why this approach:**

- No separate test suite to write and maintain — existing acceptance scenarios already describe the correct behaviour
- The `ProtocolDriver` interface is the natural injection seam; swapping stub for real is just DI configuration
- The `@live-safe` tag keeps the scope explicit and prevents accidental mutations in external systems
- Teams get real contract verification with minimal overhead

This capability is not yet implemented. The above describes the intended design for when external dependencies are introduced.

## Multi-Module Structure

In a monorepo with multiple domain modules (orders, loyalty, users, etc.), each module gets its own three layers for domain-specific behavior. Shared utilities live in a separate module that all acceptance tests depend on.

### Structure

```
modules/
├── orders/
│   └── src/test/
│       ├── resources/acceptance/specifications/  # Orders domain Gherkin
│       ├── kotlin/acceptance/dsl/                # Orders domain DSL
│       └── kotlin/acceptance/driver/             # Orders domain driver
├── loyalty/
│   └── src/test/
│       ├── resources/acceptance/specifications/  # Loyalty domain Gherkin
│       ├── kotlin/acceptance/dsl/                # Loyalty domain DSL
│       └── kotlin/acceptance/driver/             # Loyalty domain driver
├── users/
│   └── src/test/
│       └── (same structure for users domain)
└── acceptance-shared/                            # Shared library module
    └── src/main/kotlin/                          # Note: main, not test (it's a library)
        ├── utils/
        │   ├── DslContext.kt                     # Isolation utilities
        │   └── Params.kt
        ├── stubs/
        │   └── ExternalServiceStub.kt            # If multiple modules use same external service
        └── config/
            └── TestInfrastructure.kt             # DI wiring patterns, common config
```

### What Belongs Where

**Module-specific (orders/, loyalty/, users/):**
- Feature files expressing that domain's behavior in its own ubiquitous language
- DSL classes speaking that domain's vocabulary (e.g., "loyalty points" vs "order fulfillment")
- Drivers connecting to that domain's entry points (orders API vs loyalty API vs users API)

**Shared (acceptance-shared/):**
- Universal isolation utilities: `DslContext`, `Params`
- Test infrastructure patterns: DI configuration helpers, common test wiring
- External service stubs used by multiple modules (e.g., if both orders and loyalty integrate with the same payment gateway)

**DO NOT share:**
- Domain-specific DSL classes across modules — each speaks its own language
- Domain-specific drivers across modules — each connects to different entry points
- Feature files across modules — each verifies its own boundaries

### Cross-Module Scenarios

If a scenario truly spans multiple domains (e.g., "placing an order awards loyalty points"), you have two options:

1. **Create an integration module:** `acceptance-integration/` with its own three layers that compose calls to multiple module APIs
2. **Own it in one module:** Put the scenario in the primary module (e.g., orders) and have its driver call the other module's API as if it were any dependency

### Key Principles

- Each module's acceptance tests verify its own boundaries, not cross-module integration
- Modules can run their acceptance tests in parallel without interference
- Each module's tests remain isolated using the same aliasing techniques
- The three-layer separation is maintained within each module
- Shared code is limited to utilities and infrastructure — never share domain logic

### Build Configuration

Each module's test dependencies would include the shared module:

```kotlin
// Example for Gradle (build.gradle.kts in orders module)
dependencies {
    testImplementation(project(":modules:acceptance-shared"))
    testImplementation("io.cucumber:cucumber-java:7.x.x")
    testImplementation("io.insert-koin:koin-test:3.x.x")  // or Spring, etc.
}
```

The approach works with any build tool (Gradle, Maven) and any DI framework (Koin, Spring, Guice).

## Adding a New Acceptance Test

1. Create or extend a `.feature` file in `src/test/resources/acceptance/specifications/`
2. Add a `*Dsl.kt` class in `dsl/` that matches Gherkin steps and calls `ProtocolDriver` methods
3. Alias all identity values (usernames, emails, titles, tags) via `DslContext` for test isolation
4. Add the new methods to the `ProtocolDriver` interface
5. Implement them in `HttpProtocolDriver` (own DTOs, own assertions, plain `RestTemplate`)
6. If adding a new driver class, register it with `@Import` on `SpringIntegrationConfig`

Run `just test-acceptance` to verify.

Before shipping, run through the **[Acceptance Test Quality Checklist](acceptance-test-checklist.md)** — it covers every rule from this README in a pass/fail format.
