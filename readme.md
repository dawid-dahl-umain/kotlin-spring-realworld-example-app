# RealWorld API + Cucumber BDD

A Medium-clone REST API (Kotlin + Spring Boot) used as a target application for Cucumber BDD acceptance testing. Part of the Hexathon 2026 project exploring AI-powered BDD test generation.

## The App (System Under Test)

A pre-existing [RealWorld](https://github.com/gothinkster/realworld) demo API. We didn't write it, we test it.

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

Standard JUnit 5 tests that live alongside the app code. They can import services, repositories, and models directly; no HTTP involved. Spring Boot loads the application context, and tests call internal methods and assert results.

```
src/test/kotlin/io/realworld/
└── ApiApplicationTests.kt    <- JUnit 5, @SpringBootTest
```

## Acceptance Test Architecture

Three layers, each with a clear responsibility:

```
src/test/
├── resources/acceptance/
│   └── specifications/           <- Layer 1: Gherkin
│       └── tags.feature
└── kotlin/acceptance/
    ├── CucumberIT.kt             <- test runner (Failsafe picks up *IT classes)
    ├── SpringIntegrationConfig.kt
    ├── dsl/                      <- Layer 2: step definitions
    │   └── TagSteps.kt
    └── driver/
        ├── ProtocolDriver.kt     <- interface (DSL depends on this)
        └── http/                 <- Layer 3: HTTP implementation
            └── HttpProtocolDriver.kt
```

### Layer 1: Specifications

Business-readable Gherkin scenarios. No code, no technical details.

```gherkin
Feature: Tags
  Scenario: Retrieve tags from a fresh system
    When a client requests the list of tags
    Then the response is a list of tags
```

### Layer 2: DSL (Step Definitions)

Routes Gherkin steps to driver methods. Pure routing with no assertions, no HTTP, and no SUT knowledge. Only depends on the `ProtocolDriver` interface.

### Layer 3: Driver

The only layer that talks to the app. Makes HTTP calls, owns its own DTOs, does all assertions. If a driver method completes without throwing, the step passes. If it throws, the step fails.

The driver sits behind a `ProtocolDriver` interface. The current implementation is HTTP (`HttpProtocolDriver`), but the same specs and DSL could run against a CLI or web UI driver without changes.

### Decoupling Rules

| Layer | Can import from SUT? | Knows about HTTP? | Has assertions? |
|-------|---|---|---|
| `specifications/` (Gherkin) | No | No | No |
| `dsl/` (step defs) | No | No | No |
| `driver/http/` | Yes | Yes | Yes |
| `SpringIntegrationConfig` | Only `ApiApplication::class` | No | No |

To swap the protocol, change the `@Import` in `SpringIntegrationConfig`. The specs and DSL stay untouched.
