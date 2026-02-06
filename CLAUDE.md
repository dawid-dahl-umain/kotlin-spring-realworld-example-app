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
│       └── tags.feature
└── kotlin/acceptance/
    ├── CucumberIT.kt             <- test runner (Failsafe picks up *IT classes)
    ├── SpringIntegrationConfig.kt
    ├── dsl/                      <- Layer 2: step definitions (pure routing)
    │   └── TagSteps.kt
    └── driver/
        ├── ProtocolDriver.kt     <- interface (DSL depends on this)
        └── http/                 <- Layer 3: HTTP driver implementation
            └── HttpProtocolDriver.kt
```

## Conventions

- Gherkin feature files go in `src/test/resources/acceptance/specifications/`
- Step definitions (DSL) go in `src/test/kotlin/acceptance/dsl/`
- Driver implementations go in `src/test/kotlin/acceptance/driver/`
- DSL layer is pure routing: no assertions, no HTTP, no SUT knowledge
- All assertions and HTTP logic live in the driver layer only
- Write Gherkin in business language, not technical details
