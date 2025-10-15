# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the official Mixpanel tracking library for Java - a production-ready library for sending analytics events and user profile updates to Mixpanel from Java server-side applications.

## Release Process

### Quick Commands for Releases

```bash
# 1. Update version (remove -SNAPSHOT from pom.xml)
mvn versions:set -DnewVersion=1.5.4

# 2. Run tests
mvn clean test

# 3. Deploy to Maven Central Portal
mvn clean deploy

# 4. After release, prepare next version
mvn versions:set -DnewVersion=1.5.5-SNAPSHOT
```

### Key Files
- **RELEASE.md**: Complete release documentation with step-by-step instructions
- **.github/workflows/release.yml**: Automated release workflow triggered by version tags
- **.github/workflows/ci.yml**: Continuous integration for all PRs and master commits

### Maven Central Portal
- The project uses the new Maven Central Portal (not the deprecated OSSRH)
- Deployments are visible at: https://central.sonatype.com/publishing/deployments
- Published artifacts: https://central.sonatype.com/artifact/com.mixpanel/mixpanel-java

### Required GitHub Secrets for CI/CD
- `GPG_PRIVATE_KEY`: Base64-encoded GPG private key
- `GPG_PASSPHRASE`: GPG key passphrase
- `MAVEN_CENTRAL_USERNAME`: Maven Central Portal username
- `MAVEN_CENTRAL_TOKEN`: Maven Central Portal token

## Build and Development Commands

```bash
# Build the project and create JAR
mvn clean package

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=MixpanelAPITest

# Run a specific test method
mvn test -Dtest=MixpanelAPITest#testBuildEventMessage

# Install to local Maven repository
mvn install

# Generate JavaDoc
mvn javadoc:javadoc

# Clean build artifacts
mvn clean

# Run the demo application (after building)
java -cp target/mixpanel-java-1.5.3.jar:target/classes:lib/json-20231013.jar com.mixpanel.mixpanelapi.demo.MixpanelAPIDemo <YOUR_MIXPANEL_TOKEN>
```

## High-Level Architecture

### Core Design Pattern
The library implements a **Producer-Consumer** pattern with intentional thread separation:

1. **Message Production** (`MessageBuilder`): Creates properly formatted JSON messages on application threads
2. **Message Batching** (`ClientDelivery`): Collects messages into efficient batches (max 50 per request)
3. **Message Transmission** (`MixpanelAPI`): Sends batched messages to Mixpanel servers

This separation allows for flexible threading models - the library doesn't impose any specific concurrency pattern, letting applications control their own threading strategy.

### Key Architectural Decisions

**No Built-in Threading**: Unlike some analytics libraries, this one doesn't start background threads. Applications must manage their own async patterns, as demonstrated in `MixpanelAPIDemo` which uses a `ConcurrentLinkedQueue` with producer/consumer threads.

**Message Format Validation**: `MessageBuilder` performs validation during message construction, throwing `MixpanelMessageException` for malformed data before network transmission.

**Batch Encoding**: Messages are JSON-encoded, then Base64-encoded, then URL-encoded for HTTP POST transmission. This triple encoding ensures compatibility with Mixpanel's API requirements.

**Network Communication**: Uses Java's built-in `java.net.URL` and `URLConnection` classes - no external HTTP client dependencies. Connection timeout is 2 seconds, read timeout is 10 seconds.

### Message Types and Endpoints

The library supports three message categories, each sent to different endpoints:

- **Events** (`/track`): User actions and behaviors
- **People** (`/engage`): User profile updates (set, increment, append, etc.)
- **Groups** (`/groups`): Group profile updates

Each message type has specific JSON structure requirements validated by `MessageBuilder`.

## Package Structure

All production code is in the `com.mixpanel.mixpanelapi` package:

- `MixpanelAPI`: HTTP communication with Mixpanel servers
- `MessageBuilder`: Constructs and validates JSON messages
- `ClientDelivery`: Batches multiple messages for efficient transmission
- `Config`: Contains API endpoints and configuration constants
- `Base64Coder`: Base64 encoding utility (modified third-party code)
- `MixpanelMessageException`: Runtime exception for message format errors
- `MixpanelServerException`: IOException for server rejection responses

## Testing Approach

Tests extend JUnit 4's `TestCase` and are located in `MixpanelAPITest`. The test suite covers:

- Message format validation for all message types
- Property operations (set, setOnce, increment, append, union, remove, unset)
- Large batch delivery behavior
- Encoding verification
- Error conditions and exception handling

When adding new functionality, follow the existing test patterns - each message type operation has corresponding test methods that verify both the JSON structure and the encoded format.

## Common Development Tasks

### Adding a New Message Type
1. Add the message construction method to `MessageBuilder`
2. Validate required fields and structure
3. Add corresponding tests in `MixpanelAPITest`
4. Update `ClientDelivery` if special handling is needed

### Modifying Network Behavior
Network configuration is centralized in `MixpanelAPI.sendData()`. Connection and read timeouts are hardcoded but could be made configurable by modifying the `Config` class.

### Debugging Failed Deliveries
The library throws `MixpanelServerException` with the HTTP response code and server message. Check:
1. Token validity in `MessageBuilder` constructor
2. Message size (batches limited to 50 messages)
3. JSON structure using the test suite patterns

## Dependencies

The library has minimal dependencies:
- **Production**: `org.json:json:20231013` for JSON manipulation
- **Test**: `junit:junit:4.13.2` for unit testing
- **Java Version**: Requires Java 8 or higher

## API Patterns to Follow

When working with this codebase:

1. **Immutable Messages**: Once created by `MessageBuilder`, JSON messages should not be modified
2. **Fail Fast**: Validate message structure early in `MessageBuilder` rather than during transmission
3. **Preserve Thread Safety**: `MessageBuilder` instances are NOT thread-safe; create one per thread
4. **Batch Appropriately**: `ClientDelivery` handles batching; don't exceed 50 messages per delivery
5. **Exception Handling**: Distinguish between `MixpanelMessageException` (client error) and `MixpanelServerException` (server error)