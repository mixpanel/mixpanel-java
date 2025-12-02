package com.mixpanel.mixpanelapi.featureflags.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.*;

import static com.mixpanel.mixpanelapi.featureflags.provider.TestUtils.*;
import static org.junit.Assert.*;

/**
 * Edge cases for both lowercaseLeafNodes() and lowercaseAllNodes().
 */
public class JsonCaseDesensitizerTest {

    // #region lowercaseLeafNodes Tests

    @Test
    public void testLowercaseLeafNodes_Null() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(null);
        assertNull(result);
    }

    @Test
    public void testLowercaseLeafNodes_SimpleString() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes("HELLO");
        assertEquals("hello", result);
    }

    @Test
    public void testLowercaseLeafNodes_MixedCaseString() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes("HeLLo WoRLd");
        assertEquals("hello world", result);
    }

    @Test
    public void testLowercaseLeafNodes_AlreadyLowercaseString() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes("hello");
        assertEquals("hello", result);
    }

    @Test
    public void testLowercaseLeafNodes_EmptyString() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes("");
        assertEquals("", result);
    }

    @Test
    public void testLowercaseLeafNodes_StringWithNumbers() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes("TEST123");
        assertEquals("test123", result);
    }

    @Test
    public void testLowercaseLeafNodes_StringWithSpecialChars() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes("TEST@#$%");
        assertEquals("test@#$%", result);
    }

    @Test
    public void testLowercaseLeafNodes_UnicodeString() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes("CAFÉ");
        assertEquals("café", result);
    }

    @Test
    public void testLowercaseLeafNodes_Integer() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(42);
        assertEquals(42, result);
    }

    @Test
    public void testLowercaseLeafNodes_Long() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(999999999L);
        assertEquals(999999999L, result);
    }

    @Test
    public void testLowercaseLeafNodes_Double() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(3.14159);
        assertEquals(3.14159, result);
    }

    @Test
    public void testLowercaseLeafNodes_Boolean() {
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(true);
        assertEquals(true, result);
    }

    @Test
    public void testLowercaseLeafNodes_EmptyJSONObject() {
        JSONObject input = new JSONObject();
        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONObject);
        assertEquals(0, ((JSONObject) result).length());
    }

    @Test
    public void testLowercaseLeafNodes_JSONObjectWithSingleStringValue() {
        JSONObject input = new JSONObject();
        input.put("key", "VALUE");

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONObject);
        assertEquals("value", ((JSONObject) result).getString("key"));
    }

    @Test
    public void testLowercaseLeafNodes_JSONObjectWithMultipleStringValues() {
        JSONObject input = new JSONObject();
        input.put("name", "JOHN");
        input.put("city", "NEW YORK");
        input.put("country", "USA");

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONObject);
        JSONObject resultObj = (JSONObject) result;
        assertEquals("john", resultObj.getString("name"));
        assertEquals("new york", resultObj.getString("city"));
        assertEquals("usa", resultObj.getString("country"));
    }

    @Test
    public void testLowercaseLeafNodes_JSONObjectWithMixedTypes() {
        JSONObject input = new JSONObject();
        input.put("name", "ALICE");
        input.put("age", 30);
        input.put("score", 95.5);
        input.put("active", true);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONObject);
        JSONObject resultObj = (JSONObject) result;
        assertEquals("alice", resultObj.getString("name"));
        assertEquals(30, resultObj.getInt("age"));
        assertEquals(95.5, resultObj.getDouble("score"), 0.001);
        assertTrue(resultObj.getBoolean("active"));
    }

    @Test
    public void testLowercaseLeafNodes_JSONObjectKeysPreserveCase() {
        JSONObject input = new JSONObject();
        input.put("UserName", "BOB");
        input.put("EMAIL", "bob@example.com");

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONObject);
        JSONObject resultObj = (JSONObject) result;
        // Keys should preserve their case
        assertEquals("bob", resultObj.getString("UserName"));
        assertEquals("bob@example.com", resultObj.getString("EMAIL"));
    }

    @Test
    public void testLowercaseLeafNodes_NestedJSONObject() {
        JSONObject inner = new JSONObject();
        inner.put("street", "MAIN STREET");
        inner.put("zipcode", "12345");

        JSONObject outer = new JSONObject();
        outer.put("name", "ALICE");
        outer.put("address", inner);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(outer);

        assertTrue(result instanceof JSONObject);
        JSONObject resultObj = (JSONObject) result;
        assertEquals("alice", resultObj.getString("name"));

        JSONObject resultAddress = resultObj.getJSONObject("address");
        assertEquals("main street", resultAddress.getString("street"));
        assertEquals("12345", resultAddress.getString("zipcode"));
    }

    @Test
    public void testLowercaseLeafNodes_DeeplyNestedJSONObject() {
        JSONObject level3 = new JSONObject();
        level3.put("value", "DEEP");

        JSONObject level2 = new JSONObject();
        level2.put("level3", level3);

        JSONObject level1 = new JSONObject();
        level1.put("level2", level2);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(level1);

        assertTrue(result instanceof JSONObject);
        JSONObject resultObj = (JSONObject) result;
        String deepValue = resultObj.getJSONObject("level2")
                                      .getJSONObject("level3")
                                      .getString("value");
        assertEquals("deep", deepValue);
    }

    @Test
    public void testLowercaseLeafNodes_EmptyJSONArray() {
        JSONArray input = new JSONArray();

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONArray);
        assertEquals(0, ((JSONArray) result).length());
    }

    @Test
    public void testLowercaseLeafNodes_JSONArrayWithStrings() {
        JSONArray input = new JSONArray();
        input.put("ALPHA");
        input.put("BETA");
        input.put("GAMMA");

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONArray);
        JSONArray resultArray = (JSONArray) result;
        assertEquals(3, resultArray.length());
        assertEquals("alpha", resultArray.getString(0));
        assertEquals("beta", resultArray.getString(1));
        assertEquals("gamma", resultArray.getString(2));
    }

    @Test
    public void testLowercaseLeafNodes_JSONArrayWithMixedTypes() {
        JSONArray input = new JSONArray();
        input.put("STRING");
        input.put(42);
        input.put(3.14);
        input.put(true);
        input.put(false);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONArray);
        JSONArray resultArray = (JSONArray) result;
        assertEquals("string", resultArray.getString(0));
        assertEquals(42, resultArray.getInt(1));
        assertEquals(3.14, resultArray.getDouble(2), 0.001);
        assertTrue(resultArray.getBoolean(3));
        assertFalse(resultArray.getBoolean(4));
    }

    @Test
    public void testLowercaseLeafNodes_JSONArrayWithNestedArrays() {
        JSONArray inner = new JSONArray();
        inner.put("INNER1");
        inner.put("INNER2");

        JSONArray outer = new JSONArray();
        outer.put("OUTER");
        outer.put(inner);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(outer);

        assertTrue(result instanceof JSONArray);
        JSONArray resultArray = (JSONArray) result;
        assertEquals("outer", resultArray.getString(0));

        JSONArray resultInner = resultArray.getJSONArray(1);
        assertEquals("inner1", resultInner.getString(0));
        assertEquals("inner2", resultInner.getString(1));
    }

    @Test
    public void testLowercaseLeafNodes_JSONArrayWithObjects() {
        JSONObject obj1 = new JSONObject();
        obj1.put("name", "ALICE");

        JSONObject obj2 = new JSONObject();
        obj2.put("name", "BOB");

        JSONArray input = new JSONArray();
        input.put(obj1);
        input.put(obj2);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONArray);
        JSONArray resultArray = (JSONArray) result;
        assertEquals("alice", resultArray.getJSONObject(0).getString("name"));
        assertEquals("bob", resultArray.getJSONObject(1).getString("name"));
    }

    @Test
    public void testLowercaseLeafNodes_ComplexNestedStructure() {
        // Create: {"users": [{"name": "ALICE", "tags": ["ADMIN", "SUPER"]}]}
        JSONArray tags = new JSONArray();
        tags.put("ADMIN");
        tags.put("SUPER");

        JSONObject user = new JSONObject();
        user.put("name", "ALICE");
        user.put("tags", tags);

        JSONArray users = new JSONArray();
        users.put(user);

        JSONObject root = new JSONObject();
        root.put("users", users);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(root);

        assertTrue(result instanceof JSONObject);
        JSONObject resultObj = (JSONObject) result;
        JSONArray resultUsers = resultObj.getJSONArray("users");
        JSONObject resultUser = resultUsers.getJSONObject(0);
        assertEquals("alice", resultUser.getString("name"));

        JSONArray resultTags = resultUser.getJSONArray("tags");
        assertEquals("admin", resultTags.getString(0));
        assertEquals("super", resultTags.getString(1));
    }

    @Test
    public void testLowercaseLeafNodes_JSONObjectWithNullValue() {
        JSONObject input = new JSONObject();
        input.put("key", JSONObject.NULL);

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONObject);
        assertTrue(((JSONObject) result).isNull("key"));
    }

    @Test
    public void testLowercaseLeafNodes_JSONArrayWithNullValue() {
        JSONArray input = new JSONArray();
        input.put(JSONObject.NULL);
        input.put("STRING");

        Object result = JsonCaseDesensitizer.lowercaseLeafNodes(input);

        assertTrue(result instanceof JSONArray);
        JSONArray resultArray = (JSONArray) result;
        assertTrue(resultArray.isNull(0));
        assertEquals("string", resultArray.getString(1));
    }

    // #endregion

    // #region lowercaseAllNodes Tests

    @Test
    public void testLowercaseAllNodes_Null() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes(null);
        assertNull(result);
    }

    @Test
    public void testLowercaseAllNodes_SimpleString() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes("HELLO");
        assertEquals("hello", result);
    }

    @Test
    public void testLowercaseAllNodes_MixedCaseString() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes("HeLLo WoRLd");
        assertEquals("hello world", result);
    }

    @Test
    public void testLowercaseAllNodes_EmptyString() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes("");
        assertEquals("", result);
    }

    @Test
    public void testLowercaseAllNodes_Integer() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes(42);
        assertEquals(42, result);
    }

    @Test
    public void testLowercaseAllNodes_Double() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes(3.14);
        assertEquals(3.14, result);
    }

    @Test
    public void testLowercaseAllNodes_Boolean() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes(false);
        assertEquals(false, result);
    }

    @Test
    public void testLowercaseAllNodes_EmptyMap() {
        Map<String, Object> input = new HashMap<>();

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof Map);
        assertTrue(((Map<?, ?>) result).isEmpty());
    }

    @Test
    public void testLowercaseAllNodes_MapWithStringValue() {
        Map<String, Object> input = mapOf("KEY", "VALUE");

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("key"));
        assertNull(resultMap.get("KEY"));
    }

    @Test
    public void testLowercaseAllNodes_MapWithMultipleEntries() {
        Map<String, Object> input = new HashMap<>();
        input.put("NAME", "ALICE");
        input.put("CITY", "NEW YORK");
        input.put("COUNTRY", "USA");

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("alice", resultMap.get("name"));
        assertEquals("new york", resultMap.get("city"));
        assertEquals("usa", resultMap.get("country"));
    }

    @Test
    public void testLowercaseAllNodes_MapWithMixedTypes() {
        Map<String, Object> input = new HashMap<>();
        input.put("NAME", "BOB");
        input.put("AGE", 25);
        input.put("SCORE", 88.5);
        input.put("ACTIVE", true);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("bob", resultMap.get("name"));
        assertEquals(25, resultMap.get("age"));
        assertEquals(88.5, resultMap.get("score"));
        assertEquals(true, resultMap.get("active"));
    }

    @Test
    public void testLowercaseAllNodes_MapWithNonStringKeys() {
        Map<Object, Object> input = new HashMap<>();
        input.put(123, "VALUE");
        input.put("STRING_KEY", "DATA");

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<Object, Object> resultMap = (Map<Object, Object>) result;
        assertEquals("value", resultMap.get(123)); // Non-string key preserved
        assertEquals("data", resultMap.get("string_key")); // String key lowercased
    }

    @Test
    public void testLowercaseAllNodes_NestedMap() {
        Map<String, Object> inner = mapOf("STREET", "MAIN STREET");
        Map<String, Object> outer = mapOf("NAME", "ALICE", "ADDRESS", inner);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(outer);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("alice", resultMap.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultAddress = (Map<String, Object>) resultMap.get("address");
        assertEquals("main street", resultAddress.get("street"));
    }

    @Test
    public void testLowercaseAllNodes_DeeplyNestedMap() {
        Map<String, Object> level3 = mapOf("VALUE", "DEEP");
        Map<String, Object> level2 = mapOf("LEVEL3", level3);
        Map<String, Object> level1 = mapOf("LEVEL2", level2);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(level1);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        @SuppressWarnings("unchecked")
        Map<String, Object> l2 = (Map<String, Object>) resultMap.get("level2");
        @SuppressWarnings("unchecked")
        Map<String, Object> l3 = (Map<String, Object>) l2.get("level3");
        assertEquals("deep", l3.get("value"));
    }

    @Test
    public void testLowercaseAllNodes_EmptyList() {
        List<Object> input = new ArrayList<>();

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof List);
        assertTrue(((List<?>) result).isEmpty());
    }

    @Test
    public void testLowercaseAllNodes_ListWithStrings() {
        List<Object> input = listOf("ALPHA", "BETA", "GAMMA");

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;
        assertEquals(3, resultList.size());
        assertEquals("alpha", resultList.get(0));
        assertEquals("beta", resultList.get(1));
        assertEquals("gamma", resultList.get(2));
    }

    @Test
    public void testLowercaseAllNodes_ListWithMixedTypes() {
        List<Object> input = listOf("STRING", 42, 3.14, true);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;
        assertEquals("string", resultList.get(0));
        assertEquals(42, resultList.get(1));
        assertEquals(3.14, resultList.get(2));
        assertEquals(true, resultList.get(3));
    }

    @Test
    public void testLowercaseAllNodes_ListWithNestedLists() {
        List<Object> inner = listOf("INNER1", "INNER2");
        List<Object> outer = listOf("OUTER", inner);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(outer);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;
        assertEquals("outer", resultList.get(0));

        @SuppressWarnings("unchecked")
        List<Object> resultInner = (List<Object>) resultList.get(1);
        assertEquals("inner1", resultInner.get(0));
        assertEquals("inner2", resultInner.get(1));
    }

    @Test
    public void testLowercaseAllNodes_ListWithMaps() {
        Map<String, Object> map1 = mapOf("NAME", "ALICE");
        Map<String, Object> map2 = mapOf("NAME", "BOB");
        List<Object> input = listOf(map1, map2);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap1 = (Map<String, Object>) resultList.get(0);
        assertEquals("alice", resultMap1.get("name"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap2 = (Map<String, Object>) resultList.get(1);
        assertEquals("bob", resultMap2.get("name"));
    }

    @Test
    public void testLowercaseAllNodes_ComplexNestedStructure() {
        // Create: {"USERS": [{"NAME": "ALICE", "TAGS": ["ADMIN", "SUPER"]}]}
        List<Object> tags = listOf("ADMIN", "SUPER");
        Map<String, Object> user = new HashMap<>();
        user.put("NAME", "ALICE");
        user.put("TAGS", tags);

        List<Object> users = listOf(user);
        Map<String, Object> root = mapOf("USERS", users);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(root);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        @SuppressWarnings("unchecked")
        List<Object> resultUsers = (List<Object>) resultMap.get("users");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultUser = (Map<String, Object>) resultUsers.get(0);
        assertEquals("alice", resultUser.get("name"));

        @SuppressWarnings("unchecked")
        List<Object> resultTags = (List<Object>) resultUser.get("tags");
        assertEquals("admin", resultTags.get(0));
        assertEquals("super", resultTags.get(1));
    }

    @Test
    public void testLowercaseAllNodes_MapWithNullValue() {
        Map<String, Object> input = mapOf("KEY", null);

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertNull(resultMap.get("key"));
    }

    @Test
    public void testLowercaseAllNodes_ListWithNullValue() {
        List<Object> input = listOf(null, "STRING");

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;
        assertNull(resultList.get(0));
        assertEquals("string", resultList.get(1));
    }

    @Test
    public void testLowercaseAllNodes_SetAsIterable() {
        Set<String> input = new HashSet<>();
        input.add("ALPHA");
        input.add("BETA");

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof List);
        @SuppressWarnings("unchecked")
        List<Object> resultList = (List<Object>) result;
        assertEquals(2, resultList.size());
        assertTrue(resultList.contains("alpha"));
        assertTrue(resultList.contains("beta"));
    }

    @Test
    public void testLowercaseAllNodes_StringWithWhitespace() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes("  TRIM ME  ");
        assertEquals("  trim me  ", result);
    }

    @Test
    public void testLowercaseAllNodes_ZeroInteger() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes(0);
        assertEquals(0, result);
    }

    @Test
    public void testLowercaseAllNodes_NegativeNumber() {
        Object result = JsonCaseDesensitizer.lowercaseAllNodes(-42);
        assertEquals(-42, result);
    }

    @Test
    public void testLowercaseAllNodes_MapWithEmptyStringKey() {
        Map<String, Object> input = mapOf("", "VALUE");

        Object result = JsonCaseDesensitizer.lowercaseAllNodes(input);

        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get(""));
    }

    // #endregion

    // #region Comparison Tests Between Methods

    @Test
    public void testDifference_LeafVsAll_SimpleMap() {
        Map<String, Object> input = mapOf("KEY", "VALUE");

        // lowercaseLeafNodes doesn't lowercase keys
        Object leafResult = JsonCaseDesensitizer.lowercaseAllNodes(input);
        @SuppressWarnings("unchecked")
        Map<String, Object> leafMap = (Map<String, Object>) leafResult;

        // lowercaseAllNodes lowercases keys
        Object allResult = JsonCaseDesensitizer.lowercaseAllNodes(input);
        @SuppressWarnings("unchecked")
        Map<String, Object> allMap = (Map<String, Object>) allResult;

        // Both lowercase the value
        assertEquals("value", leafMap.get("key"));
        assertEquals("value", allMap.get("key"));
    }

    @Test
    public void testDifference_LeafVsAll_JSONObjectKeys() {
        JSONObject input = new JSONObject();
        input.put("UserName", "ALICE");

        // lowercaseLeafNodes preserves key case
        Object leafResult = JsonCaseDesensitizer.lowercaseLeafNodes(input);
        JSONObject leafObj = (JSONObject) leafResult;

        // Keys are not lowercased in JSONObject version
        assertTrue(leafObj.has("UserName"));
        assertEquals("alice", leafObj.getString("UserName"));
    }

    // #endregion
}
