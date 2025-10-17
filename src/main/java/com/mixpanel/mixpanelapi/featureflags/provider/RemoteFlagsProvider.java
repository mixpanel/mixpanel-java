package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remote feature flags evaluation provider.
 * <p>
 * This provider evaluates flags by making HTTP requests to the Mixpanel API.
 * Each evaluation results in a network call to fetch the variant from the server.
 * </p>
 * <p>
 * This class is thread-safe and implements AutoCloseable.
 * </p>
 */
public class RemoteFlagsProvider implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(RemoteFlagsProvider.class.getName());
    private static final int BUFFER_SIZE = 4096;

    private final String projectToken;
    private final RemoteFlagsConfig config;
    private final String sdkVersion;
    private final ExposureTracker exposureTracker;
    private final AtomicBoolean closed;

    /**
     * Interface for tracking exposure events.
     */
    public interface ExposureTracker {
        /**
         * Track an exposure event.
         *
         * @param distinctId the user's distinct ID
         * @param flagKey the flag key
         * @param variantKey the selected variant key
         * @param evaluationMode "remote" for remote evaluation
         * @param startTime ISO 8601 timestamp of evaluation start
         * @param completeTime ISO 8601 timestamp of evaluation completion
         */
        void trackExposure(String distinctId, String flagKey, String variantKey, String evaluationMode, String startTime, String completeTime);
    }

    /**
     * Creates a new RemoteFlagsProvider.
     *
     * @param projectToken the Mixpanel project token
     * @param config the remote flags configuration
     * @param sdkVersion the SDK version string
     * @param exposureTracker callback for tracking exposure events
     */
    public RemoteFlagsProvider(String projectToken, RemoteFlagsConfig config, String sdkVersion, ExposureTracker exposureTracker) {
        this.projectToken = projectToken;
        this.config = config;
        this.sdkVersion = sdkVersion;
        this.exposureTracker = exposureTracker;
        this.closed = new AtomicBoolean(false);
    }

    // #region Evaluation

    /**
     * Evaluates a flag remotely and returns the selected variant.
     *
     * @param flagKey the flag key to evaluate
     * @param fallback the fallback variant to return if evaluation fails
     * @param context the evaluation context
     * @param <T> the type of the variant value
     * @return the selected variant or fallback
     */
    public <T> SelectedVariant<T> getVariant(String flagKey, SelectedVariant<T> fallback, Map<String, Object> context) {
        if (closed.get()) {
            logger.warning("Cannot evaluate flag: provider is closed");
            return fallback;
        }

        String startTime = getCurrentIso8601Timestamp();
        long startMillis = System.currentTimeMillis();

        try {
            String endpoint = buildFlagsUrl(flagKey, context);

            String response = httpGet(endpoint);

            JSONObject root = new JSONObject(response);
            JSONObject flags = root.optJSONObject("flags");

            if (flags == null || !flags.has(flagKey)) {
                logger.warning("Flag not found in response: " + flagKey);
                return fallback;
            }

            JSONObject flagData = flags.getJSONObject(flagKey);
            String variantKey = flagData.optString("variant_key", null);
            Object variantValue = flagData.opt("variant_value");

            if (variantKey == null) {
                return fallback;
            }

            // Track exposure
            String completeTime = getCurrentIso8601Timestamp();
            trackExposureIfPossible(context, flagKey, variantKey, startTime, completeTime);

            @SuppressWarnings("unchecked")
            SelectedVariant<T> result = new SelectedVariant<>(variantKey, (T) variantValue);
            return result;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error evaluating flag remotely: " + flagKey, e);
            return fallback;
        }
    }

    // #endregion
    // #region HTTP Helpers

    /**
     * Builds the URL for remote flag evaluation.
     */
    private String buildFlagsUrl(String flagKey, Map<String, Object> context) throws UnsupportedEncodingException {
        StringBuilder url = new StringBuilder();
        url.append("https://").append(config.getApiHost()).append("/flags");
        url.append("?mp_lib=").append(URLEncoder.encode("jdk", "UTF-8"));
        url.append("&lib_version=").append(URLEncoder.encode(sdkVersion, "UTF-8"));
        url.append("&token=").append(URLEncoder.encode(projectToken, "UTF-8"));
        url.append("&flag_key=").append(URLEncoder.encode(flagKey, "UTF-8"));

        JSONObject contextJson = new JSONObject(context);
        String contextString = contextJson.toString();
        url.append("&context=").append(URLEncoder.encode(contextString, "UTF-8"));

        return url.toString();
    }

    /**
     * Performs an HTTP GET request with Basic Auth.
     */
    private String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(config.getRequestTimeoutSeconds() * 1000);
        conn.setReadTimeout(config.getRequestTimeoutSeconds() * 1000);

        // Set Basic Auth header (token as username, empty password)
        String auth = projectToken + ":";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

        // Set custom headers
        conn.setRequestProperty("X-Scheme", "https");
        conn.setRequestProperty("X-Forwarded-Proto", "https");
        conn.setRequestProperty("Content-Type", "application/json");

        InputStream responseStream = null;
        try {
            responseStream = conn.getInputStream();
            return readStream(responseStream);
        } finally {
            if (responseStream != null) {
                try {
                    responseStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Reads an input stream to a string.
     */
    private String readStream(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);

        char[] buffer = new char[BUFFER_SIZE];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            out.append(buffer, 0, count);
        }

        return out.toString();
    }

    /**
     * Gets current timestamp in ISO 8601 format.
     */
    private String getCurrentIso8601Timestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    // #endregion

    /**
     * Tracks exposure event if possible.
     */
    private void trackExposureIfPossible(Map<String, Object> context, String flagKey, String variantKey, String startTime, String completeTime) {
        if (exposureTracker == null) {
            return;
        }

        Object distinctIdObj = context.get("distinct_id");
        if (distinctIdObj == null) {
            return;
        }

        try {
            exposureTracker.trackExposure(distinctIdObj.toString(), flagKey, variantKey, "remote", startTime, completeTime);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to track exposure event", e);
        }
    }

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
        SelectedVariant<T> result = getVariant(flagKey, fallback, context);
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
        SelectedVariant<Object> result = getVariant(flagKey, new SelectedVariant<>(false), context);
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

    @Override
    public void close() {
        closed.set(true);
    }

    /**
     * Java 8 compatible Base64 utility.
     * Uses java.util.Base64 which is available in Java 8+.
     */
    private static class Base64 {
        static java.util.Base64.Encoder getEncoder() {
            return java.util.Base64.getEncoder();
        }
    }
}
