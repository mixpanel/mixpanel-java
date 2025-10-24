package com.mixpanel.mixpanelapi.featureflags.provider;

import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Unit tests for LocalFlagsProvider.
 * Tests cover all aspects of local feature flag evaluation including fallback behavior,
 * test user configuration, rollout/distribution, runtime evaluation, exposure tracking,
 * readiness checks, and polling.
 */
public class LocalFlagsProviderTest extends BaseFlagsProviderTest {

    private TestableLocalFlagsProvider provider;
    private LocalFlagsConfig config;
    private MockExposureTracker exposureTracker;

    // #region Mocks

    /**
     * Testable subclass of LocalFlagsProvider that allows mocking HTTP responses.
     */
    private static class TestableLocalFlagsProvider extends LocalFlagsProvider {
        private final MockHttpProvider httpMock = new MockHttpProvider();

        public TestableLocalFlagsProvider(LocalFlagsConfig config, String sdkVersion, ExposureTracker exposureTracker) {
            super(config, sdkVersion, exposureTracker);
        }

        public void setMockResponse(String urlPattern, String response) {
            httpMock.setMockResponse(urlPattern, response);
        }

        public void setMockException(IOException exception) {
            httpMock.setMockException(exception);
        }

        @Override
        protected String httpGet(String urlString) throws IOException {
            return httpMock.mockHttpGet(urlString);
        }
    }

    private static class MockExposureTracker extends BaseExposureTrackerMock<MockExposureTracker.ExposureEvent>
            implements LocalFlagsProvider.ExposureTracker {

        static class ExposureEvent {
            String distinctId;
            String flagKey;
            String variantKey;
            String evaluationMode;
            long latencyMs;
            java.util.UUID experimentId;
            Boolean isExperimentActive;
            Boolean isQaTester;

            ExposureEvent(String distinctId, String flagKey, String variantKey, String evaluationMode, long latencyMs, java.util.UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
                this.distinctId = distinctId;
                this.flagKey = flagKey;
                this.variantKey = variantKey;
                this.evaluationMode = evaluationMode;
                this.latencyMs = latencyMs;
                this.experimentId = experimentId;
                this.isExperimentActive = isExperimentActive;
                this.isQaTester = isQaTester;
            }
        }

        @Override
        public void trackExposure(String distinctId, String flagKey, String variantKey, String evaluationMode, long latencyMs, java.util.UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
            events.add(new ExposureEvent(distinctId, flagKey, variantKey, evaluationMode, latencyMs, experimentId, isExperimentActive, isQaTester));
        }
    }

    @Before
    public void setUp() {
        config = LocalFlagsConfig.builder()
            .projectToken(TEST_TOKEN)
            .enablePolling(false)
            .build();
        exposureTracker = new MockExposureTracker();
    }

    @Override
    protected Object getProvider() {
        return provider;
    }

    // #endregion

    // #region Helper Methods

    /**
     * Creates a test provider with custom HTTP response.
     * The response will be returned when the flags definitions URL is called.
     */
    private TestableLocalFlagsProvider createProviderWithResponse(String jsonResponse) {
        TestableLocalFlagsProvider testProvider = new TestableLocalFlagsProvider(config, SDK_VERSION, exposureTracker);

        if (jsonResponse != null) {
            // Mock the flags definitions endpoint
            testProvider.setMockResponse("/flags/definitions", jsonResponse);
        } else {
            // Simulate network error by setting exception
            testProvider.setMockException(new IOException("Simulated network error"));
        }

        return testProvider;
    }

    /**
     * Builds a complete flag definition JSON response
     */
    private String buildFlagsResponse(String flagKey, String context, List<Variant> variants,
                                     List<Rollout> rollouts, Map<String, String> testUsers) {
        return buildFlagsResponse(flagKey, context, variants, rollouts, testUsers, null, null);
    }

    /**
     * Builds a complete flag definition JSON response with experiment metadata
     */
    private String buildFlagsResponse(String flagKey, String context, List<Variant> variants,
                                     List<Rollout> rollouts, Map<String, String> testUsers,
                                     String experimentId, Boolean isExperimentActive) {
        // Convert String experimentId to UUID if provided
        UUID experimentUuid = null;
        if (experimentId != null && !experimentId.isEmpty()) {
            try {
                experimentUuid = UUID.fromString(experimentId);
            } catch (IllegalArgumentException e) {
                // If it's not a valid UUID, leave it as null
            }
        }

        // Create FlagDefinition and use shared helper
        FlagDefinition flagDef = new FlagDefinition(flagKey, context, variants, rollouts, testUsers, experimentUuid, isExperimentActive);

        try {
            JSONObject root = new JSONObject();
            JSONArray flagsArray = new JSONArray();
            flagsArray.put(buildFlagJsonObject(flagDef, "flag-1"));
            root.put("flags", flagsArray);
            return root.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test response", e);
        }
    }

    /**
     * Builds a single flag JSONObject from a FlagDefinition.
     * This is a shared helper used by both buildFlagsResponse and buildMultipleFlagsResponse.
     */
    private JSONObject buildFlagJsonObject(FlagDefinition def, String flagId) {
        JSONObject flag = new JSONObject();
        flag.put("id", flagId);
        flag.put("name", "Test Flag " + def.flagKey);
        flag.put("key", def.flagKey);
        flag.put("status", "active");
        flag.put("project_id", 123);
        flag.put("context", def.context);

        // Add experiment metadata if provided
        if (def.experimentId != null) {
            flag.put("experiment_id", def.experimentId.toString());
        }
        if (def.isExperimentActive != null) {
            flag.put("is_experiment_active", def.isExperimentActive);
        }

        JSONObject ruleset = new JSONObject();

        // Add variants
        JSONArray variantsArray = new JSONArray();
        for (Variant v : def.variants) {
            JSONObject variantJson = new JSONObject();
            variantJson.put("key", v.getKey());
            variantJson.put("value", v.getValue());
            variantJson.put("is_control", v.isControl());
            variantJson.put("split", v.getSplit());
            variantsArray.put(variantJson);
        }
        ruleset.put("variants", variantsArray);

        // Add rollouts
        JSONArray rolloutsArray = new JSONArray();
        for (Rollout r : def.rollouts) {
            JSONObject rolloutJson = new JSONObject();
            rolloutJson.put("rollout_percentage", r.getRolloutPercentage());
            if (r.hasVariantOverride()) {
                JSONObject variantOverrideObj = new JSONObject();
                variantOverrideObj.put("key", r.getVariantOverride().getKey());
                rolloutJson.put("variant_override", variantOverrideObj);
            }
            if (r.hasRuntimeEvaluation()) {
                JSONObject runtimeEval = new JSONObject(r.getRuntimeEvaluationDefinition());
                rolloutJson.put("runtime_evaluation_definition", runtimeEval);
            }
            rolloutsArray.put(rolloutJson);
        }
        ruleset.put("rollout", rolloutsArray);

        // Add test users
        if (def.testUsers != null && !def.testUsers.isEmpty()) {
            JSONObject testJson = new JSONObject();
            JSONObject usersJson = new JSONObject(def.testUsers);
            testJson.put("users", usersJson);
            ruleset.put("test", testJson);
        }

        flag.put("ruleset", ruleset);
        return flag;
    }

    /**
     * Helper class to build a flag for buildMultipleFlagsResponse
     */
    private static class FlagDefinition {
        String flagKey;
        String context;
        List<Variant> variants;
        List<Rollout> rollouts;
        Map<String, String> testUsers;
        UUID experimentId;
        Boolean isExperimentActive;

        FlagDefinition(String flagKey, String context, List<Variant> variants, List<Rollout> rollouts) {
            this(flagKey, context, variants, rollouts, null, null, null);
        }

        FlagDefinition(String flagKey, String context, List<Variant> variants, List<Rollout> rollouts,
                      Map<String, String> testUsers, UUID experimentId, Boolean isExperimentActive) {
            this.flagKey = flagKey;
            this.context = context;
            this.variants = variants;
            this.rollouts = rollouts;
            this.testUsers = testUsers;
            this.experimentId = experimentId;
            this.isExperimentActive = isExperimentActive;
        }
    }

    /**
     * Builds a response with multiple flag definitions
     */
    private String buildMultipleFlagsResponse(List<FlagDefinition> flagDefs) {
        try {
            JSONObject root = new JSONObject();
            JSONArray flagsArray = new JSONArray();

            int flagId = 1;
            for (FlagDefinition def : flagDefs) {
                flagsArray.put(buildFlagJsonObject(def, "flag-" + flagId++));
            }

            root.put("flags", flagsArray);
            return root.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build multiple flags response", e);
        }
    }

    // #endregion

    // #region Fallback Behavior Tests

    @Test
    public void testReturnFallbackWhenNoFlagDefinitionsExist() {
        // Create provider with empty flags response
        String response = "{\"flags\":[]}";
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("any-flag", "fallback", context);

        assertEquals("fallback", result);
        assertEquals(0, exposureTracker.getEventCount());
    }

    @Test
    public void testReturnFallbackWhenFlagDefinitionAPICallFails() {
        // Create provider that will throw IOException
        provider = createProviderWithResponse(null);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("fallback", result);
        assertEquals(0, exposureTracker.getEventCount());
    }

    @Test
    public void testReturnFallbackWhenRequestedFlagDoesNotExist() {
        // Create provider with one flag, but request a different one
        List<Variant> variants = Arrays.asList(new Variant("control", "blue", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("existing-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("non-existent-flag", "fallback", context);

        assertEquals("fallback", result);
        assertEquals(0, exposureTracker.getEventCount());
    }

    @Test
    public void testReturnFallbackWhenNoContextProvided() {
        List<Variant> variants = Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        // Empty context - missing required distinct_id
        Map<String, Object> context = new HashMap<>();
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("fallback", result);
        assertEquals(0, exposureTracker.getEventCount());
    }

    @Test
    public void testReturnFallbackWhenWrongContextKeyProvided() {
        // Flag configured to use "user_id" as context property
        List<Variant> variants = Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("test-flag", "user_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        // Context provides distinct_id but flag needs user_id
        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("fallback", result);
        assertEquals(0, exposureTracker.getEventCount());
    }

    @Test
    public void testReturnFallbackWhenRolloutPercentageIsZero() {
        List<Variant> variants = Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(0.0f)); // 0% rollout
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("fallback", result);
        assertEquals(0, exposureTracker.getEventCount());
    }

    // #endregion
    // #region Test User Configuration Tests

    @Test
    public void testReturnTestUserVariantWhenConfigured() {
        List<Variant> variants = Arrays.asList(
            new Variant("control", "blue", true, 0.5f),
            new Variant("treatment", "red", false, 0.5f)
        );
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));

        // Configure test user to always get "treatment" variant
        Map<String, String> testUsers = new HashMap<>();
        testUsers.put("test-user-456", "treatment");

        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, testUsers);

        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("test-user-456");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("red", result);
        assertEquals(1, exposureTracker.getEventCount());
        assertEquals("treatment", exposureTracker.getLastEvent().variantKey);
    }

    @Test
    public void testFallbackToNormalEvaluationWhenTestUserVariantIsInvalid() {
        List<Variant> variants = Arrays.asList(
            new Variant("control", "blue", true, 1.0f)
        );
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));

        // Configure test user with non-existent variant
        Map<String, String> testUsers = new HashMap<>();
        testUsers.put("test-user-789", "non-existent-variant");

        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, testUsers);

        provider = createProviderWithResponse(response);

        provider.startPollingForDefinitions();
        Map<String, Object> context = buildContext("test-user-789");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        // Should fall through to normal evaluation and select "control" based on 100% split
        assertEquals("blue", result);
        assertEquals(1, exposureTracker.getEventCount());
        assertEquals("control", exposureTracker.getLastEvent().variantKey);
    }

    // #endregion
    // #region Rollout and Distribution Tests

    @Test
    public void testReturnVariantWhenRolloutPercentageIs100() {
        List<Variant> variants = Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f)); // 100% rollout
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        provider.startPollingForDefinitions();
        Map<String, Object> context = buildContext("user-123");
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("value-a", result);
        assertEquals(1, exposureTracker.getEventCount());
    }

    @Test
    public void testSelectCorrectVariantWith100PercentSplit() {
        List<Variant> variants = Arrays.asList(
            new Variant("variant-a", "value-a", false, 0.0f),
            new Variant("variant-b", "value-b", false, 1.0f), // 100% split
            new Variant("variant-c", "value-c", false, 0.0f)
        );
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        provider.startPollingForDefinitions();
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("value-b", result);
        assertEquals(1, exposureTracker.getEventCount());
        assertEquals("variant-b", exposureTracker.getLastEvent().variantKey);
    }

    @Test
    public void testApplyVariantOverrideCorrectly() {
        List<Variant> variants = Arrays.asList(
            new Variant("control", "blue", true, 1.0f),
            new Variant("treatment", "red", false, 0.0f)
        );
        // Rollout with variant override - forces "treatment" regardless of split
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f, null, new VariantOverride("treatment"), null));
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        provider.startPollingForDefinitions();
        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("red", result);
        assertEquals(1, exposureTracker.getEventCount());
        assertEquals("treatment", exposureTracker.getLastEvent().variantKey);
    }

    // #endregion
    // #region Runtime Evaluation Tests

    @Test
    public void testReturnVariantWhenRuntimeEvaluationConditionsSatisfied() {
        List<Variant> variants = Arrays.asList(new Variant("premium-variant", "gold", false, 1.0f));

        // Runtime evaluation: requires plan=premium
        Map<String, Object> runtimeEval = new HashMap<>();
        runtimeEval.put("plan", "premium");
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f, runtimeEval, null, null));

        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        // Context with matching custom properties
        provider.startPollingForDefinitions();
        Map<String, Object> customProps = new HashMap<>();
        customProps.put("plan", "premium");
        Map<String, Object> context = buildContextWithProperties("user-123", customProps);

        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("gold", result);
        assertEquals(1, exposureTracker.getEventCount());
    }

    @Test
    public void testReturnFallbackWhenRuntimeEvaluationConditionsNotSatisfied() {
        List<Variant> variants = Arrays.asList(new Variant("premium-variant", "gold", false, 1.0f));

        // Runtime evaluation: requires plan=premium
        Map<String, Object> runtimeEval = new HashMap<>();
        runtimeEval.put("plan", "premium");
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f, runtimeEval, null, null));

        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        // Context with non-matching custom properties
        provider.startPollingForDefinitions();
        Map<String, Object> customProps = new HashMap<>();
        customProps.put("plan", "free");
        Map<String, Object> context = buildContextWithProperties("user-123", customProps);

        String result = provider.getVariantValue("test-flag", "fallback", context);

        assertEquals("fallback", result);
        assertEquals(0, exposureTracker.getEventCount());
    }

    // #endregion
    // #region Exposure Tracking Tests

    @Test
    public void testTrackExposureWhenVariantIsSelected() {
        List<Variant> variants = Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        provider.startPollingForDefinitions();
        provider.getVariantValue("test-flag", "fallback", context);

        assertEquals(1, exposureTracker.getEventCount());
        MockExposureTracker.ExposureEvent event = exposureTracker.getLastEvent();
        assertEquals("user-123", event.distinctId);
        assertEquals("test-flag", event.flagKey);
        assertEquals("variant-a", event.variantKey);
        assertEquals("local", event.evaluationMode);
        assertTrue(event.latencyMs >= 0);
    }

    @Test
    public void testDoNotTrackExposureWhenReturningFallback() {
        // Empty flags - will return fallback
        String response = "{\"flags\":[]}";
        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        provider.startPollingForDefinitions();
        provider.getVariantValue("test-flag", "fallback", context);

        assertEquals(0, exposureTracker.getEventCount());
    }

    @Test
    public void testDoNotTrackExposureWhenDistinctIdIsMissing() {
        List<Variant> variants = Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        // Context without distinct_id
        provider.startPollingForDefinitions();
        Map<String, Object> context = new HashMap<>();
        provider.getVariantValue("test-flag", "fallback", context);

        // No exposure should be tracked (and it returns fallback anyway)
        assertEquals(0, exposureTracker.getEventCount());
    }

    // #endregion
    // #region Readiness Tests

    @Test
    public void testReturnReadyWhenFlagsAreLoaded() {
        List<Variant> variants = Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        // Should not be ready before fetching
        assertFalse("Should not be ready before fetching", provider.areFlagsReady());

        // Fetch flag definitions
        provider.startPollingForDefinitions();

        // Should be ready after successful fetch
        assertTrue("Should be ready after successful fetch", provider.areFlagsReady());
    }

    @Test
    public void testReturnReadyWhenEmptyFlagsAreLoaded() {
        String response = "{\"flags\":[]}";

        provider = createProviderWithResponse(response);

        // Should not be ready before fetching
        assertFalse("Should not be ready before fetching", provider.areFlagsReady());

        // Fetch flag definitions
        provider.startPollingForDefinitions();

        // Should be ready even with empty flags
        assertTrue("Should be ready even with empty flags", provider.areFlagsReady());
    }

    // #endregion
    // #region Boolean Convenience Method Tests

    @Test
    public void testIsEnabledReturnsFalseForNonexistentFlag() {
        String response = "{\"flags\":[]}";
        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        provider.startPollingForDefinitions();
        boolean result = provider.isEnabled("non-existent-flag", context);

        assertFalse(result);
    }

    @Test
    public void testIsEnabledReturnsTrueForBooleanTrueVariant() {
        List<Variant> variants = Arrays.asList(new Variant("enabled", true, false, 1.0f));
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));
        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, null);

        provider = createProviderWithResponse(response);

        Map<String, Object> context = buildContext("user-123");
        provider.startPollingForDefinitions();
        boolean result = provider.isEnabled("test-flag", context);

        assertTrue(result);
    }

    // #endregion
    // #region Polling Tests

    @Test
    public void testUseMostRecentPolledFlagDefinitions() throws Exception {
        // Enable polling with very short interval
        config = LocalFlagsConfig.builder()
            .projectToken(TEST_TOKEN)
            .enablePolling(true)
            .pollingIntervalSeconds(1)
            .build();

        // Start with initial flag definition
        List<Variant> variants1 = Arrays.asList(new Variant("variant-old", "old-value", false, 1.0f));
        List<Rollout> rollouts1 = Arrays.asList(new Rollout(1.0f));
        String response1 = buildFlagsResponse("test-flag", "distinct_id", variants1, rollouts1, null);

        provider = new TestableLocalFlagsProvider(config, SDK_VERSION, exposureTracker);
        provider.setMockResponse("/flags/definitions", response1);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");

        // First evaluation should return old value
        String result1 = provider.getVariantValue("test-flag", "fallback", context);
        assertEquals("old-value", result1);

        // Simulate a polling update by changing the mock response
        List<Variant> variants2 = Arrays.asList(new Variant("variant-new", "new-value", false, 1.0f));
        List<Rollout> rollouts2 = Arrays.asList(new Rollout(1.0f));
        String response2 = buildFlagsResponse("test-flag", "distinct_id", variants2, rollouts2, null);
        provider.setMockResponse("/flags/definitions", response2);

        // Wait for polling to occur
        Thread.sleep(1500);

        // Second evaluation should return new value after polling update
        String result2 = provider.getVariantValue("test-flag", "fallback", context);
        assertEquals("new-value", result2);

        provider.stopPollingForDefinitions();
    }

    // #endregion
    // #region getAllVariants Tests

    @Test
    public void testGetAllVariantsReturnsAllSuccessfullySelectedVariants() {
        // Create multiple flags with 100% rollout
        List<FlagDefinition> flags = Arrays.asList(
            new FlagDefinition("flag-1", "distinct_id",
                Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f))),
            new FlagDefinition("flag-2", "distinct_id",
                Arrays.asList(new Variant("variant-b", "value-b", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f))),
            new FlagDefinition("flag-3", "distinct_id",
                Arrays.asList(new Variant("variant-c", "value-c", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f)))
        );

        String response = buildMultipleFlagsResponse(flags);
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        List<SelectedVariant<Object>> results = provider.getAllVariants(context, true);

        assertEquals(3, results.size());

        // Verify all variants are successful (not fallbacks)
        for (SelectedVariant<Object> variant : results) {
            assertTrue(variant.isSuccess());
            assertNotNull(variant.getVariantKey());
        }
    }

    @Test
    public void testGetAllVariantsReturnsEmptyListWhenNoFlagsDefined() {
        String response = "{\"flags\":[]}";
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        List<SelectedVariant<Object>> results = provider.getAllVariants(context, true);

        assertNotNull(results);
        assertEquals(0, results.size());
    }

    @Test
    public void testGetAllVariantsReturnsOnlySuccessfulVariants() {
        // Create flags with mixed rollout percentages
        List<FlagDefinition> flags = Arrays.asList(
            new FlagDefinition("flag-success-1", "distinct_id",
                Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f))),  // 100% rollout - will succeed
            new FlagDefinition("flag-fail-1", "distinct_id",
                Arrays.asList(new Variant("variant-b", "value-b", false, 1.0f)),
                Arrays.asList(new Rollout(0.0f))),  // 0% rollout - will fallback
            new FlagDefinition("flag-success-2", "distinct_id",
                Arrays.asList(new Variant("variant-c", "value-c", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f)))   // 100% rollout - will succeed
        );

        String response = buildMultipleFlagsResponse(flags);
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        List<SelectedVariant<Object>> results = provider.getAllVariants(context, true);

        // Should only return the 2 successful variants
        assertEquals(2, results.size());

        // Verify all returned variants are successful
        for (SelectedVariant<Object> variant : results) {
            assertTrue(variant.isSuccess());
        }
    }

    @Test
    public void testGetAllVariantsTracksExposureEventsWhenReportExposureTrue() {
        // Create 3 flags with 100% rollout
        List<FlagDefinition> flags = Arrays.asList(
            new FlagDefinition("flag-1", "distinct_id",
                Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f))),
            new FlagDefinition("flag-2", "distinct_id",
                Arrays.asList(new Variant("variant-b", "value-b", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f))),
            new FlagDefinition("flag-3", "distinct_id",
                Arrays.asList(new Variant("variant-c", "value-c", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f)))
        );

        String response = buildMultipleFlagsResponse(flags);
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        exposureTracker.reset();
        Map<String, Object> context = buildContext("user-123");
        List<SelectedVariant<Object>> results = provider.getAllVariants(context, true);

        // Should track 3 exposure events (one for each successful flag)
        assertEquals(3, exposureTracker.getEventCount());
        assertEquals(3, results.size());
    }

    @Test
    public void testGetAllVariantsDoesNotTrackExposureEventsWhenReportExposureFalse() {
        // Create 3 flags with 100% rollout
        List<FlagDefinition> flags = Arrays.asList(
            new FlagDefinition("flag-1", "distinct_id",
                Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f))),
            new FlagDefinition("flag-2", "distinct_id",
                Arrays.asList(new Variant("variant-b", "value-b", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f))),
            new FlagDefinition("flag-3", "distinct_id",
                Arrays.asList(new Variant("variant-c", "value-c", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f)))
        );

        String response = buildMultipleFlagsResponse(flags);
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        exposureTracker.reset();
        Map<String, Object> context = buildContext("user-123");
        List<SelectedVariant<Object>> results = provider.getAllVariants(context, false);

        // Should NOT track any exposure events
        assertEquals(0, exposureTracker.getEventCount());

        // But should still return all 3 variants
        assertEquals(3, results.size());
        for (SelectedVariant<Object> variant : results) {
            assertTrue(variant.isSuccess());
        }
    }

    @Test
    public void testGetAllVariantsReturnsVariantsWithExperimentMetadata() {
        // Create test UUIDs
        UUID experimentId1 = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID experimentId2 = UUID.fromString("223e4567-e89b-12d3-a456-426614174001");

        // Create flags with experiment metadata
        List<FlagDefinition> flags = Arrays.asList(
            new FlagDefinition("flag-1", "distinct_id",
                Arrays.asList(new Variant("variant-a", "value-a", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f)),
                null, experimentId1, true),
            new FlagDefinition("flag-2", "distinct_id",
                Arrays.asList(new Variant("variant-b", "value-b", false, 1.0f)),
                Arrays.asList(new Rollout(1.0f)),
                null, experimentId2, false)
        );

        String response = buildMultipleFlagsResponse(flags);
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        Map<String, Object> context = buildContext("user-123");
        List<SelectedVariant<Object>> results = provider.getAllVariants(context, true);

        assertEquals(2, results.size());

        // Find variants by their value (order is not guaranteed from HashMap)
        SelectedVariant<Object> variantA = null;
        SelectedVariant<Object> variantB = null;
        for (SelectedVariant<Object> variant : results) {
            if ("value-a".equals(variant.getVariantValue())) {
                variantA = variant;
            } else if ("value-b".equals(variant.getVariantValue())) {
                variantB = variant;
            }
        }

        // Verify both variants were found with their experiment metadata
        assertNotNull("variant-a should be present", variantA);
        assertNotNull(variantA.getExperimentId());
        assertEquals(experimentId1, variantA.getExperimentId());
        assertEquals(true, variantA.getIsExperimentActive());

        assertNotNull("variant-b should be present", variantB);
        assertNotNull(variantB.getExperimentId());
        assertEquals(experimentId2, variantB.getExperimentId());
        assertEquals(false, variantB.getIsExperimentActive());
    }

    // #endregion
    // #region isQaTester Calculation Tests

    @Test
    public void testIsQaTesterTrueWhenUserIsInTestUserOverrides() {
        List<Variant> variants = Arrays.asList(
            new Variant("control", "blue", true, 0.5f),
            new Variant("treatment", "red", false, 0.5f)
        );
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));

        // Configure test user override
        Map<String, String> testUsers = new HashMap<>();
        testUsers.put("test-user-123", "treatment");

        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, testUsers);
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        exposureTracker.reset();
        Map<String, Object> context = buildContext("test-user-123");
        SelectedVariant<String> result = provider.getVariant("test-flag", new SelectedVariant<>("fallback"), context, true);

        // Verify variant was selected
        assertTrue(result.isSuccess());
        assertEquals("red", result.getVariantValue());
        assertEquals("treatment", result.getVariantKey());

        // Verify exposure event was tracked with isQaTester=true
        assertEquals(1, exposureTracker.getEventCount());
        MockExposureTracker.ExposureEvent event = exposureTracker.getLastEvent();
        assertEquals("test-user-123", event.distinctId);
        assertEquals("test-flag", event.flagKey);
        assertEquals("treatment", event.variantKey);
        assertEquals(Boolean.TRUE, event.isQaTester);
    }

    @Test
    public void testIsQaTesterFalseWhenUserGoesThroughNormalRollout() {
        List<Variant> variants = Arrays.asList(
            new Variant("control", "blue", true, 1.0f)
        );
        List<Rollout> rollouts = Arrays.asList(new Rollout(1.0f));

        // Configure test user overrides for a different user
        Map<String, String> testUsers = new HashMap<>();
        testUsers.put("different-user", "control");

        String response = buildFlagsResponse("test-flag", "distinct_id", variants, rollouts, testUsers);
        provider = createProviderWithResponse(response);
        provider.startPollingForDefinitions();

        exposureTracker.reset();
        Map<String, Object> context = buildContext("normal-user-456");
        SelectedVariant<String> result = provider.getVariant("test-flag", new SelectedVariant<>("fallback"), context, true);

        // Verify variant was selected via normal rollout
        assertTrue(result.isSuccess());
        assertEquals("blue", result.getVariantValue());
        assertEquals("control", result.getVariantKey());

        // Verify exposure event was tracked with isQaTester=false
        assertEquals(1, exposureTracker.getEventCount());
        MockExposureTracker.ExposureEvent event = exposureTracker.getLastEvent();
        assertEquals("normal-user-456", event.distinctId);
        assertEquals("test-flag", event.flagKey);
        assertEquals("control", event.variantKey);
        assertEquals(Boolean.FALSE, event.isQaTester);
    }

    // #endregion
}
