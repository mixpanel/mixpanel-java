This is the official Mixpanel tracking library for Java.

## Latest Version

##### _May 08, 2024_ - [v1.5.3](https://github.com/mixpanel/mixpanel-java/releases/tag/mixpanel-java-1.5.3)

```
<dependency>
    <groupId>com.mixpanel</groupId>
    <artifactId>mixpanel-java</artifactId>
    <version>1.5.3</version>
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

The library supports gzip compression for both tracking events (`/track`) and importing historical events (`/import`). To enable gzip compression, pass `true` to the `MixpanelAPI` constructor:

    MixpanelAPI mixpanel = new MixpanelAPI(true); // Enable gzip compression

Gzip compression can reduce bandwidth usage and improve performance, especially when sending large batches of events.

### Importing Historical Events

The library supports importing historical events (events older than 5 days that are not accepted using /track) via the `/import` endpoint. Project token will be used for basic auth.

### Custom Import Batch Size

When importing large events through the `/import` endpoint, you may need to control the batch size to prevent exceeding the server's 1MB uncompressed JSON payload limit. The batch size can be configured between 1 and 2000 (default is 2000):

    // Import with default batch size (2000)
    MixpanelAPI mixpanel = new MixpanelAPI();
    
    // Import with custom batch size (500)
    MixpanelAPI mixpanel = new MixpanelAPI(500);

### Disabling Strict Import Validation

By default, the `/import` endpoint enforces strict validation (strict=1). You can disable strict validation by calling `disableStrictImport()` before delivering import messages. See the [Mixpanel Import API documentation](https://developer.mixpanel.com/reference/import-events) for more details about strict.

    MixpanelAPI mixpanel = new MixpanelAPI();
    mixpanel.disableStrictImport();  // Set strict=0 to skip validation
    mixpanel.deliver(delivery);

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

MixpanelAPI mixpanel = new MixpanelAPI(config);

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

try (MixpanelAPI mixpanel = new MixpanelAPI(config)) {
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
