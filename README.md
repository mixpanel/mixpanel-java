This is the official Mixpanel tracking library for Java.

## Latest Version

See the [releases page](https://github.com/mixpanel/mixpanel-java/releases) for the latest version.

```xml
<dependency>
    <groupId>com.mixpanel</groupId>
    <artifactId>mixpanel-java</artifactId>
    <version>1.6.0</version>
</dependency>
```

You can alternatively download the library jar directly from Maven Central [here](https://central.sonatype.com/artifact/com.mixpanel/mixpanel-java).

## How To Use

The library is designed to produce events and people updates in one process or thread, and
consume the events and people updates in another thread or process. Specially formatted JSON objects
are built by `MessageBuilder` objects, and those messages can be consumed by the
`MixpanelAPI` via `ClientDelivery` objects, possibly after serialization or IPC.

    MessageBuilder messages = new MessageBuilder("my token");
    JSONObject event = messages.event("joe@gribbl.com", "Logged In", null);

    // Later, or elsewhere...
    ClientDelivery delivery = new ClientDelivery();
    delivery.addMessage(event);

    MixpanelAPI mixpanel = new MixpanelAPI();
    mixpanel.deliver(delivery);

### Gzip Compression

The library supports gzip compression for both tracking events (`/track`) and importing historical events (`/import`). To enable gzip compression, use the builder:

```java
MixpanelAPI mixpanel = new MixpanelAPI.Builder()
    .useGzipCompression(true)
    .build();
```

Gzip compression can reduce bandwidth usage and improve performance, especially when sending large batches of events.

### Importing Historical Events

The library supports importing historical events (events older than 5 days that are not accepted using /track) via the `/import` endpoint. Project token will be used for basic auth.

### High-Performance JSON Serialization (Optional)

For applications that import large batches of events (e.g., using the `/import` endpoint), the library supports optional high-performance JSON serialization using Jackson. The Jackson extension provides **up to 5x performance improvement** for large batches.

To enable high-performance serialization, add the Jackson extension to your project:

```xml
<dependency>
    <groupId>com.mixpanel</groupId>
    <artifactId>mixpanel-java-extension-jackson</artifactId>
    <version>1.6.0</version>
</dependency>
```

Then configure the MixpanelAPI to use it:

```java
import com.mixpanel.mixpanelapi.internal.JacksonSerializer;

MixpanelAPI mixpanel = new MixpanelAPI.Builder()
    .jsonSerializer(new JacksonSerializer())
    .build();
```

**Key benefits:**
- **Significant performance gains**: 2-5x faster serialization for batches of 50+ messages
- **Optimal for `/import`**: Most beneficial when importing large batches (up to 2000 events)

The performance improvement is most noticeable when:
- Importing historical data via the `/import` endpoint
- Sending batches of 50+ events
- Processing high-volume event streams

## Feature Flags

The Mixpanel Java SDK supports feature flags with both local and remote evaluation modes.

### Local Evaluation (Recommended)

Fast, low-latency flag checks with background polling for flag definitions:

```java
import com.mixpanel.mixpanelapi.*;
import com.mixpanel.mixpanelapi.featureflags.config.*;
import java.util.*;

// Initialize with your project token
LocalFlagsConfig config = LocalFlagsConfig.builder()
    .projectToken("YOUR_PROJECT_TOKEN")
    .pollingIntervalSeconds(60)
    .build();

MixpanelAPI mixpanel = new MixpanelAPI.Builder()
    .flagsConfig(config)
    .build();

// Start polling for flag definitions
mixpanel.getLocalFlags().startPollingForDefinitions();

// Wait for flags to be ready (optional but recommended)
while (!mixpanel.getLocalFlags().areFlagsReady()) {
    Thread.sleep(100);
}

// Evaluate flags
Map<String, Object> context = new HashMap<>();
context.put("distinct_id", "user-123");

// Check if a feature is enabled
boolean isEnabled = mixpanel.getLocalFlags().isEnabled("new-feature", context);

// Get a variant value with fallback
String theme = mixpanel.getLocalFlags().getVariantValue("ui-theme", "light", context);

// Cleanup
mixpanel.close();
```

### Remote Evaluation

Real-time flag evaluation with server-side API calls:

```java
import com.mixpanel.mixpanelapi.*;
import com.mixpanel.mixpanelapi.featureflags.config.*;
import java.util.*;

RemoteFlagsConfig config = RemoteFlagsConfig.builder()
    .projectToken("YOUR_PROJECT_TOKEN")
    .build();

MixpanelAPI mixpanel = new MixpanelAPI.Builder()
    .flagsConfig(config)
    .build();

try (mixpanel) {
    Map<String, Object> context = new HashMap<>();
    context.put("distinct_id", "user-456");

    boolean isEnabled = mixpanel.getRemoteFlags().isEnabled("premium-features", context);
}
```

For complete feature flags documentation, configuration options, advanced usage, and best practices, see:

    https://docs.mixpanel.com/docs/tracking-methods/sdks/java/java-flags

## Learn More

This library in particular has more in-depth documentation at

    https://mixpanel.com/docs/integration-libraries/java

Mixpanel maintains documentation at

    http://www.mixpanel.com/docs

The library also contains a simple demo application, that demonstrates
using this library in an asynchronous environment.

There are also community supported libraries in addition to this library,
that provide a threading model, support for dealing directly with Java Servlet requests,
support for persistent properties, etc. Two interesting ones are at:

    https://github.com/eranation/mixpanel-java
    https://github.com/scalascope/mixpanel-java

## Other Mixpanel Libraries

Mixpanel also maintains a full-featured library for tracking events from Android apps at https://github.com/mixpanel/mixpanel-android

And a full-featured client side library for web applications, in Javascript, that can be loaded
directly from Mixpanel servers. To learn more about our Javascript library, see: https://mixpanel.com/docs/integration-libraries/javascript

This library is intended for use in back end applications or API services that can't take
advantage of the Android libraries or the Javascript library.

## License

```
See LICENSE File for details. The Base64Coder class used by this software
has been licensed from non-Mixpanel sources and modified for use in the library.
Please see Base64Coder.java for details.
```
