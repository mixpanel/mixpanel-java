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
        private final Map<String, Object> mCustomProperties = new HashMap<>();

        private Builder() {
        }

        /**
         * Sets the identity keys from a {@link MixpanelIdentity}: {@code distinct_id} for a
         * legacy identity, or {@code $user_id}, {@code $company_id} and {@code device_id}
         * for a hierarchical groups identity.
         */
        public Builder identity(MixpanelIdentity identity) {
            if (identity.getDistinctId() != null) distinctId(identity.getDistinctId());
            if (identity.getUserId() != null) userId(identity.getUserId());
            if (identity.getCompanyId() != null) companyId(identity.getCompanyId());
            if (identity.getDeviceId() != null) deviceId(identity.getDeviceId());
            return this;
        }

        public Builder distinctId(String distinctId) {
            mContext.put("distinct_id", distinctId);
            return this;
        }

        /** Device ids are a valid assignment key and are never namespaced by company. */
        public Builder deviceId(String deviceId) {
            mContext.put("device_id", deviceId);
            return this;
        }

        public Builder userId(String userId) {
            mContext.put("$user_id", userId);
            return this;
        }

        public Builder companyId(String companyId) {
            mContext.put("$company_id", companyId);
            return this;
        }

        /**
         * Sets a group the evaluation is for. Flags whose assignment key is this group
         * key are assigned on its value, namespaced by company when one is set.
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
            return context;
        }
    }
}
