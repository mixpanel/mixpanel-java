package com.mixpanel.mixpanelapi;

/**
 * Options for configuring how messages are delivered to Mixpanel.
 * Use the {@link Builder} to create instances.
 *
 * <p>Different options apply to different message types:
 * <ul>
 *   <li>{@code importStrictMode} - Only applies to import messages</li>
 *   <li>{@code useIpAddress} - Only applies to events, people, and groups messages (NOT imports)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * DeliveryOptions options = new DeliveryOptions.Builder()
 *     .importStrictMode(false)  // Disable strict validation for imports
 *     .useIpAddress(true)       // Use IP address for geolocation (events/people/groups only)
 *     .build();
 *
 * mixpanelApi.deliver(delivery, options);
 * }</pre>
 */
public class DeliveryOptions {

    private final boolean mImportStrictMode;
    private final boolean mUseIpAddress;

    private DeliveryOptions(Builder builder) {
        mImportStrictMode = builder.importStrictMode;
        mUseIpAddress = builder.useIpAddress;
    }

    /**
     * Returns whether strict mode is enabled for import messages.
     *
     * <p><strong>Note:</strong> This option only applies to import messages (historical events).
     * It has no effect on regular events, people, or groups messages.
     *
     * <p>When strict mode is enabled (default), the /import endpoint validates each event
     * and returns a 400 error if any event has issues. Correctly formed events are still
     * ingested, and problematic events are returned in the response with error messages.
     *
     * <p>When strict mode is disabled, validation is bypassed and all events are imported
     * regardless of their validity.
     *
     * @return true if strict mode is enabled for imports, false otherwise
     */
    public boolean isImportStrictMode() {
        return mImportStrictMode;
    }

    /**
     * Returns whether the IP address should be used for geolocation.
     *
     * <p><strong>Note:</strong> This option only applies to events, people, and groups messages.
     * It does NOT apply to import messages, which use Basic Auth and don't support the ip parameter.
     *
     * @return true if IP address should be used for geolocation, false otherwise
     */
    public boolean useIpAddress() {
        return mUseIpAddress;
    }

    /**
     * Builder for creating {@link DeliveryOptions} instances.
     */
    public static class Builder {
        private boolean importStrictMode = true;
        private boolean useIpAddress = false;

        /**
         * Sets whether to use strict mode for import messages.
         *
         * will validate the supplied events and return a 400 status code if any of the events fail validation with details of the error
         *
         * <p>Setting this value to true (default) will validate the supplied events and return 
         * a 400 status code if any of the events fail validation with details of the error.
         * Setting this value to false disables validation.
         *
         * @param importStrictMode true to enable strict validation (default), false to disable
         * @return this Builder instance for method chaining
         */
        public Builder importStrictMode(boolean importStrictMode) {
            this.importStrictMode = importStrictMode;
            return this;
        }

        /**
         * Sets whether to use the IP address for geolocation.
         *
         * <p><strong>Note:</strong> This option only applies to events, people, and groups messages.
         * It does NOT apply to import messages.
         *
         * <p>When enabled, Mixpanel will use the IP address of the request to set
         * geolocation properties on events and profiles.
         *
         * @param useIpAddress true to use IP address for geolocation, false otherwise (default)
         * @return this Builder instance for method chaining
         */
        public Builder useIpAddress(boolean useIpAddress) {
            this.useIpAddress = useIpAddress;
            return this;
        }

        /**
         * Builds and returns a new {@link DeliveryOptions} instance.
         *
         * @return a new DeliveryOptions with the configured settings
         */
        public DeliveryOptions build() {
            return new DeliveryOptions(this);
        }
    }
}
