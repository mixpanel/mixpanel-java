package com.mixpanel.mixpanelapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            if (thing1.has("$increment")) {
                incrementMessage = thing1;
            }
            else if (thing2.has("$increment")) {
                incrementMessage = thing2;
            }
            else {
                fail("Can't find $increment message in " + mPeopleMessages);
            }

            JSONObject setProps = setMessage.getJSONObject("$set");
            String propValue = setProps.getString("prop key");
            assertTrue("Set prop had expected value", "prop value".equals(propValue));

            JSONObject incrementProps = incrementMessage.getJSONObject("$increment");
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

    private MessageBuilder mBuilder;
    private JSONObject mSampleProps;
    private String mEventsMessages;
    private String mPeopleMessages;
    private long mTimeZero;
}
