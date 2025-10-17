package com.mixpanel.mixpanelapi.featureflags.config;

/**
 * Configuration for local feature flags evaluation.
 * <p>
 * Extends {@link BaseFlagsConfig} with settings specific to local evaluation mode,
 * including polling configuration for periodic flag definition synchronization.
 * </p>
 */
public final class LocalFlagsConfig extends BaseFlagsConfig {
    private final boolean enablePolling;
    private final int pollingIntervalSeconds;

    /**
     * Creates a new LocalFlagsConfig with all settings.
     *
     * @param projectToken the Mixpanel project token
     * @param apiHost the API endpoint host
     * @param requestTimeoutSeconds HTTP request timeout in seconds
     * @param enablePolling whether to periodically refresh flag definitions
     * @param pollingIntervalSeconds time between refresh cycles in seconds
     */
    private LocalFlagsConfig(String projectToken, String apiHost, int requestTimeoutSeconds, boolean enablePolling, int pollingIntervalSeconds) {
        super(projectToken, apiHost, requestTimeoutSeconds);
        this.enablePolling = enablePolling;
        this.pollingIntervalSeconds = pollingIntervalSeconds;
    }

    /**
     * @return true if polling is enabled
     */
    public boolean isEnablePolling() {
        return enablePolling;
    }

    /**
     * @return the polling interval in seconds
     */
    public int getPollingIntervalSeconds() {
        return pollingIntervalSeconds;
    }

    /**
     * Builder for LocalFlagsConfig.
     */
    public static final class Builder extends BaseFlagsConfig.Builder<Builder> {
        private boolean enablePolling = true;
        private int pollingIntervalSeconds = 60;

        /**
         * Sets whether polling should be enabled.
         *
         * @param enablePolling true to enable periodic flag definition refresh
         * @return this builder
         */
        public Builder enablePolling(boolean enablePolling) {
            this.enablePolling = enablePolling;
            return this;
        }

        /**
         * Sets the polling interval.
         *
         * @param pollingIntervalSeconds time between refresh cycles in seconds
         * @return this builder
         */
        public Builder pollingIntervalSeconds(int pollingIntervalSeconds) {
            this.pollingIntervalSeconds = pollingIntervalSeconds;
            return this;
        }

        /**
         * Builds the LocalFlagsConfig instance.
         *
         * @return a new LocalFlagsConfig
         */
        @Override
        public LocalFlagsConfig build() {
            return new LocalFlagsConfig(projectToken, apiHost, requestTimeoutSeconds, enablePolling, pollingIntervalSeconds);
        }
    }

    /**
     * Creates a new builder for LocalFlagsConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
