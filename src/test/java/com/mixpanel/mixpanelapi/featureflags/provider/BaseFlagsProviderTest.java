package com.mixpanel.mixpanelapi.featureflags.provider;

import org.junit.After;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for feature flags provider tests.
 * Provides shared test infrastructure, lifecycle management, and helper methods.
 */
public abstract class BaseFlagsProviderTest {

    // Shared constants
    protected static final String TEST_TOKEN = "test-token";
    protected static final String SDK_VERSION = "1.0.0";
    protected static final String TEST_USER = "user-123";

    /**
     * Shared test lifecycle - closes the provider after each test if it's closeable.
     */
    @After
    public void tearDown() {
        Object provider = getProvider();
        if (provider instanceof AutoCloseable) {
            try {
                ((AutoCloseable) provider).close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Abstract method for subclasses to provide their provider instance.
     * This allows the base class to manage the lifecycle.
     *
     * @return the provider instance to be closed after each test (if closeable)
     */
    protected abstract Object getProvider();

    /**
     * Helper to build a simple context with distinct_id.
     *
     * @param distinctId the distinct ID to include in the context
     * @return a context map with distinct_id
     */
    protected Map<String, Object> buildContext(String distinctId) {
        Map<String, Object> context = new HashMap<>();
        context.put("distinct_id", distinctId);
        return context;
    }

    /**
     * Helper to build context with custom properties.
     *
     * @param distinctId the distinct ID to include in the context
     * @param customProps custom properties to include
     * @return a context map with distinct_id and custom_properties
     */
    protected Map<String, Object> buildContextWithProperties(String distinctId, Map<String, Object> customProps) {
        Map<String, Object> context = buildContext(distinctId);
        context.put("custom_properties", customProps);
        return context;
    }
}
