package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Remote feature flags evaluation provider.
 * <p>
 * This provider evaluates flags by making HTTP requests to the Mixpanel API.
 * Each evaluation results in a network call to fetch the variant from the server.
 * </p>
 * <p>
 * This class is thread-safe.
 * </p>
 */
public class RemoteFlagsProvider extends BaseFlagsProvider<RemoteFlagsConfig> {
    private static final Logger logger = Logger.getLogger(RemoteFlagsProvider.class.getName());

    /**
     * Creates a new RemoteFlagsProvider.
     *
     * @param config the remote flags configuration
     * @param sdkVersion the SDK version string
     * @param eventSender the EventSender implementation for tracking exposure events
     */
    public RemoteFlagsProvider(RemoteFlagsConfig config, String sdkVersion, EventSender eventSender) {
        super(config.getProjectToken(), config, sdkVersion, eventSender);
    }

    // #region Evaluation

    /**
     * Evaluates a flag remotely and returns the selected variant.
     *
     * @param flagKey the flag key to evaluate
     * @param fallback the fallback variant to return if evaluation fails
     * @param context the evaluation context
     * @param reportExposure whether to track an exposure event for this flag evaluation
     * @param <T> the type of the variant value
     * @return the selected variant or fallback
     */
    public <T> SelectedVariant<T> getVariant(String flagKey, SelectedVariant<T> fallback, Map<String, Object> context, boolean reportExposure) {
        String startTime = getCurrentIso8601Timestamp();

        try {
            String endpoint = buildFlagsUrl(flagKey, context);

            String response = httpGet(endpoint);

            JSONObject root = new JSONObject(response);
            JSONObject flags = root.optJSONObject("flags");

            if (flags == null || !flags.has(flagKey)) {
                logger.log(Level.WARNING, "Flag not found in response: " + flagKey);
                return fallback;
            }

            JSONObject flagData = flags.getJSONObject(flagKey);
            String variantKey = flagData.optString("variant_key", null);
            Object variantValue = flagData.opt("variant_value");

            if (variantKey == null) {
                return fallback;
            }

            // Parse experiment metadata
            UUID experimentId = null;
            String experimentIdString = flagData.optString("experiment_id", null);
            if (experimentIdString != null && !experimentIdString.isEmpty()) {
                try {
                    experimentId = UUID.fromString(experimentIdString);
                } catch (IllegalArgumentException e) {
                    logger.log(Level.WARNING, "Invalid UUID for experiment_id: " + experimentIdString);
                }
            }

            Boolean isExperimentActive = null;
            if (flagData.has("is_experiment_active")) {
                isExperimentActive = flagData.optBoolean("is_experiment_active", false);
            }

            Boolean isQaTester = null;
            if (flagData.has("is_qa_tester")) {
                isQaTester = flagData.optBoolean("is_qa_tester", false);
            }

            // Track exposure
            String completeTime = getCurrentIso8601Timestamp();
            if (reportExposure) {
                trackRemoteExposure(context, flagKey, variantKey, startTime, completeTime, experimentId, isExperimentActive, isQaTester);
            }

            @SuppressWarnings("unchecked")
            SelectedVariant<T> result = new SelectedVariant<>(variantKey, (T) variantValue, experimentId, isExperimentActive, isQaTester);
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
     * Gets current timestamp in ISO 8601 format.
     */
    private String getCurrentIso8601Timestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    // #endregion

    /**
     * Tracks an exposure event for remote evaluation.
     */
    private void trackRemoteExposure(Map<String, Object> context, String flagKey, String variantKey, String startTime, String completeTime, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        if (eventSender == null) {
            return;
        }

        Object distinctIdObj = context.get("distinct_id");
        if (distinctIdObj == null) {
            return;
        }

        trackExposure(distinctIdObj.toString(), flagKey, variantKey, "remote", properties -> {
            properties.put("Variant fetch start time", startTime);
            properties.put("Variant fetch complete time", completeTime);
        }, experimentId, isExperimentActive, isQaTester);
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }
}
