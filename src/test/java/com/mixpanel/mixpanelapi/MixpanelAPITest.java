package com.mixpanel.mixpanelapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Unit test for simple App.
 */
public class MixpanelAPITest
    extends TestCase
{
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

        MixpanelAPI api = new MixpanelAPI("events url", "people url") {
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

        try {
            api.deliver(c, false);
        } catch (IOException e) {
            throw new RuntimeException("Impossible IOException", e);
        }

        mEventsMessages = sawData.get("events url?ip=0");
        mPeopleMessages = sawData.get("people url");
    }

    public void testPeopleMessageBuilds()
       throws JSONException {
        {
            JSONObject set = mBuilder.set("a distinct id", mSampleProps, mSampleModifiers);
            checkModifiers(set);
            checkPeopleProps("$set", set);
        }

        {
            JSONObject setOnce = mBuilder.setOnce("a distinct id", mSampleProps, mSampleModifiers);
            checkModifiers(setOnce);
            checkPeopleProps("$set_once", setOnce);
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
            JSONObject append = mBuilder.append("a distinct id", mSampleProps, mSampleModifiers);
            checkModifiers(append);
            checkPeopleProps("$append", append);
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

        JSONObject charge = mBuilder.trackCharge("a distinct id", 100.00, mSampleProps);
        assertTrue(c.isValidMessage(charge));
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

        JSONObject trackCharge = mBuilder.trackCharge("a distinct id", 2.2, null, mSampleModifiers);
        checkModifiers(trackCharge);
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


            Map<String, Long> increments = new HashMap<String, Long>();
            increments.put("a key", 24L);
            JSONObject increment = mBuilder.increment("a distinct id", increments);
            c.addMessage(increment);
        } catch (MixpanelMessageException e) {
            fail("Threw exception on valid message");
        }
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

    private void checkModifiers(JSONObject built) {
        try {
            JSONObject msg = built.getJSONObject("message");
            assertEquals(msg.getString("$time"), "A TIME");
            assertEquals(msg.getString("Unexpected"), "But OK");
            assertEquals(msg.getString("$distinct_id"), "a distinct id");
        } catch (JSONException e) {
            fail(e.toString());
        }
    }

    private void checkPeopleProps(String operation, JSONObject built) {
        try {
            JSONObject msg = built.getJSONObject("message");
            JSONObject props = msg.getJSONObject(operation);
            assertEquals(props.getString("prop key"), "prop value");
            assertEquals(props.getString("ratio"), "\u03C0");
        } catch (JSONException e) {
            fail(e.toString());
        }
    }

    private MessageBuilder mBuilder;
    private JSONObject mSampleProps;
    private JSONObject mSampleModifiers;
    private String mEventsMessages;
    private String mPeopleMessages;
    private long mTimeZero;
}
