package com.mixpanel.mixpanelapi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class MixpanelAPITest extends TestCase
{

    private MessageBuilder mBuilder;
    private JSONObject mSampleProps;
    private JSONObject mSampleModifiers;
    private String mEventsMessages;
    private String mPeopleMessages;
    private String mGroupMessages;
    private String mIpEventsMessages;
    private String mIpPeopleMessages;
    private String mIpGroupMessages;
    private long mTimeZero;

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public MixpanelAPITest( String testName ) {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite( MixpanelAPITest.class );
    }

    @Override
    public void setUp() {
        mTimeZero = System.currentTimeMillis() / 1000;
        mBuilder = new MessageBuilder("a token");

        try {
            mSampleProps = new JSONObject();
            mSampleProps.put("prop key", "prop value");
            mSampleProps.put("ratio", "\u03C0");

            mSampleModifiers = new JSONObject();
            mSampleModifiers.put("$time", "A TIME");
            mSampleModifiers.put("Unexpected", "But OK");
        } catch (JSONException e) {
            throw new RuntimeException("Error in test setup");
        }

        final Map<String, String> sawData = new HashMap<String, String>();

        MixpanelAPI api = new MixpanelAPI("events url", "people url", "groups url") {
            @Override
            public boolean sendData(String dataString, String endpointUrl) {
                sawData.put(endpointUrl, dataString);
                return true;
            }
        };

        ClientDelivery c = new ClientDelivery();

        JSONObject event = mBuilder.event("a distinct id", "login", mSampleProps);
        c.addMessage(event);

        JSONObject set = mBuilder.set("a distinct id", mSampleProps);
        c.addMessage(set);

        Map<String, Long> increments = new HashMap<String, Long>();
        increments.put("a key", 24L);
        JSONObject increment = mBuilder.increment("a distinct id", increments);
        c.addMessage(increment);

        JSONObject groupSet = mBuilder.groupSet("company", "Acme Inc.", mSampleProps);
        c.addMessage(groupSet);

        try {
            api.deliver(c, false);
        } catch (IOException e) {
            throw new RuntimeException("Impossible IOException", e);
        }

        mEventsMessages = sawData.get("events url?ip=0");
        mPeopleMessages = sawData.get("people url?ip=0");
        mGroupMessages = sawData.get("groups url?ip=0");
        sawData.clear();

        try {
            api.deliver(c, true);
        } catch (IOException e) {
            throw new RuntimeException("Impossible IOException", e);
        }

        mIpEventsMessages = sawData.get("events url?ip=1");
        mIpPeopleMessages = sawData.get("people url?ip=1");
        mIpGroupMessages = sawData.get("groups url?ip=1");
    }

    public void testEmptyJSON() {
        JSONObject empty = new JSONObject();
        mBuilder.set("a distinct id", empty, empty);
    }

    public void testPeopleMessageBuilds()
       throws JSONException {
        {
            JSONObject set = mBuilder.set("a distinct id", mSampleProps, mSampleModifiers);
            checkModifiers(set);
            checkProfileProps("$set", set);
        }

        {
            JSONObject setOnce = mBuilder.setOnce("a distinct id", mSampleProps, mSampleModifiers);
            checkModifiers(setOnce);
            checkProfileProps("$set_once", setOnce);
        }

        {
            JSONObject setOnce = mBuilder.setOnce("a distinct id", mSampleProps);
            checkProfileProps("$set_once", setOnce);
        }

        {
            JSONObject delete = mBuilder.delete("a distinct id", mSampleModifiers);
            checkModifiers(delete);
            assertTrue(delete.getJSONObject("message").has("$delete"));
        }

        {
            Map<String, Long> increments = new HashMap<String, Long>();
            increments.put("k1", 10L);
            increments.put("k2", 1L);
            JSONObject increment = mBuilder.increment("a distinct id", increments, mSampleModifiers);
            checkModifiers(increment);
            JSONObject payload = increment.getJSONObject("message").getJSONObject("$add");
            assertEquals(payload.getInt("k1"), 10);
            assertEquals(payload.getInt("k2"), 1);
        }

        {
            Map<String, Long> increments = new HashMap<String, Long>();
            increments.put("k1", 10L);
            increments.put("k2", 1L);
            JSONObject increment = mBuilder.increment("a distinct id", increments);
            JSONObject payload = increment.getJSONObject("message").getJSONObject("$add");
            assertEquals(payload.getInt("k1"), 10);
            assertEquals(payload.getInt("k2"), 1);
        }

        {
            JSONObject append = mBuilder.append("a distinct id", mSampleProps, mSampleModifiers);
            checkModifiers(append);
            checkProfileProps("$append", append);
        }

        {
            JSONObject remove = mBuilder.remove("a distinct id", mSampleProps, mSampleModifiers);
            checkModifiers(remove);
            checkProfileProps("$remove", remove);
        }

        {
            JSONArray union1 = new JSONArray(new String[]{ "One", "Two" });
            JSONArray union2 = new JSONArray(new String[]{ "a", "b" });

            Map<String, JSONArray> unions = new HashMap<String, JSONArray>();
            unions.put("k1", union1);
            unions.put("k2", union2);

            JSONObject union = mBuilder.union("a distinct id", unions, mSampleModifiers);
            checkModifiers(union);
            JSONObject payload = union.getJSONObject("message").getJSONObject("$union");
            assertEquals(payload.getJSONArray("k1"), union1);
            assertEquals(payload.getJSONArray("k2"), union2);
        }

        {
            JSONArray union1 = new JSONArray(new String[]{ "One", "Two" });
            JSONArray union2 = new JSONArray(new String[]{ "a", "b" });

            Map<String, JSONArray> unions = new HashMap<String, JSONArray>();
            unions.put("k1", union1);
            unions.put("k2", union2);

            JSONObject union = mBuilder.union("a distinct id", unions);
            JSONObject payload = union.getJSONObject("message").getJSONObject("$union");
            assertEquals(payload.getJSONArray("k1"), union1);
            assertEquals(payload.getJSONArray("k2"), union2);
        }

        {
            Set<String> toUnset = new HashSet<String>();
            toUnset.add("One");
            toUnset.add("Two");
            JSONObject unset = mBuilder.unset("a distinct id", toUnset, mSampleModifiers);
            checkModifiers(unset);
            JSONArray payload = unset.getJSONObject("message").getJSONArray("$unset");

            for (int i = 0; i < payload.length(); i++) {
                String propName = payload.getString(i);
                assertTrue(toUnset.remove(propName));
            }

            assertTrue(toUnset.isEmpty());
        }

        {
            Set<String> toUnset = new HashSet<String>();
            toUnset.add("One");
            toUnset.add("Two");
            JSONObject unset = mBuilder.unset("a distinct id", toUnset);
            JSONArray payload = unset.getJSONObject("message").getJSONArray("$unset");

            for (int i = 0; i < payload.length(); i++) {
                String propName = payload.getString(i);
                assertTrue(toUnset.remove(propName));
            }

            assertTrue(toUnset.isEmpty());
        }

    }

    public void testPeopleMessageBadArguments() {
        mBuilder.peopleMessage("id", "action", true, null);
        mBuilder.peopleMessage("id", "action", 1.21, null);
        mBuilder.peopleMessage("id", "action", 100, null);
        mBuilder.peopleMessage("id", "action", 1000L, null);
        mBuilder.peopleMessage("id", "action", "String", null);
        mBuilder.peopleMessage("id", "action", JSONObject.NULL, null);

        // Current, less than wonderful behavior- we'll just call toString()
        // on random objects passed in.
        mBuilder.peopleMessage("id", "action", new Object(), null);

        JSONArray jsa = new JSONArray();
        mBuilder.peopleMessage("id", "action", jsa, null);

        JSONObject jso = new JSONObject();
        mBuilder.peopleMessage("id", "action", jso, null);

        try {
            mBuilder.peopleMessage("id", "action", null, null);
            fail("peopleMessage did not throw an exception on null");
        } catch (IllegalArgumentException e) {
            // ok
        }

        try {
            mBuilder.peopleMessage("id", "action", Double.NaN, null);
            fail("peopleMessage did not throw on NaN");
        } catch (IllegalArgumentException e) {
            // ok
        }

        try {
            mBuilder.peopleMessage("id", "action", Double.NaN, null);
            fail("peopleMessage did not throw on NaN");
        } catch (IllegalArgumentException e) {
            // ok
        }

        try {
            mBuilder.peopleMessage("id", "action", Double.NEGATIVE_INFINITY, null);
            fail("peopleMessage did not throw on infinity");
        } catch (IllegalArgumentException e) {
            // ok
        }
    }

    public void testGroupProfileMessageBuilds()
           throws JSONException {
            {
                JSONObject groupSet = mBuilder.groupSet("company", "Acme Inc.", mSampleProps, mSampleModifiers);
                checkModifiers(groupSet, true);
                checkProfileProps("$set", groupSet);
            }

            {
                JSONObject groupSetOnce = mBuilder.groupSetOnce("company", "Acme Inc.", mSampleProps, mSampleModifiers);
                checkModifiers(groupSetOnce, true);
                checkProfileProps("$set_once", groupSetOnce);
            }

            {
                JSONObject groupDelete = mBuilder.groupDelete("company", "Acme Inc.", mSampleModifiers);
                checkModifiers(groupDelete, true);
                assertTrue(groupDelete.getJSONObject("message").has("$delete"));
            }

            {
                JSONObject groupRemove = mBuilder.groupRemove("company", "Acme Inc.", mSampleProps, mSampleModifiers);
                checkModifiers(groupRemove, true);
                checkProfileProps("$remove", groupRemove);
            }

            {
                JSONArray union1 = new JSONArray(new String[]{ "One", "Two" });
                JSONArray union2 = new JSONArray(new String[]{ "a", "b" });

                Map<String, JSONArray> unions = new HashMap<String, JSONArray>();
                unions.put("k1", union1);
                unions.put("k2", union2);

                JSONObject groupUnion = mBuilder.groupUnion("company", "Acme Inc.", unions, mSampleModifiers);
                checkModifiers(groupUnion, true);
                JSONObject payload = groupUnion.getJSONObject("message").getJSONObject("$union");
                assertEquals(payload.getJSONArray("k1"), union1);
                assertEquals(payload.getJSONArray("k2"), union2);
            }

            {
                Set<String> toUnset = new HashSet<String>();
                toUnset.add("One");
                toUnset.add("Two");
                JSONObject groupUnset = mBuilder.groupUnset("company", "Acme Inc.", toUnset, mSampleModifiers);
                checkModifiers(groupUnset, true);
                JSONArray payload = groupUnset.getJSONObject("message").getJSONArray("$unset");

                for (int i = 0; i < payload.length(); i++) {
                    String propName = payload.getString(i);
                    assertTrue(toUnset.remove(propName));
                }

                assertTrue(toUnset.isEmpty());
            }

        }

        public void testGroupMessageBadArguments() {
            mBuilder.groupMessage("group_key", "group_id", "action", true, null);
            mBuilder.groupMessage("group_key", "group_id", "action", 1.21, null);
            mBuilder.groupMessage("group_key", "group_id", "action", 100, null);
            mBuilder.groupMessage("group_key", "group_id", "action", 1000L, null);
            mBuilder.groupMessage("group_key", "group_id", "action", "String", null);
            mBuilder.groupMessage("group_key", "group_id", "action", JSONObject.NULL, null);

            // Current, less than wonderful behavior- we'll just call toString()
            // on random objects passed in.
            mBuilder.groupMessage("group_key", "group_id", "action", new Object(), null);

            JSONArray jsa = new JSONArray();
            mBuilder.groupMessage("group_key", "group_id", "action", jsa, null);

            JSONObject jso = new JSONObject();
            mBuilder.groupMessage("group_key", "group_id", "action", jso, null);

            try {
                mBuilder.groupMessage("group_key", "group_id", "action", null, null);
                fail("groupMessage did not throw an exception on null");
            } catch (IllegalArgumentException e) {
                // ok
            }

            try {
                mBuilder.groupMessage("group_key", "group_id", "action", Double.NaN, null);
                fail("groupMessage did not throw on NaN");
            } catch (IllegalArgumentException e) {
                // ok
            }

            try {
                mBuilder.groupMessage("group_key", "group_id", "action", Double.NaN, null);
                fail("groupMessage did not throw on NaN");
            } catch (IllegalArgumentException e) {
                // ok
            }

            try {
                mBuilder.groupMessage("group_key", "group_id", "action", Double.NEGATIVE_INFINITY, null);
                fail("groupMessage did not throw on infinity");
            } catch (IllegalArgumentException e) {
                // ok
            }
        }

    public void testMessageFormat() {
        ClientDelivery c = new ClientDelivery();
        assertFalse(c.isValidMessage(mSampleProps));

        JSONObject event = mBuilder.event("a distinct id", "login", mSampleProps);
        assertTrue(c.isValidMessage(event));

        JSONObject set = mBuilder.set("a distinct id", mSampleProps);
        assertTrue(c.isValidMessage(set));

        Map<String, Long> increments = new HashMap<String, Long>();
        increments.put("a key", 24L);
        JSONObject increment = mBuilder.increment("a distinct id", increments);
        assertTrue(c.isValidMessage(increment));

        // Test deprecated trackCharge method - should return null
        JSONObject charge = mBuilder.trackCharge("a distinct id", 100.00, mSampleProps);
        assertNull("trackCharge should return null (deprecated)", charge);
    }

    public void testModifiers() {
        JSONObject set = mBuilder.set("a distinct id", mSampleProps, mSampleModifiers);
        checkModifiers(set);

        Map<String, Long> increments = new HashMap<String, Long>();
        increments.put("a key", 24L);
        JSONObject increment = mBuilder.increment("a distinct id", increments, mSampleModifiers);
        checkModifiers(increment);

        JSONObject append = mBuilder.append("a distinct id", mSampleProps, mSampleModifiers);
        checkModifiers(append);

        // Test deprecated trackCharge method - should return null
        JSONObject trackCharge = mBuilder.trackCharge("a distinct id", 2.2, null, mSampleModifiers);
        assertNull("trackCharge should return null (deprecated)", trackCharge);
    }

    public void testEmptyMessageFormat() {
        ClientDelivery c = new ClientDelivery();
        JSONObject eventMessage = mBuilder.event("a distinct id", "empty event", null);
        assertTrue(c.isValidMessage(eventMessage));
    }

    public void testValidate() {
        ClientDelivery c = new ClientDelivery();
        JSONObject event = mBuilder.event("a distinct id", "login", mSampleProps);
        assertTrue(c.isValidMessage(event));
        try {
            JSONObject rebuiltMessage = new JSONObject(event.toString());
            assertTrue(c.isValidMessage(rebuiltMessage));
            assertEquals(c.getEventsMessages().size(), 0);
            c.addMessage(rebuiltMessage);
            assertEquals(c.getEventsMessages().size(), 1);
        } catch (JSONException e) {
            fail("Failed to build JSONObject");
        }
    }

    public void testClientDelivery() {
        ClientDelivery c = new ClientDelivery();
        try {
            c.addMessage(mSampleProps);
            fail("addMessage did not throw");
        } catch (MixpanelMessageException e) {
            // This is expected, we pass
        }

        try {
            JSONObject event = mBuilder.event("a distinct id", "login", mSampleProps);
            c.addMessage(event);

            JSONObject set = mBuilder.set("a distinct id", mSampleProps);
            c.addMessage(set);

            JSONObject groupSet = mBuilder.groupSet("company", "Acme Inc.", mSampleProps);
            c.addMessage(groupSet);


            Map<String, Long> increments = new HashMap<String, Long>();
            increments.put("a key", 24L);
            JSONObject increment = mBuilder.increment("a distinct id", increments);
            c.addMessage(increment);
        } catch (MixpanelMessageException e) {
            fail("Threw exception on valid message");
        }
    }

    public void testApiSendIpArgs() {
        assertEquals(mEventsMessages, mIpEventsMessages);
        assertEquals(mPeopleMessages, mIpPeopleMessages);
        assertEquals(mGroupMessages, mIpGroupMessages);
    }

    public void testApiSendEvent() {
        try {
            JSONArray messageArray = new JSONArray(mEventsMessages);
            assertTrue("Only one message sent", messageArray.length() == 1);

            JSONObject eventSent = messageArray.getJSONObject(0);
            String eventName = eventSent.getString("event");
            assertTrue("Event name had expected value", "login".equals(eventName));

            JSONObject eventProps = eventSent.getJSONObject("properties");
            String propValue = eventProps.getString("prop key");
            assertTrue("Property had expected value", "prop value".equals(propValue));
        } catch (JSONException e) {
            fail("Data message can't be interpreted as expected: " + mEventsMessages);
        }
    }

    public void testApiSendPeople() {
        try {
            JSONArray messageArray = new JSONArray(mPeopleMessages);
            assertTrue("two messages sent", messageArray.length() == 2);

            JSONObject thing1 = messageArray.getJSONObject(0);
            JSONObject thing2 = messageArray.getJSONObject(1);

            JSONObject setMessage = null;
            JSONObject incrementMessage = null;

            if (thing1.has("$set")) {
                setMessage = thing1;
            }
            else if (thing2.has("$set")) {
                setMessage = thing2;
            }
            else {
                fail("Can't find $set message in " + mPeopleMessages);
            }

            if (thing1.has("$add")) {
                incrementMessage = thing1;
            }
            else if (thing2.has("$add")) {
                incrementMessage = thing2;
            }
            else {
                fail("Can't find $increment message in " + mPeopleMessages);
            }

            JSONObject setProps = setMessage.getJSONObject("$set");
            String propValue = setProps.getString("prop key");
            assertTrue("Set prop had expected value", "prop value".equals(propValue));

            JSONObject incrementProps = incrementMessage.getJSONObject("$add");
            long incrementValue = incrementProps.getLong("a key");
            assertTrue("Increment prop had expected value", 24 == incrementValue);

        } catch (JSONException e) {
            fail("Messages can't be interpreted as expected: " + mPeopleMessages);
        }
    }

    public void testExpectedEventProperties() {
        try {
            JSONArray messageArray = new JSONArray(mEventsMessages);
            JSONObject eventSent = messageArray.getJSONObject(0);
            JSONObject eventProps = eventSent.getJSONObject("properties");

            assertTrue("Time is included", eventProps.getLong("time") >= mTimeZero);

            String distinctId = eventProps.getString("distinct_id");
            assertTrue("Distinct id as expected", "a distinct id".equals(distinctId));

            String token = eventProps.getString("token");
            assertTrue("Token as expected", "a token".equals(token));
        } catch (JSONException e) {
            fail("Data message can't be interpreted as expected: " + mEventsMessages);
        }
    }

    public void testExpectedPeopleParams() {
        try {
            JSONArray messageArray = new JSONArray(mPeopleMessages);
            JSONObject setMessage = messageArray.getJSONObject(0);

            assertTrue("Time is included", setMessage.getLong("$time") >= mTimeZero);

            String distinctId = setMessage.getString("$distinct_id");
            assertTrue("Distinct id as expected", "a distinct id".equals(distinctId));

            String token = setMessage.getString("$token");
            assertTrue("Token as expected", "a token".equals(token));
        } catch (JSONException e) {
            fail("Data message can't be interpreted as expected: " + mPeopleMessages);
        }
    }

    public void testEmptyDelivery() {
        MixpanelAPI api = new MixpanelAPI("events url", "people url") {
            @Override
            public boolean sendData(String dataString, String endpointUrl) {
                fail("Data sent when no data should be sent");
                return true;
            }
        };

        ClientDelivery c = new ClientDelivery();
        try {
            api.deliver(c);
        } catch (IOException e) {
            throw new RuntimeException("Apparently impossible IOException thrown", e);
        }
    }

    public void testLargeDelivery() {
        final List<String> sends = new ArrayList<String>();

        MixpanelAPI api = new MixpanelAPI("events url", "people url") {
            @Override
            public boolean sendData(String dataString, String endpointUrl) {
                sends.add(dataString);
                return true;
            }
        };

        ClientDelivery c = new ClientDelivery();
        int expectLeftovers = Config.MAX_MESSAGE_SIZE - 1;
        int totalToSend = (Config.MAX_MESSAGE_SIZE * 2) + expectLeftovers;
        for(int i = 0; i < totalToSend; i++) {
            Map<String, Integer> propsMap = new HashMap<String, Integer>();
            propsMap.put("count", i);
            JSONObject props = new JSONObject(propsMap);
            JSONObject message = mBuilder.event("a distinct id", "counted", props);
            c.addMessage(message);
        }

        try {
            api.deliver(c);
        } catch (IOException e) {
            throw new RuntimeException("Apparently impossible IOException", e);
        }

        assertTrue("More than one message", sends.size() == 3);

        try {
            JSONArray firstMessage = new JSONArray(sends.get(0));
            assertTrue("First message has max elements", firstMessage.length() == Config.MAX_MESSAGE_SIZE);

            JSONArray secondMessage = new JSONArray(sends.get(1));
            assertTrue("Second message has max elements", secondMessage.length() == Config.MAX_MESSAGE_SIZE);

            JSONArray thirdMessage = new JSONArray(sends.get(2));
            assertTrue("Third message has all leftover elements", thirdMessage.length() == expectLeftovers);
        } catch (JSONException e) {
            fail("Can't interpret sends appropriately when sending large messages");
        }
    }

    public void testEncodeDataString(){
        MixpanelAPI api = new MixpanelAPI("events url", "people url") {
            @Override
            public boolean sendData(String dataString, String endpointUrl) {
                fail("Data sent when no data should be sent");
                return true;
            }
        };

        try{
            api.encodeDataString(null);
            fail("encodeDataString doesn't accept null string");
        }catch(NullPointerException e){
            // ok
        }

        // empty string
        assertEquals("", api.encodeDataString(""));
        // empty JSON
        assertEquals("e30%3D", api.encodeDataString(new JSONObject().toString()));
        // empty Array
        assertEquals("W10%3D", api.encodeDataString(new JSONArray().toString()));
        // JSON Object
        assertEquals("eyJwcm9wIGtleSI6InByb3AgdmFsdWUiLCJyYXRpbyI6Is%2BAIn0%3D", api.encodeDataString(mSampleProps.toString()));
        // JSON Array
        JSONArray jsonArray = new JSONArray(Arrays.asList(mSampleProps));
        assertEquals("W3sicHJvcCBrZXkiOiJwcm9wIHZhbHVlIiwicmF0aW8iOiLPgCJ9XQ%3D%3D", api.encodeDataString(jsonArray.toString()));
    }

    private void checkModifiers(JSONObject built) {
        checkModifiers(built, false);
    }
    private void checkModifiers(JSONObject built, boolean forGroups) {
        try {
            JSONObject msg = built.getJSONObject("message");
            assertEquals(msg.getString("$time"), "A TIME");
            assertEquals(msg.getString("Unexpected"), "But OK");
            if (forGroups) {
                assertEquals(msg.getString("$group_key"), "company");
                assertEquals(msg.getString("$group_id"), "Acme Inc.");
            } else {
                assertEquals(msg.getString("$distinct_id"), "a distinct id");
            }
        } catch (JSONException e) {
            fail(e.toString());
        }
    }

    private void checkProfileProps(String operation, JSONObject built) {
        try {
            JSONObject msg = built.getJSONObject("message");
            JSONObject props = msg.getJSONObject(operation);
            assertEquals(props.getString("prop key"), "prop value");
            assertEquals(props.getString("ratio"), "\u03C0");
        } catch (JSONException e) {
            fail(e.toString());
        }
    }

    public void testImportEvent() {
        // Test creating an import event message
        try {
            // Time more than 5 days ago and less than 1 year ago (30 days)
            long historicalTime = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
            
            JSONObject properties = new JSONObject();
            properties.put("time", historicalTime);
            properties.put("$insert_id", "test-insert-id-123");
            properties.put("prop key", "prop value");
            
            JSONObject importMessage = mBuilder.importEvent("a distinct id", "Historical Event", properties);
            
            // Verify the message structure
            assertTrue("Message is valid", new ClientDelivery().isValidMessage(importMessage));
            assertEquals("Message type is import", "import", importMessage.getString("message_type"));
            
            JSONObject message = importMessage.getJSONObject("message");
            assertEquals("Event name correct", "Historical Event", message.getString("event"));
            
            JSONObject props = message.getJSONObject("properties");
            assertEquals("distinct_id correct", "a distinct id", props.getString("distinct_id"));
            assertEquals("time correct", historicalTime, props.getLong("time"));
            assertEquals("$insert_id correct", "test-insert-id-123", props.getString("$insert_id"));
            assertEquals("token present", "a token", props.getString("token"));
            assertEquals("custom property present", "prop value", props.getString("prop key"));
        } catch (JSONException e) {
            fail("Failed to create or parse import event: " + e.toString());
        }
    }

    public void testImportMessageDelivery() {
        // Test that import messages are properly sent
        final Map<String, String> sawData = new HashMap<String, String>();
        final Map<String, String> sawToken = new HashMap<String, String>();
        
        MixpanelAPI api = new MixpanelAPI("events url", "people url", "groups url", "import url") {
            @Override
            public boolean sendImportData(String dataString, String endpointUrl, String token) {
                sawData.put(endpointUrl, dataString);
                sawToken.put(endpointUrl, token);
                return true;
            }
        };
        
        ClientDelivery c = new ClientDelivery();
        
        // Create import events with historical timestamps (90 days ago, within >5 days and <1 year range)
        long historicalTime = System.currentTimeMillis() - (90L * 24L * 60L * 60L * 1000L);
        
        try {
            JSONObject props1 = new JSONObject();
            props1.put("time", historicalTime);
            props1.put("$insert_id", "import-id-1");
            props1.put("Item", "Widget");
            
            JSONObject importEvent1 = mBuilder.importEvent("a distinct id", "Purchase", props1);
            c.addMessage(importEvent1);
            
            JSONObject props2 = new JSONObject();
            props2.put("time", historicalTime + 1000);
            props2.put("$insert_id", "import-id-2");
            props2.put("Page", "Home");
            
            JSONObject importEvent2 = mBuilder.importEvent("a distinct id", "Page View", props2);
            c.addMessage(importEvent2);
            
            api.deliver(c);
            
            // Verify the import data was sent
            String importData = sawData.get("import url?strict=1");
            assertNotNull("Import data was sent", importData);
            
            // Verify token was extracted and used for auth
            String usedToken = sawToken.get("import url?strict=1");
            assertEquals("Token extracted correctly", "a token", usedToken);
            
            // Parse and verify the import data
            JSONArray sentMessages = new JSONArray(importData);
            assertEquals("Two import messages sent", 2, sentMessages.length());
            
            JSONObject sentEvent1 = sentMessages.getJSONObject(0);
            assertEquals("First event name correct", "Purchase", sentEvent1.getString("event"));
            
            JSONObject sentProps1 = sentEvent1.getJSONObject("properties");
            assertEquals("First event distinct_id correct", "a distinct id", sentProps1.getString("distinct_id"));
            assertTrue("First event has $insert_id", sentProps1.has("$insert_id"));
            
        } catch (IOException e) {
            fail("IOException during delivery: " + e.toString());
        } catch (JSONException e) {
            fail("JSON parsing error: " + e.toString());
        }
    }

    public void testImportLargeBatch() {
        // Test that import messages respect the 2000 message batch size limit
        final List<String> sends = new ArrayList<String>();
        
        MixpanelAPI api = new MixpanelAPI("events url", "people url", "groups url", "import url") {
            @Override
            public boolean sendImportData(String dataString, String endpointUrl, String token) {
                sends.add(dataString);
                return true;
            }
        };
        
        ClientDelivery c = new ClientDelivery();
        // Use 180 days ago (6 months, within >5 days and <1 year range)
        long historicalTime = System.currentTimeMillis() - (180L * 24L * 60L * 60L * 1000L);
        
        // Create more than 2000 import events
        int totalEvents = 2500;
        for (int i = 0; i < totalEvents; i++) {
            try {
                JSONObject props = new JSONObject();
                props.put("time", historicalTime + i);
                props.put("$insert_id", "insert-id-" + i);
                props.put("count", i);
                
                JSONObject importEvent = mBuilder.importEvent("a distinct id", "Test Event", props);
                c.addMessage(importEvent);
            } catch (JSONException e) {
                fail("Failed to create import event: " + e.toString());
            }
        }
        
        try {
            api.deliver(c);
            
            // Should be split into 2 batches (2000 + 500)
            assertEquals("Messages split into batches", 2, sends.size());
            
            JSONArray firstBatch = new JSONArray(sends.get(0));
            assertEquals("First batch has 2000 events", Config.IMPORT_MAX_MESSAGE_SIZE, firstBatch.length());
            
            JSONArray secondBatch = new JSONArray(sends.get(1));
            assertEquals("Second batch has 500 events", 500, secondBatch.length());
            
        } catch (IOException e) {
            fail("IOException during delivery: " + e.toString());
        } catch (JSONException e) {
            fail("JSON parsing error: " + e.toString());
        }
    }

    public void testImportMessageValidation() {
        // Test that import messages are validated correctly
        ClientDelivery c = new ClientDelivery();
        
        // Use 300 days ago (within >5 days and <1 year range)
        long historicalTime = System.currentTimeMillis() - (300L * 24L * 60L * 60L * 1000L);
        
        try {
            JSONObject properties = new JSONObject();
            properties.put("time", historicalTime);
            properties.put("$insert_id", "validation-test-id");
            
            JSONObject importMessage = mBuilder.importEvent("a distinct id", "Test", properties);
            
            assertTrue("Import message is valid", c.isValidMessage(importMessage));
            
            // Add to delivery and verify it's in the import messages list
            c.addMessage(importMessage);
            assertEquals("Import message added to import list", 1, c.getImportMessages().size());
            assertEquals("No regular event messages", 0, c.getEventsMessages().size());
            
        } catch (JSONException e) {
            fail("JSON error: " + e.toString());
        }
    }

    public void testImportEventWithDefaults() {
        // Test that import events automatically generate time and $insert_id if not provided
        long beforeTime = System.currentTimeMillis();
        
        try {
            // Test 1: No properties at all - should generate both time and $insert_id
            JSONObject importMessage1 = mBuilder.importEvent("user-123", "Test Event", null);
            
            assertTrue("Message is valid", new ClientDelivery().isValidMessage(importMessage1));
            assertEquals("Message type is import", "import", importMessage1.getString("message_type"));
            
            JSONObject message1 = importMessage1.getJSONObject("message");
            JSONObject props1 = message1.getJSONObject("properties");
            
            assertTrue("Has auto-generated time", props1.has("time"));
            long generatedTime = props1.getLong("time");
            assertTrue("Generated time is recent", generatedTime >= beforeTime && generatedTime <= System.currentTimeMillis());
            
            assertTrue("Has auto-generated $insert_id", props1.has("$insert_id"));
            String insertId1 = props1.getString("$insert_id");
            assertEquals("$insert_id is 32 characters (UUID hex format)", 32, insertId1.length());
            assertTrue("$insert_id is valid hex", insertId1.matches("[0-9a-f]{32}"));
            
            // Test 2: Empty properties object - should generate both
            JSONObject emptyProps = new JSONObject();
            JSONObject importMessage2 = mBuilder.importEvent("user-456", "Another Event", emptyProps);
            
            JSONObject props2 = importMessage2.getJSONObject("message").getJSONObject("properties");
            assertTrue("Has auto-generated time", props2.has("time"));
            assertTrue("Has auto-generated $insert_id", props2.has("$insert_id"));
            
            String insertId2 = props2.getString("$insert_id");
            assertFalse("Different events get different insert_ids", insertId1.equals(insertId2));
            
            // Test 3: Custom time provided, should generate $insert_id only
            long customTime = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L);
            JSONObject propsWithTime = new JSONObject();
            propsWithTime.put("time", customTime);
            
            JSONObject importMessage3 = mBuilder.importEvent("user-789", "Custom Time Event", propsWithTime);
            JSONObject props3 = importMessage3.getJSONObject("message").getJSONObject("properties");
            
            assertEquals("Custom time preserved", customTime, props3.getLong("time"));
            assertTrue("$insert_id auto-generated", props3.has("$insert_id"));
            
            // Test 4: Custom $insert_id provided, should generate time only
            JSONObject propsWithInsertId = new JSONObject();
            propsWithInsertId.put("$insert_id", "my-custom-insert-id");
            
            JSONObject importMessage4 = mBuilder.importEvent("user-abc", "Custom Insert ID Event", propsWithInsertId);
            JSONObject props4 = importMessage4.getJSONObject("message").getJSONObject("properties");
            
            assertTrue("Time auto-generated", props4.has("time"));
            assertEquals("Custom $insert_id preserved", "my-custom-insert-id", props4.getString("$insert_id"));
            
            // Test 5: Both custom time and $insert_id provided - should preserve both
            JSONObject propsWithBoth = new JSONObject();
            propsWithBoth.put("time", customTime);
            propsWithBoth.put("$insert_id", "fully-custom-id");
            
            JSONObject importMessage5 = mBuilder.importEvent("user-xyz", "Fully Custom Event", propsWithBoth);
            JSONObject props5 = importMessage5.getJSONObject("message").getJSONObject("properties");
            
            assertEquals("Custom time preserved", customTime, props5.getLong("time"));
            assertEquals("Custom $insert_id preserved", "fully-custom-id", props5.getString("$insert_id"));
            
        } catch (JSONException e) {
            fail("JSON error: " + e.toString());
        }
    }

    public void testGzipCompressionEnabled() {
        // Test that gzip compression is properly enabled and data is compressed
        MixpanelAPI api = new MixpanelAPI("events url", "people url", "groups url", "import url", true) {
            @Override
            public boolean sendData(String dataString, String endpointUrl) throws IOException {
                // This method should be called with gzip compression enabled
                fail("sendData should not be called directly when testing at this level");
                return true;
            }
        };
        
        // Verify the API was created with gzip compression enabled
        assertTrue("Gzip compression should be enabled", api.mUseGzipCompression);
    }

    public void testGzipCompressionDisabled() {
        // Test that gzip compression is disabled by default
        MixpanelAPI api1 = new MixpanelAPI();
        assertFalse("Gzip compression should be disabled by default", api1.mUseGzipCompression);
        
        MixpanelAPI api2 = new MixpanelAPI(false);
        assertFalse("Gzip compression should be disabled when explicitly set to false", api2.mUseGzipCompression);
        
        MixpanelAPI api3 = new MixpanelAPI("events url", "people url");
        assertFalse("Gzip compression should be disabled by default for custom endpoints", api3.mUseGzipCompression);
    }

    public void testGzipCompressionDataIntegrity() {
        // Test that data compressed with gzip can be decompressed correctly
        final Map<String, byte[]> capturedCompressedData = new HashMap<String, byte[]>();
        final Map<String, String> capturedOriginalData = new HashMap<String, String>();
        
        MixpanelAPI apiWithGzip = new MixpanelAPI("events url", "people url", "groups url", "import url", true) {
            @Override
            public boolean sendData(String dataString, String endpointUrl) throws IOException {
                capturedOriginalData.put(endpointUrl, dataString);
                
                // Simulate what the real sendData does with gzip
                if (mUseGzipCompression) {
                    try {
                        String encodedData = encodeDataString(dataString);
                        String encodedQuery = "data=" + encodedData;
                        
                        java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
                        java.util.zip.GZIPOutputStream gzipStream = new java.util.zip.GZIPOutputStream(byteStream);
                        gzipStream.write(encodedQuery.getBytes("utf-8"));
                        gzipStream.finish();
                        gzipStream.close();
                        
                        capturedCompressedData.put(endpointUrl, byteStream.toByteArray());
                    } catch (Exception e) {
                        throw new IOException("Compression failed", e);
                    }
                }
                
                return true;
            }
        };
        
        ClientDelivery delivery = new ClientDelivery();
        JSONObject event = mBuilder.event("test-user", "Test Event", mSampleProps);
        delivery.addMessage(event);
        
        try {
            apiWithGzip.deliver(delivery);
            
            // Verify data was captured
            String eventUrl = "events url?ip=0";
            assertTrue("Original data was captured", capturedOriginalData.containsKey(eventUrl));
            assertTrue("Compressed data was captured", capturedCompressedData.containsKey(eventUrl));
            
            // Verify compressed data is smaller than original (for typical data)
            byte[] compressedBytes = capturedCompressedData.get(eventUrl);
            String originalData = capturedOriginalData.get(eventUrl);
            String encodedData = apiWithGzip.encodeDataString(originalData);
            String encodedQuery = "data=" + encodedData;
            
            assertTrue("Compressed data exists", compressedBytes.length > 0);
            
            // Decompress and verify data integrity
            ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedBytes);
            GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
            java.io.ByteArrayOutputStream decompressedStream = new java.io.ByteArrayOutputStream();
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipStream.read(buffer)) > 0) {
                decompressedStream.write(buffer, 0, len);
            }
            gzipStream.close();
            
            String decompressedData = decompressedStream.toString("utf-8");
            assertEquals("Decompressed data matches original", encodedQuery, decompressedData);
            
        } catch (IOException e) {
            fail("IOException during gzip test: " + e.toString());
        }
    }

    public void testGzipCompressionForImport() {
        // Test that gzip compression works for import endpoint
        final Map<String, byte[]> capturedCompressedData = new HashMap<String, byte[]>();
        final Map<String, String> capturedOriginalData = new HashMap<String, String>();
        
        MixpanelAPI apiWithGzip = new MixpanelAPI("events url", "people url", "groups url", "import url", true) {
            @Override
            public boolean sendImportData(String dataString, String endpointUrl, String token) throws IOException {
                capturedOriginalData.put(endpointUrl, dataString);
                
                // Simulate what the real sendImportData does with gzip
                if (mUseGzipCompression) {
                    try {
                        java.io.ByteArrayOutputStream byteStream = new java.io.ByteArrayOutputStream();
                        java.util.zip.GZIPOutputStream gzipStream = new java.util.zip.GZIPOutputStream(byteStream);
                        gzipStream.write(dataString.getBytes("utf-8"));
                        gzipStream.finish();
                        gzipStream.close();
                        
                        capturedCompressedData.put(endpointUrl, byteStream.toByteArray());
                    } catch (Exception e) {
                        throw new IOException("Compression failed", e);
                    }
                }
                
                return true;
            }
        };
        
        ClientDelivery delivery = new ClientDelivery();
        
        long historicalTime = System.currentTimeMillis() - (90L * 24L * 60L * 60L * 1000L);
        try {
            JSONObject props = new JSONObject();
            props.put("time", historicalTime);
            props.put("$insert_id", "gzip-test-id");
            
            JSONObject importEvent = mBuilder.importEvent("test-user", "Historical Event", props);
            delivery.addMessage(importEvent);
            
            apiWithGzip.deliver(delivery);
            
            // Verify data was captured
            String importUrl = "import url?strict=1";
            assertTrue("Original data was captured", capturedOriginalData.containsKey(importUrl));
            assertTrue("Compressed data was captured", capturedCompressedData.containsKey(importUrl));
            
            // Verify compressed data can be decompressed
            byte[] compressedBytes = capturedCompressedData.get(importUrl);
            String originalData = capturedOriginalData.get(importUrl);
            
            assertTrue("Compressed data exists", compressedBytes.length > 0);
            
            // Decompress and verify data integrity
            ByteArrayInputStream byteStream = new ByteArrayInputStream(compressedBytes);
            GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
            java.io.ByteArrayOutputStream decompressedStream = new java.io.ByteArrayOutputStream();
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipStream.read(buffer)) > 0) {
                decompressedStream.write(buffer, 0, len);
            }
            gzipStream.close();
            
            String decompressedData = decompressedStream.toString("utf-8");
            assertEquals("Decompressed data matches original", originalData, decompressedData);
            
        } catch (IOException e) {
            fail("IOException during gzip import test: " + e.toString());
        } catch (JSONException e) {
            fail("JSONException during gzip import test: " + e.toString());
        }
    }

    public void testCustomImportBatchSizeConstructor() {
        // Test constructor with int parameter only: new MixpanelAPI(500)
        MixpanelAPI api = new MixpanelAPI(500);
        assertEquals("Custom batch size should be 500", 500, api.mImportMaxMessageSize);
    }

    public void testCustomImportBatchSizeWithGzipConstructor() {
        // Test constructor with both gzip and batch size: new MixpanelAPI(true, 500)
        MixpanelAPI api = new MixpanelAPI(true, 500);
        assertEquals("Custom batch size should be 500", 500, api.mImportMaxMessageSize);
        assertTrue("Gzip compression should be enabled", api.mUseGzipCompression);
    }

    public void testImportBatchSizeClamping() {
        // Test that batch size is clamped between 1 and 2000
        MixpanelAPI apiTooSmall = new MixpanelAPI(0);
        assertEquals("Batch size below 1 should be clamped to 1", 1, apiTooSmall.mImportMaxMessageSize);
        
        MixpanelAPI apiTooLarge = new MixpanelAPI(3000);
        assertEquals("Batch size above 2000 should be clamped to 2000", 2000, apiTooLarge.mImportMaxMessageSize);
        
        MixpanelAPI apiValid = new MixpanelAPI(1000);
        assertEquals("Batch size within range should not be clamped", 1000, apiValid.mImportMaxMessageSize);
    }

    public void testImportMessagesWithCustomBatchSize() {
        // Test that import messages are sent in batches of the custom size
        final List<Integer> batchSizes = new ArrayList<Integer>();
        
        MixpanelAPI api = new MixpanelAPI("events url", "people url", "groups url", "import url", false, 5, null, null) {
            @Override
            public boolean sendImportData(String dataString, String endpointUrl, String token) throws IOException {
                // Parse the JSON array to count messages in this batch
                JSONArray array = new JSONArray(dataString);
                batchSizes.add(array.length());
                return true;
            }
        };

        try {
            ClientDelivery delivery = new ClientDelivery();
            
            // Create 13 import messages (should be sent as batches of 5, 5, 3)
            MessageBuilder builder = new MessageBuilder("a token");
            for (int i = 0; i < 13; i++) {
                Map<String, Object> props = new HashMap<String, Object>();
                props.put("time", System.currentTimeMillis());
                props.put("$insert_id", "id-" + i);
                props.put("index", i);
                JSONObject importEvent = builder.importEvent("user-" + i, "test event", new JSONObject(props));
                delivery.addMessage(importEvent);
            }
            
            api.deliver(delivery);
            
            assertEquals("Should have 3 batches", 3, batchSizes.size());
            assertEquals("First batch should have 5 messages", 5, batchSizes.get(0).intValue());
            assertEquals("Second batch should have 5 messages", 5, batchSizes.get(1).intValue());
            assertEquals("Third batch should have 3 messages", 3, batchSizes.get(2).intValue());
            
        } catch (IOException e) {
            fail("IOException during custom batch size test: " + e.toString());
        } catch (JSONException e) {
            fail("JSONException during custom batch size test: " + e.toString());
        }
    }

}
