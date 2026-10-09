package com.mixpanel.mixpanelapi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class BotClassifyingMessageBuilderTest extends TestCase {

    private static final String TOKEN = "test-token";
    private MessageBuilder mDelegate;
    private BotClassifyingMessageBuilder mBotBuilder;

    public BotClassifyingMessageBuilderTest(String testName) { super(testName); }
    public static Test suite() { return new TestSuite(BotClassifyingMessageBuilderTest.class); }

    @Override
    public void setUp() {
        mDelegate = new MessageBuilder(TOKEN);
        mBotBuilder = new BotClassifyingMessageBuilder(mDelegate);
    }

    // === CORE ENRICHMENT ===

    public void testEnrichesEventWhenUserAgentIsAiBot() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "Mozilla/5.0 (compatible; GPTBot/1.2; +https://openai.com/gptbot)");
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        JSONObject props = envelope.getJSONObject("message").getJSONObject("properties");
        assertTrue("$is_ai_bot should be true", props.getBoolean("$is_ai_bot"));
        assertEquals("GPTBot", props.getString("$ai_bot_name"));
        assertEquals("OpenAI", props.getString("$ai_bot_provider"));
        assertEquals("indexing", props.getString("$ai_bot_category"));
    }

    public void testSetsIsAiBotFalseForNonBot() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "Mozilla/5.0 Chrome/120.0.0.0 Safari/537.36");
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        JSONObject props = envelope.getJSONObject("message").getJSONObject("properties");
        assertFalse("$is_ai_bot should be false", props.getBoolean("$is_ai_bot"));
        assertFalse("$ai_bot_name should not be present", props.has("$ai_bot_name"));
        assertFalse("$ai_bot_provider should not be present", props.has("$ai_bot_provider"));
        assertFalse("$ai_bot_category should not be present", props.has("$ai_bot_category"));
    }

    public void testNoEnrichmentWhenUserAgentAbsent() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("page", "/home");
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        JSONObject props = envelope.getJSONObject("message").getJSONObject("properties");
        assertFalse("$is_ai_bot should not be present", props.has("$is_ai_bot"));
        assertFalse("$ai_bot_name should not be present", props.has("$ai_bot_name"));
    }

    public void testNoEnrichmentWhenPropertiesNull() throws JSONException {
        JSONObject envelope = mBotBuilder.event("user123", "page_view", null);
        JSONObject props = envelope.getJSONObject("message").getJSONObject("properties");
        assertFalse("$is_ai_bot should not be present", props.has("$is_ai_bot"));
    }

    // === PROPERTY PRESERVATION ===

    public void testPreservesUserProperties() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "GPTBot/1.2");
        properties.put("page_url", "/products");
        properties.put("custom_prop", "value");
        properties.put("count", 42);
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        JSONObject props = envelope.getJSONObject("message").getJSONObject("properties");
        assertEquals("/products", props.getString("page_url"));
        assertEquals("value", props.getString("custom_prop"));
        assertEquals(42, props.getInt("count"));
        assertTrue("$is_ai_bot should be present", props.getBoolean("$is_ai_bot"));
    }

    public void testPreservesSDKDefaultProperties() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "GPTBot/1.2");
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        JSONObject props = envelope.getJSONObject("message").getJSONObject("properties");
        assertEquals(TOKEN, props.getString("token"));
        assertTrue("Time should be set", props.has("time"));
        assertEquals("jdk", props.getString("mp_lib"));
        assertEquals("user123", props.getString("distinct_id"));
    }

    public void testPreservesEventName() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "GPTBot/1.2");
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        assertEquals("page_view", envelope.getJSONObject("message").getString("event"));
    }

    public void testPreservesEnvelopeStructure() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "GPTBot/1.2");
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        assertEquals(1, envelope.getInt("envelope_version"));
        assertEquals("event", envelope.getString("message_type"));
        assertTrue("message key should exist", envelope.has("message"));
    }

    // === DOES NOT MUTATE ORIGINAL ===

    public void testDoesNotMutateOriginalProperties() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "GPTBot/1.2");
        properties.put("page", "/home");
        String originalString = properties.toString();
        mBotBuilder.event("user123", "page_view", properties);
        assertEquals("Original properties should not be mutated", originalString, properties.toString());
    }

    // === END-TO-END WITH MixpanelAPI ===

    public void testEndToEndWithSendMessage() throws JSONException {
        final Map<String, String> sawData = new HashMap<String, String>();
        MixpanelAPI api = new MixpanelAPI("events url", "people url", "groups url") {
            @Override
            public boolean sendData(String dataString, String endpointUrl) {
                sawData.put(endpointUrl, dataString);
                return true;
            }
        };
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "Mozilla/5.0 (compatible; ClaudeBot/1.0; +claudebot@anthropic.com)");
        properties.put("page", "/about");
        JSONObject envelope = mBotBuilder.event("user123", "page_view", properties);
        try { api.sendMessage(envelope); }
        catch (IOException e) { fail("IOException during sendMessage: " + e.toString()); }
        String sentData = sawData.get("events url?ip=0");
        assertNotNull("Event data should have been sent", sentData);
        JSONArray sentArray = new JSONArray(sentData);
        assertEquals(1, sentArray.length());
        JSONObject sentProps = sentArray.getJSONObject(0).getJSONObject("properties");
        assertTrue(sentProps.getBoolean("$is_ai_bot"));
        assertEquals("ClaudeBot", sentProps.getString("$ai_bot_name"));
        assertEquals("Anthropic", sentProps.getString("$ai_bot_provider"));
        assertEquals("indexing", sentProps.getString("$ai_bot_category"));
        assertEquals("/about", sentProps.getString("page"));
        assertEquals(TOKEN, sentProps.getString("token"));
    }

    public void testEndToEndWithClientDelivery() throws JSONException {
        final Map<String, String> sawData = new HashMap<String, String>();
        MixpanelAPI api = new MixpanelAPI("events url", "people url", "groups url") {
            @Override
            public boolean sendData(String dataString, String endpointUrl) {
                sawData.put(endpointUrl, dataString);
                return true;
            }
        };
        ClientDelivery delivery = new ClientDelivery();
        // Bot event
        JSONObject botProps = new JSONObject();
        botProps.put("$user_agent", "GPTBot/1.2");
        delivery.addMessage(mBotBuilder.event("bot1", "page_view", botProps));
        // Non-bot event
        JSONObject userProps = new JSONObject();
        userProps.put("$user_agent", "Chrome/120.0.0.0");
        delivery.addMessage(mBotBuilder.event("user1", "page_view", userProps));
        // No user-agent event
        JSONObject noUaProps = new JSONObject();
        noUaProps.put("page", "/home");
        delivery.addMessage(mBotBuilder.event("user2", "page_view", noUaProps));
        try { api.deliver(delivery); }
        catch (IOException e) { fail("IOException during deliver: " + e.toString()); }
        String sentData = sawData.get("events url?ip=0");
        assertNotNull("Event data should have been sent", sentData);
        JSONArray sentArray = new JSONArray(sentData);
        assertEquals("Should have sent three events", 3, sentArray.length());
        // Bot event
        JSONObject botSentProps = sentArray.getJSONObject(0).getJSONObject("properties");
        assertTrue(botSentProps.getBoolean("$is_ai_bot"));
        assertEquals("GPTBot", botSentProps.getString("$ai_bot_name"));
        // Non-bot event
        JSONObject userSentProps = sentArray.getJSONObject(1).getJSONObject("properties");
        assertFalse(userSentProps.getBoolean("$is_ai_bot"));
        // No user-agent event
        JSONObject noUaSentProps = sentArray.getJSONObject(2).getJSONObject("properties");
        assertFalse("No-UA event: $is_ai_bot should not be present", noUaSentProps.has("$is_ai_bot"));
    }

    // === MULTIPLE BOT TYPES ===

    public void testClassifiesMultipleDifferentBots() throws JSONException {
        String[][] bots = {
            {"GPTBot/1.2", "GPTBot", "OpenAI"},
            {"ClaudeBot/1.0", "ClaudeBot", "Anthropic"},
            {"PerplexityBot/1.0", "PerplexityBot", "Perplexity"},
            {"CCBot/2.0", "CCBot", "Common Crawl"},
        };
        for (String[] botInfo : bots) {
            JSONObject properties = new JSONObject();
            properties.put("$user_agent", botInfo[0]);
            JSONObject envelope = mBotBuilder.event("user1", "page_view", properties);
            JSONObject props = envelope.getJSONObject("message").getJSONObject("properties");
            assertTrue("Failed for " + botInfo[0], props.getBoolean("$is_ai_bot"));
            assertEquals("Wrong name for " + botInfo[0], botInfo[1], props.getString("$ai_bot_name"));
            assertEquals("Wrong provider for " + botInfo[0], botInfo[2], props.getString("$ai_bot_provider"));
        }
    }

    // === DELEGATE PASSTHROUGH ===

    public void testDelegatesToMessageBuilderForPeopleMessages() throws JSONException {
        JSONObject setProps = new JSONObject();
        setProps.put("$name", "Test User");
        JSONObject setMessage = mBotBuilder.set("user123", setProps);
        assertEquals("people", setMessage.getString("message_type"));
        JSONObject msg = setMessage.getJSONObject("message");
        assertEquals("user123", msg.getString("$distinct_id"));
        assertEquals(TOKEN, msg.getString("$token"));
    }

    public void testDelegatesToMessageBuilderForGroupMessages() throws JSONException {
        JSONObject groupProps = new JSONObject();
        groupProps.put("$name", "Acme Inc.");
        JSONObject groupMessage = mBotBuilder.groupSet("company", "acme", groupProps);
        assertEquals("group", groupMessage.getString("message_type"));
        JSONObject msg = groupMessage.getJSONObject("message");
        assertEquals("company", msg.getString("$group_key"));
        assertEquals("acme", msg.getString("$group_id"));
    }

    public void testDelegatesToMessageBuilderForImportEvents() throws JSONException {
        JSONObject properties = new JSONObject();
        properties.put("$user_agent", "GPTBot/1.2");
        JSONObject importMessage = mBotBuilder.importEvent("user123", "page_view", properties);
        assertEquals("import", importMessage.getString("message_type"));
        JSONObject props = importMessage.getJSONObject("message").getJSONObject("properties");
        assertTrue("Import events should be classified too", props.getBoolean("$is_ai_bot"));
        assertEquals("GPTBot", props.getString("$ai_bot_name"));
    }
}
