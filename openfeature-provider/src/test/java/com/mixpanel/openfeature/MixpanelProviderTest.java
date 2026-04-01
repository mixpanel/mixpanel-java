package com.mixpanel.openfeature;

import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;
import com.mixpanel.mixpanelapi.featureflags.provider.BaseFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.LocalFlagsProvider;
import dev.openfeature.sdk.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MixpanelProviderTest {

    private BaseFlagsProvider<?> mockFlagsProvider;
    private MixpanelProvider provider;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        mockFlagsProvider = mock(BaseFlagsProvider.class);
        provider = new MixpanelProvider(mockFlagsProvider);
    }

    // Metadata

    @Test
    public void testGetMetadataReturnsCorrectName() {
        Metadata metadata = provider.getMetadata();
        assertEquals("mixpanel-provider", metadata.getName());
    }

    // Boolean evaluation

    @SuppressWarnings("unchecked")
    @Test
    public void testBooleanEvaluationSuccess() {
        SelectedVariant<Object> variant = new SelectedVariant<>("on", true, null, null, null);
        when(mockFlagsProvider.getVariant(eq("bool-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("bool-flag", false, new ImmutableContext());

        assertTrue(result.getValue());
        assertEquals("on", result.getVariant());
        assertEquals("STATIC", result.getReason());
        assertNull(result.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBooleanEvaluationTypeMismatch() {
        SelectedVariant<Object> variant = new SelectedVariant<>("on", "not-a-boolean", null, null, null);
        when(mockFlagsProvider.getVariant(eq("bool-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("bool-flag", false, new ImmutableContext());

        assertFalse(result.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBooleanEvaluationFlagNotFound() {
        SelectedVariant<Object> fallback = new SelectedVariant<>(false);
        when(mockFlagsProvider.getVariant(eq("missing-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(fallback);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("missing-flag", false, new ImmutableContext());

        assertFalse(result.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    // String evaluation

    @SuppressWarnings("unchecked")
    @Test
    public void testStringEvaluationSuccess() {
        SelectedVariant<Object> variant = new SelectedVariant<>("blue", "blue", null, null, null);
        when(mockFlagsProvider.getVariant(eq("color-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<String> result = provider.getStringEvaluation("color-flag", "red", new ImmutableContext());

        assertEquals("blue", result.getValue());
        assertEquals("blue", result.getVariant());
        assertEquals("STATIC", result.getReason());
        assertNull(result.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStringEvaluationTypeMismatch() {
        SelectedVariant<Object> variant = new SelectedVariant<>("on", 42, null, null, null);
        when(mockFlagsProvider.getVariant(eq("string-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<String> result = provider.getStringEvaluation("string-flag", "default", new ImmutableContext());

        assertEquals("default", result.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testStringEvaluationFlagNotFound() {
        SelectedVariant<Object> fallback = new SelectedVariant<>(null);
        when(mockFlagsProvider.getVariant(eq("missing-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(fallback);

        ProviderEvaluation<String> result = provider.getStringEvaluation("missing-flag", "fallback", new ImmutableContext());

        assertEquals("fallback", result.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    // Integer evaluation

    @SuppressWarnings("unchecked")
    @Test
    public void testIntegerEvaluationSuccess() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", 42, null, null, null);
        when(mockFlagsProvider.getVariant(eq("int-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());

        assertEquals(Integer.valueOf(42), result.getValue());
        assertEquals("v1", result.getVariant());
        assertEquals("STATIC", result.getReason());
        assertNull(result.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIntegerEvaluationFromLong() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", 42L, null, null, null);
        when(mockFlagsProvider.getVariant(eq("int-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());

        assertEquals(Integer.valueOf(42), result.getValue());
        assertEquals("STATIC", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIntegerEvaluationFromDouble() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", 42.0, null, null, null);
        when(mockFlagsProvider.getVariant(eq("int-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());

        assertEquals(Integer.valueOf(42), result.getValue());
        assertEquals("STATIC", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIntegerEvaluationTypeMismatch() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", "not-a-number", null, null, null);
        when(mockFlagsProvider.getVariant(eq("int-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());

        assertEquals(Integer.valueOf(0), result.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIntegerEvaluationFlagNotFound() {
        SelectedVariant<Object> fallback = new SelectedVariant<>(null);
        when(mockFlagsProvider.getVariant(eq("missing-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(fallback);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("missing-flag", 99, new ImmutableContext());

        assertEquals(Integer.valueOf(99), result.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testIntegerEvaluationOverflowReturnsMismatch() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", Long.MAX_VALUE, null, null, null);
        when(mockFlagsProvider.getVariant(eq("int-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Integer> result = provider.getIntegerEvaluation("int-flag", 0, new ImmutableContext());

        assertEquals(Integer.valueOf(0), result.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, result.getErrorCode());
    }

    // Double evaluation

    @SuppressWarnings("unchecked")
    @Test
    public void testDoubleEvaluationSuccess() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", 3.14, null, null, null);
        when(mockFlagsProvider.getVariant(eq("double-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Double> result = provider.getDoubleEvaluation("double-flag", 0.0, new ImmutableContext());

        assertEquals(Double.valueOf(3.14), result.getValue());
        assertEquals("v1", result.getVariant());
        assertEquals("STATIC", result.getReason());
        assertNull(result.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoubleEvaluationFromInteger() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", 42, null, null, null);
        when(mockFlagsProvider.getVariant(eq("double-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Double> result = provider.getDoubleEvaluation("double-flag", 0.0, new ImmutableContext());

        assertEquals(Double.valueOf(42.0), result.getValue());
        assertEquals("STATIC", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoubleEvaluationTypeMismatch() {
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", "not-a-number", null, null, null);
        when(mockFlagsProvider.getVariant(eq("double-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Double> result = provider.getDoubleEvaluation("double-flag", 0.0, new ImmutableContext());

        assertEquals(Double.valueOf(0.0), result.getValue());
        assertEquals(ErrorCode.TYPE_MISMATCH, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDoubleEvaluationFlagNotFound() {
        SelectedVariant<Object> fallback = new SelectedVariant<>(null);
        when(mockFlagsProvider.getVariant(eq("missing-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(fallback);

        ProviderEvaluation<Double> result = provider.getDoubleEvaluation("missing-flag", 9.9, new ImmutableContext());

        assertEquals(Double.valueOf(9.9), result.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    // Object evaluation

    @SuppressWarnings("unchecked")
    @Test
    public void testObjectEvaluationSuccess() {
        Map<String, Object> objValue = new HashMap<>();
        objValue.put("key", "value");
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", objValue, null, null, null);
        when(mockFlagsProvider.getVariant(eq("obj-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Value> result = provider.getObjectEvaluation("obj-flag", new Value(), new ImmutableContext());

        assertNotNull(result.getValue());
        assertEquals("v1", result.getVariant());
        assertEquals("STATIC", result.getReason());
        assertNull(result.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testObjectEvaluationFlagNotFound() {
        SelectedVariant<Object> fallback = new SelectedVariant<>(null);
        when(mockFlagsProvider.getVariant(eq("missing-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(fallback);

        Value defaultValue = new Value("default");
        ProviderEvaluation<Value> result = provider.getObjectEvaluation("missing-flag", defaultValue, new ImmutableContext());

        assertEquals(defaultValue, result.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    // Context handling — merged context from ctx parameter is forwarded

    @SuppressWarnings("unchecked")
    @Test
    public void testPerEvaluationContextIsForwarded() {
        SelectedVariant<Object> variant = new SelectedVariant<>("on", true, null, null, null);
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), contextCaptor.capture(), eq(true)))
                .thenReturn(variant);

        Map<String, Value> perEvalAttrs = new HashMap<>();
        perEvalAttrs.put("plan", new Value("pro"));
        perEvalAttrs.put("age", new Value(25));
        provider.getBooleanEvaluation("flag", false, new ImmutableContext(perEvalAttrs));

        Map<String, Object> captured = contextCaptor.getValue();
        assertEquals("pro", captured.get("plan"));
        assertEquals(25, captured.get("age"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPerEvaluationContextIsForwardedForObjectEvaluation() {
        Map<String, Object> objValue = new HashMap<>();
        objValue.put("key", "value");
        SelectedVariant<Object> variant = new SelectedVariant<>("v1", objValue, null, null, null);
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        when(mockFlagsProvider.getVariant(eq("obj-flag"), any(SelectedVariant.class), contextCaptor.capture(), eq(true)))
                .thenReturn(variant);

        Map<String, Value> perEvalAttrs = new HashMap<>();
        perEvalAttrs.put("source", new Value("per-eval"));
        provider.getObjectEvaluation("obj-flag", new Value(), new ImmutableContext(perEvalAttrs));

        Map<String, Object> captured = contextCaptor.getValue();
        assertEquals("per-eval", captured.get("source"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTargetingKeyIsRegularProperty() {
        SelectedVariant<Object> variant = new SelectedVariant<>("on", true, null, null, null);
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), contextCaptor.capture(), eq(true)))
                .thenReturn(variant);

        Map<String, Value> attrs = new HashMap<>();
        attrs.put("targetingKey", new Value("tk-value"));
        attrs.put("distinct_id", new Value("user-123"));
        provider.getBooleanEvaluation("flag", false, new ImmutableContext(attrs));

        Map<String, Object> captured = contextCaptor.getValue();
        // targetingKey should be passed as-is, not treated specially
        assertEquals("tk-value", captured.get("targetingKey"));
        assertEquals("user-123", captured.get("distinct_id"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTargetingKeyFromGetTargetingKeyIsIncluded() {
        SelectedVariant<Object> variant = new SelectedVariant<>("on", true, null, null, null);
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), contextCaptor.capture(), eq(true)))
                .thenReturn(variant);

        Map<String, Value> attrs = new HashMap<>();
        attrs.put("distinct_id", new Value("user-123"));
        attrs.put("plan", new Value("pro"));
        // ImmutableContext(targetingKey, attributes) sets getTargetingKey() separately from keySet()
        provider.getBooleanEvaluation("flag", false, new ImmutableContext("tk-from-constructor", attrs));

        Map<String, Object> captured = contextCaptor.getValue();
        assertEquals("tk-from-constructor", captured.get("targetingKey"));
        assertEquals("user-123", captured.get("distinct_id"));
        assertEquals("pro", captured.get("plan"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExplicitTargetingKeyAttributeOverriddenByGetTargetingKey() {
        SelectedVariant<Object> variant = new SelectedVariant<>("on", true, null, null, null);
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), contextCaptor.capture(), eq(true)))
                .thenReturn(variant);

        Map<String, Value> attrs = new HashMap<>();
        attrs.put("targetingKey", new Value("from-attribute"));
        attrs.put("distinct_id", new Value("user-123"));
        // ImmutableContext merges the constructor targeting key into keySet(),
        // so the constructor value takes precedence over an explicit attribute
        provider.getBooleanEvaluation("flag", false, new ImmutableContext("from-constructor", attrs));

        Map<String, Object> captured = contextCaptor.getValue();
        // The SDK's ImmutableContext uses the constructor targeting key as the
        // "targetingKey" entry in keySet(), overriding the explicit attribute
        assertEquals("from-constructor", captured.get("targetingKey"));
        assertEquals("user-123", captured.get("distinct_id"));
    }

    // PROVIDER_NOT_READY

    @Test
    public void testProviderNotReadyWithLocalProvider() {
        LocalFlagsProvider mockLocal = mock(LocalFlagsProvider.class);
        when(mockLocal.areFlagsReady()).thenReturn(false);
        MixpanelProvider localProvider = new MixpanelProvider(mockLocal);

        ProviderEvaluation<Boolean> result = localProvider.getBooleanEvaluation("flag", true, new ImmutableContext());

        assertTrue(result.getValue());
        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @Test
    public void testProviderNotReadyObjectEvaluation() {
        LocalFlagsProvider mockLocal = mock(LocalFlagsProvider.class);
        when(mockLocal.areFlagsReady()).thenReturn(false);
        MixpanelProvider localProvider = new MixpanelProvider(mockLocal);

        Value defaultValue = new Value("default");
        ProviderEvaluation<Value> result = localProvider.getObjectEvaluation("flag", defaultValue, new ImmutableContext());

        assertEquals(defaultValue, result.getValue());
        assertEquals(ErrorCode.PROVIDER_NOT_READY, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProviderReadyWithLocalProvider() {
        LocalFlagsProvider mockLocal = mock(LocalFlagsProvider.class);
        when(mockLocal.areFlagsReady()).thenReturn(true);
        SelectedVariant<Object> variant = new SelectedVariant<>("on", true, null, null, null);
        when(mockLocal.getVariant(eq("flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);
        MixpanelProvider localProvider = new MixpanelProvider(mockLocal);

        ProviderEvaluation<Boolean> result = localProvider.getBooleanEvaluation("flag", false, new ImmutableContext());

        assertTrue(result.getValue());
        assertEquals("STATIC", result.getReason());
        assertNull(result.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testProviderNotReadySkippedForNonLocalProvider() {
        // BaseFlagsProvider (non-local) should not check readiness
        SelectedVariant<Object> variant = new SelectedVariant<>("on", true, null, null, null);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("flag", false, new ImmutableContext());

        assertTrue(result.getValue());
        assertEquals("STATIC", result.getReason());
        assertNull(result.getErrorCode());
    }

    // Exception handling

    @SuppressWarnings("unchecked")
    @Test
    public void testExceptionReturnDefaultValue() {
        when(mockFlagsProvider.getVariant(eq("error-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenThrow(new RuntimeException("something went wrong"));

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("error-flag", true, new ImmutableContext());

        assertTrue(result.getValue());
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    // Object evaluation type mismatch (object eval returns value as-is, so this tests exception path)

    @SuppressWarnings("unchecked")
    @Test
    public void testObjectEvaluationException() {
        when(mockFlagsProvider.getVariant(eq("obj-flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenThrow(new RuntimeException("conversion error"));

        Value defaultValue = new Value("default");
        ProviderEvaluation<Value> result = provider.getObjectEvaluation("obj-flag", defaultValue, new ImmutableContext());

        assertEquals(defaultValue, result.getValue());
        assertEquals(ErrorCode.GENERAL, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    // Variant key passthrough

    @SuppressWarnings("unchecked")
    @Test
    public void testVariantKeyPassedThroughOnBooleanEvaluation() {
        SelectedVariant<Object> variant = new SelectedVariant<>("my-variant", true, null, null, null);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("flag", false, new ImmutableContext());

        assertEquals("my-variant", result.getVariant());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testVariantKeyPassedThroughOnObjectEvaluation() {
        SelectedVariant<Object> variant = new SelectedVariant<>("obj-variant", "some-value", null, null, null);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Value> result = provider.getObjectEvaluation("flag", new Value(), new ImmutableContext());

        assertEquals("obj-variant", result.getVariant());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNullVariantKeyTreatedAsFallbackOnBooleanEvaluation() {
        SelectedVariant<Object> variant = new SelectedVariant<>(null, true, null, null, null);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        ProviderEvaluation<Boolean> result = provider.getBooleanEvaluation("flag", false, new ImmutableContext());

        assertFalse(result.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNullVariantKeyTreatedAsFallbackOnObjectEvaluation() {
        SelectedVariant<Object> variant = new SelectedVariant<>(null, "some-value", null, null, null);
        when(mockFlagsProvider.getVariant(eq("flag"), any(SelectedVariant.class), anyMap(), eq(true)))
                .thenReturn(variant);

        Value defaultValue = new Value("default");
        ProviderEvaluation<Value> result = provider.getObjectEvaluation("flag", defaultValue, new ImmutableContext());

        assertEquals(defaultValue, result.getValue());
        assertEquals(ErrorCode.FLAG_NOT_FOUND, result.getErrorCode());
        assertEquals("ERROR", result.getReason());
    }

    // Shutdown

    @Test
    public void testShutdownIsNoOp() {
        // Should not throw
        provider.shutdown();
    }
}
