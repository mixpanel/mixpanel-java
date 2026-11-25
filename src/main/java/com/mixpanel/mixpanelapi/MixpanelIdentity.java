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

    /**
     * Starts building an identity.
     *
     * @return a new builder with nothing set
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link MixpanelIdentity}. Values left unset are omitted from what is sent. */
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
         *
         * @param distinctId the distinct id, or null to leave it unset
         * @return this builder
         * @throws IllegalArgumentException if the id is empty
         */
        public Builder distinctId(String distinctId) {
            mDistinctId = requireNonEmptyOrNull(distinctId, "distinctId");
            return this;
        }

        /**
         * The user's id. On a hierarchical groups project it only needs to be unique within the company.
         *
         * @param userId the user's id, or null to leave it unset
         * @return this builder
         * @throws IllegalArgumentException if the id is empty or only whitespace
         */
        public Builder userId(String userId) {
            mUserId = requireNonBlankOrNull(userId, "userId");
            return this;
        }

        /**
         * The id of the top-level company, on a hierarchical groups project.
         *
         * @param companyId the company's id, or null to leave it unset
         * @return this builder
         * @throws IllegalArgumentException if the id is empty or only whitespace
         */
        public Builder companyId(String companyId) {
            mCompanyId = requireNonBlankOrNull(companyId, "companyId");
            return this;
        }

        /**
         * The client's device id. Events carrying one are attributed to the device until a
         * later event names the user, at which point that earlier activity follows the user.
         *
         * @param deviceId the device id, or null to leave it unset
         * @return this builder
         * @throws IllegalArgumentException if the id is empty
         */
        public Builder deviceId(String deviceId) {
            mDeviceId = requireNonEmptyOrNull(deviceId, "deviceId");
            return this;
        }

        /**
         * Builds the identity.
         *
         * @return an immutable identity with the values set on this builder
         * @throws IllegalArgumentException if none of distinctId, userId, companyId or deviceId
         *           is set
         */
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

        /** As {@link #requireNonEmptyOrNull}, and a whitespace-only id names a junk user or company. */
        private static String requireNonBlankOrNull(String value, String name) {
            requireNonEmptyOrNull(value, name);
            if (value != null && isBlank(value)) {
                throw new IllegalArgumentException(name + " must not be only whitespace");
            }
            return value;
        }
    }

    /** @return true if the string is non-empty and consists only of whitespace */
    /* package */ static boolean isBlank(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                return false;
            }
        }
        return true;
    }

    private static final String COMBINED_COMPANY_PREFIX = "$company:";
    private static final String COMBINED_USER_SEPARATOR = "|$user:";

    /**
     * Parses a distinct id Mixpanel built for a hierarchical groups user, of the form
     * {@code $company:{company}|$user:{user}}. The company is the text before the first
     * {@code |$user:}; the user is everything after it.
     *
     * @return an identity with the parsed user and company, or null if the distinct id is not
     *           in that form
     * @throws IllegalArgumentException if the distinct id is in that form but its company or user
     *           is empty or only whitespace
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
        boolean blankCompany = companyId.isEmpty() || isBlank(companyId);
        if (blankCompany || userId.isEmpty() || isBlank(userId)) {
            throw new IllegalArgumentException("distinct_id " + distinctId + " names a "
                    + (blankCompany ? "company" : "user") + " that is empty or only whitespace. Nothing sent");
        }
        return builder().userId(userId).companyId(companyId).build();
    }

    /** @return the distinct id, or null if unset */
    public String getDistinctId() {
        return mDistinctId;
    }

    /** @return the user's id, or null if unset */
    public String getUserId() {
        return mUserId;
    }

    /** @return the company's id, or null if unset */
    public String getCompanyId() {
        return mCompanyId;
    }

    /** @return the device id, or null if unset */
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
     * Writes the keys this identity sets into the given event properties, replacing any
     * caller-supplied values for those keys. A key this identity leaves unset is left as
     * the caller gave it.
     */
    /* package */ void applyTo(JSONObject properties) throws JSONException {
        putIfSet(properties, "distinct_id", mDistinctId);
        putIfSet(properties, "$user_id", mUserId);
        putIfSet(properties, "$company_id", mCompanyId);
        putIfSet(properties, "$device_id", mDeviceId);
    }

    private static void putIfSet(JSONObject properties, String key, String value) throws JSONException {
        if (value != null) {
            properties.put(key, value);
        }
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
