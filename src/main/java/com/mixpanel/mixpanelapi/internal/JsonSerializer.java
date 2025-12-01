package com.mixpanel.mixpanelapi.internal;

import org.json.JSONObject;
import java.io.IOException;
import java.util.List;

/**
 * Internal interface for JSON serialization.
 * Provides methods to serialize lists of JSONObjects to various formats.
 * This allows for different implementations (org.json, Jackson) to be used
 * based on performance requirements and available dependencies.
 *
 * @since 1.6.0
 */
public interface JsonSerializer {

    /**
     * Serializes a list of JSONObjects to a JSON array string.
     *
     * @param messages The list of JSONObjects to serialize
     * @return A JSON array string representation
     * @throws IOException if serialization fails
     */
    String serializeArray(List<JSONObject> messages) throws IOException;
}