package com.mixpanel.mixpanelapi.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

/**
 * High-performance JSON serialization implementation using Jackson's streaming API.
 * This implementation provides significant performance improvements for large batches
 * while maintaining compatibility with org.json JSONObjects.
 *
 * @since 1.6.0
 */
public class JacksonSerializer implements JsonSerializer {

    private final JsonFactory jsonFactory;

    public JacksonSerializer() {
        this.jsonFactory = new JsonFactory();
    }

    @Override
    public String serializeArray(List<JSONObject> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            return "[]";
        }

        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = jsonFactory.createGenerator(writer)) {
            writeJsonArray(generator, messages);
        }
        return writer.toString();
    }

    @Override
    public byte[] serializeArrayToBytes(List<JSONObject> messages) throws IOException {
        if (messages == null || messages.isEmpty()) {
            return "[]".getBytes("UTF-8");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (JsonGenerator generator = jsonFactory.createGenerator(outputStream)) {
            writeJsonArray(generator, messages);
        }
        return outputStream.toByteArray();
    }

    @Override
    public String getImplementationName() {
        return "Jackson";
    }

    /**
     * Writes a JSON array of messages using the Jackson streaming API.
     */
    private void writeJsonArray(JsonGenerator generator, List<JSONObject> messages) throws IOException {
        generator.writeStartArray();
        for (JSONObject message : messages) {
            writeJsonObject(generator, message);
        }
        generator.writeEndArray();
    }

    /**
     * Recursively writes a JSONObject using Jackson's streaming API.
     * This avoids the conversion overhead while leveraging Jackson's performance.
     */
    private void writeJsonObject(JsonGenerator generator, JSONObject jsonObject) throws IOException {
        generator.writeStartObject();

        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = jsonObject.opt(key);

            if (value == null || value == JSONObject.NULL) {
                generator.writeNullField(key);
            } else if (value instanceof String) {
                generator.writeStringField(key, (String) value);
            } else if (value instanceof Number) {
                if (value instanceof Integer) {
                    generator.writeNumberField(key, (Integer) value);
                } else if (value instanceof Long) {
                    generator.writeNumberField(key, (Long) value);
                } else if (value instanceof Double) {
                    generator.writeNumberField(key, (Double) value);
                } else if (value instanceof Float) {
                    generator.writeNumberField(key, (Float) value);
                } else {
                    // Handle other Number types
                    generator.writeNumberField(key, ((Number) value).doubleValue());
                }
            } else if (value instanceof Boolean) {
                generator.writeBooleanField(key, (Boolean) value);
            } else if (value instanceof JSONObject) {
                generator.writeFieldName(key);
                writeJsonObject(generator, (JSONObject) value);
            } else if (value instanceof JSONArray) {
                generator.writeFieldName(key);
                writeJsonArray(generator, (JSONArray) value);
            } else {
                // For any other type, use toString()
                generator.writeStringField(key, value.toString());
            }
        }

        generator.writeEndObject();
    }

    /**
     * Recursively writes a JSONArray using Jackson's streaming API.
     */
    private void writeJsonArray(JsonGenerator generator, JSONArray jsonArray) throws IOException {
        generator.writeStartArray();

        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.opt(i);

            if (value == null || value == JSONObject.NULL) {
                generator.writeNull();
            } else if (value instanceof String) {
                generator.writeString((String) value);
            } else if (value instanceof Number) {
                if (value instanceof Integer) {
                    generator.writeNumber((Integer) value);
                } else if (value instanceof Long) {
                    generator.writeNumber((Long) value);
                } else if (value instanceof Double) {
                    generator.writeNumber((Double) value);
                } else if (value instanceof Float) {
                    generator.writeNumber((Float) value);
                } else {
                    generator.writeNumber(((Number) value).doubleValue());
                }
            } else if (value instanceof Boolean) {
                generator.writeBoolean((Boolean) value);
            } else if (value instanceof JSONObject) {
                writeJsonObject(generator, (JSONObject) value);
            } else if (value instanceof JSONArray) {
                writeJsonArray(generator, (JSONArray) value);
            } else {
                generator.writeString(value.toString());
            }
        }

        generator.writeEndArray();
    }
}