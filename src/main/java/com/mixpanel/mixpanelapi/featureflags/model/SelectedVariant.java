package com.mixpanel.mixpanelapi.featureflags.model;

/**
 * Represents the result of a feature flag evaluation.
 * <p>
 * Contains the selected variant key and its value. Both may be null if the
 * fallback was returned (e.g., flag not found, evaluation error).
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 *
 * @param <T> the type of the variant value
 */
public final class SelectedVariant<T> {
    private final String variantKey;
    private final T variantValue;

    /**
     * Creates a new SelectedVariant with both key and value.
     *
     * @param variantKey the key of the selected variant (may be null for fallback)
     * @param variantValue the value of the selected variant (may be null for fallback)
     */
    public SelectedVariant(String variantKey, T variantValue) {
        this.variantKey = variantKey;
        this.variantValue = variantValue;
    }

    /**
     * Creates a SelectedVariant with only a value (key is null).
     * This is typically used for fallback responses.
     *
     * @param variantValue the fallback value
     */
    public SelectedVariant(T variantValue) {
        this(null, variantValue);
    }

    /**
     * @return the variant key, or null if this is a fallback
     */
    public String getVariantKey() {
        return variantKey;
    }

    /**
     * @return the variant value
     */
    public T getVariantValue() {
        return variantValue;
    }

    /**
     * @return true if this represents a successfully selected variant (not a fallback)
     */
    public boolean isSuccess() {
        return variantKey != null;
    }

    /**
     * @return true if this represents a fallback value
     */
    public boolean isFallback() {
        return variantKey == null;
    }

    @Override
    public String toString() {
        return "SelectedVariant{" +
                "variantKey='" + variantKey + '\'' +
                ", variantValue=" + variantValue +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SelectedVariant<?> that = (SelectedVariant<?>) o;

        if (variantKey != null ? !variantKey.equals(that.variantKey) : that.variantKey != null) return false;
        return variantValue != null ? variantValue.equals(that.variantValue) : that.variantValue == null;
    }
}
