package com.mixpanel.mixpanelapi;

import java.util.List;
import java.util.regex.Pattern;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class AiBotClassifierTest extends TestCase {

    public AiBotClassifierTest(String testName) { super(testName); }
    public static Test suite() { return new TestSuite(AiBotClassifierTest.class); }

    // === POSITIVE MATCHES: OpenAI ===

    public void testClassifiesGPTBot() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; GPTBot/1.2; +https://openai.com/gptbot)");
        assertTrue("GPTBot should be classified as AI bot", result.isAiBot());
        assertEquals("GPTBot", result.getBotName());
        assertEquals("OpenAI", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    public void testClassifiesChatGPTUser() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; ChatGPT-User/1.0; +https://openai.com/bot)");
        assertTrue("ChatGPT-User should be classified as AI bot", result.isAiBot());
        assertEquals("ChatGPT-User", result.getBotName());
        assertEquals("OpenAI", result.getProvider());
        assertEquals("retrieval", result.getCategory());
    }

    public void testClassifiesOAISearchBot() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 (compatible; OAI-SearchBot/1.0; +https://openai.com/searchbot)");
        assertTrue("OAI-SearchBot should be classified as AI bot", result.isAiBot());
        assertEquals("OAI-SearchBot", result.getBotName());
        assertEquals("OpenAI", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    // === POSITIVE MATCHES: Anthropic ===

    public void testClassifiesClaudeBot() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 (compatible; ClaudeBot/1.0; +claudebot@anthropic.com)");
        assertTrue("ClaudeBot should be classified as AI bot", result.isAiBot());
        assertEquals("ClaudeBot", result.getBotName());
        assertEquals("Anthropic", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    public void testClassifiesClaudeUser() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 (compatible; Claude-User/1.0)");
        assertTrue("Claude-User should be classified as AI bot", result.isAiBot());
        assertEquals("Claude-User", result.getBotName());
        assertEquals("Anthropic", result.getProvider());
        assertEquals("retrieval", result.getCategory());
    }

    // === POSITIVE MATCHES: Google, Perplexity, ByteDance, Common Crawl, Apple, Meta, Cohere ===

    public void testClassifiesGoogleExtended() {
        AiBotClassification result = AiBotClassifier.classify("Mozilla/5.0 (compatible; Google-Extended/1.0)");
        assertTrue(result.isAiBot());
        assertEquals("Google-Extended", result.getBotName());
        assertEquals("Google", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    public void testClassifiesPerplexityBot() {
        AiBotClassification result = AiBotClassifier.classify("Mozilla/5.0 (compatible; PerplexityBot/1.0)");
        assertTrue(result.isAiBot());
        assertEquals("PerplexityBot", result.getBotName());
        assertEquals("Perplexity", result.getProvider());
        assertEquals("retrieval", result.getCategory());
    }

    public void testClassifiesBytespider() {
        AiBotClassification result = AiBotClassifier.classify("Mozilla/5.0 (compatible; Bytespider/1.0)");
        assertTrue(result.isAiBot());
        assertEquals("Bytespider", result.getBotName());
        assertEquals("ByteDance", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    public void testClassifiesCCBot() {
        AiBotClassification result = AiBotClassifier.classify("CCBot/2.0 (https://commoncrawl.org/faq/)");
        assertTrue(result.isAiBot());
        assertEquals("CCBot", result.getBotName());
        assertEquals("Common Crawl", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    public void testClassifiesApplebotExtended() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Applebot-Extended/0.1");
        assertTrue(result.isAiBot());
        assertEquals("Applebot-Extended", result.getBotName());
        assertEquals("Apple", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    public void testClassifiesMetaExternalAgent() {
        AiBotClassification result = AiBotClassifier.classify("Mozilla/5.0 (compatible; Meta-ExternalAgent/1.0)");
        assertTrue(result.isAiBot());
        assertEquals("Meta-ExternalAgent", result.getBotName());
        assertEquals("Meta", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    public void testClassifiesCohereAi() {
        AiBotClassification result = AiBotClassifier.classify("Mozilla/5.0 (compatible; cohere-ai/1.0)");
        assertTrue(result.isAiBot());
        assertEquals("cohere-ai", result.getBotName());
        assertEquals("Cohere", result.getProvider());
        assertEquals("indexing", result.getCategory());
    }

    // === NEGATIVE CASES ===

    public void testDoesNotClassifyChromeAsAiBot() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        assertFalse("Chrome should NOT be classified as AI bot", result.isAiBot());
        assertNull(result.getBotName());
        assertNull(result.getProvider());
        assertNull(result.getCategory());
    }

    public void testDoesNotClassifyGooglebotAsAiBot() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        assertFalse("Regular Googlebot should NOT be classified as AI bot", result.isAiBot());
    }

    public void testDoesNotClassifyBingbotAsAiBot() {
        AiBotClassification result = AiBotClassifier.classify(
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)");
        assertFalse("Regular Bingbot should NOT be classified as AI bot", result.isAiBot());
    }

    public void testDoesNotClassifyCurlAsAiBot() {
        AiBotClassification result = AiBotClassifier.classify("curl/7.64.1");
        assertFalse("curl should NOT be classified as AI bot", result.isAiBot());
    }

    public void testEmptyUserAgent() {
        AiBotClassification result = AiBotClassifier.classify("");
        assertFalse("Empty string should NOT be classified as AI bot", result.isAiBot());
    }

    public void testNullUserAgent() {
        AiBotClassification result = AiBotClassifier.classify(null);
        assertFalse("null should NOT be classified as AI bot", result.isAiBot());
    }

    // === CASE INSENSITIVITY ===

    public void testCaseInsensitiveMatching() {
        AiBotClassification result = AiBotClassifier.classify("mozilla/5.0 (compatible; gptbot/1.2)");
        assertTrue("Case-insensitive match should work", result.isAiBot());
        assertEquals("GPTBot", result.getBotName());
    }

    public void testUpperCaseMatching() {
        AiBotClassification result = AiBotClassifier.classify("GPTBOT/1.2");
        assertTrue("Upper case match should work", result.isAiBot());
        assertEquals("GPTBot", result.getBotName());
    }

    // === RETURN SHAPE ===

    public void testMatchReturnsAllFields() {
        AiBotClassification result = AiBotClassifier.classify("GPTBot/1.2");
        assertTrue(result.isAiBot());
        assertNotNull(result.getBotName());
        assertNotNull(result.getProvider());
        assertNotNull(result.getCategory());
        assertTrue("category must be indexing, retrieval, or agent",
            "indexing".equals(result.getCategory()) ||
            "retrieval".equals(result.getCategory()) ||
            "agent".equals(result.getCategory()));
    }

    public void testNonMatchReturnsOnlyFalse() {
        AiBotClassification result = AiBotClassifier.classify("Chrome/120");
        assertFalse(result.isAiBot());
        assertNull(result.getBotName());
        assertNull(result.getProvider());
        assertNull(result.getCategory());
    }

    // === CUSTOM BOT REGISTRATION ===

    public void testCustomBotRegistration() {
        AiBotClassifier classifier = new AiBotClassifier.Builder()
            .addBot(new AiBotEntry(
                Pattern.compile("MyCustomBot/", Pattern.CASE_INSENSITIVE),
                "MyCustomBot", "CustomCorp", "indexing", "Custom bot for testing"))
            .build();
        AiBotClassification result = classifier.classifyUserAgent("Mozilla/5.0 (compatible; MyCustomBot/1.0)");
        assertTrue("Custom bot should be classified", result.isAiBot());
        assertEquals("MyCustomBot", result.getBotName());
        assertEquals("CustomCorp", result.getProvider());
    }

    public void testCustomBotTakesPriority() {
        AiBotClassifier classifier = new AiBotClassifier.Builder()
            .addBot(new AiBotEntry(
                Pattern.compile("GPTBot/", Pattern.CASE_INSENSITIVE),
                "GPTBot-Custom", "CustomProvider", "retrieval", "Overridden GPTBot"))
            .build();
        AiBotClassification result = classifier.classifyUserAgent("GPTBot/1.2");
        assertEquals("Custom bot should take priority", "GPTBot-Custom", result.getBotName());
        assertEquals("CustomProvider", result.getProvider());
        assertEquals("retrieval", result.getCategory());
    }

    public void testCustomBotWithBuiltInStillWorks() {
        AiBotClassifier classifier = new AiBotClassifier.Builder()
            .addBot(new AiBotEntry(
                Pattern.compile("MyBot/", Pattern.CASE_INSENSITIVE),
                "MyBot", "MyCorp", "indexing", "My bot"))
            .build();
        AiBotClassification custom = classifier.classifyUserAgent("MyBot/1.0");
        assertTrue("Custom bot detected", custom.isAiBot());
        assertEquals("MyBot", custom.getBotName());
        AiBotClassification builtIn = classifier.classifyUserAgent("ClaudeBot/1.0");
        assertTrue("Built-in bot still detected", builtIn.isAiBot());
        assertEquals("ClaudeBot", builtIn.getBotName());
    }

    // === BOT DATABASE ACCESSOR ===

    public void testGetBotDatabase() {
        List<AiBotEntry> db = AiBotClassifier.getBotDatabase();
        assertNotNull("Database should not be null", db);
        assertTrue("Database should have entries", db.size() >= 12);
    }

    public void testBotDatabaseEntriesHaveRequiredFields() {
        List<AiBotEntry> db = AiBotClassifier.getBotDatabase();
        for (AiBotEntry entry : db) {
            assertNotNull("Pattern should not be null", entry.getPattern());
            assertNotNull("Name should not be null", entry.getName());
            assertNotNull("Provider should not be null", entry.getProvider());
            assertNotNull("Category should not be null", entry.getCategory());
            assertTrue("Category must be valid",
                "indexing".equals(entry.getCategory()) ||
                "retrieval".equals(entry.getCategory()) ||
                "agent".equals(entry.getCategory()));
        }
    }
}
