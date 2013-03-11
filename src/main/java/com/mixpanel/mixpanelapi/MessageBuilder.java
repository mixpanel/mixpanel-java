package com.mixpanel.mixpanelapi;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class writes JSONObjects of a form appropriate to send as Mixpanel events and
 * updates to people analytics profiles via the MixpanelAPI class.
 *
 * Instances of this class can be instantiated separately from instances of MixpanelAPI,
 * and the resulting messages are suitable for enqueuing or sending over a local network.
 */
public class MessageBuilder {
    public MessageBuilder(String token) {
        mToken = token;
    }

    /***
     * Creates a message tracking an event, for consumption by MixpanelAPI
     * See:
     *
     *    http://blog.mixpanel.com/2012/09/12/best-practices-updated/
     *
     * for a detailed discussion of event names, distinct ids, event properties, and how to use them
     * to get the most out of your metrics.
     *
     * @param distinctId a string uniquely identifying the individual cause associated with this event
     *           (for example, the user id of a signing-in user, or the hostname of a server)
     * @param eventName a human readable name for the event, for example "Purchase", or "Threw Exception"
     * @param properties a JSONObject associating properties with the event. These are useful
     *           for reporting and segmentation of events. It is often useful not only to include
     *           properties of the event itself (for example { 'Item Purchased' : 'Hat' } or
     *           { 'ExceptionType' : 'OutOfMemory' }), but also properties associated with the
     *           identified user (for example { 'MemberSince' : '2012-01-10' } or { 'TotalMemory' : '10TB' })
     */
    public JSONObject event(String distinctId, String eventName, JSONObject properties) {
        long time = System.currentTimeMillis() / 1000;

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
     * Sets a People Analytics property on the profile associated with
     * the given distinctId. When sent, this message will overwrite any
     * existing values for the given properties. So, to set some properties
     * on user 12345, one might call:
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
     * @param distinctId a string uniquely identifying the people analytics profile to change,
     *           for example, a user id of an app, or the hostname of a server. If no profile
     *           exists for the given id, a new one will be created.
     * @param properties a collection of properties to set on the associated profile. Each key
     *            in the properties argument will be updated on on the people profile.
     */
    public JSONObject set(String distinctId, JSONObject properties) {
        return stdPeopleMessage(distinctId, "$set", properties);
    }

    /**
     * For each key and value in the properties argument, adds that amount
     * to the associated property in the People Analytics profile with the given distinct id.
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
     */
    public JSONObject increment(String distinctId, Map<String, Long> properties) {
        JSONObject jsonProperties = new JSONObject(properties);
        return stdPeopleMessage(distinctId, "$add", jsonProperties);
    }

    /**
     * For each key and value in the properties argument, attempts to append
     * that value to a list associated with the key in the identified People Analytics profile.
     */
    public JSONObject append(String distinctId, JSONObject properties) {
        return stdPeopleMessage(distinctId, "$append", properties);
    }

    /**
     * Tracks revenue associated with the given distinctId.
     *
     * @param distinctId an identifier associated with a People Analytics profile
     * @param amount a double revenue amount. Positive amounts represent income for your business.
     * @param properties can be null. If provided, a set of properties to associate with
     *           the individual transaction.
     */
    public JSONObject trackCharge(String distinctId, double amount, JSONObject properties) {
        JSONObject transactionValue = new JSONObject();
        JSONObject appendProperties = new JSONObject();
        try {
            transactionValue.put("$amount", amount);
            transactionValue.put("$time", ENGAGE_DATE_FORMAT.format(new Date()));

            if (null != properties) {
                for (Iterator<?> iter = properties.keys(); iter.hasNext();) {
                    String key = (String) iter.next();
                    transactionValue.put(key, transactionValue.get(key));
                }
            }

            appendProperties.put("$transactions", transactionValue);

            return this.append(distinctId, appendProperties);
        } catch (JSONException e) {
            throw new RuntimeException("Cannot create trackCharge message", e);
        }
    }

    private JSONObject stdPeopleMessage(String distinctId, String actionType, JSONObject properties) {
        // Nothing below should EVER throw a JSONException.
        try {
            JSONObject dataObj = new JSONObject();
            dataObj.put(actionType, properties);
            dataObj.put("$token", mToken);
            dataObj.put("$distinct_id", distinctId);
            dataObj.put("$time", System.currentTimeMillis());

            JSONObject envelope = new JSONObject();
            envelope.put("envelope_version", 1);
            envelope.put("message_type", "people");
            envelope.put("message", dataObj);
            return envelope;
        } catch (JSONException e) {
            throw new RuntimeException("Can't construct a Mixpanel message", e);
        }
    }

    private final String mToken;

    private static final DateFormat ENGAGE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    static {
        ENGAGE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
}
