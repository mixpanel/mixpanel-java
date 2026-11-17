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

        long hash = fnv1a64(key, salt);

        // Normalize to [0.0, 1.0) matching Python's approach
        // Use Long.remainderUnsigned to handle negative values correctly
        return (float) (Long.remainderUnsigned(hash, 100) / 100.0);
    }

    /**
     * Computes the allocation bucket (0-99) that assigns a subject to an experiment entry
     * of a multi-experiment flag.
     * <p>
     * Uses the same mixed hash as {@link #mixedNormalizedHash}, so every bucket bit depends on
     * every input byte.
     * </p>
     *
     * @param key the input string to hash (typically the context value)
     * @param salt the salt to append to the input (typically flag key + hash salt)
     * @return an allocation bucket in the range [0, 99]
     * @throws IllegalArgumentException if key or salt is null
     */
    public static int allocationBucket(String key, String salt) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (salt == null) {
            throw new IllegalArgumentException("Salt cannot be null");
        }
        return (int) Long.remainderUnsigned(fmix64(fnv1a64(key, salt)), 100);
    }

    /**
     * Generates a normalized hash value in the range [0.0, 1.0) by passing the FNV-1a hash
     * through the MurmurHash3 64-bit finalizer, so every output bit depends on every input byte.
     * <p>
     * Used for variant selection within an experiment entry of a multi-experiment flag.
     * </p>
     *
     * @param key the input string to hash (typically user identifier + flag key)
     * @param salt the salt to append to the input
     * @return a float value in the range [0.0, 1.0)
     * @throws IllegalArgumentException if key or salt is null
     */
    public static float mixedNormalizedHash(String key, String salt) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (salt == null) {
            throw new IllegalArgumentException("Salt cannot be null");
        }
        return (float) (Long.remainderUnsigned(fmix64(fnv1a64(key, salt)), 100) / 100.0);
    }

    private static long fnv1a64(String key, String salt) {
        byte[] bytes = (key + salt).getBytes(StandardCharsets.UTF_8);

        long hash = FNV_OFFSET_BASIS_64;
        for (byte b : bytes) {
            // XOR with byte (converting to unsigned)
            hash ^= (b & 0xff);
            // Multiply by FNV prime
            hash *= FNV_PRIME_64;
        }
        return hash;
    }

    /** MurmurHash3 64-bit finalizer. */
    private static long fmix64(long hash) {
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= hash >>> 33;
        return hash;
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
