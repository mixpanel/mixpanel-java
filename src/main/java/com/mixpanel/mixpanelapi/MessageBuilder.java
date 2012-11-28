package com.mixpanel.mixpanelapi;

import java.util.Map;

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

    public JSONObject event(String distinctId, String eventName, JSONObject properties) {
        long time = System.currentTimeMillis() / 1000;

        // Nothing below should EVER throw a JSONException.
        try {
            JSONObject dataObj = new JSONObject();
            dataObj.put("event", eventName);

            JSONObject propertiesObj = new JSONObject(properties.toString());
            if (! propertiesObj.has("token")) propertiesObj.put("token", mToken);
            if (! propertiesObj.has("time")) propertiesObj.put("time", time);

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

    public JSONObject set(String distinctId, JSONObject properties) {
        return stdPeopleMessage(distinctId, "$set", properties);
    }

    public JSONObject increment(String distinctId, Map<String, Long> properties) {
        JSONObject jsonProperties = new JSONObject(properties);
        return stdPeopleMessage(distinctId, "$add", jsonProperties);
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
}