package com.mixpanel.openfeature;

import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;
import com.mixpanel.mixpanelapi.featureflags.provider.BaseFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.LocalFlagsProvider;
import dev.openfeature.sdk.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MixpanelProvider implements FeatureProvider {

    private static final Logger logger = Logger.getLogger(MixpanelProvider.class.getName());
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
        return evaluate(key, defaultValue, ctx,
                result -> objectToValue(result.getVariantValue()),
                "Expected Value");
    }

    @Override
    public void shutdown() {
        try {
            flagsProvider.shutdown();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error shutting down Mixpanel flags provider", e);
        }
    }

    private <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, Class<T> expectedType, EvaluationContext ctx) {
        return evaluate(key, defaultValue, ctx,
                result -> coerce(result.getVariantValue(), expectedType),
                "Expected " + expectedType.getSimpleName());
    }

    private <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx,
                                                java.util.function.Function<SelectedVariant<Object>, T> mapper,
                                                String typeDescription) {
        ProviderEvaluation<T> notReadyResult = checkNotReady(defaultValue);
        if (notReadyResult != null) {
            return notReadyResult;
        }

        SelectedVariant<Object> result;
        try {
            result = fetchVariant(key, ctx);
        } catch (Exception e) {
            return errorResult(defaultValue, ErrorCode.GENERAL, e.getMessage());
        }

        if (result.isFallback()) {
            return errorResult(defaultValue, ErrorCode.FLAG_NOT_FOUND, "Flag not found: " + key);
        }

        T value = mapper.apply(result);
        if (value == null) {
            return errorResult(defaultValue, ErrorCode.TYPE_MISMATCH,
                    typeDescription + " but got " + result.getVariantValue().getClass().getSimpleName());
        }

        return successResult(value, result.getVariantKey());
    }

    private SelectedVariant<Object> fetchVariant(String key, EvaluationContext ctx) {
        SelectedVariant<Object> fallback = new SelectedVariant<>(null);
        return flagsProvider.getVariant(key, fallback, convertContext(ctx), true);
    }

    private <T> ProviderEvaluation<T> errorResult(T defaultValue, ErrorCode errorCode, String errorMessage) {
        return ProviderEvaluation.<T>builder()
                .value(defaultValue)
                .reason("ERROR")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private <T> ProviderEvaluation<T> successResult(T value, String variantKey) {
        return ProviderEvaluation.<T>builder()
                .value(value)
                .variant(variantKey)
                .reason("STATIC")
                .build();
    }

    private <T> ProviderEvaluation<T> checkNotReady(T defaultValue) {
        if (flagsProvider instanceof LocalFlagsProvider) {
            LocalFlagsProvider localProvider = (LocalFlagsProvider) flagsProvider;
            if (!localProvider.areFlagsReady()) {
                return errorResult(defaultValue, ErrorCode.PROVIDER_NOT_READY, "Provider not ready");
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
            if (value instanceof Double || value instanceof Float) {
                double doubleVal = ((Number) value).doubleValue();
                if (doubleVal != Math.floor(doubleVal)) {
                    return null;
                }
            }
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
        String targetingKey = ctx.getTargetingKey();
        if (targetingKey != null && !targetingKey.isEmpty()) {
            context.put("targetingKey", targetingKey);
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
            return Arrays.asList(arr);
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
            ArrayList<Value> values = new ArrayList<>();
            for (Object item : list) {
                values.add(objectToValue(item));
            }
            return new Value(values);
        }
        if (obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;
            ArrayList<Value> values = new ArrayList<>();
            for (Object item : arr) {
                values.add(objectToValue(item));
            }
            return new Value(values);
        }
        return new Value(obj.toString());
    }
}
