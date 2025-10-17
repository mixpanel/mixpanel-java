package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.config.BaseFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;
import com.mixpanel.mixpanelapi.featureflags.util.TraceparentUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

    /**
     * Creates a new BaseFlagsProvider.
     *
     * @param projectToken the Mixpanel project token
     * @param config the flags configuration
     * @param sdkVersion the SDK version string
     */
    protected BaseFlagsProvider(String projectToken, C config, String sdkVersion) {
        this.projectToken = projectToken;
        this.config = config;
        this.sdkVersion = sdkVersion;
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
     * Treats the variant value as truthy (non-null, non-false, non-zero).
     * </p>
     *
     * @param flagKey the flag key to evaluate
     * @param context the evaluation context
     * @return true if the variant value is truthy, false otherwise
     */
    public boolean isEnabled(String flagKey, Map<String, Object> context) {
        SelectedVariant<Object> result = getVariant(flagKey, new SelectedVariant<>(false), context, true);
        Object value = result.getVariantValue();

        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        if (value instanceof String) {
            String str = (String) value;
            return !str.isEmpty() && !str.equalsIgnoreCase("false");
        }

        return true;
    }

    // #endregion
}
