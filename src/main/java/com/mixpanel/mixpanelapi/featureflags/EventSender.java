package com.mixpanel.mixpanelapi.featureflags;

import org.json.JSONObject;

/**
 * Interface for sending events to an analytics backend.
 * <p>
 * Implementations are responsible for constructing the event payload
 * and delivering it to the appropriate destination.
 * </p>
 */
@FunctionalInterface
public interface EventSender {
    /**
     * Sends an event with the specified properties.
     *
     * @param distinctId the user's distinct ID
     * @param eventName the name of the event (e.g., "$experiment_started")
     * @param properties the event properties as a JSONObject
     */
    void sendEvent(String distinctId, String eventName, JSONObject properties);
}
