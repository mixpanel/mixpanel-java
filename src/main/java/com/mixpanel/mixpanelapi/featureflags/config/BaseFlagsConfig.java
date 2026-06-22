package com.mixpanel.mixpanelapi.featureflags.config;

import java.util.concurrent.Executor;

import com.mixpanel.mixpanelapi.ServiceAccountCredential;

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
    private final Executor exposureExecutor;
    private final ServiceAccountCredential credentials;

    /**
     * Creates a new BaseFlagsConfig with specified settings.
     *
     * @param projectToken the Mixpanel project token
     * @param apiHost the API endpoint host
     * @param requestTimeoutSeconds HTTP request timeout in seconds
     * @param exposureExecutor executor used to dispatch exposure event HTTP sends; may be null
     * @param credentials service account credentials for authentication; may be null
     */
    protected BaseFlagsConfig(String projectToken, String apiHost, int requestTimeoutSeconds, Executor exposureExecutor, ServiceAccountCredential credentials) {
        this.projectToken = projectToken;
        this.apiHost = apiHost;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.exposureExecutor = exposureExecutor;
        this.credentials = credentials;
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
     * @return the Executor used to dispatch exposure event HTTP sends, or null for synchronous dispatch
     */
    public Executor getExposureExecutor() {
        return exposureExecutor;
    }

    /**
     * @return the service account credentials for authentication, or null if not configured
     */
    public ServiceAccountCredential getCredentials() {
        return credentials;
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
        protected Executor exposureExecutor;
        protected ServiceAccountCredential credentials;

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
         * Sets the executor used to dispatch exposure event HTTP sends.
         * <p>
         * When null (the default), exposure events are sent synchronously on the
         * calling thread — this adds HTTP latency to every flag evaluation when {@code reportExposure} is
         * enabled.
         * </p>
         * <p>
         * When set, the executor receives one {@link Runnable} per exposure event;
         * each {@code Runnable} performs a single HTTP POST. If the
         * executor fails to accept the task, the exposure event is dropped and a warning is logged.
         * </p>
         *
         * @param exposureExecutor executor for exposure event dispatch, or null for synchronous
         * @return this builder
         */
        public T exposureExecutor(Executor exposureExecutor) {
            this.exposureExecutor = exposureExecutor;
            return (T) this;
        }

        /**
         * Sets the service account credentials for authentication.
         * <p>
         * When provided, feature flag endpoints will use HTTP Basic Authentication with the
         * service account username and secret, and include project_id as a query parameter
         * instead of the token parameter.
         * </p>
         *
         * @param credentials service account credentials for authentication
         * @return this builder
         */
        public T credentials(ServiceAccountCredential credentials) {
            this.credentials = credentials;
            return (T) this;
        }

        /**
         * Builds the BaseFlagsConfig instance.
         *
         * @return a new BaseFlagsConfig
         */
        public BaseFlagsConfig build() {
            return new BaseFlagsConfig(projectToken, apiHost, requestTimeoutSeconds, exposureExecutor, credentials);
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
