package com.mixpanel.mixpanelapi.featureflags.model;

/**
 * Represents the status of a feature flag.
 * <p>
 * This enum is used to indicate whether a flag is currently active or inactive.
 * </p>
 */
public enum FlagStatus {
    /**
     * The flag is active and can be evaluated.
     */
    ACTIVE("active"),

    /**
     * The flag is inactive and should not be evaluated.
     */
    INACTIVE("inactive");

    private final String value;

    FlagStatus(String value) {
        this.value = value;
    }

    /**
     * Gets the string representation of this status.
     *
     * @return the status value as a string
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a FlagStatus from a string value.
     *
     * @param value the string value to parse
     * @return the corresponding FlagStatus, or INACTIVE if the value is not recognized
     */
    public static FlagStatus fromString(String value) {
        if (value == null || value.isEmpty()) {
            return INACTIVE;
        }

        for (FlagStatus status : FlagStatus.values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }

        return INACTIVE;
    }

    @Override
    public String toString() {
        return value;
    }
}
