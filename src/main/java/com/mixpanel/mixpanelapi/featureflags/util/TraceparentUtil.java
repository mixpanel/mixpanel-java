package com.mixpanel.mixpanelapi.featureflags.util;

import java.util.UUID;

/**
 * Utility class for generating W3C Trace Context traceparent headers.
 * <p>
 * Generates traceparent headers in the format: 00-{trace_id}-{span_id}-01
 * where trace_id is a 32-character hex string and span_id is a 16-character hex string.
 * </p>
 * <p>
 * This class is thread-safe.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
 */
public final class TraceparentUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private TraceparentUtil() {
        throw new AssertionError("TraceparentUtil should not be instantiated");
    }

    /**
     * Generates a W3C traceparent header value.
     * <p>
     * Format: 00-{trace_id}-{span_id}-01
     * Uses two separate UUIDs with dashes removed - one for trace_id (32 chars)
     * and one for span_id (16 chars).
     * </p>
     *
     * @return a traceparent header value
     */
    public static String generateTraceparent() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
