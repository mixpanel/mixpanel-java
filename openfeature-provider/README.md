# Mixpanel Java OpenFeature Provider

[![Maven Central](https://img.shields.io/maven-central/v/com.mixpanel/mixpanel-java-openfeature.svg)](https://central.sonatype.com/artifact/com.mixpanel/mixpanel-java-openfeature)
[![OpenFeature](https://img.shields.io/badge/OpenFeature-compatible-green)](https://openfeature.dev/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/mixpanel/mixpanel-java/blob/master/LICENSE)

##### _April 22, 2026_ - [openfeature/v0.1.1](https://github.com/mixpanel/mixpanel-java/releases/tag/openfeature/v0.1.1)

An [OpenFeature](https://openfeature.dev/) provider that integrates Mixpanel's feature flags with the OpenFeature Java SDK. This allows you to use Mixpanel's feature flagging capabilities through OpenFeature's standardized, vendor-agnostic API.

## Overview

This package provides a bridge between Mixpanel's native feature flags implementation and the OpenFeature specification. By using this provider, you can:

- Leverage Mixpanel's powerful feature flag and experimentation platform
- Use OpenFeature's standardized API for flag evaluation
- Easily switch between feature flag providers without changing your application code
- Integrate with OpenFeature's ecosystem of tools and frameworks

## Installation

### Maven

```xml
<dependency>
    <groupId>com.mixpanel</groupId>
    <artifactId>mixpanel-java-openfeature</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.mixpanel:mixpanel-java-openfeature:0.1.0'
```

You will also need the OpenFeature Java SDK:

```xml
<dependency>
    <groupId>dev.openfeature</groupId>
    <artifactId>sdk</artifactId>
    <version>1.20.1</version>
</dependency>
```

## Quick Start

```java
import com.mixpanel.openfeature.MixpanelProvider;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Client;

// 1. Create and register the provider with local evaluation
MixpanelProvider provider = new MixpanelProvider(
    "YOUR_PROJECT_TOKEN",
    new LocalFlagsConfig("YOUR_PROJECT_TOKEN")
);
OpenFeatureAPI api = OpenFeatureAPI.getInstance();
api.setProvider(provider);

// 2. Get a client and evaluate flags
Client client = api.getClient();
boolean showNewFeature = client.getBooleanValue("new-feature-flag", false);

if (showNewFeature) {
    System.out.println("New feature is enabled!");
}
```

## Initialization

The provider supports three constructors depending on your evaluation strategy:

### Local Evaluation

Evaluates flags locally using cached flag definitions that are polled from Mixpanel. This is the recommended approach for most server-side applications as it minimizes latency.

```java
MixpanelProvider provider = new MixpanelProvider(
    "YOUR_PROJECT_TOKEN",
    new LocalFlagsConfig("YOUR_PROJECT_TOKEN")
);
```

This automatically starts polling for flag definitions in the background.

### Remote Evaluation

Evaluates flags by making a request to Mixpanel's servers for each evaluation. Use this when you need real-time flag values and can tolerate the additional network latency.

```java
MixpanelProvider provider = new MixpanelProvider(
    "YOUR_PROJECT_TOKEN",
    new RemoteFlagsConfig("YOUR_PROJECT_TOKEN")
);
```

### Using an Existing MixpanelAPI Instance

If your application already has a `MixpanelAPI` instance configured, you can create the provider from its flags provider directly rather than having the provider create a new one:

```java
// Your existing MixpanelAPI instance
MixpanelAPI mixpanel = new MixpanelAPI(new LocalFlagsConfig("YOUR_PROJECT_TOKEN"));
LocalFlagsProvider localFlags = mixpanel.getLocalFlags();
localFlags.startPollingForDefinitions();

// Wrap the existing flags provider with OpenFeature
MixpanelProvider provider = new MixpanelProvider(localFlags);
```

> **Note:** When using this constructor, `provider.getMixpanel()` will return `null` since the provider does not own the `MixpanelAPI` instance.

## Usage Examples

### Basic Boolean Flag

```java
Client client = api.getClient();

// Get a boolean flag with a default value
boolean isFeatureEnabled = client.getBooleanValue("my-feature", false);

if (isFeatureEnabled) {
    // Show the new feature
}
```

### Mixpanel Flag Types and OpenFeature Evaluation Methods

Mixpanel feature flags support three flag types. Use the corresponding OpenFeature evaluation method based on your flag's variant values:

| Mixpanel Flag Type | Variant Values | OpenFeature Method |
|---|---|---|
| Feature Gate | `true` / `false` | `getBooleanValue()` |
| Experiment | boolean, string, number, or JSON object | `getBooleanValue()`, `getStringValue()`, `getIntegerValue()`, `getDoubleValue()`, or `getObjectValue()` |
| Dynamic Config | JSON object | `getObjectValue()` |

```java
Client client = api.getClient();

// Feature Gate - boolean variants
boolean isFeatureOn = client.getBooleanValue("new-checkout", false);

// Experiment with string variants
String buttonColor = client.getStringValue("button-color-test", "blue");

// Experiment with integer variants
int maxItems = client.getIntegerValue("max-items", 10);

// Experiment with double variants
double threshold = client.getDoubleValue("score-threshold", 0.5);

// Dynamic Config - JSON object variants
Value featureConfig = client.getObjectValue("homepage-layout", new Value("default"));
```

### Getting Full Resolution Details

If you need additional metadata about the flag evaluation:

```java
Client client = api.getClient();

FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("my-feature", false);

System.out.println(details.getValue());       // The resolved value
System.out.println(details.getVariant());     // The variant key from Mixpanel
System.out.println(details.getReason());      // Why this value was returned
System.out.println(details.getErrorCode());   // Error code if evaluation failed
```

### Setting Context

You can pass evaluation context that will be sent to Mixpanel for flag evaluation:

```java
MutableContext context = new MutableContext();
context.setTargetingKey("user-123");
context.add("email", "user@example.com");
context.add("plan", "premium");
context.add("beta_tester", true);

boolean value = client.getBooleanValue("premium-feature", false, context);
```

### Accessing the Underlying MixpanelAPI

If you initialized the provider with a token and config, you can access the underlying `MixpanelAPI` instance for sending events or profile updates:

```java
MixpanelAPI mixpanel = provider.getMixpanel();
```

> **Note:** This returns `null` if the provider was constructed with a `BaseFlagsProvider` directly.

### Shutdown

When your application is shutting down, call `shutdown()` to clean up resources:

```java
provider.shutdown();
```

## Context Mapping

### All Properties Passed Directly

All properties in the OpenFeature `EvaluationContext` are passed directly to Mixpanel's feature flag evaluation. There is no transformation or filtering of properties.

```java
// This OpenFeature context...
MutableContext context = new MutableContext();
context.setTargetingKey("user-123");
context.add("email", "user@example.com");
context.add("plan", "premium");

// ...is passed to Mixpanel as-is for flag evaluation
```

### targetingKey is Not Special

Unlike some feature flag providers, `targetingKey` is **not** used as a special bucketing key in Mixpanel. It is simply passed as another context property. Mixpanel's server-side configuration determines which properties are used for targeting rules and bucketing.

## Error Handling

The provider uses OpenFeature's standard error codes to indicate issues during flag evaluation:

### PROVIDER_NOT_READY

Returned when flags are evaluated before the local flags provider has finished loading flag definitions. This only applies when using local evaluation.

```java
FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("my-feature", false);

if (details.getErrorCode() == ErrorCode.PROVIDER_NOT_READY) {
    System.out.println("Provider still loading, using default value");
}
```

### FLAG_NOT_FOUND

Returned when the requested flag does not exist in Mixpanel.

```java
FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("nonexistent-flag", false);

if (details.getErrorCode() == ErrorCode.FLAG_NOT_FOUND) {
    System.out.println("Flag does not exist, using default value");
}
```

### TYPE_MISMATCH

Returned when the flag value type does not match the requested type. The provider supports some numeric coercions (e.g., a `Long` flag value can be retrieved via `getIntegerValue()` if it fits within `Integer` bounds, and any numeric type can be retrieved via `getDoubleValue()`), but incompatible types will return this error.

```java
// If 'my-flag' is configured as a string in Mixpanel...
FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("my-flag", false);

if (details.getErrorCode() == ErrorCode.TYPE_MISMATCH) {
    System.out.println("Flag is not a boolean, using default value");
}
```

## Troubleshooting

### Flags Always Return Default Values

**Possible causes:**

1. **Provider not ready (local evaluation):** The local flags provider may still be loading flag definitions. Flag definitions are polled asynchronously after the provider is created. Allow time for the initial fetch to complete, or check the `PROVIDER_NOT_READY` error code.

2. **Invalid project token:** Verify the token passed to the config matches your Mixpanel project.

3. **Flag not configured:** Verify the flag exists in your Mixpanel project and is enabled.

4. **Network issues:** Check that your application can reach Mixpanel's API servers.

### Type Mismatch Errors

If you are getting `TYPE_MISMATCH` errors:

1. **Check flag configuration:** Verify the flag's value type in Mixpanel matches how you are evaluating it. For example, if the flag value is the string `"true"`, use `getStringValue()`, not `getBooleanValue()`.

2. **Use `getObjectValue()` for complex types:** For JSON objects or arrays, use `getObjectValue()`.

3. **Numeric coercion:** Integer evaluation accepts `Long` and whole-number `Double` values within `Integer` bounds. Double evaluation accepts any numeric type.

### Exposure Events Not Tracking

If `$experiment_started` events are not appearing in Mixpanel:

1. **Verify Mixpanel tracking is working:** Test that other Mixpanel events are being tracked successfully.

2. **Check for duplicate evaluations:** Mixpanel only tracks the first exposure per flag per session to avoid duplicate events.

## Requirements

- Java 8 or higher
- `mixpanel-java` 1.8.0+
- OpenFeature SDK 1.20.1+

## License

Apache-2.0
