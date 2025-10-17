package com.mixpanel.mixpanelapi.featureflags.config;

/**
 * Base configuration for feature flags providers.
 * <p>
 * Contains common configuration settings shared by both local and remote evaluation modes.
 * </p>
 */
public class BaseFlagsConfig {
    private final String projectToken;
    private final String apiHost;
    private final int requestTimeoutSeconds;

    /**
     * Creates a new BaseFlagsConfig with specified settings.
     *
     * @param projectToken the Mixpanel project token
     * @param apiHost the API endpoint host
     * @param requestTimeoutSeconds HTTP request timeout in seconds
     */
    protected BaseFlagsConfig(String projectToken, String apiHost, int requestTimeoutSeconds) {
        this.projectToken = projectToken;
        this.apiHost = apiHost;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    /**
     * @return the Mixpanel project token
     */
    public String getProjectToken() {
        return projectToken;
    }

    /**
     * @return the API endpoint host
     */
    public String getApiHost() {
        return apiHost;
    }

    /**
     * @return the HTTP request timeout in seconds
     */
    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    /**
     * Builder for BaseFlagsConfig.
     *
     * @param <T> the type of builder (for subclass builders)
     */
    @SuppressWarnings("unchecked")
    public static class Builder<T extends Builder<T>> {
        protected String projectToken;
        protected String apiHost = "api.mixpanel.com";
        protected int requestTimeoutSeconds = 10;

        /**
         * Sets the project token.
         *
         * @param projectToken the Mixpanel project token
         * @return this builder
         */
        public T projectToken(String projectToken) {
            this.projectToken = projectToken;
            return (T) this;
        }

        /**
         * Sets the API host.
         *
         * @param apiHost the API endpoint host (e.g., "api.mixpanel.com", "api-eu.mixpanel.com")
         * @return this builder
         */
        public T apiHost(String apiHost) {
            this.apiHost = apiHost;
            return (T) this;
        }

        /**
         * Sets the request timeout.
         *
         * @param requestTimeoutSeconds HTTP request timeout in seconds
         * @return this builder
         */
        public T requestTimeoutSeconds(int requestTimeoutSeconds) {
            this.requestTimeoutSeconds = requestTimeoutSeconds;
            return (T) this;
        }

        /**
         * Builds the BaseFlagsConfig instance.
         *
         * @return a new BaseFlagsConfig
         */
        public BaseFlagsConfig build() {
            return new BaseFlagsConfig(projectToken, apiHost, requestTimeoutSeconds);
        }
    }

    /**
     * Creates a new builder for BaseFlagsConfig.
     *
     * @return a new builder instance
     */
    public static Builder<?> builder() {
        return new Builder<>();
    }
}
