package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.*;
import com.mixpanel.mixpanelapi.featureflags.util.HashUtils;
import com.mixpanel.mixpanelapi.featureflags.util.JsonLogicEngine;

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

    // Guards start/stop of pollingExecutor so concurrent startPollingForDefinitions
    // calls don't both create executors (leaking the first).
    private final Object pollingLock = new Object();
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
            int intervalSeconds = config.getPollingIntervalSeconds();
            if (intervalSeconds <= 0) {
                // Validate before allocating. If we let scheduleAtFixedRate reject
                // a non-positive period, pollingExecutor would already be assigned
                // and the idempotency guard below would block every retry.
                logger.log(Level.WARNING, "Polling interval must be > 0 seconds; got " + intervalSeconds + ". Polling not started.");
                return;
            }

            synchronized (pollingLock) {
                if (pollingExecutor != null) {
                    // Idempotent: a previous call already scheduled the poller.
                    // Without this guard, two concurrent start calls would each
                    // allocate a new ScheduledExecutorService and the earlier
                    // one would leak.
                    return;
                }

                pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "mixpanel-flags-poller");
                    t.setDaemon(true);
                    return t;
                });

                pollingExecutor.scheduleAtFixedRate(
                    this::fetchDefinitions,
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS
                );

                logger.log(Level.INFO, "Started polling for flag definitions every " + intervalSeconds + " seconds");
            }
        }
    }

    /**
     * Stops polling for flag definitions and releases resources.
     */
    public void stopPollingForDefinitions() {
        ScheduledExecutorService toShutdown;
        synchronized (pollingLock) {
            toShutdown = pollingExecutor;
            pollingExecutor = null;
        }
        if (toShutdown == null) {
            return;
        }
        toShutdown.shutdown();
        try {
            if (!toShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
                toShutdown.shutdownNow();
            }
        } catch (InterruptedException e) {
            toShutdown.shutdownNow();
            Thread.currentThread().interrupt();
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
        } catch (Throwable e) {
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

        // Parse hash_salt (may be null for legacy flags)
        String hashSalt = json.optString("hash_salt", null);

        RuleSet ruleset = parseRuleSet(json.optJSONObject("ruleset"));

        return new ExperimentationFlag(id, name, key, status, projectId, ruleset, context, experimentId, isExperimentActive, hashSalt);
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

        // Parse legacy runtime evaluation (simple key-value format)
        Map<String, Object> legacyRuntimeEval = null;
        JSONObject legacyRuntimeEvalJson = json.optJSONObject("runtime_evaluation_definition");
        if (legacyRuntimeEvalJson != null) {
            legacyRuntimeEval = new HashMap<>();
            for (String key : legacyRuntimeEvalJson.keySet()) {
                legacyRuntimeEval.put(key, legacyRuntimeEvalJson.get(key));
            }
        }

        JSONObject runtimeEvaluationRule = json.optJSONObject("runtime_evaluation_rule");

        Map<String, Float> variantSplits = null;
        JSONObject variantSplitsJson = json.optJSONObject("variant_splits");
        if (variantSplitsJson != null) {
            variantSplits = new HashMap<>();
            for (String key : variantSplitsJson.keySet()) {
                variantSplits.put(key, (float) variantSplitsJson.optDouble(key, 0.0));
            }
        }

        return new Rollout(rolloutPercentage, runtimeEvaluationRule, legacyRuntimeEval, variantOverride, variantSplits);
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
                return fallback.withSource(Source.fallback(Source.Fallback.Reason.FLAG_NOT_FOUND));
            }

            // Extract context value
            String contextProperty = flag.getContext();
            Object contextValueObj = context.get(contextProperty);
            if (contextValueObj == null) {
                logger.log(Level.WARNING, "Variant assignment key property '" + contextProperty + "' not found for flag: " + flagKey);
                return fallback.withSource(Source.fallback(Source.Fallback.Reason.MISSING_CONTEXT_KEY));
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
                            return buildResult(variant, flag, true, flagKey, context, startTime, reportExposure);
                        }
                    }
                }
            }

            // Evaluate rollouts
            List<Rollout> rollouts = ruleset.getRollouts();
            for (int rolloutIndex = 0; rolloutIndex < rollouts.size(); rolloutIndex++) {
                Rollout rollout = rollouts.get(rolloutIndex);

                // Calculate rollout hash
                float rolloutHash = calculateRolloutHash(contextValue, flagKey, flag.getHashSalt(), rolloutIndex);

                if (rolloutHash >= rollout.getRolloutPercentage()) {
                    continue;
                }

                // Check runtime evaluation, continue if this rollout has runtime conditions and it doesn't match
                if (rollout.hasLegacyRuntimeEvaluation() && !matchesLegacyRuntimeConditions(rollout, context)) {
                    continue;
                }

                if (rollout.hasRuntimeEvaluation() && !matchesRuntimeConditions(rollout, context)) {
                    continue;
                }

                // This rollout is selected - determine variant
                Variant selectedVariant;
                if (rollout.hasVariantOverride()) {
                    selectedVariant = findVariantByKey(ruleset.getVariants(), rollout.getVariantOverride().getKey());
                } else {
                    float variantHash = calculateVariantHash(contextValue, flagKey, flag.getHashSalt());
                    selectedVariant = selectVariantBySplit(ruleset.getVariants(), variantHash, rollout);
                }

                if (selectedVariant != null) {
                    return buildResult(selectedVariant, flag, false, flagKey, context, startTime, reportExposure);
                }

                break; // Rollout selected but no variant found
            }

            // No rollout matched
            return fallback.withSource(Source.fallback(Source.Fallback.Reason.NO_ROLLOUT_MATCH));

        } catch (Exception e) {
            logger.log(Level.WARNING, "Error evaluating flag: " + flagKey, e);
            return fallback.withSource(Source.fallback(Source.Fallback.Reason.BACKEND_ERROR));
        }
    }

    /**
     * Builds a SelectedVariant result and optionally tracks exposure.
     */
    @SuppressWarnings("unchecked")
    private <T> SelectedVariant<T> buildResult(Variant variant, ExperimentationFlag flag, boolean isQaTester,
                                                String flagKey, Map<String, Object> context,
                                                long startTime, boolean reportExposure) {
        SelectedVariant<T> result = new SelectedVariant<T>(
            variant.getKey(),
            (T) variant.getValue(),
            flag.getExperimentId(),
            flag.getIsExperimentActive(),
            isQaTester,
            Source.local()
        );
        if (reportExposure) {
            trackLocalExposure(context, flagKey, variant.getKey(), System.currentTimeMillis() - startTime,
                    flag.getExperimentId(), flag.getIsExperimentActive(), isQaTester);
        }
        return result;
    }

    private boolean matchesRuntimeConditions(Rollout rollout, Map<String,Object> context) {
        Map<String, Object> customProperties = getCustomProperties(context);
        return JsonLogicEngine.evaluate(rollout.getRuntimeEvaluationRule(), customProperties);
    }

    /**
     * Evaluates runtime conditions for a rollout.
     * 
     * @return true if all runtime conditions match, false otherwise (or if custom_properties is missing)
     */
    private boolean matchesLegacyRuntimeConditions(Rollout rollout, Map<String, Object> context) {
        Map<String, Object> customProperties = getCustomProperties(context);
        if (customProperties == null) {
            return false;
        }

        Map<String, Object> runtimeEval = rollout.getLegacyRuntimeEvaluationDefinition();
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
     * Applies variant split overrides from a rollout to the flag's variants.
     * <p>
     * Creates a new list of variants with updated split values where overrides are specified.
     * Variants not in the overrides map retain their original split values.
     * </p>
     *
     * @param variants the original list of variants from the flag definition
     * @param variantSplits the map of variant key to split percentage overrides
     * @return a new list with variant split overrides applied
     */
    private List<Variant> applyVariantSplitOverrides(List<Variant> variants, Map<String, Float> variantSplits) {
        List<Variant> result = new ArrayList<>(variants.size());

        for (Variant variant : variants) {
            if (variantSplits.containsKey(variant.getKey())) {
                // Create new variant with overridden split value
                float overriddenSplit = variantSplits.get(variant.getKey());
                Variant updatedVariant = new Variant(
                    variant.getKey(),
                    variant.getValue(),
                    variant.isControl(),
                    overriddenSplit
                );
                result.add(updatedVariant);
            } else {
                // Keep original variant with its original split
                result.add(variant);
            }
        }

        return result;
    }

    /**
     * Selects a variant based on hash and split percentages.
     * <p>
     * If the rollout has variant_splits configured, those override the flag-level splits.
     * Otherwise, uses the default split values from the variants.
     * </p>
     *
     * @param variants the list of variants to select from
     * @param hash the normalized hash value (0.0 to 1.0) for selection
     * @param rollout the rollout being evaluated (may be null or have no variant_splits)
     * @return the selected variant, or null if variants list is empty
     */
    private Variant selectVariantBySplit(List<Variant> variants, float hash, Rollout rollout) {
        // Apply variant split overrides if the rollout specifies them
        List<Variant> variantsToUse = variants;
        if (rollout != null && rollout.hasVariantSplits()) {
            variantsToUse = applyVariantSplitOverrides(variants, rollout.getVariantSplits());
        }

        // Select variant using cumulative split percentages
        float cumulative = 0.0f;
        for (Variant variant : variantsToUse) {
            cumulative += variant.getSplit();
            if (hash < cumulative) {
                return variant;
            }
        }

        // If no variant selected (due to rounding), return last variant
        return variantsToUse.isEmpty() ? null : variantsToUse.get(variantsToUse.size() - 1);
    }

    /**
     * Calculates the rollout hash for a given context and rollout index.
     * <p>
     * This method can be overridden in tests to verify hash parameters.
     * </p>
     *
     * @param contextValue the context value (e.g., user ID)
     * @param flagKey the flag key
     * @param hashSalt the hash salt (null or empty for legacy behavior)
     * @param rolloutIndex the index of the rollout being evaluated
     * @return the normalized hash value (0.0 to 1.0)
     */
    protected float calculateRolloutHash(String contextValue, String flagKey,
                                        String hashSalt, int rolloutIndex) {
        if (hashSalt != null && !hashSalt.isEmpty()) {
            return HashUtils.normalizedHash(contextValue + flagKey, hashSalt + rolloutIndex);
        } else {
            return HashUtils.normalizedHash(contextValue + flagKey, "rollout");
        }
    }

    /**
     * Calculates the variant hash for a given context.
     * <p>
     * This method can be overridden in tests to verify hash parameters.
     * </p>
     *
     * @param contextValue the context value (e.g., user ID)
     * @param flagKey the flag key
     * @param hashSalt the hash salt (null or empty for legacy behavior)
     * @return the normalized hash value (0.0 to 1.0)
     */
    protected float calculateVariantHash(String contextValue, String flagKey, String hashSalt) {
        if (hashSalt != null && !hashSalt.isEmpty()) {
            return HashUtils.normalizedHash(contextValue + flagKey, hashSalt + "variant");
        } else {
            return HashUtils.normalizedHash(contextValue + flagKey, "variant");
        }
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
     * @deprecated Use {@link #getAllVariantsByFlag(Map, boolean)} which returns a map keyed by
     *             flag key, so each result can be associated with the flag it was selected for.
     */
    @Deprecated
    public List<SelectedVariant<Object>> getAllVariants(Map<String, Object> context, boolean reportExposure) {
        return new ArrayList<>(getAllVariantsByFlag(context, reportExposure).values());
    }

    /**
     * Evaluates all flags and returns their selected variants keyed by flag key.
     * <p>
     * Evaluates all flag definitions for the given context and returns a map from
     * flag key to the successfully selected variant. Flags whose evaluation fell
     * back (i.e., did not produce a successful variant) are omitted from the map.
     * </p>
     *
     * @param context the evaluation context
     * @param reportExposure whether to track exposure events for flag evaluations
     * @return map from flag key to selected variant for all flags where a variant was selected
     */
    public Map<String, SelectedVariant<Object>> getAllVariantsByFlag(Map<String, Object> context, boolean reportExposure) {
        Map<String, SelectedVariant<Object>> results = new HashMap<>();
        Map<String, ExperimentationFlag> definitions = flagDefinitions.get();

        for (ExperimentationFlag flag : definitions.values()) {
            SelectedVariant<Object> fallback = new SelectedVariant<>(null);
            SelectedVariant<Object> result = getVariant(flag.getKey(), fallback, context, reportExposure);

            if (result.isSuccess()) {
                results.put(flag.getKey(), result);
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
            // Local eval succeeds when the flag's Variant Assignment Key is
            // something other than distinct_id (e.g., device_id), but the
            // exposure event still needs distinct_id to attribute the user.
            // Surface the drop instead of silently returning so callers can
            // see they need to include distinct_id in the context.
            logger.log(Level.WARNING,
                "Cannot track exposure event for flag ''{0}'' without a distinct_id in the context",
                flagKey);
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

    @Override
    public void shutdown() {
        close();
    }
}
