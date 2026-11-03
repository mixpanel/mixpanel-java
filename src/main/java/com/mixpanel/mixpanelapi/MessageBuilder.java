package com.mixpanel.mixpanelapi;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class writes JSONObjects of a form appropriate to send as Mixpanel events and
 * updates to profiles via the MixpanelAPI class.
 *
 * Instances of this class can be instantiated separately from instances of MixpanelAPI,
 * and the resulting messages are suitable for enqueuing or sending over a local network.
 */
public class MessageBuilder {

    private static final String ENGAGE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String USER_ID_GROUP_KEY = "$user_id";
    // /groups has no $add, $append or $merge
    private static final Set<String> GROUP_OPERATIONS = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList("$set", "$set_once", "$unset", "$union", "$remove", "$delete")));

    private final String mToken;
    private final boolean mHierarchicalGroups;
    private final String mCompanyId;

    public MessageBuilder(String token) {
        this(token, false);
    }

    /**
     * Creates a MessageBuilder for a project that may use hierarchical groups.
     *
     * When hierarchicalGroups is true, Mixpanel builds the distinct id from {@code $user_id} and
     * {@code $company_id}. Prefer the {@link MixpanelIdentity} overloads, such as
     * {@link #event(MixpanelIdentity, String, JSONObject)}, which send those properties.
     * {@link #event(String, String, JSONObject)} still works, and Mixpanel accepts a distinct id
     * it built itself, but a distinct id that is not in that form names a different user that
     * nothing reconciles later. See {@link MixpanelIdentity.Builder#distinctId(String)}.
     *
     * User profiles are group profiles keyed by {@code $user_id}, so they support only what
     * {@code /groups} supports. {@link #increment}, {@link #append} and {@link #trackCharge} have
     * no equivalent there and throw {@link UnsupportedOperationException}; set, setOnce, unset,
     * union, remove and delete work, and {@link #set(MixpanelIdentity, JSONObject)} and its
     * siblings address a profile without needing a distinct id. No user profile update goes to
     * {@code /engage}: the distinct id overloads, such as {@link #set(String, JSONObject)}, accept
     * only a distinct id of the form {@code $company:{company}|$user:{user}}, send it to
     * {@code /groups} as that user's profile within the company, and throw
     * {@link IllegalArgumentException} for any other distinct id. Group profile messages require a
     * known company, either through {@link #forCompany(String)} or a {@code $company_id} modifier.
     *
     * @param token the Mixpanel project token
     * @param hierarchicalGroups true if the project uses hierarchical groups
     */
    public MessageBuilder(String token, boolean hierarchicalGroups) {
        this(token, hierarchicalGroups, null);
    }

    private MessageBuilder(String token, boolean hierarchicalGroups, String companyId) {
        mToken = token;
        mHierarchicalGroups = hierarchicalGroups;
        mCompanyId = companyId;
    }

    /**
     * Returns a MessageBuilder that adds {@code $company_id} to every group profile
     * message it builds. Hierarchical group projects require {@code $company_id} on
     * every {@code /groups} write, including the company's own profile and user profiles:
     * <pre>
     * {@code
     *     MessageBuilder acme = messageBuilder.forCompany("acme");
     *     acme.groupSet("$company_id", "acme", companyProperties);
     *     acme.groupSet("$user_id", "user_123", userProperties);
     *     acme.groupSet("department", "finance", departmentProperties);
     * }
     * </pre>
     *
     * @param companyId the id of the top-level company
     * @return a new MessageBuilder bound to the given company
     */
    public MessageBuilder forCompany(String companyId) {
        if (companyId == null || companyId.isEmpty()) {
            throw new IllegalArgumentException("companyId must be non-empty");
        }
        return new MessageBuilder(mToken, mHierarchicalGroups, companyId);
    }

    /***
     * Creates a message tracking an event, for consumption by MixpanelAPI
     * See:
     *
     *    https://help.mixpanel.com/hc/en-us/articles/360000857366-Guide-to-Mixpanel-Basics
     *
     * for a detailed discussion of event names, distinct ids, event properties, and how to use them
     * to get the most out of your metrics.
     *
     * @param distinctId a string uniquely identifying the individual cause associated with this event
     *           (for example, the user id of a signing-in user, or the hostname of a server).
     *           On a hierarchical groups project this must be a distinct id Mixpanel built, of the
     *           form {@code $company:{company}|$user:{user}}, which ingestion parses back into
     *           {@code $company_id} and {@code $user_id}. Any other value names a separate user.
     * @param eventName a human readable name for the event, for example "Purchase", or "Threw Exception"
     * @param properties a JSONObject associating properties with the event. These are useful
     *           for reporting and segmentation of events. It is often useful not only to include
     *           properties of the event itself (for example { 'Item Purchased' : 'Hat' } or
     *           { 'ExceptionType' : 'OutOfMemory' }), but also properties associated with the
     *           identified user (for example { 'MemberSince' : '2012-01-10' } or { 'TotalMemory' : '10TB' })
     * @return event message for consumption by MixpanelAPI
     */
    public JSONObject event(String distinctId, String eventName, JSONObject properties) {
        long time = System.currentTimeMillis();

        // Nothing below should EVER throw a JSONException.
        try {
            JSONObject dataObj = new JSONObject();
            dataObj.put("event", eventName);

            JSONObject propertiesObj = null;
            if (properties == null) {
                propertiesObj = new JSONObject();
            }
            else {
                propertiesObj = new JSONObject(properties.toString());
            }

            if (! propertiesObj.has("token")) propertiesObj.put("token", mToken);
            if (! propertiesObj.has("time")) propertiesObj.put("time", time);
            if (! propertiesObj.has("mp_lib")) propertiesObj.put("mp_lib", "jdk");

            if (distinctId != null)
                propertiesObj.put("distinct_id", distinctId);

            dataObj.put("properties", propertiesObj);

            JSONObject envelope = new JSONObject();
            envelope.put("envelope_version", 1);
            envelope.put("message_type", "event");
            envelope.put("message", dataObj);
            return envelope;
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct a Mixpanel message", e);
        }
    }

    /**
     * Creates a message tracking an event for the given identity. In hierarchical
     * group mode the message carries {@code $user_id} and {@code $company_id} instead
     * of {@code distinct_id}; Mixpanel constructs the distinct id from them.
     * Identity keys replace any values for the same keys in properties.
     *
     * @param identity who the event belongs to
     * @param eventName a human readable name for the event
     * @param properties a JSONObject associating properties with the event, or null
     * @return event message for consumption by MixpanelAPI
     * @see #event(String, String, JSONObject)
     */
    public JSONObject event(MixpanelIdentity identity, String eventName, JSONObject properties) {
        requireIdentityMatchesProject(identity);
        return event(identity.getDistinctId(), eventName, withIdentity(identity, properties));
    }

    /**
     * Creates a message importing a historical event for the given identity.
     *
     * @param identity who the event belongs to
     * @param eventName a human readable name for the event
     * @param properties a JSONObject associating properties with the event, or null
     * @return import event message for consumption by MixpanelAPI
     * @see #importEvent(String, String, JSONObject)
     * @see #event(MixpanelIdentity, String, JSONObject)
     */
    public JSONObject importEvent(MixpanelIdentity identity, String eventName, JSONObject properties) {
        requireIdentityMatchesProject(identity);
        return importEvent(identity.getDistinctId(), eventName, withIdentity(identity, properties));
    }

    /**
     * Hierarchical groups is a project setting, so a hierarchical identity is only valid on a
     * builder created for a hierarchical groups project. Ingestion accepts the keys on any
     * project but ignores them, so a mismatch would be silently mis-attributed.
     */
    private void requireIdentityMatchesProject(MixpanelIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("identity must be non-null");
        }
        if (identity.isHierarchical() && !mHierarchicalGroups) {
            throw new IllegalArgumentException(identity + " requires a hierarchical groups MessageBuilder: "
                    + "new MessageBuilder(token, true)");
        }
        // The company is part of a hierarchical user's identity, not a one-time association. A
        // $user_id sent without one resolves to a separate user that nothing reconciles later.
        // A device id rescues that case: Mixpanel ignores the user id, keeps the event under the
        // device, and links it to the company-scoped user once a later event carries all three.
        if (mHierarchicalGroups && identity.getUserId() != null && identity.getCompanyId() == null
                && identity.getDeviceId() == null) {
            throw new IllegalArgumentException(identity + " needs a company on a hierarchical groups project. "
                    + "A user id sent without one, and without a device id, names a separate user that cannot "
                    + "be merged afterwards.");
        }
    }

    private static JSONObject withIdentity(MixpanelIdentity identity, JSONObject properties) {
        try {
            JSONObject merged = properties == null ? new JSONObject() : new JSONObject(properties.toString());
            identity.applyTo(merged);
            return merged;
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct a Mixpanel message", e);
        }
    }

    /**
     * Creates a message for importing historical events (events older than 5 days) to Mixpanel via the /import endpoint.
     * This method is similar to event(), but is designed for the import endpoint which requires:
     * - A custom timestamp (defaults to current time if not provided)
     * - An insert_id for deduplication (auto-generated if not provided)
     * - Basic authentication using the project token
     *
     * See:
     *    https://developer.mixpanel.com/reference/import-events
     *
     * @param distinctId a string uniquely identifying the individual cause associated with this event
     * @param eventName a human readable name for the event, for example "Purchase", or "Threw Exception"
     * @param properties a JSONObject associating properties with the event. Optional properties:
     *           - "time": timestamp in milliseconds since epoch (defaults to current time)
     *           - "$insert_id": unique identifier for deduplication (auto-generated if not provided)
     * @return import event message for consumption by MixpanelAPI
     */
    public JSONObject importEvent(String distinctId, String eventName, JSONObject properties) {
        long time = System.currentTimeMillis();
        
        // Nothing below should EVER throw a JSONException.
        try {
            JSONObject dataObj = new JSONObject();
            dataObj.put("event", eventName);

            JSONObject propertiesObj = null;
            if (properties == null) {
                propertiesObj = new JSONObject();
            }
            else {
                propertiesObj = new JSONObject(properties.toString());
            }
            // no need to add $import true property as this is added by the backend for any event imported.
            if (! propertiesObj.has("token")) propertiesObj.put("token", mToken);
            
            // Set default time to current time if not provided
            if (! propertiesObj.has("time")) propertiesObj.put("time", time);
            
            // Generate default $insert_id if not provided (to prevent duplicates)
            // Uses UUID v4 (random) in hex format, matching Python SDK implementation
            if (! propertiesObj.has("$insert_id")) {
                String insertId = UUID.randomUUID().toString().replace("-", "");
                propertiesObj.put("$insert_id", insertId);
            }
            
            if (! propertiesObj.has("mp_lib")) propertiesObj.put("mp_lib", "jdk");

            if (distinctId != null)
                propertiesObj.put("distinct_id", distinctId);

            dataObj.put("properties", propertiesObj);

            JSONObject envelope = new JSONObject();
            envelope.put("envelope_version", 1);
            envelope.put("message_type", "import");
            envelope.put("message", dataObj);
            return envelope;
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct a Mixpanel import message", e);
        }
    }

    /**
     * Sets a property on the profile associated with the given distinctId. When
     * sent, this message will overwrite any existing values for the given
     * properties. So, to set some properties on user 12345, one might call:
     * <pre>
     * {@code
     *     JSONObject userProperties = new JSONObject();
     *     userProperties.put("Company", "Uneeda Medical Supply");
     *     userProperties.put("Easter Eggs", "Hatched");
     *     JSONObject message = messageBuilder.set("12345", userProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the profile.
     * @return user profile set message for consumption by MixpanelAPI
     */
    public JSONObject set(String distinctId, JSONObject properties) {
        return set(distinctId, properties, null);
    }

    /**
     * Sets a property on the profile associated with the given distinctId. When
     * sent, this message will overwrite any existing values for the given
     * properties. So, to set some properties on user 12345, one might call:
     * <pre>
     * {@code
     *     JSONObject userProperties = new JSONObject();
     *     userProperties.put("Company", "Uneeda Medical Supply");
     *     userProperties.put("Easter Eggs", "Hatched");
     *     JSONObject message = messageBuilder.set("12345", userProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the profile
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile set message for consumption by MixpanelAPI
     */
    public JSONObject set(String distinctId, JSONObject properties, JSONObject modifiers) {
        return peopleMessage(distinctId, "$set", properties, modifiers);
    }

    /**
    * Sets a property on the profile associated with the given distinctId,
    * only if that property is not already set on the associated profile. So,
    * to set a new property on on user 12345 if it is not already present, one
    * might call:
    * <pre>
    * {@code
    *     JSONObject userProperties = new JSONObject();
    *     userProperties.put("Date Began", "2014-08-16");
    *
    *     // "Date Began" will not be overwritten, but if it isn't already
    *     // present it will be set when we send this message.
    *     JSONObject message = messageBuilder.setOnce("12345", userProperties);
    *     mixpanelApi.sendMessage(message);
    * }
    * </pre>
    *
    * @param distinctId a string uniquely identifying the profile to change,
    *           for example, a user id of an app, or the hostname of a server. If no profile
    *           exists for the given id, a new one will be created.
    * @param properties a collection of properties to set on the associated profile. Each key
    *            in the properties argument will be updated on on the profile
    * @return user profile setOnce message for consumption by MixpanelAPI
    */
    public JSONObject setOnce(String distinctId, JSONObject properties) {
        return setOnce(distinctId, properties, null);
    }

    /**
     * Sets a property on the profile associated with the given distinctId,
     * only if that property is not already set on the associated profile. So,
     * to set a new property on on user 12345 if it is not already present, one
     * might call:
     * <pre>
     * {@code
     *     JSONObject userProperties = new JSONObject();
     *     userProperties.put("Date Began", "2014-08-16");
     *
     *     // "Date Began" will not be overwritten, but if it isn't already
     *     // present it will be set when we send this message.
     *     JSONObject message = messageBuilder.setOnce("12345", userProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the profile
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile setOnce message for consumption by MixpanelAPI
     */
    public JSONObject setOnce(String distinctId, JSONObject properties, JSONObject modifiers) {
        return peopleMessage(distinctId, "$set_once", properties, modifiers);
    }

    /**
     * Deletes the profile associated with the given distinctId.
     *
     * <pre>
     * {@code
     *     JSONObject message = messageBuilder.delete("12345");
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param distinctId a string uniquely identifying the profile to delete
     * @return user profile delete message for consumption by MixpanelAPI
     */
    public JSONObject delete(String distinctId) {
        return delete(distinctId, null);
    }

    /**
     * Deletes the profile associated with the given distinctId.
     *
     * <pre>
     * {@code
     *     JSONObject message = messageBuilder.delete("12345");
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param distinctId a string uniquely identifying the profile to delete
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile delete message for consumption by MixpanelAPI
     */
    public JSONObject delete(String distinctId, JSONObject modifiers) {
        return peopleMessage(distinctId, "$delete", new JSONObject(), modifiers);
    }

    /**
     * For each key and value in the properties argument, adds that amount
     * to the associated property in the profile with the given distinct id.
     * So, to maintain a login count for user 12345, one might run the following code
     * at every login:
     * <pre>
     * {@code
     *    Map<String, Long> updates = new HashMap<String, Long>();
     *    updates.put('Logins', 1);
     *    JSONObject message = messageBuilder.set("12345", updates);
     *    mixpanelApi.sendMessage(message);
     * }
     * </pre>
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties a collection of properties to change on the associated profile,
     *           each associated with a numeric value.
     * @return user profile increment message for consumption by MixpanelAPI
     */
    public JSONObject increment(String distinctId, Map<String, Long> properties) {
        return increment(distinctId, properties, null);
    }

    /**
     * For each key and value in the properties argument, adds that amount
     * to the associated property in the profile with the given distinct id.
     * So, to maintain a login count for user 12345, one might run the following code
     * at every login:
     * <pre>
     * {@code
     *    Map<String, Long> updates = new HashMap<String, Long>();
     *    updates.put('Logins', 1);
     *    JSONObject message = messageBuilder.set("12345", updates);
     *    mixpanelApi.sendMessage(message);
     * }
     * </pre>
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties a collection of properties to change on the associated profile,
     *           each associated with a numeric value.
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile increment message for consumption by MixpanelAPI
     */
    public JSONObject increment(String distinctId, Map<String, Long> properties, JSONObject modifiers) {
        JSONObject jsonProperties = new JSONObject(properties);
        return peopleMessage(distinctId, "$add", jsonProperties, modifiers);
    }

    /**
     * For each key and value in the properties argument, attempts to append
     * that value to a list associated with the key in the identified profile.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties properties for the append operation
     * @return user profile append message for consumption by MixpanelAPI
     */
    public JSONObject append(String distinctId, JSONObject properties) {
        return append(distinctId, properties, null);
    }

    /**
     * For each key and value in the properties argument, attempts to append
     * that value to a list associated with the key in the identified profile.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties properties for the append operation
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile append message for consumption by MixpanelAPI
     */
    public JSONObject append(String distinctId, JSONObject properties, JSONObject modifiers) {
        return peopleMessage(distinctId, "$append", properties, modifiers);
    }

    /**
     * For each key and value in the properties argument, attempts to remove
     * that value from a list associated with the key in the specified user profile.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties properties for the remove operation
     * @return user profile remove message for consumption by MixpanelAPI
     */
    public JSONObject remove(String distinctId, JSONObject properties) {
        return remove(distinctId, properties, null);
    }

    /**
     * For each key and value in the properties argument, attempts to remove
     * that value from a list associated with the key in the specified user profile.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties properties for the remove operation
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile remove message for consumption by MixpanelAPI
     */
    public JSONObject remove(String distinctId, JSONObject properties, JSONObject modifiers) {
        return peopleMessage(distinctId, "$remove", properties, modifiers);
    }

    /**
     * Merges list-valued properties into a user profile.
     * The list values in the given are merged with the existing list on the user profile,
     * ignoring duplicate list values.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties properties for the union operation
     * @return user profile union message for consumption by MixpanelAPI
     */
    public JSONObject union(String distinctId, Map<String, JSONArray> properties) {
        return union(distinctId, properties, null);
    }

    /**
     * Merges list-valued properties into a user profile.
     * The list values in the given are merged with the existing list on the user profile,
     * ignoring duplicate list values.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties properties for the union operation
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile union message for consumption by MixpanelAPI
     */
    public JSONObject union(String distinctId, Map<String, JSONArray> properties, JSONObject modifiers) {
        JSONObject jsonProperties = new JSONObject(properties);
        return peopleMessage(distinctId, "$union", jsonProperties, modifiers);
    }

    /**
     * Removes the properties named in propertyNames from the profile identified by distinctId.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param propertyNames properties for the unset operation
     * @return user profile unset message for consumption by MixpanelAPI
     */
    public JSONObject unset(String distinctId, Collection<String> propertyNames) {
        return unset(distinctId, propertyNames, null);
    }

    /**
     * Removes the properties named in propertyNames from the profile identified by distinctId.
     * @param distinctId a string uniquely identifying the profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param propertyNames properties for the unset operation
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return user profile unset message for consumption by MixpanelAPI
     */
    public JSONObject unset(String distinctId, Collection<String> propertyNames, JSONObject modifiers) {
        JSONArray propNamesArray = new JSONArray(propertyNames);
        return peopleMessage(distinctId, "$unset", propNamesArray, modifiers);
    }

    /**
     * Sets properties on the user profile for the given identity. For a hierarchical
     * groups identity the update is sent to {@code /groups} as the {@code $user_id}
     * group profile within the identity's company; for a distinct id identity it is
     * the same as {@link #set(String, JSONObject)}.
     *
     * <pre>
     * {@code
     *     JSONObject message = messageBuilder.set(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(),
     *             userProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * Hierarchical groups projects support set, setOnce, unset, union, remove and delete.
     * {@code $add} and {@code $append} have no group profile equivalent, so increment,
     * append and trackCharge have no identity overload.
     *
     * @param identity whose profile to change
     * @param properties a collection of properties to set on the profile
     * @return profile set message for consumption by MixpanelAPI
     *
     * @throws IllegalArgumentException if the identity cannot name a user profile in this project,
     *           for example a company-only identity, or on a hierarchical groups builder a distinct id
     *           not of the form {@code $company:{company}|$user:{user}}
     */
    public JSONObject set(MixpanelIdentity identity, JSONObject properties) {
        return set(identity, properties, null);
    }

    /**
     * @see #set(MixpanelIdentity, JSONObject)
     */
    public JSONObject set(MixpanelIdentity identity, JSONObject properties, JSONObject modifiers) {
        return profileMessage(identity, "$set", properties, modifiers);
    }

    /**
     * Sets properties on the user profile for the given identity only if they are not already set.
     *
     * @see #set(MixpanelIdentity, JSONObject)
     */
    public JSONObject setOnce(MixpanelIdentity identity, JSONObject properties) {
        return setOnce(identity, properties, null);
    }

    /**
     * @see #setOnce(MixpanelIdentity, JSONObject)
     */
    public JSONObject setOnce(MixpanelIdentity identity, JSONObject properties, JSONObject modifiers) {
        return profileMessage(identity, "$set_once", properties, modifiers);
    }

    /**
     * Deletes the user profile for the given identity.
     *
     * @see #set(MixpanelIdentity, JSONObject)
     */
    public JSONObject delete(MixpanelIdentity identity) {
        return delete(identity, null);
    }

    /**
     * @see #delete(MixpanelIdentity)
     */
    public JSONObject delete(MixpanelIdentity identity, JSONObject modifiers) {
        return profileMessage(identity, "$delete", new JSONObject(), modifiers);
    }

    /**
     * Removes values from list properties on the user profile for the given identity.
     *
     * @see #set(MixpanelIdentity, JSONObject)
     */
    public JSONObject remove(MixpanelIdentity identity, JSONObject properties) {
        return remove(identity, properties, null);
    }

    /**
     * @see #remove(MixpanelIdentity, JSONObject)
     */
    public JSONObject remove(MixpanelIdentity identity, JSONObject properties, JSONObject modifiers) {
        return profileMessage(identity, "$remove", properties, modifiers);
    }

    /**
     * Merges values into list properties on the user profile for the given identity,
     * ignoring values already present.
     *
     * @see #set(MixpanelIdentity, JSONObject)
     */
    public JSONObject union(MixpanelIdentity identity, Map<String, JSONArray> properties) {
        return union(identity, properties, null);
    }

    /**
     * @see #union(MixpanelIdentity, Map)
     */
    public JSONObject union(MixpanelIdentity identity, Map<String, JSONArray> properties, JSONObject modifiers) {
        return profileMessage(identity, "$union", new JSONObject(properties), modifiers);
    }

    /**
     * Removes properties from the user profile for the given identity.
     *
     * @see #set(MixpanelIdentity, JSONObject)
     */
    public JSONObject unset(MixpanelIdentity identity, Collection<String> propertyNames) {
        return unset(identity, propertyNames, null);
    }

    /**
     * @see #unset(MixpanelIdentity, Collection)
     */
    public JSONObject unset(MixpanelIdentity identity, Collection<String> propertyNames, JSONObject modifiers) {
        return profileMessage(identity, "$unset", new JSONArray(propertyNames), modifiers);
    }

    private JSONObject profileMessage(MixpanelIdentity identity, String actionType, Object properties,
                                      JSONObject modifiers) {
        requireIdentityMatchesProject(identity);
        if (identity.getDistinctId() != null) {
            // On a hierarchical groups builder peopleMessage parses a distinct id Mixpanel built into
            // its company and user, so this reaches the same /groups profile as the hierarchical form.
            return peopleMessage(identity.getDistinctId(), actionType, properties, modifiers);
        }
        if (!mHierarchicalGroups) {
            throw new IllegalArgumentException(identity + " cannot identify a profile on this project. "
                    + "User profiles are addressed by distinct id unless the project uses hierarchical groups.");
        }
        if (identity.getUserId() == null) {
            if (identity.getCompanyId() == null) {
                throw new IllegalArgumentException(identity + " names no user, so it cannot identify a profile. "
                        + "Device ids identify anonymous events, not profiles.");
            }
            throw new IllegalArgumentException("User profile updates require a user id, and " + identity
                    + " has none. Use groupSet(\"$company_id\", companyId, properties) to update the company's "
                    + "profile.");
        }
        return buildGroupMessage(USER_ID_GROUP_KEY, identity.getUserId(), actionType, properties, modifiers,
                identity.getCompanyId());
    }

    /**
     * Tracks revenue associated with the given distinctId.
     *
     * @param distinctId an identifier associated with a profile
     * @param amount a double revenue amount. Positive amounts represent income for your business.
     * @param properties can be null. If provided, a set of properties to associate with
     *           the individual transaction.
     * @return user profile trackCharge message for consumption by MixpanelAPI
     */
    public JSONObject trackCharge(String distinctId, double amount, JSONObject properties) {
        return trackCharge(distinctId, amount, properties, null);
    }

    /**
     * Tracks revenue associated with the given distinctId.
     *
     * @param distinctId an identifier associated with a profile
     * @param amount a double revenue amount. Positive amounts represent income for your business.
     * @param properties can be null. If provided, a set of properties to associate with
     *           the individual transaction.
     * @param modifiers can be null. If provided, the keys and values in the object will
     *           be merged as modifiers associated with the update message (for example, "$time" or "$ignore_time")
     * @return user profile trackCharge message for consumption by MixpanelAPI
     */
    public JSONObject trackCharge(String distinctId, double amount, JSONObject properties, JSONObject modifiers) {
        JSONObject transactionValue = new JSONObject();
        JSONObject appendProperties = new JSONObject();
        try {
            transactionValue.put("$amount", amount);
            DateFormat dateFormat = new SimpleDateFormat(ENGAGE_DATE_FORMAT);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            transactionValue.put("$time", dateFormat.format(new Date()));

            if (null != properties) {
                for (Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    String key = (String) iter.next();
                    transactionValue.put(key, properties.get(key));
                }
            }

            appendProperties.put("$transactions", transactionValue);

            return this.append(distinctId, appendProperties, modifiers);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot create trackCharge message", e);
        }
    }

    /**
     * Formats a generic user profile message.
     * Use of this method requires familiarity with the underlying Mixpanel HTTP API,
     * and it may be simpler and clearer to use the pre-built functions for setting,
     * incrementing, and appending to properties. Use this method directly only
     * when interacting with experimental APIs, or APIS that the rest of this library
     * does not yet support.
     *
     * The underlying API is documented at https://developer.mixpanel.com/docs/http
     *
     * @param distinctId a string uniquely identifying the individual cause associated with this event
     *           (for example, the user id of a signing-in user, or the hostname of a server).
     *           On a hierarchical groups builder this must be of the form
     *           {@code $company:{company}|$user:{user}}, and the message is a {@code /groups} update of
     *           the {@code $user_id} profile within that company, without {@code $distinct_id}. A
     *           {@code $distinct_id} modifier replaces this argument and is held to the same rule.
     * @param actionType a string associated in the HTTP api with the operation (for example, $set or $add).
     *           On a hierarchical groups builder user profiles are group profiles, so only $set, $set_once,
     *           $unset, $union, $remove and $delete are supported; $add and $append throw
     *           UnsupportedOperationException.
     * @param properties a payload of the operation. Will be converted to JSON, and should be of types
     *           Boolean, Double, Integer, Long, String, JSONArray, JSONObject, the JSONObject.NULL object, or null.
     *           NaN and negative/positive infinity will throw an IllegalArgumentException
     * @param modifiers if provided, the keys and values in the modifiers object will
     *           be merged as modifiers associated with the update message (for example, "$time" or "$ignore_time")
     * @return generic user profile message for consumption by MixpanelAPI
     *
     * @throws IllegalArgumentException if properties is not intelligible as a JSONObject property, or on a
     *           hierarchical groups builder if the distinct id does not name both a company and a user
     * @throws UnsupportedOperationException on a hierarchical groups builder, for $add, $append or any other
     *           operation /groups does not support
     *
     * @see MessageBuilder#set(String distinctId, JSONObject properties)
     * @see MessageBuilder#delete(String distinctId)
     * @see MessageBuilder#append(String distinctId, JSONObject properties, JSONObject modifiers)
     */
    public JSONObject peopleMessage(String distinctId, String actionType, Object properties, JSONObject modifiers) {
        if (mHierarchicalGroups && !GROUP_OPERATIONS.contains(actionType)) {
            // A hierarchical groups user profile is a group profile, so it supports what /groups
            // supports. $add and $append have no equivalent there, and no other call reaches them.
            throw new UnsupportedOperationException(actionType + " is not supported for hierarchical groups "
                    + "projects, whose user profiles are group profiles keyed by $user_id. Supported operations "
                    + "are " + GROUP_OPERATIONS + ", so increment, append and trackCharge have no hierarchical "
                    + "groups equivalent.");
        }
        if (mHierarchicalGroups) {
            return hierarchicalUserProfileMessage(distinctId, actionType, properties, modifiers);
        }
        JSONObject dataObj = new JSONObject();
        if (null == properties) {
            throw new IllegalArgumentException("Cannot send null properties, use JSONObject.NULL instead");
        }

        try {
            dataObj.put(actionType, properties);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Cannot interpret properties as a JSON payload", e);
        }

        // At this point, nothing should ever throw a JSONException
        try {
            dataObj.put("$token", mToken);
            dataObj.put("$distinct_id", distinctId);
            dataObj.put("$time", System.currentTimeMillis());
            if (null != modifiers) {
                final String[] keys = JSONObject.getNames(modifiers);
                if (keys != null) {
                  for(String key : keys) {
                      dataObj.put(key, modifiers.get(key));
                  }
                }
            }
            JSONObject envelope = new JSONObject();
            envelope.put("envelope_version", 1);
            envelope.put("message_type", "people");
            envelope.put("message", dataObj);
            return envelope;
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct a Mixpanel message", e);
        }
    }

    /**
     * A hierarchical groups user profile is the {@code $user_id} group profile within the user's
     * company, so a user profile update never goes to {@code /engage}. The message's distinct id
     * (a {@code $distinct_id} modifier, if given, replaces the argument just as it would in the
     * message) must be one Mixpanel built, which names both the user and the company.
     */
    private JSONObject hierarchicalUserProfileMessage(String distinctId, String actionType, Object properties,
                                                      JSONObject modifiers) {
        JSONObject groupModifiers = modifiers;
        if (modifiers != null && modifiers.has("$distinct_id")) {
            Object override = modifiers.opt("$distinct_id");
            distinctId = override instanceof String ? (String) override : null;
            groupModifiers = new JSONObject(modifiers, JSONObject.getNames(modifiers));
            groupModifiers.remove("$distinct_id");
        }
        MixpanelIdentity identity = MixpanelIdentity.fromCombinedDistinctId(distinctId);
        if (identity == null) {
            throw new IllegalArgumentException("User profile updates on hierarchical groups projects need a user "
                    + "and a company, and the distinct id \"" + distinctId + "\" does not name both. Pass "
                    + "MixpanelIdentity.builder().userId(userId).companyId(companyId).build(), or a distinct id "
                    + "of the form $company:{company}|$user:{user}.");
        }
        return buildGroupMessage(USER_ID_GROUP_KEY, identity.getUserId(), actionType, properties, groupModifiers,
                identity.getCompanyId());
    }

    /**
     * Sets properties on the group profile identified by the given groupKey
     * and groupId, creating the profile if needed. Existing values for the
     * given properties are replaced. Example:
     * <pre>
     * {@code
     *     JSONObject groupProperties = new JSONObject();
     *     groupProperties.put("$name", "Acme Incorporated");
     *     groupProperties.put("Industry", "Manufacturing");
     *     JSONObject message = messageBuilder.groupSet("company", "Acme Inc.", groupProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the profile.
     * @return group profile set message for consumption by MixpanelAPI
     */
    public JSONObject groupSet(String groupKey, String groupId, JSONObject properties) {
        return groupSet(groupKey, groupId, properties, null);
    }

    /**
     * Sets properties on the group profile identified by the given groupKey
     * and groupId, creating the profile if needed. Existing values for the
     * given properties are replaced. Example:
     * <pre>
     * {@code
     *     JSONObject groupProperties = new JSONObject();
     *     groupProperties.put("$name", "Acme Incorporated");
     *     groupProperties.put("Industry", "Manufacturing");
     *     JSONObject message = messageBuilder.groupSet("company", "Acme Inc.", groupProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the profile.
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return group profile set message for consumption by MixpanelAPI
     */
    public JSONObject groupSet(String groupKey, String groupId, JSONObject properties, JSONObject modifiers) {
        return groupMessage(groupKey, groupId, "$set", properties, modifiers);
    }

    /**
     * Sets properties if they do not already exist on the group profile identified by the given groupKey
     * and groupId. Example:
     * <pre>
     * {@code
     *     JSONObject groupProperties = new JSONObject();
     *     groupProperties.put("First Purchase", "Steel");
     *     JSONObject message = messageBuilder.groupSetOnce("company", "Acme Inc.", groupProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the profile.
     * @return group profile setOnce message for consumption by MixpanelAPI
     */
    public JSONObject groupSetOnce(String groupKey, String groupId, JSONObject properties) {
        return groupSetOnce(groupKey, groupId, properties, null);
    }

    /**
     * Sets properties if they do not already exist on the group profile identified by the given groupKey
     * and groupId. Example:
     * <pre>
     * {@code
     *     JSONObject groupProperties = new JSONObject();
     *     groupProperties.put("First Purchase", "Steel");
     *     JSONObject message = messageBuilder.groupSetOnce("company", "Acme Inc.", groupProperties);
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the profile.
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return group profile setOnce message for consumption by MixpanelAPI
     */
    public JSONObject groupSetOnce(String groupKey, String groupId, JSONObject properties, JSONObject modifiers) {
        return groupMessage(groupKey, groupId, "$set_once", properties, modifiers);
    }

    /**
     * Deletes the group profile identified by the given groupKey and groupId.
     *
     * <pre>
     * {@code
     *     JSONObject message = messageBuilder.groupDelete("company", "Acme Inc.");
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @return group profile delete message for consumption by MixpanelAPI
     */
    public JSONObject groupDelete(String groupKey, String groupId) {
        return groupDelete(groupKey, groupId, null);
    }

    /**
     * Deletes the group profile identified by the given groupKey and groupId.
     *
     * <pre>
     * {@code
     *     JSONObject message = messageBuilder.groupDelete("company", "Acme Inc.");
     *     mixpanelApi.sendMessage(message);
     * }
     * </pre>
     *
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return group profile delete message for consumption by MixpanelAPI
     */
    public JSONObject groupDelete(String groupKey, String groupId, JSONObject modifiers) {
        return groupMessage(groupKey, groupId, "$delete", new JSONObject(), modifiers);
    }

    /**
     * For each key and value in the properties argument, attempts to remove
     * that value from a list associated with the key in the specified group profile.
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties properties for the remove operation
     * @return group profile remove message for consumption by MixpanelAPI
     */
    public JSONObject groupRemove(String groupKey, String groupId, JSONObject properties) {
        return groupRemove(groupKey, groupId, properties, null);
    }

    /**
     * For each key and value in the properties argument, attempts to remove
     * that value from a list associated with the key in the specified group profile.
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties properties for the remove operation
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return group profile remove message for consumption by MixpanelAPI
     */
    public JSONObject groupRemove(String groupKey, String groupId, JSONObject properties, JSONObject modifiers) {
        return groupMessage(groupKey, groupId, "$remove", properties, modifiers);
    }

    /**
     * Merges list-valued properties into a group profile.
     * The list values given are merged with the existing list on the group profile,
     * ignoring duplicate list values.
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties properties for the union operation
     * @return group profile union message for consumption by MixpanelAPI
     */
    public JSONObject groupUnion(String groupKey, String groupId, Map<String, JSONArray> properties) {
        return groupUnion(groupKey, groupId, properties, null);
    }

    /**
     * Merges list-valued properties into a group profile.
     * The list values given are merged with the existing list on the group profile,
     * ignoring duplicate list values.
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param properties properties for the union operation
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return group profile union message for consumption by MixpanelAPI
     */
    public JSONObject groupUnion(String groupKey, String groupId, Map<String, JSONArray> properties,
                              JSONObject modifiers) {
        JSONObject jsonProperties = new JSONObject(properties);
        return groupMessage(groupKey, groupId, "$union", jsonProperties, modifiers);
    }

    /**
     * Removes the properties named in propertyNames from the group profile identified by groupKey and groupId.
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param propertyNames properties for the unset operation
     * @return group profile unset message for consumption by MixpanelAPI
     */
    public JSONObject groupUnset(String groupKey, String groupId, Collection<String> propertyNames) {
        return groupUnset(groupKey, groupId, propertyNames, null);
    }

    /**
     * Removes the properties named in propertyNames from the group profile identified by groupKey and groupId.
     * @param groupKey the property that connects event data for Group Analytics
     * @param groupId the identifier for a specific group
     * @param propertyNames properties for the unset operation
     * @param modifiers Modifiers associated with the update message. (for example "$time" or "$ignore_time").
     *            this can be null- if non-null, the keys and values in the modifiers
     *            object will be associated directly with the update.
     * @return group profile unset message for consumption by MixpanelAPI
     */
    public JSONObject groupUnset(String groupKey, String groupId, Collection<String> propertyNames,
                                 JSONObject modifiers) {
        JSONArray propNamesArray = new JSONArray(propertyNames);
        return groupMessage(groupKey, groupId, "$unset", propNamesArray, modifiers);
    }

    /**
     * Formats a generic group profile message.
     * Use of this method requires familiarity with the underlying Mixpanel HTTP API,
     * and it may be simpler and clearer to use the pre-built update methods. Use this
     * method directly only when interacting with experimental APIs, or APIS that the
     * rest of this library does not yet support.
     *
     * The underlying API is documented at https://mixpanel.com/help/reference/http
     *
     * @param groupKey string identifier for the type of group, e.g. 'Company'
     * @param groupId unique string identifier for the group, e.g. 'Acme Inc.'
     * @param actionType a string associated in the HTTP api with the operation (for example, $set or $union).
     *           /groups has no $add, $append or $merge; a hierarchical groups builder rejects them.
     * @param properties a payload of the operation. Will be converted to JSON, and should be of types
     *           Boolean, Double, Integer, Long, String, JSONArray, JSONObject, the JSONObject.NULL object, or null.
     *           NaN and negative/positive infinity will throw an IllegalArgumentException
     * @param modifiers if provided, the keys and values in the modifiers object will
     *           be merged as modifiers associated with the update message (for example, "$time" or "$ignore_time")
     * @return generic group profile message for consumption by MixpanelAPI
     *
     * @throws IllegalArgumentException if properties is not intelligible as a JSONObject property
     * @throws IllegalStateException for hierarchical groups projects when no company is known
     * @throws UnsupportedOperationException on a hierarchical groups builder, for an operation /groups lacks
     *           ($add, $append or $merge)
     *
     * @see MessageBuilder#groupSet(String groupKey, String groupId, JSONObject properties)
     * @see MessageBuilder#groupSetOnce(String groupKey, String groupId, JSONObject properties)
     * @see MessageBuilder#groupRemove(String groupKey, String groupId, JSONObject properties)
     * @see MessageBuilder#groupDelete(String groupKey, String groupId)
     */
    public JSONObject groupMessage(String groupKey, String groupId, String actionType, Object properties,
                                   JSONObject modifiers) {
        // Only the generic entry point can carry an arbitrary actionType; the groupSet family passes constants.
        if (mHierarchicalGroups && !GROUP_OPERATIONS.contains(actionType)) {
            throw new UnsupportedOperationException(actionType + " is not supported for group profiles on "
                    + "hierarchical groups projects. Supported operations are " + GROUP_OPERATIONS + ".");
        }
        return buildGroupMessage(groupKey, groupId, actionType, properties, modifiers, mCompanyId);
    }

    private JSONObject buildGroupMessage(String groupKey, String groupId, String actionType, Object properties,
                                         JSONObject modifiers, String companyId) {
        if (mHierarchicalGroups && companyId == null && (modifiers == null || !modifiers.has("$company_id"))) {
            throw new IllegalStateException("Group profile updates for hierarchical groups projects require a "
                    + "company. Use forCompany(companyId) to build them. Nothing to send for group "
                    + groupKey + "=" + groupId);
        }
        JSONObject dataObj = new JSONObject();
        if (null == properties) {
            throw new IllegalArgumentException("Cannot send null properties, use JSONObject.NULL instead");
        }

        try {
            dataObj.put(actionType, properties);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Cannot interpret properties as a JSON payload", e);
        }

        // At this point, nothing should ever throw a JSONException
        try {
            dataObj.put("$token", mToken);
            dataObj.put("$group_key", groupKey);
            dataObj.put("$group_id", groupId);
            dataObj.put("$time", System.currentTimeMillis());
            if (null != modifiers) {
                final String[] keys = JSONObject.getNames(modifiers);
                if (keys != null) {
                  for(String key : keys) {
                      dataObj.put(key, modifiers.get(key));
                  }
                }
            }
            // $company_id must be top-level, not inside the operation payload
            if (companyId != null) {
                dataObj.put("$company_id", companyId);
            }
            JSONObject envelope = new JSONObject();
            envelope.put("envelope_version", 1);
            envelope.put("message_type", "group");
            envelope.put("message", dataObj);
            return envelope;
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct a Mixpanel message", e);
        }
    }

}
