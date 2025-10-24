package com.mixpanel.mixpanelapi.featureflags.model;

/**
 * Represents a variant override within a rollout rule.
 * <p>
 * A variant override forces selection of a specific variant when a rollout matches.
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class VariantOverride {
    private final String key;

    /**
     * Creates a new VariantOverride.
     *
     * @param key the variant key to force selection of
     */
    public VariantOverride(String key) {
        this.key = key;
    }

    /**
     * @return the variant key
     */
    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "VariantOverride{" +
                "key='" + key + '\'' +
                '}';
    }
}
