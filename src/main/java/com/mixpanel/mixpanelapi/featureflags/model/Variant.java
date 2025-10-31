package com.mixpanel.mixpanelapi.featureflags.model;

/**
 * Represents a variant within a feature flag experiment.
 * <p>
 * A variant defines a specific variation of a feature flag with its key, value,
 * control status, and percentage split allocation.
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class Variant {
    private final String key;
    private final Object value;
    private final boolean isControl;
    private final float split;

    /**
     * Creates a new Variant.
     *
     * @param key the unique identifier for this variant
     * @param value the value associated with this variant (can be boolean, string, number, or JSON object)
     * @param isControl whether this variant is the control variant
     * @param split the percentage split allocation for this variant (0.0-1.0)
     */
    public Variant(String key, Object value, boolean isControl, float split) {
        this.key = key;
        this.value = value;
        this.isControl = isControl;
        this.split = split;
    }

    /**
     * @return the unique identifier for this variant
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the value associated with this variant
     */
    public Object getValue() {
        return value;
    }

    /**
     * @return true if this is the control variant
     */
    public boolean isControl() {
        return isControl;
    }

    /**
     * @return the percentage split allocation (0.0-1.0)
     */
    public float getSplit() {
        return split;
    }

    @Override
    public String toString() {
        return "Variant{" +
                "key='" + key + '\'' +
                ", value=" + value +
                ", isControl=" + isControl +
                ", split=" + split +
                '}';
    }
}
