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

    /**
     * Starts building a flag evaluation context.
     *
     * @return a new builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a flag evaluation context map. */
    public static final class Builder {
        private final Map<String, Object> context = new HashMap<>();
        // Identity keys are applied last in build(), so they win over group() and
        // customProperty() whatever the call order; among them the later call wins.
        private final Map<String, Object> identity = new HashMap<>();
        private final Map<String, Object> customProperties = new HashMap<>();

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
         *
         * @param identity the identity whose set values to copy; unset values are skipped
         * @return this builder
         */
        public Builder identity(MixpanelIdentity identity) {
            if (identity.getDistinctId() != null) distinctId(identity.getDistinctId());
            if (identity.getUserId() != null) userId(identity.getUserId());
            if (identity.getCompanyId() != null) companyId(identity.getCompanyId());
            if (identity.getDeviceId() != null) deviceId(identity.getDeviceId());
            return this;
        }

        /**
         * Sets the {@code distinct_id}, used as the assignment key by flags that assign on it.
         *
         * @param distinctId the distinct id
         * @return this builder
         */
        public Builder distinctId(String distinctId) {
            identity.put("distinct_id", distinctId);
            return this;
        }

        /**
         * Sets the device id, used as the assignment key by flags that assign on {@code device_id}.
         *
         * @param deviceId the device id
         * @return this builder
         */
        public Builder deviceId(String deviceId) {
            identity.put("device_id", deviceId);
            return this;
        }

        /**
         * Sets the {@code $user_id}.
         *
         * @param userId the user's id
         * @return this builder
         */
        public Builder userId(String userId) {
            identity.put("$user_id", userId);
            return this;
        }

        /**
         * Sets the {@code $company_id}.
         *
         * @param companyId the company's id
         * @return this builder
         */
        public Builder companyId(String companyId) {
            identity.put("$company_id", companyId);
            return this;
        }

        /**
         * Sets a group the evaluation is for. Flags whose assignment key is this group
         * key are assigned on the key their server-published {@code context_value_template}
         * builds from the context (for example {@code $company:{$company_id}|department:{department}}),
         * or on the raw group value when the template is absent or cannot be filled.
         *
         * @param groupKey the group key
         * @param groupValue the group's value
         * @return this builder
         */
        public Builder group(String groupKey, Object groupValue) {
            context.put(groupKey, groupValue);
            return this;
        }

        /**
         * Adds a property used by runtime targeting rules.
         *
         * @param name the property name
         * @param value the property value
         * @return this builder
         */
        public Builder customProperty(String name, Object value) {
            customProperties.put(name, value);
            return this;
        }

        /**
         * Builds the context.
         *
         * @return a new map holding the groups, any custom properties under
         *           {@code custom_properties}, and the identity keys
         */
        public Map<String, Object> build() {
            Map<String, Object> context = new HashMap<>(this.context);
            if (!customProperties.isEmpty()) {
                context.put("custom_properties", new HashMap<>(customProperties));
            }
            context.putAll(identity);
            return context;
        }
    }
}
