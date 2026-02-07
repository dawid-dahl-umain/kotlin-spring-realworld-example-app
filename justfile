# Default Java version for this project (Kotlin 1.8.21 requires Java 21)
export JAVA_HOME := `/usr/libexec/java_home -v 21 2>/dev/null || echo $JAVA_HOME`

# List available commands
default:
    @just --list

# Run the app on localhost:8080
run:
    ./mvnw spring-boot:run

# Run all tests (unit + acceptance)
test:
    ./mvnw verify

# Run only unit tests
test-unit:
    ./mvnw test

# Run only acceptance tests (Cucumber BDD)
test-acceptance:
    ./mvnw verify -DskipUnitTests=true

# Download ktlint if not present
[private]
ensure-ktlint:
    #!/usr/bin/env bash
    if [ ! -f .ktlint/ktlint ]; then
        mkdir -p .ktlint
        curl -sSL https://github.com/pinterest/ktlint/releases/download/1.8.0/ktlint -o .ktlint/ktlint
        chmod +x .ktlint/ktlint
    fi

# Lint all Kotlin files
lint: ensure-ktlint
    .ktlint/ktlint "src/**/*.kt"

# Format all Kotlin files
format: ensure-ktlint
    .ktlint/ktlint -F "src/**/*.kt"

# Run all tests, then lint and format
check: test lint format