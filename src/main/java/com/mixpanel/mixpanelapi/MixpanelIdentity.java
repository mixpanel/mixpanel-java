package com.mixpanel.mixpanelapi;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Identifies who an event or profile update belongs to.
 *
 * Mixpanel resolves an identity from whichever of {@code distinct_id}, {@code $user_id},
 * {@code $company_id} and {@code $device_id} an event carries, so this type sets any
 * combination of them and leaves the rest unset.
 *
 * <pre>
 * {@code
 *     MixpanelIdentity.builder().distinctId("user_123").build();               // a plain distinct id
 *     MixpanelIdentity.builder().userId("user_123").deviceId("device_abc").build();  // simplified id merge
 *     MixpanelIdentity.builder().userId("user_123").companyId("acme").build();  // hierarchical groups
 *     MixpanelIdentity.builder().deviceId("device_abc").build();                // anonymous, no user yet
 *     MixpanelIdentity.builder().companyId("acme").build();                     // a company, no user
 * }
 * </pre>
 *
 * Which combinations are valid depends on the project, so {@link MessageBuilder} rather than
 * this type decides. On a hierarchical groups project the company is part of the user's
 * identity: a {@code $user_id} sent without a {@code $company_id} resolves to a different user
 * that nothing reconciles later, so include the company whenever the user has one. Events still
 * send a user id without one as given; user profile updates need the company. A user who
 * belongs to two companies is two users, by design.
 *
 * Instances are immutable.
 */
public final class MixpanelIdentity {

    private final String mDistinctId;
    private final String mUserId;
    private final String mCompanyId;
    private final String mDeviceId;

    private MixpanelIdentity(Builder builder) {
        mDistinctId = builder.mDistinctId;
        mUserId = builder.mUserId;
        mCompanyId = builder.mCompanyId;
        mDeviceId = builder.mDeviceId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String mDistinctId;
        private String mUserId;
        private String mCompanyId;
        private String mDeviceId;

        private Builder() {
        }

        /**
         * A distinct id Mixpanel resolves directly, or one it built itself.
         *
         * On a hierarchical groups project Mixpanel builds the distinct id from the user and
         * company, so a raw distinct id names a different, unrelated user. The two are counted
         * separately, never appear in the same company report, and cannot be merged afterwards:
         * correcting it means re-importing the events. Set one here only when it is one Mixpanel
         * itself built, of the form {@code $company:{company}|$user:{user}}. User profile updates
         * on a hierarchical groups project accept only that form, and send it to {@code /groups}
         * as the user's profile within the company.
         */
        public Builder distinctId(String distinctId) {
            mDistinctId = requireNonEmptyOrNull(distinctId, "distinctId");
            return this;
        }

        /** The user's id. On a hierarchical groups project it only needs to be unique within the company. */
        public Builder userId(String userId) {
            mUserId = requireNonEmptyOrNull(userId, "userId");
            return this;
        }

        /** The id of the top-level company, on a hierarchical groups project. */
        public Builder companyId(String companyId) {
            mCompanyId = requireNonEmptyOrNull(companyId, "companyId");
            return this;
        }

        /**
         * The client's device id. Events carrying one are attributed to the device until a
         * later event names the user, at which point that earlier activity follows the user.
         */
        public Builder deviceId(String deviceId) {
            mDeviceId = requireNonEmptyOrNull(deviceId, "deviceId");
            return this;
        }

        public MixpanelIdentity build() {
            if (mDistinctId == null && mUserId == null && mCompanyId == null && mDeviceId == null) {
                throw new IllegalArgumentException("An identity needs at least one of distinctId, userId, "
                        + "companyId or deviceId");
            }
            return new MixpanelIdentity(this);
        }

        /** Null leaves the value unset; an empty string is a caller error rather than an absent value. */
        private static String requireNonEmptyOrNull(String value, String name) {
            if (value != null && value.isEmpty()) {
                throw new IllegalArgumentException(name + " must be non-empty");
            }
            return value;
        }
    }

    private static final String COMBINED_COMPANY_PREFIX = "$company:";
    private static final String COMBINED_USER_SEPARATOR = "|$user:";

    /**
     * Parses a distinct id Mixpanel built for a hierarchical groups user, of the form
     * {@code $company:{company}|$user:{user}}. The company is the text before the first
     * {@code |$user:}; the user is everything after it.
     *
     * @return an identity with the parsed user and company, or null if the distinct id is not
     *           in that form or either part is empty
     */
    /* package */ static MixpanelIdentity fromCombinedDistinctId(String distinctId) {
        if (distinctId == null || !distinctId.startsWith(COMBINED_COMPANY_PREFIX)) {
            return null;
        }
        int separator = distinctId.indexOf(COMBINED_USER_SEPARATOR, COMBINED_COMPANY_PREFIX.length());
        if (separator < 0) {
            return null;
        }
        String companyId = distinctId.substring(COMBINED_COMPANY_PREFIX.length(), separator);
        String userId = distinctId.substring(separator + COMBINED_USER_SEPARATOR.length());
        if (companyId.isEmpty() || userId.isEmpty()) {
            return null;
        }
        return builder().userId(userId).companyId(companyId).build();
    }

    public String getDistinctId() {
        return mDistinctId;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getCompanyId() {
        return mCompanyId;
    }

    public String getDeviceId() {
        return mDeviceId;
    }

    /**
     * @return true if this identity names a company, which is what sends it down Mixpanel's
     *           hierarchical groups resolution
     */
    public boolean isHierarchical() {
        return mCompanyId != null;
    }

    /**
     * Writes this identity's keys into the given event properties, replacing any
     * caller-supplied values for the same keys.
     */
    /* package */ void applyTo(JSONObject properties) throws JSONException {
        if (mDistinctId != null) properties.put("distinct_id", mDistinctId);
        if (mUserId != null) properties.put("$user_id", mUserId);
        if (mCompanyId != null) properties.put("$company_id", mCompanyId);
        if (mDeviceId != null) properties.put("$device_id", mDeviceId);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("MixpanelIdentity{");
        append(out, "distinctId", mDistinctId);
        append(out, "userId", mUserId);
        append(out, "companyId", mCompanyId);
        append(out, "deviceId", mDeviceId);
        return out.append("}").toString();
    }

    private static void append(StringBuilder out, String name, String value) {
        if (value == null) {
            return;
        }
        if (out.charAt(out.length() - 1) != '{') {
            out.append(", ");
        }
        out.append(name).append("=").append(value);
    }
}
