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
    ./mvnw verify -Dsurefire.skip