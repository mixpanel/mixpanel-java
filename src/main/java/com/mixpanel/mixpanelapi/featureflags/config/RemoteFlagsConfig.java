package com.mixpanel.mixpanelapi.featureflags.config;

import java.util.concurrent.Executor;

/**
 * Configuration for remote feature flags evaluation.
 * <p>
 * Extends {@link BaseFlagsConfig} with settings specific to remote evaluation mode.
 * Currently contains no additional configuration beyond the base settings.
 * </p>
 */
public final class RemoteFlagsConfig extends BaseFlagsConfig {

    /**
     * Creates a new RemoteFlagsConfig with specified settings.
     *
     * @param projectToken the Mixpanel project token
     * @param apiHost the API endpoint host
     * @param requestTimeoutSeconds HTTP request timeout in seconds
     * @param exposureExecutor executor used to dispatch exposure event HTTP sends; may be null
     */
    private RemoteFlagsConfig(String projectToken, String apiHost, int requestTimeoutSeconds, Executor exposureExecutor) {
        super(projectToken, apiHost, requestTimeoutSeconds, exposureExecutor);
    }

    /**
     * Builder for RemoteFlagsConfig.
     */
    public static final class Builder extends BaseFlagsConfig.Builder<Builder> {

        /**
         * Builds the RemoteFlagsConfig instance.
         *
         * @return a new RemoteFlagsConfig
         */
        @Override
        public RemoteFlagsConfig build() {
            return new RemoteFlagsConfig(projectToken, apiHost, requestTimeoutSeconds, exposureExecutor);
        }
    }

    /**
     * Creates a new builder for RemoteFlagsConfig.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
