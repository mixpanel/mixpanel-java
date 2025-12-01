# Mixpanel Java SDK - Jackson Extension

High-performance Jackson serializer extension for the Mixpanel Java SDK. This extension provides improved JSON serialization performance for large batch operations.

## Installation

Add this dependency to your project:

### Maven
```xml
<dependency>
    <groupId>com.mixpanel</groupId>
    <artifactId>mixpanel-java-extension-jackson</artifactId>
    <version>1.6.1</version>
</dependency>
```

### Gradle
```gradle
implementation 'com.mixpanel:mixpanel-java-extension-jackson:1.6.1'
```

This extension includes:
- `mixpanel-java` (core SDK)
- `jackson-core` 2.20.0

## Usage

To use the Jackson serializer, pass an instance to the MixpanelAPI builder:

```java
import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.mixpanel.mixpanelapi.internal.JacksonSerializer;

MixpanelAPI mixpanel = new MixpanelAPI.Builder()
    .jsonSerializer(new JacksonSerializer())
    .build();
```

## Key benefits
- **Significant performance gains**: 2-5x faster serialization for batches of 50+ messages
- **Optimal for `/import`**: Most beneficial when importing large batches (up to 2000 events)

The performance improvement is most noticeable when:
- Importing historical data via the `/import` endpoint
- Sending batches of 50+ events
- Processing high-volume event streams

## License

```
See LICENSE File for details.
```