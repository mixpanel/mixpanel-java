package com.mixpanel.mixpanelapi.featureflags.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.JSONObject;

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
    private final JSONObject runtimeEvaluationRule;
    private final Map<String, Object> legacyRuntimeEvaluationDefinition;
    private final VariantOverride variantOverride;
    private final Map<String, Float> variantSplits;
    private final String type;
    private final UUID experimentId;
    private final String rawExperimentId;
    private final String experimentStatus;
    private final Boolean isExperimentActive;
    private final List<AllocationRange> allocation;

    /**
     * Creates a new Rollout with all parameters, including the fields of an experiment entry
     * of a multi-experiment flag. Experiment entries assign subjects by allocation bucket and
     * ignore rolloutPercentage.
     *
     * @param rolloutPercentage the percentage of users to include (0.0-1.0)
     * @param runtimeEvaluationRule optional JSONObject containing jsonLogic rule for targeting
     * @param legacyRuntimeEvaluationDefinition optional map of property name to expected value for targeting
     * @param variantOverride optional variant override to force selection
     * @param variantSplits optional map of variant key to split percentage at assignment group level
     * @param type optional rollout type; "experiment" marks an experiment entry
     * @param experimentId optional experiment ID of an experiment entry
     * @param rawExperimentId optional experiment ID of an experiment entry as it appears in the flag definition
     * @param experimentStatus optional experiment status of an experiment entry (e.g. "draft", "active", "concluded")
     * @param isExperimentActive optional flag for whether the experiment of an experiment entry is active
     * @param allocation optional allocation bucket ranges of an experiment entry
     */
    public Rollout(float rolloutPercentage, JSONObject runtimeEvaluationRule, Map<String, Object> legacyRuntimeEvaluationDefinition,
                   VariantOverride variantOverride, Map<String, Float> variantSplits, String type, UUID experimentId,
                   String rawExperimentId, String experimentStatus, Boolean isExperimentActive, List<AllocationRange> allocation) {
        this.rolloutPercentage = rolloutPercentage;
        this.legacyRuntimeEvaluationDefinition = legacyRuntimeEvaluationDefinition != null
            ? Collections.unmodifiableMap(legacyRuntimeEvaluationDefinition)
            : null;
        this.runtimeEvaluationRule = runtimeEvaluationRule;
        this.variantOverride = variantOverride;
        this.variantSplits = variantSplits != null
            ? Collections.unmodifiableMap(variantSplits)
            : null;
        this.type = type;
        this.experimentId = experimentId;
        this.rawExperimentId = rawExperimentId;
        this.experimentStatus = experimentStatus;
        this.isExperimentActive = isExperimentActive;
        this.allocation = allocation != null
            ? Collections.unmodifiableList(allocation)
            : null;
    }

    /**
     * Creates a new Rollout with all parameters.
     *
     * @param rolloutPercentage the percentage of users to include (0.0-1.0)
     * @param runtimeEvaluationRule optional JSONObject containing jsonLogic rule for targeting
     * @param legacyRuntimeEvaluationDefinition optional map of property name to expected value for targeting
     * @param variantOverride optional variant override to force selection
     * @param variantSplits optional map of variant key to split percentage at assignment group level
     */
    public Rollout(float rolloutPercentage, JSONObject runtimeEvaluationRule, Map<String, Object> legacyRuntimeEvaluationDefinition, VariantOverride variantOverride, Map<String, Float> variantSplits) {
        this(rolloutPercentage, runtimeEvaluationRule, legacyRuntimeEvaluationDefinition, variantOverride, variantSplits,
            null, null, null, null, null, null);
    }

    /**
     * Creates a new Rollout with all legacy parameters.
     *
     * @param rolloutPercentage the percentage of users to include (0.0-1.0)
     * @param legacyRuntimeEvaluationDefinition optional map of property name to expected value for targeting
     * @param variantOverride optional variant override to force selection
     * @param variantSplits optional map of variant key to split percentage at assignment group level
     */
    public Rollout(float rolloutPercentage, Map<String, Object> legacyRuntimeEvaluationDefinition, VariantOverride variantOverride, Map<String, Float> variantSplits) {
        this(rolloutPercentage, null, legacyRuntimeEvaluationDefinition, variantOverride, variantSplits);
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
    public boolean hasLegacyRuntimeEvaluation() {
        return legacyRuntimeEvaluationDefinition != null && !legacyRuntimeEvaluationDefinition.isEmpty();
    }

    /**
     * @return true if this rollout has runtime evaluation criteria
     */
    public boolean hasRuntimeEvaluation() {
        return runtimeEvaluationRule != null && runtimeEvaluationRule.length() > 0;
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

    /**
     * @return the rollout type, or null if not set
     */
    public String getType() {
        return type;
    }

    /**
     * @return the experiment ID of an experiment entry, or null if not set or not a valid UUID
     */
    public UUID getExperimentId() {
        return experimentId;
    }

    /**
     * @return the experiment ID of an experiment entry as it appears in the flag definition, or null if not set
     */
    public String getRawExperimentId() {
        return rawExperimentId;
    }

    /**
     * @return the experiment status of an experiment entry, or null if not set
     */
    public String getExperimentStatus() {
        return experimentStatus;
    }

    /**
     * @return whether the experiment of an experiment entry is active, or null if not set
     */
    public Boolean getIsExperimentActive() {
        return isExperimentActive;
    }

    /**
     * @return the allocation bucket ranges of an experiment entry, or null if not set
     */
    public List<AllocationRange> getAllocation() {
        return allocation;
    }

    /**
     * @return true if this rollout is an experiment entry of a multi-experiment flag
     */
    public boolean isExperiment() {
        return "experiment".equals(type);
    }

    /**
     * @return true if this rollout is an experiment entry whose experiment is still a draft
     */
    public boolean isDraftExperiment() {
        return "draft".equals(experimentStatus);
    }

    /**
     * @param bucket the allocation bucket (0-99)
     * @return true if any allocation range contains the bucket
     */
    public boolean allocationContains(int bucket) {
        if (allocation == null) {
            return false;
        }
        for (AllocationRange allocationRange : allocation) {
            if (allocationRange.contains(bucket)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Rollout{" +
                "rolloutPercentage=" + rolloutPercentage +
                ", legacyRuntimeEvaluationDefinition=" + legacyRuntimeEvaluationDefinition +
                ", runtimeEvaluationRule=" + runtimeEvaluationRule +
                ", variantOverride='" + variantOverride + '\'' +
                ", variantSplits=" + variantSplits +
                ", type=" + type +
                ", experimentId=" + rawExperimentId +
                ", experimentStatus=" + experimentStatus +
                ", isExperimentActive=" + isExperimentActive +
                ", allocation=" + allocation +
                '}';
    }

    /**
     * @return optional JSONObject containing JsonLogic rule for runtime evaluation, or null if not set
     */
    public JSONObject getRuntimeEvaluationRule() {
        return runtimeEvaluationRule;
    }
}
