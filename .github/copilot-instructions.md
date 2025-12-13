# Copilot Instructions for mixpanel-java

## Project Overview

This is the official Mixpanel tracking library for Java - a production-ready library for sending analytics events and user profile updates to Mixpanel from server-side Java applications.

**Project Type:** Java library (JAR)  
**Build Tool:** Maven 3.x  
**Java Version:** 8+ (compiled for Java 8, tested on 8, 11, 17, 21)  
**Main Dependency:** org.json:json:20231013  
**Test Framework:** JUnit 4.13.2

## Build Commands

Always run these commands from the repository root directory.

### Essential Commands

```bash
# Run all tests (81 tests, ~5-20 seconds)
mvn test

# Build JAR without tests
mvn clean package -DskipTests

# Full build with tests (~20-30 seconds)
mvn clean package

# Clean build artifacts
mvn clean

# Generate JavaDoc
mvn javadoc:javadoc
```

### Running Specific Tests

```bash
# Run a specific test class
mvn test -Dtest=MixpanelAPITest

# Run a specific test method
mvn test -Dtest=MixpanelAPITest#testBuildEventMessage
```

## Project Structure

```
mixpanel-java/
├── pom.xml                          # Maven build configuration
├── src/
│   ├── main/java/com/mixpanel/mixpanelapi/
│   │   ├── MixpanelAPI.java         # Main API class, HTTP communication
│   │   ├── MessageBuilder.java      # Constructs JSON messages
│   │   ├── ClientDelivery.java      # Batches messages for transmission
│   │   ├── Config.java              # API endpoints and constants
│   │   ├── Base64Coder.java         # Base64 encoding utility
│   │   ├── MixpanelMessageException.java  # Client-side errors
│   │   ├── MixpanelServerException.java   # Server-side errors
│   │   ├── featureflags/            # Feature flags implementation
│   │   │   ├── config/              # Flag configuration classes
│   │   │   ├── model/               # Flag data models
│   │   │   ├── provider/            # Flag evaluation providers
│   │   │   └── util/                # Utility classes
│   │   └── internal/                # Internal serialization (Jackson/org.json)
│   ├── main/resources/
│   │   └── mixpanel-version.properties  # Version info (filtered by Maven)
│   ├── test/java/com/mixpanel/mixpanelapi/
│   │   ├── MixpanelAPITest.java     # Main test class (27 tests)
│   │   ├── featureflags/provider/   # Feature flags tests (~54 tests)
│   │   └── internal/                # Serializer tests
│   └── demo/java/com/mixpanel/mixpanelapi/demo/
│       └── MixpanelAPIDemo.java     # Demo application
└── .github/workflows/
    ├── ci.yml                       # CI pipeline (tests on Java 8, 11, 17, 21)
    └── release.yml                  # Release to Maven Central
```

## CI/CD Pipeline

The CI workflow (`.github/workflows/ci.yml`) runs on PRs and pushes to master:

1. **Tests:** `mvn clean test` (on Java 8, 11, 17, 21)
2. **Build:** `mvn clean package`
3. **JavaDoc:** `mvn javadoc:javadoc`
4. **Dependency check:** `mvn versions:display-dependency-updates`

**Before submitting changes, always run:**
```bash
mvn clean test
```

## Architecture Notes

### Core Design Pattern
The library uses a **Producer-Consumer** pattern:
1. `MessageBuilder` creates JSON messages on application threads
2. `ClientDelivery` batches messages (max 50 per request, 2000 for imports)
3. `MixpanelAPI` sends batched messages to Mixpanel servers

### Message Types and Endpoints
- **Events** (`/track`): User actions via `messageBuilder.event()`
- **People** (`/engage`): Profile updates via `messageBuilder.set()`, `increment()`, etc.
- **Groups** (`/groups`): Group profile updates
- **Import** (`/import`): Historical events via `messageBuilder.importEvent()`

### Key Constants (Config.java)
- `MAX_MESSAGE_SIZE = 50` (regular batches)
- `IMPORT_MAX_MESSAGE_SIZE = 2000` (import batches)
- Connection timeout: 2 seconds, Read timeout: 10 seconds

## Testing Guidelines

Tests use JUnit 4's `TestCase` style. When adding functionality:

1. Add tests in `MixpanelAPITest.java` for core API changes
2. Add tests in `featureflags/provider/` for flag-related changes
3. Follow existing test patterns - verify both JSON structure and encoded format

Example test pattern:
```java
public void testNewFeature() {
    JSONObject message = mBuilder.newMethod("distinctId", params);
    // Verify message structure
    assertTrue(delivery.isValidMessage(message));
}
```

## Common Tasks

### Adding a New Message Type
1. Add method to `MessageBuilder.java`
2. Validate required fields in the method
3. Add tests in `MixpanelAPITest.java`
4. Update `ClientDelivery.java` if special handling needed

### Modifying Network Behavior
Network configuration is in `MixpanelAPI.sendData()`. Timeouts are hardcoded but can be made configurable via `Config.java`.

## Dependencies

**Runtime:**
- `org.json:json:20231013` - JSON manipulation (required)
- `com.fasterxml.jackson.core:jackson-databind:2.20.0` - High-performance serialization (optional, provided scope)

**Test:**
- `junit:junit:4.13.2`

## Important Notes

- `MessageBuilder` instances are NOT thread-safe; create one per thread
- Messages are JSON → Base64 → URL encoded for transmission
- The library does NOT start background threads; applications manage their own threading
- JavaDoc warnings during build are expected and do not indicate failures

Trust these instructions. Only search for additional information if commands fail or behavior differs from what is documented here.
