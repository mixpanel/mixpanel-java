package com.mixpanel.mixpanelapi.featureflags.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents the complete set of rules for a feature flag experiment.
 * <p>
 * A ruleset contains all variants available for the flag, rollout rules
 * (evaluated in order), and optional test user overrides.
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class RuleSet {
    private final List<Variant> variants;
    private final List<Rollout> rollouts;
    private final Map<String, String> testUserOverrides;

    /**
     * Creates a new RuleSet with all components.
     *
     * @param variants the list of available variants for this flag
     * @param rollouts the list of rollout rules (evaluated in order)
     * @param testUserOverrides optional map of distinct_id to variant key for test users
     */
    public RuleSet(List<Variant> variants, List<Rollout> rollouts, Map<String, String> testUserOverrides) {
        this.variants = variants != null ? Collections.unmodifiableList(variants) : Collections.emptyList();
        this.rollouts = rollouts != null ? Collections.unmodifiableList(rollouts) : Collections.emptyList();
        this.testUserOverrides = testUserOverrides != null
            ? Collections.unmodifiableMap(testUserOverrides)
            : null;
    }

    /**
     * Creates a new RuleSet without test user overrides.
     *
     * @param variants the list of available variants for this flag
     * @param rollouts the list of rollout rules (evaluated in order)
     */
    public RuleSet(List<Variant> variants, List<Rollout> rollouts) {
        this(variants, rollouts, null);
    }

    /**
     * @return the list of available variants
     */
    public List<Variant> getVariants() {
        return variants;
    }

    /**
     * @return the list of rollout rules
     */
    public List<Rollout> getRollouts() {
        return rollouts;
    }

    /**
     * @return the map of test user overrides (distinct_id to variant key), or null if not set
     */
    public Map<String, String> getTestUserOverrides() {
        return testUserOverrides;
    }

    /**
     * @return true if test user overrides are configured
     */
    public boolean hasTestUserOverrides() {
        return testUserOverrides != null && !testUserOverrides.isEmpty();
    }

    @Override
    public String toString() {
        return "RuleSet{" +
                "variants=" + variants +
                ", rollouts=" + rollouts +
                ", testUserOverrides=" + testUserOverrides +
                '}';
    }
}
