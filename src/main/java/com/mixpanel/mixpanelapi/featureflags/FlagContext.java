package com.mixpanel.mixpanelapi.featureflags;

import com.mixpanel.mixpanelapi.MixpanelIdentity;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a flag evaluation context with the keys Mixpanel expects, so callers do not
 * hand-write reserved keys such as {@code $company_id}.
 *
 * <pre>
 * {@code
 *     Map<String, Object> context = FlagContext.builder()
 *         .identity(MixpanelIdentity.builder().userId("user_123").companyId("acme").build())
 *         .group("department", "finance")
 *         .customProperty("plan", "enterprise")
 *         .build();
 *     boolean enabled = mixpanel.getLocalFlags().isEnabled("new-checkout", context);
 * }
 * </pre>
 */
public final class FlagContext {

    private FlagContext() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Object> mContext = new HashMap<>();
        // Identity keys are applied last in build(), so they win over group() and
        // customProperty() whatever the call order; among them the later call wins.
        private final Map<String, Object> mIdentity = new HashMap<>();
        private final Map<String, Object> mCustomProperties = new HashMap<>();

        private Builder() {
        }

        /**
         * Sets the identity keys from a {@link MixpanelIdentity}: {@code distinct_id} for a
         * legacy identity, or {@code $user_id}, {@code $company_id} and {@code device_id}
         * for a hierarchical groups identity.
         *
         * <p>Identity keys, whether set here or with {@link #distinctId}, {@link #userId},
         * {@link #companyId} or {@link #deviceId}, win over the same keys set with
         * {@link #group} or {@link #customProperty}, whatever the call order. Among the
         * identity setters, the later call wins.
         */
        public Builder identity(MixpanelIdentity identity) {
            if (identity.getDistinctId() != null) distinctId(identity.getDistinctId());
            if (identity.getUserId() != null) userId(identity.getUserId());
            if (identity.getCompanyId() != null) companyId(identity.getCompanyId());
            if (identity.getDeviceId() != null) deviceId(identity.getDeviceId());
            return this;
        }

        public Builder distinctId(String distinctId) {
            mIdentity.put("distinct_id", distinctId);
            return this;
        }

        /** Sets the device id, used as the assignment key by flags that assign on {@code device_id}. */
        public Builder deviceId(String deviceId) {
            mIdentity.put("device_id", deviceId);
            return this;
        }

        public Builder userId(String userId) {
            mIdentity.put("$user_id", userId);
            return this;
        }

        public Builder companyId(String companyId) {
            mIdentity.put("$company_id", companyId);
            return this;
        }

        /**
         * Sets a group the evaluation is for. Flags whose assignment key is this group
         * key are assigned on the key their server-published {@code context_value_template}
         * builds from the context (for example {@code $company:{$company_id}|department:{department}}),
         * or on the raw group value when the template is absent or cannot be filled.
         */
        public Builder group(String groupKey, Object groupValue) {
            mContext.put(groupKey, groupValue);
            return this;
        }

        /** Adds a property used by runtime targeting rules. */
        public Builder customProperty(String name, Object value) {
            mCustomProperties.put(name, value);
            return this;
        }

        public Map<String, Object> build() {
            Map<String, Object> context = new HashMap<>(mContext);
            if (!mCustomProperties.isEmpty()) {
                context.put("custom_properties", new HashMap<>(mCustomProperties));
            }
            context.putAll(mIdentity);
            return context;
        }
    }
}
