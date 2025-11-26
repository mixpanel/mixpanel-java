package com.mixpanel.mixpanelapi.internal;

import junit.framework.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JacksonSerializerTest extends TestCase {

    public void testJacksonSerializer() throws IOException {
        JsonSerializer serializer = new JacksonSerializer();

        // Test empty list
        List<JSONObject> messages = new ArrayList<>();
        String result = serializer.serializeArray(messages);
        assertEquals("[]", result);

        // Test single message
        JSONObject message = new JSONObject();
        message.put("event", "jackson_test");
        message.put("value", 123);
        messages = Arrays.asList(message);

        result = serializer.serializeArray(messages);
        JSONArray array = new JSONArray(result);
        assertEquals(1, array.length());
        JSONObject parsed = array.getJSONObject(0);
        assertEquals("jackson_test", parsed.getString("event"));
        assertEquals(123, parsed.getInt("value"));

        // Test implementation name
        assertEquals("com.mixpanel.mixpanelapi.internal.JacksonSerializer", serializer.getClass().getName());
    }

    public void testJacksonSerializerComplexObject() throws IOException {
        JsonSerializer serializer = new JacksonSerializer();

        JSONObject message = new JSONObject();
        message.put("event", "complex_jackson_event");
        message.put("null_value", JSONObject.NULL);
        message.put("boolean_value", false);
        message.put("int_value", 42);
        message.put("long_value", 9999999999L);
        message.put("double_value", 3.14159);
        message.put("float_value", 2.5f);
        message.put("string_value", "test with \"quotes\" and special chars: \n\t");

        JSONObject nested = new JSONObject();
        nested.put("level2", new JSONObject().put("level3", "deep value"));
        message.put("nested", nested);

        JSONArray array = new JSONArray();
        array.put("string");
        array.put(100);
        array.put(false);
        array.put(JSONObject.NULL);
        array.put(new JSONObject().put("in_array", true));
        message.put("array", array);

        List<JSONObject> messages = Arrays.asList(message);
        String result = serializer.serializeArray(messages);

        // Verify the result can be parsed back correctly
        JSONArray parsedArray = new JSONArray(result);
        JSONObject parsed = parsedArray.getJSONObject(0);

        assertEquals("complex_jackson_event", parsed.getString("event"));
        assertTrue(parsed.isNull("null_value"));
        assertEquals(false, parsed.getBoolean("boolean_value"));
        assertEquals(42, parsed.getInt("int_value"));
        assertEquals(9999999999L, parsed.getLong("long_value"));
        assertEquals(3.14159, parsed.getDouble("double_value"), 0.00001);
        assertEquals(2.5f, parsed.getFloat("float_value"), 0.01);
        assertEquals("test with \"quotes\" and special chars: \n\t", parsed.getString("string_value"));

        assertEquals("deep value",
                parsed.getJSONObject("nested")
                        .getJSONObject("level2")
                        .getString("level3"));

        JSONArray parsedInnerArray = parsed.getJSONArray("array");
        assertEquals(5, parsedInnerArray.length());
        assertEquals("string", parsedInnerArray.getString(0));
        assertEquals(100, parsedInnerArray.getInt(1));
        assertEquals(false, parsedInnerArray.getBoolean(2));
        assertTrue(parsedInnerArray.isNull(3));
        assertEquals(true, parsedInnerArray.getJSONObject(4).getBoolean("in_array"));
    }

    public void testLargeBatchSerialization() throws IOException {
        // Test with a large batch to verify performance doesn't degrade
        JsonSerializer serializer = new JacksonSerializer();
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
