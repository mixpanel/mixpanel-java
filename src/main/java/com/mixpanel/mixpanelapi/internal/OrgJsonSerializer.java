package com.mixpanel.mixpanelapi.internal;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JSON serialization implementation using org.json library.
 * This is the default implementation that maintains backward compatibility.
 *
 * @since 1.6.0
 */
public class OrgJsonSerializer implements JsonSerializer {

    @Override
    public String serializeArray(List<JSONObject> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }

        JSONArray array = new JSONArray();
        for (JSONObject message : messages) {
            array.put(message);
        }
        return array.toString();
    }

    @Override
    public byte[] serializeArrayToBytes(List<JSONObject> messages) throws IOException {
        return serializeArray(messages).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getImplementationName() {
        return "org.json";
    }
}