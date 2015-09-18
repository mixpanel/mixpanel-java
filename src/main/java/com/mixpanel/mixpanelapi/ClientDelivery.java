package com.mixpanel.mixpanelapi;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A ClientDelivery can be used to send multiple messages to Mixpanel.
 */
public class ClientDelivery {

    private final List<JSONObject> mEventsMessages = new ArrayList<JSONObject>();
    private final List<JSONObject> mPeopleMessages = new ArrayList<JSONObject>();

    /**
     * Adds an individual message to this delivery. Messages to Mixpanel are often more efficient when sent in batches.
     *
     * @param message a JSONObject produced by #{@link MessageBuilder}. Arguments not from MessageBuilder will throw an exception.
     * @throws MixpanelMessageException if the given JSONObject is not formatted appropriately.
     * @see MessageBuilder
     */
    public void addMessage(JSONObject message) {
        if (! isValidMessage(message)) {
            throw new MixpanelMessageException("Given JSONObject was not a valid Mixpanel message", message);
        }
        // ELSE message is valid

        try {
            String messageType = message.getString("message_type");
            JSONObject messageContent = message.getJSONObject("message");

            if (messageType.equals("event")) {
                mEventsMessages.add(messageContent);
            }
            else if (messageType.equals("people")) {
                mPeopleMessages.add(messageContent);
            }
        } catch (JSONException e) {
            throw new RuntimeException("Apparently valid mixpanel message could not be interpreted.", e);
        }
    }

    /**
     * Returns true if the given JSONObject appears to be a valid Mixpanel message, created with #{@link MessageBuilder}.
     * @param message a JSONObject to be tested
     * @return true if the argument appears to be a Mixpanel message
     */
    public boolean isValidMessage(JSONObject message) {
        // See MessageBuilder for how these messages are formatted.
        boolean ret = true;
        try {
            int envelopeVersion = message.getInt("envelope_version");
            if (envelopeVersion > 0) {
                String messageType = message.getString("message_type");
                JSONObject messageContents = message.getJSONObject("message");

                if (messageContents == null) {
                    ret = false;
                }
                else if (!messageType.equals("event") && !messageType.equals("people")) {
                    ret = false;
                }
            }
        } catch (JSONException e) {
            ret = false;
        }

        return ret;
    }

    /* package */ List<JSONObject> getEventsMessages() {
        return mEventsMessages;
    }

    /* package */ List<JSONObject> getPeopleMessages() {
        return mPeopleMessages;
    }

}
