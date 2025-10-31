package com.mixpanel.mixpanelapi.featureflags.model;

import java.util.UUID;

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
    private final UUID experimentId;
    private final Boolean isExperimentActive;
    private final Boolean isQaTester;

    /**
     * Creates a SelectedVariant with only a value (key is null).
     * This is typically used for fallback responses.
     *
     * @param variantValue the fallback value
     */
    public SelectedVariant(T variantValue) {
        this(null, variantValue, null, null, null);
    }

    /**
     * Creates a new SelectedVariant with experimentation metadata.
     *
     * @param variantKey the key of the selected variant (may be null for fallback)
     * @param variantValue the value of the selected variant (may be null for fallback)
     * @param experimentId the experiment ID (may be null)
     * @param isExperimentActive whether the experiment is active (may be null)
     * @param isQaTester whether the user is a QA tester (may be null)
     */
    public SelectedVariant(String variantKey, T variantValue, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        this.variantKey = variantKey;
        this.variantValue = variantValue;
        this.experimentId = experimentId;
        this.isExperimentActive = isExperimentActive;
        this.isQaTester = isQaTester;
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
     * @return the experiment ID, or null if not set
     */
    public UUID getExperimentId() {
        return experimentId;
    }

    /**
     * @return whether the experiment is active, or null if not set
     */
    public Boolean getIsExperimentActive() {
        return isExperimentActive;
    }

    /**
     * @return whether the user is a QA tester, or null if not set
     */
    public Boolean getIsQaTester() {
        return isQaTester;
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
                ", experimentId=" + experimentId +
                ", isExperimentActive=" + isExperimentActive +
                ", isQaTester=" + isQaTester +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SelectedVariant<?> that = (SelectedVariant<?>) o;

        if (variantKey != null ? !variantKey.equals(that.variantKey) : that.variantKey != null) return false;
        if (variantValue != null ? !variantValue.equals(that.variantValue) : that.variantValue != null) return false;
        if (experimentId != null ? !experimentId.equals(that.experimentId) : that.experimentId != null) return false;
        if (isExperimentActive != null ? !isExperimentActive.equals(that.isExperimentActive) : that.isExperimentActive != null) return false;
        return isQaTester != null ? isQaTester.equals(that.isQaTester) : that.isQaTester == null;
    }
}
