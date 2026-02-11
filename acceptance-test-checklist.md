# Acceptance Test Quality Checklist

Before shipping a new or modified acceptance test, verify every item below. If all checks pass, the test meets the quality bar for this project.

These rules are **opinionated about Cucumber/Gherkin** for the specification layer (Layer 1) but **language-independent** for everything else. They assume a DI framework is available but do not prescribe a specific programming language or toolchain. Where a rule has a project-specific implementation detail, it appears in parentheses beneath the rule.

Refer to the [README](readme.md) for full architectural context.

| Part | Focus | Subsections |
|------|-------|-------------|
| **Part 1: Three-Layer Model** | What each layer does and doesn't do | Layer 1 (Gherkin), Layer 2 (DSL), Layer 3 (Driver) |
| **Part 2: Test Isolation** | How tests stay independent | System-Level, Functional, Temporal |
| **Part 3: State Management** | Where state lives and the one exception | State Rules, Shared State |
| **Part 4: External Dependencies** | Mocking and live mode | |
| **Part 5: Infrastructure & Verification** | Wiring, safety, and final checks | DI, Naming, Environment, Final Verification |

---

## Part 1: Three-Layer Model

The acceptance test architecture separates concerns into three layers. Each layer has strict boundaries — violating them creates coupling, brittleness, and tests that break when the implementation changes.

### Layer 1: Gherkin (Executable Specifications)

- [ ] Scenarios are written in **business language** — no technical details, no HTTP verbs, no status codes, no implementation hints
- [ ] Each scenario reads as something a non-technical stakeholder could understand
- [ ] The specification is **self-documenting** — no comments are needed to explain what the test does. If a scenario requires a comment to be understood, the Gherkin itself needs to be rewritten
- [ ] Feature file has a descriptive `Feature:` title and a one-line summary beneath it
- [ ] No logic or code appears in the feature file — it is pure specification
- [ ] **Each Gherkin step maps to exactly one DSL method call** — there is a 1:1 correspondence between lines in the feature file and calls in the DSL
- [ ] **Seeding is visible in Given steps** — when a scenario needs data to exist (a user, an article, a tag), it is seeded explicitly in a Given step, never hidden inside a When or Then step
- [ ] Feature files are placed in the designated specifications directory
  *(In this project: `src/test/resources/acceptance/specifications/`)*

### Layer 2: DSL (Domain-Specific Language)

- [ ] DSL has **no assertions** — it does not verify anything itself
- [ ] DSL has **no network calls** — it never talks to external systems or the SUT directly
- [ ] DSL has **no SUT knowledge** — it does not know endpoints, URLs, status codes, response shapes, or any implementation detail of the system under test
- [ ] DSL depends only on the driver **abstraction** (interface/protocol), never on a concrete driver implementation
- [ ] The driver abstraction is injected via the DI framework, not instantiated manually
- [ ] DSL is pure routing and isolation: each step method handles aliasing via isolation utilities and delegates to driver methods — nothing else
- [ ] DSL uses **sensible defaults** for technical details that are irrelevant to a given scenario — the test author only specifies what matters for the behaviour being verified, and the DSL fills in the rest
- [ ] Confirmation/verification methods use a consistent prefix convention across DSL and driver
  *(In this project: the `confirm` prefix, e.g., `confirmArticlePublished`)*
- [ ] DSL confirmation method names match their driver counterparts **1:1** — same name, same parameters
- [ ] The codebase includes **isolation utilities** (aliasing context and parameter helpers) that the DSL uses to manage test isolation cleanly. If these utilities do not exist yet, they must be added before writing DSL code
  *(In this project: `DslContext` for alias generation and `Params` for parameter handling, in `src/test/kotlin/acceptance/dsl/utils/`)*
- [ ] DSL class is placed in the designated DSL directory and follows the project's naming convention
  *(In this project: `src/test/kotlin/acceptance/dsl/*Dsl.kt`)*

**Recommended naming conventions for DSL methods:**

- `has X` for Given steps (seeding): e.g., `hasAccount`, `hasCompletedTodo`, `hasPublishedArticle`
- Action verbs for When steps: e.g., `archives`, `creates`, `registers`
- `confirm` prefix for Then steps (verification): e.g., `confirmInArchive`, `confirmArticlePublished`

### Layer 3: Driver (Protocol Driver)

- [ ] Driver **owns all verification logic** — assertions about responses, status codes, and field values live here and nowhere else
- [ ] Driver follows the pass/fail convention: **method completes normally = step passes; method throws a standard language-level error = step fails**. No custom result types, no return values for pass/fail, no framework-specific assertion methods (e.g., not `expect.fail()` or `assert.throws()`) — this keeps drivers portable across test frameworks and protocols
- [ ] **Each driver method is atomic** — it either fully succeeds or clearly fails with a descriptive, contextual error message that includes relevant data (e.g., `"Expected tag 'kotlin' in list: [java, spring]"`)
- [ ] **Complex flows are encapsulated in the driver, not the DSL** — if a business action requires multiple technical steps (e.g., "the user has an account" requires register + login + set auth context), all of that complexity lives in the driver. The DSL makes a single call and does not know or care about the internal steps
- [ ] Driver implements the shared driver abstraction (interface/protocol)
- [ ] Driver **owns its own response types** — data structures for parsing responses are defined in the driver, not shared with or imported from the SUT
- [ ] Driver uses a standard HTTP client, not a test-augmented variant provided by the application framework — the driver must be independent of the SUT's internal configuration
  *(In this project: plain `RestTemplate`, not `TestRestTemplate`)*
- [ ] Confirmation methods use the project's agreed prefix convention
- [ ] Driver implementation is placed in the designated driver directory
  *(In this project: `src/test/kotlin/acceptance/driver/` or a subdirectory like `http/`)*

---

## Part 2: Test Isolation

Acceptance testing requires three distinct levels of isolation. All three must be satisfied for tests to be reliable, fast, and deterministic.

### System-Level Isolation

- [ ] The **boundaries of the SUT are clearly defined** — you know exactly what is inside the system under test and what is outside it
- [ ] **Internal dependencies are real, not stubbed** — your own database, cache, message queues, and internal services are part of the SUT and must be exercised in a production-like environment. Never stub what you own and control
- [ ] The SUT uses a **dedicated test database**, completely separate from development and production databases
- [ ] **External dependencies are stubbed** — third-party APIs, payment gateways, notification services, and anything you do not control are replaced with stubs or fakes for deterministic testing (see Part 4 for live mode exceptions)

### Functional Isolation (Parallel Execution Safety)

- [ ] **Every identity value** (usernames, emails, article titles, tags — anything that must be unique across scenarios) is aliased so that no two scenarios share the same data
  *(In this project: via `context.alias()`)*
- [ ] The scenario creates **all of its own data** — it does not depend on data created by another scenario
- [ ] The scenario does not assume any execution order — it would pass if run first, last, or alone
- [ ] The scenario would pass if run **in parallel** with every other scenario
- [ ] No shared mutable state between scenarios except through a controlled setup hook pattern (see Part 3: Shared State)
- [ ] The aliasing context is **scenario-scoped** — a fresh instance is created for each scenario and never reused

### Temporal Isolation (Repeatability)

- [ ] The **same test produces the same result when run multiple times** against the same SUT — aliasing ensures each run creates uniquely-named data that does not collide with data from previous runs
- [ ] **Data accumulates safely during a test run** — aliased data does not need to be cleaned up between scenarios
- [ ] **Cleanup happens only once, at the start of the next test run** (e.g., clear the database before all scenarios execute). Never clean up after individual scenarios. Never clean up after all scenarios complete

---

## Part 3: State Management

### State Rules

- [ ] **The SUT is the single source of truth for state** — no test layer maintains its own copy of "what the system should look like"
- [ ] The driver holds only **transient data**: the last response (overwritten on every call) and session credentials. Nothing else accumulates
- [ ] No layer stores a list of "things created so far" or builds up state across steps — each step either acts or verifies, using the SUT as the authority
- [ ] DSL classes hold no state beyond what is needed to bridge multi-step Gherkin sequences within a single scenario (e.g., a pending title set in one step and used in the next). This state is local to the scenario instance and does not survive across scenarios

### Shared State

The one permitted exception to "no shared state." Use sparingly.

- [ ] Only **expensive, idempotent setup** is shared across scenarios (e.g., a pre-registered user's auth token)
- [ ] **Test data is never shared** — domain objects (articles, tags, comments, etc.) are always created per-scenario via aliasing
- [ ] Shared state lives in a dedicated setup hook with static/class-level storage, guarded by an initialization flag so it runs exactly once per test run
- [ ] Shared fields are thread-safe — if scenarios can run in parallel, shared state must be protected against concurrent access (e.g., volatile fields, atomic types, or equivalent)
- [ ] The setup hook runs before any scenario's steps execute, guaranteeing shared state is available
- [ ] Gherkin steps that use shared state do not reveal the sharing mechanism — they read as normal business language (e.g., "Given a registered user is logged in")

---

## Part 4: External Dependencies

- [ ] All external services we do not control (third-party APIs, payment providers, etc.) are **mocked by default** — no flag or configuration needed for the default path
- [ ] If a scenario is intended to run against a real external service, it is tagged (e.g., `@live-safe`) at the **scenario level**, not the feature level
- [ ] Only **read-only** operations (GETs, queries) are tagged as live-safe — mutations in systems we don't control are not tagged unless the team explicitly decides otherwise
- [ ] Live mode is activated only via an explicit opt-in flag (environment variable, system property, or similar) — it is never the default

---

## Part 5: Infrastructure & Verification

### DI and Test Wiring

- [ ] DI configuration (bean/service registration) lives in a dedicated configuration class, **not** inside the test runner or test bootstrap class
- [ ] Scenario-scoped components are registered using the BDD framework's native scoping mechanism — avoid proxy-based scoping if it conflicts with the language's class model
  *(In this project: use `@Scope("cucumber-glue")`, not `@ScenarioScope`, because CGLIB proxies break Kotlin classes)*
- [ ] New driver or configuration components are registered with the DI framework so they are discoverable at test runtime

### Naming and File Placement

- [ ] Feature files, DSL classes, driver implementations, and utilities each have a designated directory — new files go in the correct one
- [ ] Acceptance test runner classes and unit test classes follow distinct naming conventions so the build tool runs them in the correct phase
  *(In this project: `*IT` for acceptance tests via Maven Failsafe, `*Test` for unit tests via Maven Surefire)*

### Environment Safety

- [ ] The test does not make outbound network calls to services outside the test environment (in default mocked mode)
- [ ] The test does not write to the filesystem beyond standard build output
- [ ] The test does not depend on a specific port — the SUT starts on a random or dynamically assigned port
- [ ] The SUT is configured for testing: fast startup and ability to handle concurrent test data from parallel scenario execution

### Final Verification

- [ ] All acceptance tests pass with the new test included
- [ ] All acceptance tests still pass when the new test is the **only** test that runs (no hidden dependency on other scenarios)
- [ ] Linting passes
- [ ] New driver methods have been added to the driver abstraction (interface), not just the concrete implementation
- [ ] The test adds value — it verifies a meaningful behaviour, not an implementation detail
