package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.*;
import com.mixpanel.mixpanelapi.featureflags.util.HashUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
public class LocalFlagsProvider extends BaseFlagsProvider<LocalFlagsConfig> implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(LocalFlagsProvider.class.getName());

    private final AtomicReference<Map<String, ExperimentationFlag>> flagDefinitions;
    private final AtomicBoolean ready;
    private final AtomicBoolean closed;

    private ScheduledExecutorService pollingExecutor;

    /**
     * Creates a new LocalFlagsProvider.
     *
     * @param config the local flags configuration
     * @param sdkVersion the SDK version string
     * @param eventSender the EventSender implementation for tracking exposure events
     */
    public LocalFlagsProvider(LocalFlagsConfig config, String sdkVersion, EventSender eventSender) {
        super(config.getProjectToken(), config, sdkVersion, eventSender);

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
            logger.log(Level.WARNING, "Cannot start polling: provider is closed");
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

            logger.log(Level.INFO, "Started polling for flag definitions every " + config.getPollingIntervalSeconds() + " seconds");
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

            logger.log(Level.FINE, "Successfully fetched " + newDefinitions.size() + " flag definitions");
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

        // Parse experiment metadata
        UUID experimentId = null;
        String experimentIdString = json.optString("experiment_id", null);
        if (experimentIdString != null && !experimentIdString.isEmpty()) {
            try {
                experimentId = UUID.fromString(experimentIdString);
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Invalid UUID for experiment_id: " + experimentIdString);
            }
        }

        Boolean isExperimentActive = null;
        if (json.has("is_experiment_active")) {
            isExperimentActive = json.optBoolean("is_experiment_active", false);
        }

        RuleSet ruleset = parseRuleSet(json.optJSONObject("ruleset"));

        return new ExperimentationFlag(id, name, key, status, projectId, ruleset, context, experimentId, isExperimentActive);
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
            JSONObject usersJson = testJson.optJSONObject("users");
            if (usersJson != null) {
                testOverrides = new HashMap<>();
                for (String distinctId : usersJson.keySet()) {
                    testOverrides.put(distinctId, usersJson.getString(distinctId));
                }
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
        VariantOverride variantOverride = null;

        if (json.has("variant_override") && !json.isNull("variant_override")) {
            JSONObject variantObj = json.optJSONObject("variant_override");
            if (variantObj != null) {
                String key = variantObj.optString("key", "");
                if (!key.isEmpty()) {
                    variantOverride = new VariantOverride(key);
                }
            }
        }

        Map<String, Object> runtimeEval = null;
        JSONObject runtimeEvalJson = json.optJSONObject("runtime_evaluation_definition");
        if (runtimeEvalJson != null) {
            runtimeEval = new HashMap<>();
            for (String key : runtimeEvalJson.keySet()) {
                runtimeEval.put(key, runtimeEvalJson.get(key));
            }
        }

        Map<String, Float> variantSplits = null;
        JSONObject variantSplitsJson = json.optJSONObject("variant_splits");
        if (variantSplitsJson != null) {
            variantSplits = new HashMap<>();
            for (String key : variantSplitsJson.keySet()) {
                variantSplits.put(key, (float) variantSplitsJson.optDouble(key, 0.0));
            }
        }

        return new Rollout(rolloutPercentage, runtimeEval, variantOverride, variantSplits);
    }

    // #endregion

    // #region Evaluation

    /**
     * Evaluates a flag and returns the selected variant.
     *
     * @param flagKey the flag key to evaluate
     * @param fallback the fallback variant to return if evaluation fails
     * @param context the evaluation context (must contain the property specified in flag's context field)
     * @param reportExposure whether to track an exposure event for this flag evaluation
     * @param <T> the type of the variant value
     * @return the selected variant or fallback
     */
    public <T> SelectedVariant<T> getVariant(String flagKey, SelectedVariant<T> fallback, Map<String, Object> context, boolean reportExposure) {
        long startTime = System.currentTimeMillis();

        try {
            // Get flag definition
            Map<String, ExperimentationFlag> definitions = flagDefinitions.get();
            ExperimentationFlag flag = definitions.get(flagKey);

            if (flag == null) {
                logger.log(Level.WARNING, "Flag not found: " + flagKey);
                return fallback;
            }

            // Extract context value
            String contextProperty = flag.getContext();
            Object contextValueObj = context.get(contextProperty);
            if (contextValueObj == null) {
                logger.log(Level.WARNING, "Variant assignment key property '" + contextProperty + "' not found for flag: " + flagKey);
                return fallback;
            }
            String contextValue = contextValueObj.toString();

            // Check test user overrides
            RuleSet ruleset = flag.getRuleset();
            Boolean isQaTester = null;
            if (ruleset.hasTestUserOverrides()) {
                String distinctId = context.get("distinct_id") != null ? context.get("distinct_id").toString() : null;
                if (distinctId != null) {
                    String testVariantKey = ruleset.getTestUserOverrides().get(distinctId);
                    if (testVariantKey != null) {
                        Variant variant = findVariantByKey(ruleset.getVariants(), testVariantKey);
                        if (variant != null) {
                            isQaTester = true;
                            @SuppressWarnings("unchecked")
                            SelectedVariant<T> result = new SelectedVariant<>(
                                variant.getKey(),
                                (T) variant.getValue(),
                                flag.getExperimentId(),
                                flag.getIsExperimentActive(),
                                isQaTester
                            );
                            if (reportExposure) {
                                trackLocalExposure(context, flagKey, variant.getKey(), System.currentTimeMillis() - startTime, flag.getExperimentId(), flag.getIsExperimentActive(), isQaTester);
                            }
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
                    selectedVariant = findVariantByKey(ruleset.getVariants(), rollout.getVariantOverride().getKey());
                } else {
                    // Use variant hash to select from split
                    float variantHash = HashUtils.normalizedHash(contextValue + flagKey, "variant");
                    selectedVariant = selectVariantBySplit(ruleset.getVariants(), variantHash);
                }

                if (selectedVariant != null) {
                    if (isQaTester == null) {
                        isQaTester = false;
                    }
                    @SuppressWarnings("unchecked")
                    SelectedVariant<T> result = new SelectedVariant<>(
                        selectedVariant.getKey(),
                        (T) selectedVariant.getValue(),
                        flag.getExperimentId(),
                        flag.getIsExperimentActive(),
                        isQaTester
                    );
                    if (reportExposure) {
                        trackLocalExposure(context, flagKey, selectedVariant.getKey(), System.currentTimeMillis() - startTime, flag.getExperimentId(), flag.getIsExperimentActive(), isQaTester);
                    }
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
     * Evaluates all flags and returns their selected variants.
     * <p>
     * This method evaluates all flag definitions for the given context and returns
     * a list of successfully selected variants (excludes fallbacks).
     * </p>
     *
     * @param context the evaluation context
     * @param reportExposure whether to track exposure events for flag evaluations
     * @return list of selected variants for all flags where a variant was selected
     */
    public List<SelectedVariant<Object>> getAllVariants(Map<String, Object> context, boolean reportExposure) {
        List<SelectedVariant<Object>> results = new ArrayList<>();
        Map<String, ExperimentationFlag> definitions = flagDefinitions.get();

        for (ExperimentationFlag flag : definitions.values()) {
            SelectedVariant<Object> fallback = new SelectedVariant<>(null);
            SelectedVariant<Object> result = getVariant(flag.getKey(), fallback, context, reportExposure);

            // Only include successfully selected variants (not fallbacks)
            if (result.isSuccess()) {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Tracks an exposure event for local evaluation.
     */
    private void trackLocalExposure(Map<String, Object> context, String flagKey, String variantKey, long latencyMs, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        if (eventSender == null) {
            return;
        }

        Object distinctIdObj = context.get("distinct_id");
        if (distinctIdObj == null) {
            return;
        }

        trackExposure(distinctIdObj.toString(), flagKey, variantKey, "local", properties -> {
            properties.put("Variant fetch latency (ms)", latencyMs);
        }, experimentId, isExperimentActive, isQaTester);
    }

    // #endregion

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            stopPollingForDefinitions();
        }
    }
}
