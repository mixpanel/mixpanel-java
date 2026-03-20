package com.mixpanel.openfeature;

import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;
import com.mixpanel.mixpanelapi.featureflags.provider.BaseFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.LocalFlagsProvider;
import dev.openfeature.sdk.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixpanelProvider implements FeatureProvider {

    private final BaseFlagsProvider<?> flagsProvider;

    public MixpanelProvider(BaseFlagsProvider<?> flagsProvider) {
        this.flagsProvider = flagsProvider;
    }

    @Override
    public Metadata getMetadata() {
        return () -> "mixpanel-provider";
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        return evaluate(key, defaultValue, Boolean.class, ctx);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        return evaluate(key, defaultValue, String.class, ctx);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        return evaluate(key, defaultValue, Integer.class, ctx);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        return evaluate(key, defaultValue, Double.class, ctx);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        ProviderEvaluation<Value> notReadyResult = checkNotReady(defaultValue);
        if (notReadyResult != null) {
            return notReadyResult;
        }

        SelectedVariant<Object> fallback = new SelectedVariant<>(null);

        SelectedVariant<Object> result;
        try {
            result = flagsProvider.getVariant(key, fallback, convertContext(ctx), true);
        } catch (Exception e) {
            return ProviderEvaluation.<Value>builder()
                    .value(defaultValue)
                    .reason("ERROR")
                    .errorCode(ErrorCode.GENERAL)
                    .errorMessage(e.getMessage())
                    .build();
        }

        if (result.isFallback()) {
            return ProviderEvaluation.<Value>builder()
                    .value(defaultValue)
                    .reason("ERROR")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage("Flag not found: " + key)
                    .build();
        }

        Value value = objectToValue(result.getVariantValue());
        return ProviderEvaluation.<Value>builder()
                .value(value)
                .variant(result.getVariantKey())
                .reason("STATIC")
                .build();
    }

    @Override
    public void shutdown() {
        // No-op
    }

    private <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, Class<T> expectedType, EvaluationContext ctx) {
        ProviderEvaluation<T> notReadyResult = checkNotReady(defaultValue);
        if (notReadyResult != null) {
            return notReadyResult;
        }

        SelectedVariant<Object> fallback = new SelectedVariant<>(null);

        SelectedVariant<Object> result;
        try {
            result = flagsProvider.getVariant(key, fallback, convertContext(ctx), true);
        } catch (Exception e) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason("ERROR")
                    .errorCode(ErrorCode.GENERAL)
                    .errorMessage(e.getMessage())
                    .build();
        }

        if (result.isFallback()) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason("ERROR")
                    .errorCode(ErrorCode.FLAG_NOT_FOUND)
                    .errorMessage("Flag not found: " + key)
                    .build();
        }

        T typedValue = coerce(result.getVariantValue(), expectedType);
        if (typedValue == null) {
            return ProviderEvaluation.<T>builder()
                    .value(defaultValue)
                    .reason("ERROR")
                    .errorCode(ErrorCode.TYPE_MISMATCH)
                    .errorMessage("Expected " + expectedType.getSimpleName() + " but got " + result.getVariantValue().getClass().getSimpleName())
                    .build();
        }

        return ProviderEvaluation.<T>builder()
                .value(typedValue)
                .variant(result.getVariantKey())
                .reason("STATIC")
                .build();
    }

    private <T> ProviderEvaluation<T> checkNotReady(T defaultValue) {
        if (flagsProvider instanceof LocalFlagsProvider) {
            LocalFlagsProvider localProvider = (LocalFlagsProvider) flagsProvider;
            if (!localProvider.areFlagsReady()) {
                return ProviderEvaluation.<T>builder()
                        .value(defaultValue)
                        .reason("ERROR")
                        .errorCode(ErrorCode.PROVIDER_NOT_READY)
                        .errorMessage("Provider not ready")
                        .build();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T coerce(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        if (targetType == Integer.class && value instanceof Number) {
            long longVal = ((Number) value).longValue();
            if (longVal < Integer.MIN_VALUE || longVal > Integer.MAX_VALUE) {
                return null;
            }
            return (T) Integer.valueOf((int) longVal);
        }
        if (targetType == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        return null;
    }

    static Map<String, Object> convertContext(EvaluationContext ctx) {
        Map<String, Object> context = new HashMap<>();
        if (ctx == null) {
            return context;
        }
        for (String key : ctx.keySet()) {
            Value val = ctx.getValue(key);
            context.put(key, unwrapValue(val));
        }
        return context;
    }

    private static Object unwrapValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            double d = value.asDouble();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                    return (int) d;
                }
                if (d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                    return (long) d;
                }
            }
            return d;
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isList()) {
            List<Value> list = value.asList();
            Object[] arr = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = unwrapValue(list.get(i));
            }
            return java.util.Arrays.asList(arr);
        }
        if (value.isStructure()) {
            Map<String, Value> struct = value.asStructure().asMap();
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<String, Value> entry : struct.entrySet()) {
                map.put(entry.getKey(), unwrapValue(entry.getValue()));
            }
            return map;
        }
        return value.asObject();
    }

    private static Value objectToValue(Object obj) {
        if (obj == null) {
            return new Value();
        }
        if (obj instanceof Boolean) {
            return new Value((Boolean) obj);
        }
        if (obj instanceof Integer) {
            return new Value((Integer) obj);
        }
        if (obj instanceof Long) {
            long l = (Long) obj;
            if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                return new Value((int) l);
            }
            return new Value((double) l);
        }
        if (obj instanceof Double) {
            return new Value((Double) obj);
        }
        if (obj instanceof Float) {
            return new Value(((Float) obj).doubleValue());
        }
        if (obj instanceof String) {
            return new Value((String) obj);
        }
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            Map<String, Value> structure = new HashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                structure.put(entry.getKey(), objectToValue(entry.getValue()));
            }
            return new Value(new ImmutableStructure(structure));
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            java.util.ArrayList<Value> values = new java.util.ArrayList<>();
            for (Object item : list) {
                values.add(objectToValue(item));
            }
            return new Value(values);
        }
        return new Value(obj.toString());
    }
}
