package com.mixpanel.mixpanelapi.featureflags.model;

import java.util.UUID;

/**
 * Represents the result of a feature flag evaluation.
 *
 * <p>Contains the selected variant key and its value. Both may be null if the
 * fallback was returned (e.g., flag not found, evaluation error). The
 * {@link Source} on the variant explains where it came from: {@link Source.Local}
 * / {@link Source.Remote} on a real evaluation, {@link Source.Fallback} (with a
 * specific {@link Source.Fallback.Reason}) when the SDK fell back.</p>
 *
 * <p>This class is immutable and thread-safe.</p>
 *
 * @param <T> the type of the variant value
 */
public final class SelectedVariant<T> {
    private final String variantKey;
    private final T variantValue;
    private final UUID experimentId;
    private final Boolean isExperimentActive;
    private final Boolean isQaTester;
    private final Source source;

    /**
     * Creates a SelectedVariant with only a value (key is null) and a default
     * fallback source. Used by callers constructing a fallback to pass into
     * {@code getVariant} — the SDK stamps a specific source/reason before
     * returning.
     *
     * @param variantValue the fallback value
     */
    public SelectedVariant(T variantValue) {
        this(null, variantValue, null, null, null, Source.fallback(Source.Fallback.Reason.FLAG_NOT_FOUND));
    }

    /**
     * Creates a new SelectedVariant with experimentation metadata. Defaults
     * source to {@link Source#local()}.
     *
     * @deprecated Retained for source compatibility with v1.9.0. Prefer the
     * 6-arg overload to pass an explicit {@link Source}, or the 1-arg
     * {@link #SelectedVariant(Object)} constructor for consumer-side fallbacks.
     *
     * @param variantKey the variant key (null if this is a fallback)
     * @param variantValue the variant value
     * @param experimentId the experiment ID, or null
     * @param isExperimentActive whether the experiment is active, or null
     * @param isQaTester whether the user is a QA tester, or null
     */
    @Deprecated
    public SelectedVariant(String variantKey, T variantValue, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        this(variantKey, variantValue, experimentId, isExperimentActive, isQaTester, Source.local());
    }

    /**
     * Creates a new SelectedVariant with experimentation metadata and an explicit source.
     *
     * @param variantKey the variant key (null if this is a fallback)
     * @param variantValue the variant value
     * @param experimentId the experiment ID, or null
     * @param isExperimentActive whether the experiment is active, or null
     * @param isQaTester whether the user is a QA tester, or null
     * @param source where this variant came from; null is coalesced to {@link Source#local()}
     */
    public SelectedVariant(String variantKey, T variantValue, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester, Source source) {
        this.variantKey = variantKey;
        this.variantValue = variantValue;
        this.experimentId = experimentId;
        this.isExperimentActive = isExperimentActive;
        this.isQaTester = isQaTester;
        this.source = source != null ? source : Source.local();
    }

    /** @return where this variant came from; never null. */
    public Source getSource() {
        return source;
    }

    /**
     * Returns a copy of this variant tagged with the given source.
     * Used by providers so they don't mutate the caller's fallback object
     * when returning it on a no-match path.
     */
    public SelectedVariant<T> withSource(Source source) {
        return new SelectedVariant<T>(variantKey, variantValue, experimentId, isExperimentActive, isQaTester, source);
    }

    /** @return the variant key, or null if this is a fallback */
    public String getVariantKey() {
        return variantKey;
    }

    /** @return the variant value */
    public T getVariantValue() {
        return variantValue;
    }

    /** @return the experiment ID, or null if not set */
    public UUID getExperimentId() {
        return experimentId;
    }

    /** @return whether the experiment is active, or null if not set */
    public Boolean getIsExperimentActive() {
        return isExperimentActive;
    }

    /** @return whether the user is a QA tester, or null if not set */
    public Boolean getIsQaTester() {
        return isQaTester;
    }

    /**
     * @return true if this represents a successfully selected variant (not a fallback).
     * <p>Determined by the {@link Source} rather than by {@code variantKey != null}:
     * a fallback is whatever the SDK stamped as {@link Source.Fallback}, regardless
     * of whether the caller's fallback object happened to carry a key.</p>
     */
    public boolean isSuccess() {
        return !(source instanceof Source.Fallback);
    }

    /** @return true if this represents a fallback value */
    public boolean isFallback() {
        return source instanceof Source.Fallback;
    }

    @Override
    public String toString() {
        return "SelectedVariant{" +
                "variantKey='" + variantKey + '\'' +
                ", variantValue=" + variantValue +
                ", experimentId=" + experimentId +
                ", isExperimentActive=" + isExperimentActive +
                ", isQaTester=" + isQaTester +
                ", source=" + source +
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
        if (isQaTester != null ? !isQaTester.equals(that.isQaTester) : that.isQaTester != null) return false;
        return source.equals(that.source);
    }

    @Override
    public int hashCode() {
        int result = variantKey != null ? variantKey.hashCode() : 0;
        result = 31 * result + (variantValue != null ? variantValue.hashCode() : 0);
        result = 31 * result + (experimentId != null ? experimentId.hashCode() : 0);
        result = 31 * result + (isExperimentActive != null ? isExperimentActive.hashCode() : 0);
        result = 31 * result + (isQaTester != null ? isQaTester.hashCode() : 0);
        result = 31 * result + source.hashCode();
        return result;
    }
}
