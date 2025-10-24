package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for RemoteFlagsProvider.
 * Tests cover error handling, successful variant retrieval, exposure tracking,
 * and boolean convenience methods for remote flag evaluation.
 */
public class RemoteFlagsProviderTest extends BaseFlagsProviderTest {

    private TestableRemoteFlagsProvider provider;
    private RemoteFlagsConfig config;
    private MockEventSender eventSender;

    /**
     * Testable subclass of RemoteFlagsProvider that allows mocking HTTP responses.
     */
    private static class TestableRemoteFlagsProvider extends RemoteFlagsProvider {
        private final MockHttpProvider httpMock = new MockHttpProvider();

        public TestableRemoteFlagsProvider(RemoteFlagsConfig config, String sdkVersion, EventSender eventSender) {
            super(config, sdkVersion, eventSender);
        }

        public void setMockResponse(String urlPattern, String response) {
            httpMock.setMockResponse(urlPattern, response);
        }

        public void setMockException(IOException exception) {
            httpMock.setMockException(exception);
        }

        @Override
        protected String httpGet(String urlString) throws IOException {
            return httpMock.mockHttpGet(urlString);
        }
    }

    private static class MockEventSender implements EventSender {
        private final List<ExposureEvent> events = new ArrayList<>();

        static class ExposureEvent {
            String distinctId;
            String eventName;
            JSONObject properties;

            ExposureEvent(String distinctId, String eventName, JSONObject properties) {
                this.distinctId = distinctId;
                this.eventName = eventName;
                this.properties = properties;
            }
        }

        @Override
        public void sendEvent(String distinctId, String eventName, JSONObject properties) {
            events.add(new ExposureEvent(distinctId, eventName, properties));
        }

        public List<ExposureEvent> getEvents() {
            return events;
        }

        public void reset() {
            events.clear();
        }
    }

    @Before
    public void setUp() {
        config = RemoteFlagsConfig.builder()
            .projectToken(TEST_TOKEN)
            .requestTimeoutSeconds(5)
            .build();
        eventSender = new MockEventSender();
    }

    @Override
    protected Object getProvider() {
        return provider;
    }

    // #endregion

    // #region Helper Methods

    /**
     * Builds a mock remote flags API response
     */
    private String buildRemoteFlagsResponse(String flagKey, String variantKey, Object variantValue) {
        try {
            JSONObject root = new JSONObject();
            JSONObject flags = new JSONObject();
            JSONObject flagData = new JSONObject();
            flagData.put("variant_key", variantKey);
            flagData.put("variant_value", variantValue);
            flags.put(flagKey, flagData);
            root.put("flags", flags);
            return root.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test response", e);
        }
    }

    /**
     * Creates a test provider with custom HTTP response.
     * The response will be returned when the flags API URL is called.
     */
    private TestableRemoteFlagsProvider createProviderWithResponse(String jsonResponse) {
        TestableRemoteFlagsProvider testProvider = new TestableRemoteFlagsProvider(config, SDK_VERSION, eventSender);

        if (jsonResponse != null) {
            // Mock the flags endpoint
            testProvider.setMockResponse("/flags", jsonResponse);
        } else {
            // Simulate network error by setting exception
            testProvider.setMockException(new IOException("Simulated network error"));
        }

        return testProvider;
    }

    // #endregion

    // #region Error Handling Tests

    @Test
    public void testReturnFallbackWhenAPICallFails() {
        // Create provider that will throw IOException
        provider = createProviderWithResponse(null);

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        // Should return fallback due to network error
        assertEquals("fallback", result);
        assertEquals(0, eventSender.getEvents().size());
    }

    @Test
    public void testReturnFallbackWhenResponseFormatIsInvalid() {
        provider = new TestableRemoteFlagsProvider(config, SDK_VERSION, eventSender);

        // Set invalid JSON response
        provider.setMockResponse("/flags", "invalid json {{{");

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        // Should return fallback due to JSON parse error
        assertEquals("fallback", result);
        assertEquals(0, eventSender.getEvents().size());
    }

    @Test
    public void testReturnFallbackWhenFlagNotFoundInSuccessfulResponse() {
        // Set response with a different flag
        String response = buildRemoteFlagsResponse("other-flag", "variant-a", "value-a");
        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("non-existent-flag", "fallback", context);

        // Should return fallback when flag not found
        assertEquals("fallback", result);
        assertEquals(0, eventSender.getEvents().size());
    }

    // #endregion

    // #region Successful Variant Retrieval Tests

    @Test
    public void testReturnExpectedVariantFromAPI() {
        // Set up a successful response
        String response = buildRemoteFlagsResponse("test-flag", "variant-a", "test-value");
        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        // Should return the variant value from the API
        assertEquals("test-value", result);
    }

    // #endregion

    // #region Exposure Tracking Tests

    @Test
    public void testTrackExposureWhenVariantIsSelected() {
        // Set up a successful response
        String response = buildRemoteFlagsResponse("test-flag", "variant-a", "test-value");
        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        provider.getVariantValue("test-flag", "fallback", context);

        // Should track exposure
        assertEquals(1, eventSender.getEvents().size());
        MockEventSender.ExposureEvent event = eventSender.getEvents().get(eventSender.getEvents().size() - 1);
        assertEquals("user-123", event.distinctId);
        assertEquals("$experiment_started", event.eventName);
        assertEquals("test-flag", event.properties.getString("Experiment name"));
        assertEquals("variant-a", event.properties.getString("Variant name"));
        assertEquals("remote", event.properties.getString("Flag evaluation mode"));
        assertNotNull(event.properties.getString("Variant fetch start time"));
        assertNotNull(event.properties.getString("Variant fetch complete time"));
    }

    @Test
    public void testDoNotTrackExposureWhenReturningFallback() {
        // Create provider that will throw IOException
        provider = createProviderWithResponse(null);

        Map<String, Object> context = buildContext("user-123");
        provider.getVariantValue("test-flag", "fallback", context);

        // Should not track exposure when returning fallback
        assertEquals(0, eventSender.getEvents().size());
    }

    // #endregion

    // #region Boolean Convenience Method Tests

    @Test
    public void testIsEnabledReturnsTrueForBooleanTrueVariant() {
        // Set up response with boolean true value
        String response = buildRemoteFlagsResponse("test-flag", "enabled", true);
        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        boolean result = provider.isEnabled("test-flag", context);

        // Should return true for boolean true variant
        assertTrue(result);
    }

    @Test
    public void testIsEnabledReturnsFalseForBooleanFalseVariant() {
        // Set up response with boolean false value
        String response = buildRemoteFlagsResponse("test-flag", "disabled", false);
        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        boolean result = provider.isEnabled("test-flag", context);

        // Should return false for boolean false variant
        assertFalse(result);
    }

    // #endregion
}

