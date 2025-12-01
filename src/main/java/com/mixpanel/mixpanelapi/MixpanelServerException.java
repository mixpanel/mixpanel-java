package com.mixpanel.mixpanelapi;

import java.io.IOException;
import java.util.List;

import org.json.JSONObject;

/**
 * Thrown when the Mixpanel server refuses to accept a set of messages.
 *
 * This exception can be thrown when messages are too large,
 * event times are too old to accept, the api key is invalid, etc.
 * 
 * The exception provides access to:
 * - The error message describing what went wrong
 * - The list of messages that were rejected
 * - The HTTP status code from the server (if available)
 * - The raw response body from the server (if available)
 */
public class MixpanelServerException extends IOException {

    private static final long serialVersionUID = 8230724556897575457L;

    private final List<JSONObject> mBadDelivery;
    private final int mHttpStatusCode;
    private final String mResponseBody;

    /**
     * Creates a new MixpanelServerException with a message and the rejected delivery.
     *
     * @param message the error message
     * @param badDelivery the list of messages that were rejected
     */
    public MixpanelServerException(String message, List<JSONObject> badDelivery) {
        this(message, badDelivery, 0, null);
    }

    /**
     * Creates a new MixpanelServerException with full error details.
     *
     * @param message the error message
     * @param badDelivery the list of messages that were rejected
     * @param httpStatusCode the HTTP status code from the server (0 if not available)
     * @param responseBody the raw response body from the server (null if not available)
     */
    public MixpanelServerException(String message, List<JSONObject> badDelivery, int httpStatusCode, String responseBody) {
        super(message);
        mBadDelivery = badDelivery;
        mHttpStatusCode = httpStatusCode;
        mResponseBody = responseBody;
    }

    /**
     * @return the list of messages that were rejected by the server
     */
    public List<JSONObject> getBadDeliveryContents() {
        return mBadDelivery;
    }

    /**
     * Returns the HTTP status code from the server response.
     * 
     * Common status codes:
     * - 400: Bad Request (malformed messages or validation errors)
     * - 401: Unauthorized (invalid token)
     * - 413: Payload Too Large
     * - 500: Internal Server Error
     *
     * @return the HTTP status code, or 0 if not available
     */
    public int getHttpStatusCode() {
        return mHttpStatusCode;
    }

    /**
     * Returns the raw response body from the server.
     * This can be useful for debugging server-side validation errors,
     * especially when using strict import mode which returns detailed error information.
     *
     * @return the response body string, or null if not available
     */
    public String getResponseBody() {
        return mResponseBody;
    }

}
