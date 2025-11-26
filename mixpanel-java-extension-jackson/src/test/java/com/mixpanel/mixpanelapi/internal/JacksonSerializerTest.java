package com.mixpanel.mixpanelapi.internal;

import junit.framework.TestCase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JacksonSerializerTest extends TestCase {

    public void testJacksonMatchesOrgJsonEmptyList() throws Exception {
        JsonSerializer jacksonSerializer = new JacksonSerializer();
        JsonSerializer orgSerializer = new OrgJsonSerializer();

        List<JSONObject> messages = new ArrayList<>();
        String jacksonResult = jacksonSerializer.serializeArray(messages);
        String orgResult = orgSerializer.serializeArray(messages);

        assertEquals("[]", jacksonResult);
        JSONAssert.assertEquals(orgResult, jacksonResult, JSONCompareMode.STRICT);
    }

    public void testJacksonMatchesOrgJsonSingleMessage() throws Exception {
        JsonSerializer jacksonSerializer = new JacksonSerializer();
        JsonSerializer orgSerializer = new OrgJsonSerializer();

        JSONObject message = new JSONObject();
        message.put("event", "test_event");
        message.put("value", 123);
        List<JSONObject> messages = Arrays.asList(message);

        String jacksonResult = jacksonSerializer.serializeArray(messages);
        String orgResult = orgSerializer.serializeArray(messages);

        jacksonResult = jacksonSerializer.serializeArray(messages);
        JSONArray array = new JSONArray(jacksonResult);
        assertEquals(1, array.length());
        JSONObject parsed = array.getJSONObject(0);
        assertEquals("test_event", parsed.getString("event"));
        assertEquals(123, parsed.getInt("value"));
        JSONAssert.assertEquals(orgResult, jacksonResult, JSONCompareMode.STRICT);
    }

    public void testJacksonMatchesOrgJsonComplexObject() throws Exception {
        JsonSerializer jacksonSerializer = new JacksonSerializer();
        JsonSerializer orgSerializer = new OrgJsonSerializer();

        JSONObject message = new JSONObject();
        message.put("event", "complex_event");
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
        
        String jacksonResult = jacksonSerializer.serializeArray(messages);
        String orgResult = orgSerializer.serializeArray(messages);

        JSONArray parsedArray = new JSONArray(jacksonResult);
        JSONObject parsed = parsedArray.getJSONObject(0);

        assertEquals("complex_event", parsed.getString("event"));
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
        // Verify both serializers produce equivalent JSON
        JSONAssert.assertEquals(orgResult, jacksonResult, JSONCompareMode.STRICT);
    }

    public void testJacksonMatchesOrgJsonMultipleMessages() throws Exception {
        JsonSerializer jacksonSerializer = new JacksonSerializer();
        JsonSerializer orgSerializer = new OrgJsonSerializer();

        List<JSONObject> messages = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            JSONObject message = new JSONObject();
            message.put("event", "event_" + i);
            message.put("index", i);
            message.put("timestamp", System.currentTimeMillis());
            message.put("properties", new JSONObject()
                .put("user_id", "user_" + i)
                .put("amount", i * 10.5));
            messages.add(message);
        }

        String jacksonResult = jacksonSerializer.serializeArray(messages);
        String orgResult = orgSerializer.serializeArray(messages);
        
        // Verify both serializers produce equivalent JSON
        JSONAssert.assertEquals(orgResult, jacksonResult, JSONCompareMode.STRICT);
    }

    public void testLargeBatchSerialization() throws Exception {
        // Test with a large batch to verify performance and that output matches OrgJson
        JsonSerializer jacksonSerializer = new JacksonSerializer();
        JsonSerializer orgSerializer = new OrgJsonSerializer();
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

        long jacksonStart = System.currentTimeMillis();
        String jacksonResult = jacksonSerializer.serializeArray(messages);
        long jacksonEnd = System.currentTimeMillis();

        long orgStart = System.currentTimeMillis();
        String orgResult = orgSerializer.serializeArray(messages);
        long orgEnd = System.currentTimeMillis();

        // Verify both produce equivalent JSON
        JSONAssert.assertEquals(orgResult, jacksonResult, JSONCompareMode.STRICT);

        // Parse to verify correctness
        JSONArray array = new JSONArray(jacksonResult);
        assertEquals(2000, array.length());
        assertEquals("batch_event", array.getJSONObject(0).getString("event"));
        assertEquals(0, array.getJSONObject(0).getJSONObject("properties").getInt("index"));
        assertEquals(1999, array.getJSONObject(1999).getJSONObject("properties").getInt("index"));

        // Log serialization times for comparison
        long jacksonTime = jacksonEnd - jacksonStart;
        long orgTime = orgEnd - orgStart;
        System.out.println("Jackson serialized 2000 messages in " + jacksonTime + "ms");
        System.out.println("OrgJson serialized 2000 messages in " + orgTime + "ms");
        System.out.println("Performance improvement: " + String.format("%.2fx", (double) orgTime / jacksonTime));
    }
}
