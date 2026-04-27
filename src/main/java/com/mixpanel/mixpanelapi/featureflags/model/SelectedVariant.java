package com.mixpanel.mixpanelapi.featureflags.model;

import java.util.UUID;

/**
 * Represents the result of a feature flag evaluation.
 * <p>
 * Contains the originating flag key, the selected variant key, and the variant value.
 * The variant key and value may be null if the fallback was returned (e.g., flag not
 * found, evaluation error); the flag key is populated by the SDK whenever a variant is
 * returned from {@code getVariant}, including on fallback paths.
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 *
 * @param <T> the type of the variant value
 */
public final class SelectedVariant<T> {
    private final String flagKey;
    private final String variantKey;
    private final T variantValue;
    private final UUID experimentId;
    private final Boolean isExperimentActive;
    private final Boolean isQaTester;

    /**
     * Creates a SelectedVariant carrying only a value. Both the flag key and variant key are
     * null. This is the typical form for constructing a fallback to pass into
     * {@code getVariant}; the SDK will stamp the requested flag key onto the returned variant.
     *
     * @param variantValue the fallback value
     */
    public SelectedVariant(T variantValue) {
        this(null, null, variantValue, null, null, null);
    }

    /**
     * Creates a new SelectedVariant with experimentation metadata. The flag key will be null.
     *
     * @param variantKey the key of the selected variant (may be null for fallback)
     * @param variantValue the value of the selected variant (may be null for fallback)
     * @param experimentId the experiment ID (may be null)
     * @param isExperimentActive whether the experiment is active (may be null)
     * @param isQaTester whether the user is a QA tester (may be null)
     * @deprecated Use {@link #SelectedVariant(String, String, Object, UUID, Boolean, Boolean)}
     *             which also accepts the originating flag key, so the resulting variant can be
     *             associated with the flag it was selected for.
     */
    @Deprecated
    public SelectedVariant(String variantKey, T variantValue, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        this(null, variantKey, variantValue, experimentId, isExperimentActive, isQaTester);
    }

    /**
     * Creates a new SelectedVariant with the originating flag key and experimentation metadata.
     *
     * @param flagKey the key of the flag this variant was selected for (may be null for fallback)
     * @param variantKey the key of the selected variant (may be null for fallback)
     * @param variantValue the value of the selected variant (may be null for fallback)
     * @param experimentId the experiment ID (may be null)
     * @param isExperimentActive whether the experiment is active (may be null)
     * @param isQaTester whether the user is a QA tester (may be null)
     */
    public SelectedVariant(String flagKey, String variantKey, T variantValue, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        this.flagKey = flagKey;
        this.variantKey = variantKey;
        this.variantValue = variantValue;
        this.experimentId = experimentId;
        this.isExperimentActive = isExperimentActive;
        this.isQaTester = isQaTester;
    }

    /**
     * @return the flag key this variant was selected for, or null if not set (e.g., for fallbacks
     *         or variants returned by code paths that don't propagate the flag key)
     */
    public String getFlagKey() {
        return flagKey;
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
     * Returns a SelectedVariant with the given flag key, copying all other fields from this instance.
     * Returns this instance unchanged if the flag key already matches.
     *
     * @param flagKey the flag key to associate with the variant (may be null)
     * @return a SelectedVariant with the requested flag key
     */
    public SelectedVariant<T> withFlagKey(String flagKey) {
        if (flagKey == null ? this.flagKey == null : flagKey.equals(this.flagKey)) {
            return this;
        }
        return new SelectedVariant<>(flagKey, variantKey, variantValue, experimentId, isExperimentActive, isQaTester);
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
                "flagKey='" + flagKey + '\'' +
                ", variantKey='" + variantKey + '\'' +
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

        if (flagKey != null ? !flagKey.equals(that.flagKey) : that.flagKey != null) return false;
        if (variantKey != null ? !variantKey.equals(that.variantKey) : that.variantKey != null) return false;
        if (variantValue != null ? !variantValue.equals(that.variantValue) : that.variantValue != null) return false;
        if (experimentId != null ? !experimentId.equals(that.experimentId) : that.experimentId != null) return false;
        if (isExperimentActive != null ? !isExperimentActive.equals(that.isExperimentActive) : that.isExperimentActive != null) return false;
        return isQaTester != null ? isQaTester.equals(that.isQaTester) : that.isQaTester == null;
    }
}
