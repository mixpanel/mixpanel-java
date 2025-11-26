package com.mixpanel.mixpanelapi.featureflags.model;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a rollout rule within a feature flag experiment.
 * <p>
 * A rollout defines the percentage of users that should receive this experiment,
 * optional runtime evaluation criteria, and an optional variant override.
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class Rollout {
    private final float rolloutPercentage;
    private Map<String, Object> runtimeEvaluationRule;
    private final Map<String, Object> legacyRuntimeEvaluationDefinition;
    private final VariantOverride variantOverride;
    private final Map<String, Float> variantSplits;

    /**
     * Creates a new Rollout with all parameters.
     *
     * @param rolloutPercentage the percentage of users to include (0.0-1.0)
     * @param runtimeEvaluationDefinition optional map of property name to expected value for targeting
     * @param variantOverride optional variant override to force selection
     * @param variantSplits optional map of variant key to split percentage at assignment group level
     */
    public Rollout(float rolloutPercentage, Map<String, Object> runtimeEvaluationDefinition, VariantOverride variantOverride, Map<String, Float> variantSplits) {
        this.rolloutPercentage = rolloutPercentage;
        this.legacyRuntimeEvaluationDefinition = runtimeEvaluationDefinition != null
            ? Collections.unmodifiableMap(runtimeEvaluationDefinition)
            : null;
        this.variantOverride = variantOverride;
        this.variantSplits = variantSplits != null
            ? Collections.unmodifiableMap(variantSplits)
            : null;
    }

    /**
     * Creates a new Rollout without runtime evaluation or variant override.
     *
     * @param rolloutPercentage the percentage of users to include (0.0-1.0)
     */
    public Rollout(float rolloutPercentage) {
        this(rolloutPercentage, null, null, null);
    }

    /**
     * @return the percentage of users to include in this rollout (0.0-1.0)
     */
    public float getRolloutPercentage() {
        return rolloutPercentage;
    }

    /**
     * @return optional map of property name to expected value for runtime evaluation, or null if not set
     */
    public Map<String, Object> getLegacyRuntimeEvaluationDefinition() {
        return legacyRuntimeEvaluationDefinition;
    }

    /**
     * @return optional variant override to force selection, or null if not set
     */
    public VariantOverride getVariantOverride() {
        return variantOverride;
    }

    /**
     * @return optional map of variant key to split percentage at assignment group level, or null if not set
     */
    public Map<String, Float> getVariantSplits() {
        return variantSplits;
    }

    /**
     * @return true if this rollout has runtime evaluation criteria
     */
    public boolean hasRuntimeEvaluation() {
        return legacyRuntimeEvaluationDefinition != null && !legacyRuntimeEvaluationDefinition.isEmpty();
    }

    /**
     * @return true if this rollout has a variant override
     */
    public boolean hasVariantOverride() {
        return variantOverride != null;
    }

    /**
     * @return true if this rollout has variant splits
     */
    public boolean hasVariantSplits() {
        return variantSplits != null && !variantSplits.isEmpty();
    }

    @Override
    public String toString() {
        return "Rollout{" +
                "rolloutPercentage=" + rolloutPercentage +
                ", runtimeEvaluationDefinition=" + legacyRuntimeEvaluationDefinition +
                ", variantOverride='" + variantOverride + '\'' +
                ", variantSplits=" + variantSplits +
                '}';
    }
}
