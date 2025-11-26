package com.mixpanel.mixpanelapi.internal;

import junit.framework.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for JsonSerializer implementations.
 */
public class JsonSerializerTest extends TestCase {

    public void testOrgJsonSerializerEmptyList() throws IOException {
        JsonSerializer serializer = new OrgJsonSerializer();
        List<JSONObject> messages = new ArrayList<>();

        String result = serializer.serializeArray(messages);
        assertEquals("[]", result);

        byte[] bytes = serializer.serializeArrayToBytes(messages);
        assertEquals("[]", new String(bytes, "UTF-8"));
    }

    public void testOrgJsonSerializerSingleMessage() throws IOException {
        JsonSerializer serializer = new OrgJsonSerializer();
        JSONObject message = new JSONObject();
        message.put("event", "test_event");
        message.put("properties", new JSONObject().put("key", "value"));

        List<JSONObject> messages = Arrays.asList(message);
        String result = serializer.serializeArray(messages);

        // Parse result to verify structure
        JSONArray array = new JSONArray(result);
        assertEquals(1, array.length());
        JSONObject parsed = array.getJSONObject(0);
        assertEquals("test_event", parsed.getString("event"));
        assertEquals("value", parsed.getJSONObject("properties").getString("key"));
    }

    public void testOrgJsonSerializerMultipleMessages() throws IOException {
        JsonSerializer serializer = new OrgJsonSerializer();
        List<JSONObject> messages = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            JSONObject message = new JSONObject();
            message.put("event", "event_" + i);
            message.put("value", i);
            messages.add(message);
        }

        String result = serializer.serializeArray(messages);
        JSONArray array = new JSONArray(result);
        assertEquals(5, array.length());

        for (int i = 0; i < 5; i++) {
            JSONObject parsed = array.getJSONObject(i);
            assertEquals("event_" + i, parsed.getString("event"));
            assertEquals(i, parsed.getInt("value"));
        }
    }

    public void testOrgJsonSerializerComplexObject() throws IOException {
        JsonSerializer serializer = new OrgJsonSerializer();

        JSONObject message = new JSONObject();
        message.put("event", "complex_event");
        message.put("null_value", JSONObject.NULL);
        message.put("boolean_value", true);
        message.put("number_value", 42.5);
        message.put("string_value", "test string");

        JSONObject nested = new JSONObject();
        nested.put("nested_key", "nested_value");
        message.put("nested_object", nested);

        JSONArray array = new JSONArray();
        array.put("item1");
        array.put(2);
        array.put(true);
        message.put("array_value", array);

        List<JSONObject> messages = Arrays.asList(message);
        String result = serializer.serializeArray(messages);

        // Verify the result can be parsed back
        JSONArray parsedArray = new JSONArray(result);
        JSONObject parsed = parsedArray.getJSONObject(0);

        assertEquals("complex_event", parsed.getString("event"));
        assertTrue(parsed.isNull("null_value"));
        assertEquals(true, parsed.getBoolean("boolean_value"));
        assertEquals(42.5, parsed.getDouble("number_value"), 0.001);
        assertEquals("test string", parsed.getString("string_value"));
        assertEquals("nested_value", parsed.getJSONObject("nested_object").getString("nested_key"));

        JSONArray parsedInnerArray = parsed.getJSONArray("array_value");
        assertEquals(3, parsedInnerArray.length());
        assertEquals("item1", parsedInnerArray.getString(0));
        assertEquals(2, parsedInnerArray.getInt(1));
        assertEquals(true, parsedInnerArray.getBoolean(2));
    }

    public void testLargeBatchSerialization() throws IOException {
        // Test with a large batch to verify performance doesn't degrade
        JsonSerializer serializer = new OrgJsonSerializer();
        List<JSONObject> messages = new ArrayList<>();

        // Create 2000 messages (max batch size for /import)
        for (int i = 0; i < 2000; i++) {
            JSONObject message = new JSONObject();
            message.put("event", "batch_event");
            message.put("properties", new JSONObject()
                .put("index", i)
                .put("timestamp", System.currentTimeMillis())
                .put("data", "Some test data for message " + i));
            messages.add(message);
        }

        long startTime = System.currentTimeMillis();
        String result = serializer.serializeArray(messages);
        long endTime = System.currentTimeMillis();

        // Verify the result
        assertNotNull(result);
        assertTrue(result.startsWith("["));
        assertTrue(result.endsWith("]"));

        // Parse to verify correctness (just check a few)
        JSONArray array = new JSONArray(result);
        assertEquals(2000, array.length());
        assertEquals("batch_event", array.getJSONObject(0).getString("event"));
        assertEquals(0, array.getJSONObject(0).getJSONObject("properties").getInt("index"));
        assertEquals(1999, array.getJSONObject(1999).getJSONObject("properties").getInt("index"));

        // Log serialization time for reference
        System.out.println("Serialized 2000 messages in " + (endTime - startTime) +
                         "ms using " + serializer.getClass().getName());
    }
}