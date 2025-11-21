package com.mixpanel.mixpanelapi.internal;

import java.util.logging.Logger;

/**
 * Factory for creating JsonSerializer instances.
 * Automatically detects if Jackson is available on the classpath and returns
 * the appropriate implementation for optimal performance.
 *
 * @since 1.6.0
 */
public class SerializerFactory {

    private static final Logger LOGGER = Logger.getLogger(SerializerFactory.class.getName());
    private static final boolean JACKSON_AVAILABLE;
    private static JsonSerializer instance;

    static {
        boolean jacksonFound = false;
        try {
            // Check if Jackson classes are available
            Class.forName("com.fasterxml.jackson.core.JsonFactory");
            Class.forName("com.fasterxml.jackson.core.JsonGenerator");
            jacksonFound = true;
            LOGGER.info("Jackson detected on classpath. High-performance JSON serialization will be used for large batches.");
        } catch (ClassNotFoundException e) {
            LOGGER.info("Jackson not found on classpath. Using standard org.json serialization. " +
                    "Add jackson-databind dependency for improved performance with large batches.");
        }
        JACKSON_AVAILABLE = jacksonFound;
    }

    /**
     * Returns a singleton JsonSerializer instance.
     * If Jackson is available on the classpath, returns a JacksonSerializer for better performance.
     * Otherwise, returns an OrgJsonSerializer for compatibility.
     *
     * @return A JsonSerializer instance
     */
    public static synchronized JsonSerializer getInstance() {
        if (instance == null) {
            if (JACKSON_AVAILABLE) {
                try {
                    instance = new JacksonSerializer();
                    LOGGER.fine("Using Jackson serializer for high performance");
                } catch (NoClassDefFoundError e) {
                    // Fallback if runtime loading fails
                    LOGGER.warning("Failed to initialize Jackson serializer, falling back to org.json: " + e.getMessage());
                    instance = new OrgJsonSerializer();
                }
            } else {
                instance = new OrgJsonSerializer();
                LOGGER.fine("Using org.json serializer");
            }
        }
        return instance;
    }

    /**
     * Checks if Jackson is available on the classpath.
     *
     * @return true if Jackson is available, false otherwise
     */
    public static boolean isJacksonAvailable() {
        return JACKSON_AVAILABLE;
    }

    /**
     * Gets the name of the current serializer implementation.
     *
     * @return The implementation name
     */
    public static String getCurrentImplementation() {
        return getInstance().getImplementationName();
    }

    /**
     * For testing purposes - allows resetting the singleton instance.
     * Should not be used in production code.
     */
    static void resetInstance() {
        instance = null;
    }
}