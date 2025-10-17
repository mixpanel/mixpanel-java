package com.mixpanel.mixpanelapi.featureflags.model;

/**
 * Represents the evaluation mode for feature flags.
 * <p>
 * This enum is used to indicate whether a flag is evaluated locally or remotely.
 * </p>
 */
public enum EvaluationMode {
    /**
     * Local evaluation mode - flags are evaluated client-side using cached definitions.
     */
    LOCAL("local"),

    /**
     * Remote evaluation mode - flags are evaluated server-side via API calls.
     */
    REMOTE("remote");

    private final String value;

    EvaluationMode(String value) {
        this.value = value;
    }

    /**
     * Gets the string representation of this evaluation mode.
     *
     * @return the evaluation mode value as a string
     */
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
