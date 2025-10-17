package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.*;
import com.mixpanel.mixpanelapi.featureflags.util.HashUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Local feature flags evaluation provider.
 * <p>
 * This provider fetches flag definitions from the Mixpanel API and evaluates
 * variants locally using the FNV-1a hash algorithm. Supports optional background
 * polling for automatic definition refresh.
 * </p>
 * <p>
 * This class is thread-safe and implements AutoCloseable for resource cleanup.
 * </p>
 */
public class LocalFlagsProvider implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(LocalFlagsProvider.class.getName());
    private static final int BUFFER_SIZE = 4096;

    private final String projectToken;
    private final LocalFlagsConfig config;
    private final String sdkVersion;
    private final ExposureTracker exposureTracker;

    private final AtomicReference<Map<String, ExperimentationFlag>> flagDefinitions;
    private final AtomicBoolean ready;
    private final AtomicBoolean closed;

    private ScheduledExecutorService pollingExecutor;

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
         * @param evaluationMode "local" for local evaluation
         * @param latencyMs evaluation time in milliseconds
         */
        void trackExposure(String distinctId, String flagKey, String variantKey, String evaluationMode, long latencyMs);
    }

    /**
     * Creates a new LocalFlagsProvider.
     *
     * @param projectToken the Mixpanel project token
     * @param config the local flags configuration
     * @param sdkVersion the SDK version string
     * @param exposureTracker callback for tracking exposure events
     */
    public LocalFlagsProvider(String projectToken, LocalFlagsConfig config, String sdkVersion, ExposureTracker exposureTracker) {
        this.projectToken = projectToken;
        this.config = config;
        this.sdkVersion = sdkVersion;
        this.exposureTracker = exposureTracker;

        this.flagDefinitions = new AtomicReference<>(new HashMap<>());
        this.ready = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);
    }

    // #region Polling

    /**
     * Starts polling for flag definitions.
     * <p>
     * Performs an initial fetch, then starts background polling if enabled in configuration.
     * </p>
     */
    public void startPollingForDefinitions() {
        if (closed.get()) {
            logger.warning("Cannot start polling: provider is closed");
            return;
        }

        // Initial fetch
        fetchDefinitions();

        // Start background polling if enabled
        if (config.isEnablePolling()) {
            pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mixpanel-flags-poller");
                t.setDaemon(true);
                return t;
            });

            pollingExecutor.scheduleAtFixedRate(
                this::fetchDefinitions,
                config.getPollingIntervalSeconds(),
                config.getPollingIntervalSeconds(),
                TimeUnit.SECONDS
            );

            logger.info("Started polling for flag definitions every " + config.getPollingIntervalSeconds() + " seconds");
        }
    }

    /**
     * Stops polling for flag definitions and releases resources.
     */
    public void stopPollingForDefinitions() {
        if (pollingExecutor != null) {
            pollingExecutor.shutdown();
            try {
                if (!pollingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    pollingExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                pollingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            pollingExecutor = null;
        }
    }

    // #endregion

    // #region Fetch Definitions

    /**
     * @return true if flag definitions have been successfully fetched at least once
     */
    public boolean areFlagsReady() {
        return ready.get();
    }

    /**
     * Fetches flag definitions from the Mixpanel API.
     */
    private void fetchDefinitions() {
        try {
            String endpoint = buildDefinitionsUrl();
            String response = httpGet(endpoint);

            Map<String, ExperimentationFlag> newDefinitions = parseDefinitions(response);
            flagDefinitions.set(newDefinitions);
            ready.set(true);

            logger.fine("Successfully fetched " + newDefinitions.size() + " flag definitions");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to fetch flag definitions", e);
        }
    }

    /**
     * Builds the URL for fetching flag definitions.
     */
    private String buildDefinitionsUrl() throws UnsupportedEncodingException {
        StringBuilder url = new StringBuilder();
        url.append("https://").append(config.getApiHost()).append("/flags/definitions");
        url.append("?mp_lib=").append(URLEncoder.encode("java", "UTF-8"));
        url.append("&lib_version=").append(URLEncoder.encode(sdkVersion, "UTF-8"));
        url.append("&token=").append(URLEncoder.encode(projectToken, "UTF-8"));
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
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
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
                    logger.warning("Failed to close Mixpanel feature flags response stream", e);
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

    // #endregion

    // #region JSON Parsing

    /**
     * Parses flag definitions from JSON response.
     */
    private Map<String, ExperimentationFlag> parseDefinitions(String jsonResponse) {
        Map<String, ExperimentationFlag> definitions = new HashMap<>();

        try {
            JSONObject root = new JSONObject(jsonResponse);
            JSONArray flags = root.optJSONArray("flags");

            if (flags == null) {
                return definitions;
            }

            for (int i = 0; i < flags.length(); i++) {
                JSONObject flagJson = flags.getJSONObject(i);
                ExperimentationFlag flag = parseFlag(flagJson);
                definitions.put(flag.getKey(), flag);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse flag definitions", e);
        }

        return definitions;
    }

    /**
     * Parses a single flag from JSON.
     */
    private ExperimentationFlag parseFlag(JSONObject json) {
        String id = json.optString("id", "");
        String name = json.optString("name", "");
        String key = json.optString("key", "");
        String status = json.optString("status", "");
        int projectId = json.optInt("project_id", 0);
        String context = json.optString("context", "distinct_id");

        RuleSet ruleset = parseRuleSet(json.optJSONObject("ruleset"));

        return new ExperimentationFlag(id, name, key, status, projectId, ruleset, context);
    }

    /**
     * Parses a ruleset from JSON.
     */
    private RuleSet parseRuleSet(JSONObject json) {
        if (json == null) {
            return new RuleSet(Collections.emptyList(), Collections.emptyList());
        }

        // Parse variants
        List<Variant> variants = new ArrayList<>();
        JSONArray variantsJson = json.optJSONArray("variants");
        if (variantsJson != null) {
            for (int i = 0; i < variantsJson.length(); i++) {
                variants.add(parseVariant(variantsJson.getJSONObject(i)));
            }
        }

        // Sort variants by key for consistent ordering
        variants.sort(Comparator.comparing(Variant::getKey));

        // Parse rollouts
        List<Rollout> rollouts = new ArrayList<>();
        JSONArray rolloutsJson = json.optJSONArray("rollout");
        if (rolloutsJson != null) {
            for (int i = 0; i < rolloutsJson.length(); i++) {
                rollouts.add(parseRollout(rolloutsJson.getJSONObject(i)));
            }
        }

        // Parse test user overrides
        Map<String, String> testOverrides = null;
        JSONObject testJson = json.optJSONObject("test");
        if (testJson != null) {
            testOverrides = new HashMap<>();
            for (String distinctId : testJson.keySet()) {
                testOverrides.put(distinctId, testJson.getString(distinctId));
            }
        }

        return new RuleSet(variants, rollouts, testOverrides);
    }

    /**
     * Parses a variant from JSON.
     */
    private Variant parseVariant(JSONObject json) {
        String key = json.optString("key", "");
        Object value = json.opt("value");
        boolean isControl = json.optBoolean("is_control", false);
        float split = (float) json.optDouble("split", 0.0);

        return new Variant(key, value, isControl, split);
    }

    /**
     * Parses a rollout from JSON.
     */
    private Rollout parseRollout(JSONObject json) {
        float rolloutPercentage = (float) json.optDouble("rollout_percentage", 0.0);
        String variantOverride = json.optString("variant_override", null);
        if ("".equals(variantOverride)) {
            variantOverride = null;
        }

        Map<String, Object> runtimeEval = null;
        JSONObject runtimeEvalJson = json.optJSONObject("runtime_evaluation_definition");
        if (runtimeEvalJson != null) {
            runtimeEval = new HashMap<>();
            for (String key : runtimeEvalJson.keySet()) {
                runtimeEval.put(key, runtimeEvalJson.get(key));
            }
        }

        return new Rollout(rolloutPercentage, runtimeEval, variantOverride);
    }

    // #endregion

    // #region Evaluation

    /**
     * Evaluates a flag and returns the selected variant.
     *
     * @param flagKey the flag key to evaluate
     * @param fallback the fallback variant to return if evaluation fails
     * @param context the evaluation context (must contain the property specified in flag's context field)
     * @param <T> the type of the variant value
     * @return the selected variant or fallback
     */
    public <T> SelectedVariant<T> getVariant(String flagKey, SelectedVariant<T> fallback, Map<String, Object> context) {
        long startTime = System.currentTimeMillis();

        try {
            // Get flag definition
            Map<String, ExperimentationFlag> definitions = flagDefinitions.get();
            ExperimentationFlag flag = definitions.get(flagKey);

            if (flag == null) {
                logger.warning("Flag not found: " + flagKey);
                return fallback;
            }

            // Extract context value
            String contextProperty = flag.getContext();
            Object contextValueObj = context.get(contextProperty);
            if (contextValueObj == null) {
                logger.warning("Variant assignment key property '" + contextProperty + "' not found for flag: " + flagKey);
                return fallback;
            }
            String contextValue = contextValueObj.toString();

            // Check test user overrides
            RuleSet ruleset = flag.getRuleset();
            if (ruleset.hasTestUserOverrides()) {
                String distinctId = context.get("distinct_id") != null ? context.get("distinct_id").toString() : null;
                if (distinctId != null) {
                    String testVariantKey = ruleset.getTestUserOverrides().get(distinctId);
                    if (testVariantKey != null) {
                        Variant variant = findVariantByKey(ruleset.getVariants(), testVariantKey);
                        if (variant != null) {
                            @SuppressWarnings("unchecked")
                            SelectedVariant<T> result = new SelectedVariant<>(variant.getKey(), (T) variant.getValue());
                            trackExposureIfPossible(context, flagKey, variant.getKey(), System.currentTimeMillis() - startTime);
                            return result;
                        }
                    }
                }
            }

            // Evaluate rollouts
            float rolloutHash = HashUtils.normalizedHash(contextValue + flagKey, "rollout");

            for (Rollout rollout : ruleset.getRollouts()) {
                if (rolloutHash >= rollout.getRolloutPercentage()) {
                    continue;
                }

                // Check runtime evaluation, continue if this rollout has runtime conditions and it doesn't match
                if (rollout.hasRuntimeEvaluation()) {
                    if (!matchesRuntimeConditions(rollout, context)) {
                        continue;
                    }
                }

                // This rollout is selected - determine variant
                Variant selectedVariant = null;

                if (rollout.hasVariantOverride()) {
                    selectedVariant = findVariantByKey(ruleset.getVariants(), rollout.getVariantOverride());
                } else {
                    // Use variant hash to select from split
                    float variantHash = HashUtils.normalizedHash(contextValue + flagKey, "variant");
                    selectedVariant = selectVariantBySplit(ruleset.getVariants(), variantHash);
                }

                if (selectedVariant != null) {
                    @SuppressWarnings("unchecked")
                    SelectedVariant<T> result = new SelectedVariant<>(selectedVariant.getKey(), (T) selectedVariant.getValue());
                    trackExposureIfPossible(context, flagKey, selectedVariant.getKey(), System.currentTimeMillis() - startTime);
                    return result;
                }

                break; // Rollout selected but no variant found
            }

            // No rollout matched
            return fallback;

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error evaluating flag: " + flagKey, e);
            return fallback;
        }
    }

    /**
     * Evaluates runtime conditions for a rollout.
     * 
     * @return true if all runtime conditions match, false otherwise (or if custom_properties is missing)
     */
    private boolean matchesRuntimeConditions(Rollout rollout, Map<String, Object> context) {
        Map<String, Object> customProperties = getCustomProperties(context);
        if (customProperties == null) {
            return false;
        }

        Map<String, Object> runtimeEval = rollout.getRuntimeEvaluationDefinition();
        for (Map.Entry<String, Object> entry : runtimeEval.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = customProperties.get(key);

            // Case-insensitive comparison for strings
            if (!valuesEqual(expectedValue, actualValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts custom_properties from context.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getCustomProperties(Map<String, Object> context) {
        Object customPropsObj = context.get("custom_properties");
        if (customPropsObj instanceof Map) {
            return (Map<String, Object>) customPropsObj;
        }
        return null;
    }

    /**
     * Compares two values with case-insensitive string comparison.
     */
    private boolean valuesEqual(Object expected, Object actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }

        // Case-insensitive comparison for strings
        if (expected instanceof String && actual instanceof String) {
            return ((String) expected).equalsIgnoreCase((String) actual);
        }

        return expected.equals(actual);
    }

    /**
     * Finds a variant by key.
     */
    private Variant findVariantByKey(List<Variant> variants, String key) {
        for (Variant variant : variants) {
            if (variant.getKey().equals(key)) {
                return variant;
            }
        }
        return null;
    }

    /**
     * Selects a variant based on hash and split percentages.
     */
    private Variant selectVariantBySplit(List<Variant> variants, float hash) {
        float cumulative = 0.0f;

        for (Variant variant : variants) {
            cumulative += variant.getSplit();
            if (hash < cumulative) {
                return variant;
            }
        }

        // If no variant selected (due to rounding), return last variant
        return variants.isEmpty() ? null : variants.get(variants.size() - 1);
    }

    /**
     * Tracks exposure event if possible.
     */
    private void trackExposureIfPossible(Map<String, Object> context, String flagKey, String variantKey, long latencyMs) {
        if (exposureTracker == null) {
            return;
        }

        Object distinctIdObj = context.get("distinct_id");
        if (distinctIdObj == null) {
            return;
        }

        try {
            exposureTracker.trackExposure(distinctIdObj.toString(), flagKey, variantKey, "local", latencyMs);
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

    // #endregion

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            stopPollingForDefinitions();
        }
    }
}
