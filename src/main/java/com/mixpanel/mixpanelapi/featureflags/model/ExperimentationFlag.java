package com.mixpanel.mixpanelapi.featureflags.model;

import java.util.UUID;

/**
 * Represents a complete feature flag definition.
 * <p>
 * An experimentation flag contains metadata (id, name, key, status, project)
 * and the ruleset that defines how variants are assigned to users.
 * </p>
 * <p>
 * This class is immutable and thread-safe.
 * </p>
 */
public final class ExperimentationFlag {
    private final String id;
    private final String name;
    private final String key;
    private final String status;
    private final int projectId;
    private final RuleSet ruleset;
    private final String context;
    private final UUID experimentId;
    private final Boolean isExperimentActive;
    private final String hashSalt;

    /**
     * Creates a new ExperimentationFlag.
     *
     * @param id the unique identifier for this flag
     * @param name the human-readable name of this flag
     * @param key the key used to reference this flag in code
     * @param status the current status of this flag
     * @param projectId the Mixpanel project ID this flag belongs to
     * @param ruleset the ruleset defining variant assignment logic
     * @param context the property name used for rollout hashing (e.g., "distinct_id")
     * @param experimentId the experiment ID (may be null)
     * @param isExperimentActive whether the experiment is active (may be null)
     * @param hashSalt the hash salt for this flag (may be null for legacy flags)
     */
    public ExperimentationFlag(String id, String name, String key, String status, int projectId, RuleSet ruleset, String context, UUID experimentId, Boolean isExperimentActive, String hashSalt) {
        this.id = id;
        this.name = name;
        this.key = key;
        this.status = status;
        this.projectId = projectId;
        this.ruleset = ruleset;
        this.context = context;
        this.experimentId = experimentId;
        this.isExperimentActive = isExperimentActive;
        this.hashSalt = hashSalt;
    }

    /**
     * @return the unique identifier for this flag
     */
    public String getId() {
        return id;
    }

    /**
     * @return the human-readable name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the key used to reference this flag
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the current status
     */
    public String getStatus() {
        return status;
    }

    /**
     * @return the project ID
     */
    public int getProjectId() {
        return projectId;
    }

    /**
     * @return the ruleset defining variant assignment
     */
    public RuleSet getRuleset() {
        return ruleset;
    }

    /**
     * @return the property name used for rollout hashing (e.g., "distinct_id")
     */
    public String getContext() {
        return context;
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
     * @return the hash salt for this flag, or null for legacy flags
     */
    public String getHashSalt() {
        return hashSalt;
    }

    @Override
    public String toString() {
        return "ExperimentationFlag{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", key='" + key + '\'' +
                ", status=" + status +
                ", projectId=" + projectId +
                ", ruleset=" + ruleset +
                ", context='" + context + '\'' +
                ", experimentId=" + experimentId +
                ", isExperimentActive=" + isExperimentActive +
                ", hashSalt='" + hashSalt + '\'' +
                '}';
    }
}
