package com.mixpanel.mixpanelapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Classifies user-agent strings to determine whether they belong to known AI bots.
 * Use the static {@link #classify(String)} method for default classification, or
 * create a custom instance via {@link Builder} to add additional bot patterns.
 * Classification is synchronous and thread-safe.
 *
 * @see AiBotClassification
 * @see AiBotEntry
 */
public class AiBotClassifier {
    private static final List<AiBotEntry> DEFAULT_BOT_DATABASE;

    static {
        List<AiBotEntry> bots = new ArrayList<AiBotEntry>();
        bots.add(new AiBotEntry(Pattern.compile("GPTBot/", Pattern.CASE_INSENSITIVE),
            "GPTBot", "OpenAI", "indexing", "OpenAI web crawler for model training data"));
        bots.add(new AiBotEntry(Pattern.compile("ChatGPT-User/", Pattern.CASE_INSENSITIVE),
            "ChatGPT-User", "OpenAI", "retrieval", "ChatGPT real-time retrieval for user queries (RAG)"));
        bots.add(new AiBotEntry(Pattern.compile("OAI-SearchBot/", Pattern.CASE_INSENSITIVE),
            "OAI-SearchBot", "OpenAI", "indexing", "OpenAI search indexing crawler"));
        bots.add(new AiBotEntry(Pattern.compile("ClaudeBot/", Pattern.CASE_INSENSITIVE),
            "ClaudeBot", "Anthropic", "indexing", "Anthropic web crawler for model training"));
        bots.add(new AiBotEntry(Pattern.compile("Claude-User/", Pattern.CASE_INSENSITIVE),
            "Claude-User", "Anthropic", "retrieval", "Claude real-time retrieval for user queries"));
        bots.add(new AiBotEntry(Pattern.compile("Google-Extended/", Pattern.CASE_INSENSITIVE),
            "Google-Extended", "Google", "indexing", "Google AI training data crawler (separate from Googlebot)"));
        bots.add(new AiBotEntry(Pattern.compile("PerplexityBot/", Pattern.CASE_INSENSITIVE),
            "PerplexityBot", "Perplexity", "retrieval", "Perplexity AI search crawler"));
        bots.add(new AiBotEntry(Pattern.compile("Bytespider/", Pattern.CASE_INSENSITIVE),
            "Bytespider", "ByteDance", "indexing", "ByteDance/TikTok AI crawler"));
        bots.add(new AiBotEntry(Pattern.compile("CCBot/", Pattern.CASE_INSENSITIVE),
            "CCBot", "Common Crawl", "indexing", "Common Crawl bot (data used by many AI models)"));
        bots.add(new AiBotEntry(Pattern.compile("Applebot-Extended/", Pattern.CASE_INSENSITIVE),
            "Applebot-Extended", "Apple", "indexing", "Apple AI/Siri training data crawler"));
        bots.add(new AiBotEntry(Pattern.compile("Meta-ExternalAgent/", Pattern.CASE_INSENSITIVE),
            "Meta-ExternalAgent", "Meta", "indexing", "Meta/Facebook AI training data crawler"));
        bots.add(new AiBotEntry(Pattern.compile("cohere-ai/", Pattern.CASE_INSENSITIVE),
            "cohere-ai", "Cohere", "indexing", "Cohere AI training data crawler"));
        DEFAULT_BOT_DATABASE = Collections.unmodifiableList(bots);
    }

    private final List<AiBotEntry> mBotDatabase;

    private AiBotClassifier() { mBotDatabase = DEFAULT_BOT_DATABASE; }

    private AiBotClassifier(Builder builder) {
        List<AiBotEntry> combined = new ArrayList<AiBotEntry>(builder.mAdditionalBots);
        combined.addAll(DEFAULT_BOT_DATABASE);
        mBotDatabase = Collections.unmodifiableList(combined);
    }

    /**
     * Classify a user-agent string against the default AI bot database.
     * @param userAgent the user-agent string to classify, may be null
     * @return an {@link AiBotClassification} with the result; never null
     */
    public static AiBotClassification classify(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return AiBotClassification.noMatch();
        for (AiBotEntry bot : DEFAULT_BOT_DATABASE) {
            if (bot.matches(userAgent))
                return AiBotClassification.match(bot.getName(), bot.getProvider(), bot.getCategory());
        }
        return AiBotClassification.noMatch();
    }

    /**
     * Classify a user-agent string against this classifier's bot database
     * (including any custom bots added via {@link Builder}).
     * @param userAgent the user-agent string to classify, may be null
     * @return an {@link AiBotClassification} with the result; never null
     */
    public AiBotClassification classifyUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return AiBotClassification.noMatch();
        for (AiBotEntry bot : mBotDatabase) {
            if (bot.matches(userAgent))
                return AiBotClassification.match(bot.getName(), bot.getProvider(), bot.getCategory());
        }
        return AiBotClassification.noMatch();
    }

    /** Returns an unmodifiable view of the default bot database for inspection. */
    public static List<AiBotEntry> getBotDatabase() { return DEFAULT_BOT_DATABASE; }

    /**
     * Builder for creating an {@link AiBotClassifier} with custom bot patterns.
     * Custom bots are checked before built-in bots, allowing overrides.
     */
    public static class Builder {
        private final List<AiBotEntry> mAdditionalBots = new ArrayList<AiBotEntry>();

        /** Adds a custom bot entry. Custom bots are checked before built-in bots. */
        public Builder addBot(AiBotEntry entry) {
            if (entry == null) throw new IllegalArgumentException("entry must not be null");
            mAdditionalBots.add(entry);
            return this;
        }

        /** Adds multiple custom bot entries. Custom bots are checked before built-in bots. */
        public Builder addBots(List<AiBotEntry> entries) {
            if (entries == null) throw new IllegalArgumentException("entries must not be null");
            for (AiBotEntry entry : entries) {
                if (entry == null) {
                    throw new IllegalArgumentException("entries must not contain null elements");
                }
            }
            mAdditionalBots.addAll(entries);
            return this;
        }

        public AiBotClassifier build() { return new AiBotClassifier(this); }
    }
}
