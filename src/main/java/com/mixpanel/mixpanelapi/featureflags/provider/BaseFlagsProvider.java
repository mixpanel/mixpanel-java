package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.config.BaseFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;
import com.mixpanel.mixpanelapi.featureflags.util.TraceparentUtil;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for feature flags providers.
 * <p>
 * Contains shared HTTP functionality and common evaluation helpers.
 * Subclasses implement specific evaluation strategies (local or remote).
 * </p>
 *
 * @param <C> the config type extending BaseFlagsConfig
 */
public abstract class BaseFlagsProvider<C extends BaseFlagsConfig> {
    protected static final int BUFFER_SIZE = 4096;

    protected final String projectToken;
    protected final C config;
    protected final String sdkVersion;
    protected final EventSender eventSender;

    /**
     * Creates a new BaseFlagsProvider.
     *
     * @param projectToken the Mixpanel project token
     * @param config the flags configuration
     * @param sdkVersion the SDK version string
     * @param eventSender the EventSender implementation for tracking exposure events
     */
    protected BaseFlagsProvider(String projectToken, C config, String sdkVersion, EventSender eventSender) {
        this.projectToken = projectToken;
        this.config = config;
        this.sdkVersion = sdkVersion;
        this.eventSender = eventSender;
    }

    // #region HTTP Methods

    /**
     * Performs an HTTP GET request with Basic Auth.
     * <p>
     * This method is protected to allow test subclasses to override HTTP behavior.
     * </p>
     */
    protected String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(config.getRequestTimeoutSeconds() * 1000);
        conn.setReadTimeout(config.getRequestTimeoutSeconds() * 1000);

        // Set Basic Auth header (token as username, empty password)
        String auth = projectToken + ":";
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

        // Set custom headers
        conn.setRequestProperty("X-Scheme", "https");
        conn.setRequestProperty("X-Forwarded-Proto", "https");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("traceparent", TraceparentUtil.generateTraceparent());

        InputStream responseStream = null;
        try {
            responseStream = conn.getInputStream();
            return readStream(responseStream);
        } finally {
            if (responseStream != null) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    getLogger().log(Level.WARNING, "Failed to close response stream", e);
                }
            }
        }
    }

    /**
     * Reads an input stream to a string.
     */
    protected String readStream(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);

        char[] buffer = new char[BUFFER_SIZE];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            out.append(buffer, 0, count);
        }

        return out.toString();
    }

    // #endregion

    // #region Abstract Methods

    /**
     * Evaluates a flag and returns the selected variant.
     * <p>
     * Subclasses must implement this method to provide local or remote evaluation logic.
     * </p>
     *
     * @param flagKey the flag key to evaluate
     * @param fallback the fallback variant to return if evaluation fails
     * @param context the evaluation context
     * @param reportExposure whether to track an exposure event for this flag evaluation
     * @param <T> the type of the variant value
     * @return the selected variant or fallback
     */
    public abstract <T> SelectedVariant<T> getVariant(String flagKey, SelectedVariant<T> fallback, Map<String, Object> context, boolean reportExposure);

    /**
     * Evaluates a flag and returns the selected variant.
     * <p>
     * This is a convenience method that defaults reportExposure to true.
     * </p>
     *
     * @param flagKey the flag key to evaluate
     * @param fallback the fallback variant to return if evaluation fails
     * @param context the evaluation context
     * @param <T> the type of the variant value
     * @return the selected variant or fallback
     */
    public <T> SelectedVariant<T> getVariant(String flagKey, SelectedVariant<T> fallback, Map<String, Object> context) {
        return getVariant(flagKey, fallback, context, true);
    }

    /**
     * Gets the logger for this provider.
     * Subclasses should override this to return their class-specific logger.
     *
     * @return the logger instance
     */
    protected abstract Logger getLogger();

    // #endregion

    // #region Variant Value Methods

    /**
     * Evaluates a flag and returns the variant value.
     *
     * @param flagKey the flag key to evaluate
     * @param fallbackValue the fallback value to return if evaluation fails
     * @param context the evaluation context
     * @param <T> the type of the variant value
     * @return the selected variant value or fallback
     */
    public <T> T getVariantValue(String flagKey, T fallbackValue, Map<String, Object> context) {
        SelectedVariant<T> fallback = new SelectedVariant<>(fallbackValue);
        SelectedVariant<T> result = getVariant(flagKey, fallback, context, true);
        return result.getVariantValue();
    }

    /**
     * Evaluates a flag and returns whether it is enabled.
     * <p>
     * Returns true only if the variant value is exactly Boolean true.
     * Returns false for all other cases (false, null, numbers, strings, etc.).
     * </p>
     *
     * @param flagKey the flag key to evaluate
     * @param context the evaluation context
     * @return true if the variant value is exactly Boolean true, false otherwise
     */
    public boolean isEnabled(String flagKey, Map<String, Object> context) {
        SelectedVariant<Object> result = getVariant(flagKey, new SelectedVariant<>(false), context, true);
        Object value = result.getVariantValue();

        return value instanceof Boolean && (Boolean) value;
    }

    // #endregion

    // #region Exposure Tracking

    /**
     * Common helper method for tracking exposure events.
     */
    protected void trackExposure(String distinctId, String flagKey, String variantKey,
                                 String evaluationMode, Consumer<JSONObject> addTimingProperties,
                                 UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        try {
            JSONObject properties = new JSONObject();
            properties.put("Experiment name", flagKey);
            properties.put("Variant name", variantKey);
            properties.put("$experiment_type", "feature_flag");
            properties.put("Flag evaluation mode", evaluationMode);

            // Add experiment metadata
            if (experimentId != null) {
                properties.put("$experiment_id", experimentId.toString());
            }
            if (isExperimentActive != null) {
                properties.put("$is_experiment_active", isExperimentActive);
            }
            if (isQaTester != null) {
                properties.put("$is_qa_tester", isQaTester);
            }

            // Add timing-specific properties
            addTimingProperties.accept(properties);

            // Send via EventSender interface
            eventSender.sendEvent(distinctId, "$experiment_started", properties);

            getLogger().log(Level.FINE, "Tracked exposure event for flag: " + flagKey + ", variant: " + variantKey);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error tracking exposure event for flag: " + flagKey + ", variant: " + variantKey + " - " + e.getMessage(), e);
        }
    }

    // #endregion
}
