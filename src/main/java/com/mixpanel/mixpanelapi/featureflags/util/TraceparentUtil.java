package com.mixpanel.mixpanelapi.featureflags.util;

import java.security.SecureRandom;

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
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

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
     * </p>
     *
     * @return a traceparent header value
     */
    public static String generateTraceparent() {
        String traceId = generateRandomHex(32);
        String spanId = generateRandomHex(16);
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /**
     * Generates a random hexadecimal string of the specified length.
     *
     * @param length the number of hex characters to generate
     * @return a random hex string
     */
    private static String generateRandomHex(int length) {
        byte[] bytes = new byte[length / 2];
        RANDOM.nextBytes(bytes);

        char[] hexChars = new char[length];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }

        return new String(hexChars);
    }
}
