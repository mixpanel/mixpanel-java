package com.mixpanel.mixpanelapi.featureflags.util;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for hashing operations used in feature flag evaluation.
 * <p>
 * Implements the FNV-1a (Fowler-Noll-Vo hash, variant 1a) algorithm to generate
 * deterministic, uniformly distributed hash values in the range [0.0, 1.0).
 * </p>
 * <p>
 * This class is thread-safe and all methods are static.
 * </p>
 */
public final class HashUtils {

    /**
     * FNV-1a 64-bit offset basis constant.
     */
    private static final long FNV_OFFSET_BASIS_64 = 0xcbf29ce484222325L;

    /**
     * FNV-1a 64-bit prime constant.
     */
    private static final long FNV_PRIME_64 = 0x100000001b3L;

    // Private constructor to prevent instantiation
    private HashUtils() {
        throw new AssertionError("HashUtils should not be instantiated");
    }

    /**
     * Generates a normalized hash value in the range [0.0, 1.0) using the FNV-1a algorithm.
     *
     * @param key the input string to hash (typically user identifier + flag key)
     * @param salt the salt to append to the input (e.g., "rollout" or "variant")
     * @return a float value in the range [0.0, 1.0)
     * @throws IllegalArgumentException if key or salt is null
     */
    public static float normalizedHash(String key, String salt) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (salt == null) {
            throw new IllegalArgumentException("Salt cannot be null");
        }

        // Combine key and salt
        String combined = key + salt;
        byte[] bytes = combined.getBytes(StandardCharsets.UTF_8);

        // FNV-1a 64-bit hash
        long hash = FNV_OFFSET_BASIS_64;
        for (byte b : bytes) {
            // XOR with byte (converting to unsigned)
            hash ^= (b & 0xff);
            // Multiply by FNV prime
            hash *= FNV_PRIME_64;
        }

        // Normalize to [0.0, 1.0) matching Python's approach
        // Use Long.remainderUnsigned to handle negative values correctly
        return (float) (Long.remainderUnsigned(hash, 100) / 100.0);
    }

    /**
     * Generates a normalized hash value for rollout selection.
     * <p>
     * Convenience method that uses "rollout" as the salt.
     * </p>
     *
     * @param input the input string to hash (typically user identifier + flag key)
     * @return a float value in the range [0.0, 1.0)
     */
    public static float rolloutHash(String input) {
        return normalizedHash(input, "rollout");
    }

    /**
     * Generates a normalized hash value for variant selection.
     * <p>
     * Convenience method that uses "variant" as the salt.
     * </p>
     *
     * @param input the input string to hash (typically user identifier + flag key)
     * @return a float value in the range [0.0, 1.0)
     */
    public static float variantHash(String input) {
        return normalizedHash(input, "variant");
    }
}
