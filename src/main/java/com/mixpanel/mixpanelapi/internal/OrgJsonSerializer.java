package com.mixpanel.mixpanelapi.internal;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/**
 * JSON serialization implementation using org.json library.
 * This is the default implementation that maintains backward compatibility.
 *
 * @since 1.6.0
 */
public class OrgJsonSerializer implements JsonSerializer {

    @Override
    public String serializeArray(List<JSONObject> messages) {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }

        JSONArray array = new JSONArray();
        for (JSONObject message : messages) {
            array.put(message);
        }
        return array.toString();
    }
}