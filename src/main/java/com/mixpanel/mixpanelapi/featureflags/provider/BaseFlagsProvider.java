package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.ServiceAccountCredential;
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
    protected final ServiceAccountCredential credentials;

    /**
     * Creates a new BaseFlagsProvider.
     *
     * @param projectToken the Mixpanel project token
     * @param config the flags configuration
     * @param sdkVersion the SDK version string
     * @param eventSender the EventSender implementation for tracking exposure events
     * @param credentials service account credentials for authentication (may be null)
     */
    protected BaseFlagsProvider(String projectToken, C config, String sdkVersion, EventSender eventSender, ServiceAccountCredential credentials) {
        this.projectToken = projectToken;
        this.config = config;
        this.sdkVersion = sdkVersion;
        this.eventSender = eventSender;
        this.credentials = credentials;
    }

    // #region HTTP Methods

    /**
     * Performs an HTTP GET request with Basic Auth.
     * <p>
     * When service account credentials are configured, uses username:secret for authentication.
     * Otherwise, uses token-based authentication (token as username, empty password).
     * </p>
     * <p>
     * This method is protected to allow test subclasses to override HTTP behavior.
     * </p>
     */
    protected String httpGet(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection conn = url.openConnection();
        conn.setConnectTimeout(config.getRequestTimeoutSeconds() * 1000);
        conn.setReadTimeout(config.getRequestTimeoutSeconds() * 1000);

        // Set Basic Auth header
        String auth;
        if (credentials != null) {
            // Service account auth: username:secret
            auth = credentials.getUsername() + ":" + credentials.getSecret();
        } else {
            // Token-based auth: token:empty
            auth = projectToken + ":";
        }
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
     * Returns true if an exposure event for this context can be attributed: by distinct_id,
     * or on a hierarchical groups project by $company_id as well (plus $user_id when present),
     * from which Mixpanel constructs the distinct id.
     */
    protected boolean canAttributeExposure(Map<String, Object> context) {
        if (!config.isHierarchicalGroups()) {
            return context.get("distinct_id") != null;
        }
        // A distinct id attributes on any project, including one Mixpanel built from a
        // company and user, which ingestion parses back into its parts.
        return hasId(context.get("$company_id")) || isPresent(context.get("distinct_id"));
    }

    private static boolean isPresent(Object value) {
        return value != null && !value.toString().isEmpty();
    }

    /**
     * Returns true if a $company_id or $user_id is a string other than an empty or
     * whitespace-only one. Any other value, such as a number, is treated as absent, matching
     * how flag evaluation reads these ids.
     */
    private static boolean hasId(Object value) {
        return value instanceof String && !isBlankOrEmpty((String) value);
    }

    /**
     * Common helper method for tracking exposure events.
     */
    protected void trackExposure(String distinctId, String flagKey, String variantKey,
                                 String evaluationMode, Consumer<JSONObject> addTimingProperties,
                                 UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        sendExposure(distinctId, new JSONObject(), flagKey, variantKey, evaluationMode,
            addTimingProperties, experimentId, isExperimentActive, isQaTester);
    }

    /**
     * Tracks an exposure event for a flag context. For hierarchical groups the event carries
     * $user_id and $company_id instead of distinct_id. Without hierarchical groups this calls
     * {@link #trackExposure(String, String, String, String, Consumer, UUID, Boolean, Boolean)}
     * with the context's distinct_id, so subclasses overriding that method still take effect.
     */
    protected void trackExposure(Map<String, Object> context, String flagKey, String variantKey,
                                 String evaluationMode, Consumer<JSONObject> addTimingProperties,
                                 UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        if (!config.isHierarchicalGroups()) {
            Object distinctIdObj = context.get("distinct_id");
            trackExposure(distinctIdObj != null ? distinctIdObj.toString() : null, flagKey, variantKey,
                evaluationMode, addTimingProperties, experimentId, isExperimentActive, isQaTester);
            return;
        }
        JSONObject identity = new JSONObject();
        String distinctId;
        try {
            // Carry whichever identity keys the caller sent; Mixpanel resolves the user
            // from the pair, or from a distinct id it built itself. A user id is only
            // attached alongside a company, since it names no one on its own.
            Object companyId = context.get("$company_id");
            if (hasId(companyId)) {
                identity.put("$company_id", (String) companyId);
                Object userId = context.get("$user_id");
                if (hasId(userId)) {
                    identity.put("$user_id", (String) userId);
                }
            }
            Object distinctIdObj = context.get("distinct_id");
            distinctId = isPresent(distinctIdObj) ? distinctIdObj.toString() : null;
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error tracking exposure event for flag: " + flagKey + ", variant: " + variantKey + " - " + e.getMessage(), e);
            return;
        }
        String blankPart = blankCombinedDistinctIdPart(distinctId);
        if (blankPart != null) {
            getLogger().log(Level.WARNING, "Exposure event for flag: " + flagKey + ", variant: " + variantKey
                + " dropped - distinct_id " + distinctId + " names a " + blankPart
                + " that is empty or only whitespace. Nothing sent");
            return;
        }
        String conflict = combinedDistinctIdConflict(distinctId, identity);
        if (conflict != null) {
            getLogger().log(Level.WARNING, "Exposure event for flag: " + flagKey + ", variant: " + variantKey
                + " dropped - " + conflict);
            return;
        }
        sendExposure(distinctId, identity, flagKey, variantKey, evaluationMode,
            addTimingProperties, experimentId, isExperimentActive, isQaTester);
    }

    /* package */ static final String COMBINED_COMPANY_PREFIX = "$company:";
    /* package */ static final String COMBINED_USER_SEPARATOR = "|$user:";

    /**
     * For a distinct id of the form {@code $company:{company}|$user:{user}}, split as
     * MixpanelIdentity splits it (the company is the text before the first {@code |$user:}),
     * returns "company" or "user" if that part is empty or only whitespace. Such an id becomes a
     * junk user, so its exposure is not sent.
     *
     * @return the blank part, or null if the id is not in that form or names both parts
     */
    private static String blankCombinedDistinctIdPart(String distinctId) {
        String[] parts = splitCombinedDistinctId(distinctId);
        if (parts == null) {
            return null;
        }
        if (isBlankOrEmpty(parts[0])) {
            return "company";
        }
        if (isBlankOrEmpty(parts[1])) {
            return "user";
        }
        return null;
    }

    /**
     * Splits a distinct id of the form {@code $company:{company}|$user:{user}} as
     * MixpanelIdentity splits it (the company is the text before the first {@code |$user:}).
     *
     * @return {company, user}, or null if the id is not in that form
     */
    private static String[] splitCombinedDistinctId(String distinctId) {
        if (distinctId == null || !distinctId.startsWith(COMBINED_COMPANY_PREFIX)) {
            return null;
        }
        int separator = distinctId.indexOf(COMBINED_USER_SEPARATOR, COMBINED_COMPANY_PREFIX.length());
        if (separator < 0) {
            return null;
        }
        return new String[] {
            distinctId.substring(COMBINED_COMPANY_PREFIX.length(), separator),
            distinctId.substring(separator + COMBINED_USER_SEPARATOR.length())
        };
    }

    /**
     * For a distinct id of the form {@code $company:{company}|$user:{user}}, describes how it names
     * a different company or user than the $company_id or $user_id the exposure carries, compared
     * as strings.
     * Ingestion would resolve such an event to the company alone, dropping the user, so it is not sent.
     *
     * @return the conflict, or null if the id is not in that form or agrees with the exposure's ids
     */
    private static String combinedDistinctIdConflict(String distinctId, JSONObject identity) {
        String[] parts = splitCombinedDistinctId(distinctId);
        if (parts == null) {
            return null;
        }
        String company = parts[0];
        String user = parts[1];
        String companyId = identity.optString("$company_id", null);
        if (companyId != null && !company.equals(companyId)) {
            return "distinct_id " + distinctId + " names company " + company + ", but $company_id is "
                + companyId + ". Nothing sent";
        }
        String userId = identity.optString("$user_id", null);
        if (userId != null && !user.equals(userId)) {
            return "distinct_id " + distinctId + " names user " + user + ", but $user_id is "
                + userId + ". Nothing sent";
        }
        return null;
    }

    private static boolean isBlankOrEmpty(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return true;
    }

    private void sendExposure(String distinctId, JSONObject properties, String flagKey, String variantKey,
                              String evaluationMode, Consumer<JSONObject> addTimingProperties,
                              UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        try {
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

    /**
     * Context key set by the Mixpanel OpenFeature provider (SDK-448). When present and
     * {@code true}, exposure events are tagged with {@code "$openfeature_provider": true}.
     */
    static final String OPENFEATURE_PROVIDER_KEY = "$openfeature_provider";

    /**
     * Adds {@code "$openfeature_provider": true} to the exposure properties when the
     * evaluation context was built by the Mixpanel OpenFeature provider.
     */
    static void addOpenFeatureProviderProperty(Map<String, Object> context, JSONObject properties) {
        if (context != null && Boolean.TRUE.equals(context.get(OPENFEATURE_PROVIDER_KEY))) {
            properties.put(OPENFEATURE_PROVIDER_KEY, true);
        }
    }

    // #endregion

    /**
     * Shuts down this provider and releases any resources.
     * Subclasses should override this to perform cleanup.
     */
    public void shutdown() {
        // No-op by default
    }
}
