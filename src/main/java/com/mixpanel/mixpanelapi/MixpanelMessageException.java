package com.mixpanel.mixpanelapi;

import org.json.JSONObject;

/**
 * Thrown when the library detects malformed or invalid Mixpanel messages.
 *
 * Mixpanel messages are represented as JSONObjects, but not all JSONObjects represent valid Mixpanel messages.
 * MixpanelMessageExceptions are thrown when a JSONObject is passed to the Mixpanel library that can't be
 * passed on to the Mixpanel service.
 *
 * This is a runtime exception, since in most cases it is thrown due to errors in your application architecture.
 */
public class MixpanelMessageException extends RuntimeException {

    private static final long serialVersionUID = -6256936727567434262L;

    /* package */ MixpanelMessageException(String message, JSONObject cause) {
        super(message);
        mBadMessage = cause;
    }

    /**
     * Returns the (possibly null) JSONObject that is associated with the message failure.
     * @return
     */
    public JSONObject getBadMessage() {
        return mBadMessage;
    }

    private JSONObject mBadMessage = null;
}
