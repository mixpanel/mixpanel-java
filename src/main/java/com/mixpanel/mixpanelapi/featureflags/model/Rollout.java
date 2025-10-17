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
    private final Map<String, Object> runtimeEvaluationDefinition;
    private final String variantOverride;

    /**
     * Creates a new Rollout with runtime evaluation and variant override.
     *
     * @param rolloutPercentage the percentage of users to include (0.0-1.0)
     * @param runtimeEvaluationDefinition optional map of property name to expected value for targeting
     * @param variantOverride optional variant key to force selection
     */
    public Rollout(float rolloutPercentage, Map<String, Object> runtimeEvaluationDefinition, String variantOverride) {
        this.rolloutPercentage = rolloutPercentage;
        this.runtimeEvaluationDefinition = runtimeEvaluationDefinition != null
            ? Collections.unmodifiableMap(runtimeEvaluationDefinition)
            : null;
        this.variantOverride = variantOverride;
    }

    /**
     * Creates a new Rollout without runtime evaluation or variant override.
     *
     * @param rolloutPercentage the percentage of users to include (0.0-1.0)
     */
    public Rollout(float rolloutPercentage) {
        this(rolloutPercentage, null, null);
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
    public Map<String, Object> getRuntimeEvaluationDefinition() {
        return runtimeEvaluationDefinition;
    }

    /**
     * @return optional variant key to force selection, or null if not set
     */
    public String getVariantOverride() {
        return variantOverride;
    }

    /**
     * @return true if this rollout has runtime evaluation criteria
     */
    public boolean hasRuntimeEvaluation() {
        return runtimeEvaluationDefinition != null && !runtimeEvaluationDefinition.isEmpty();
    }

    /**
     * @return true if this rollout has a variant override
     */
    public boolean hasVariantOverride() {
        return variantOverride != null;
    }

    @Override
    public String toString() {
        return "Rollout{" +
                "rolloutPercentage=" + rolloutPercentage +
                ", runtimeEvaluationDefinition=" + runtimeEvaluationDefinition +
                ", variantOverride='" + variantOverride + '\'' +
                '}';
    }
}
