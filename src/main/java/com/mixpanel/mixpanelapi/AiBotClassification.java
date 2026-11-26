package com.mixpanel.mixpanelapi;

/**
 * Result of classifying a user-agent string against the AI bot database.
 * If matched, {@link #isAiBot()} returns true and bot details are available.
 * If not matched, {@link #isAiBot()} returns false and all other fields are null.
 * Instances are immutable and thread-safe.
 *
 * @see AiBotClassifier
 */
public class AiBotClassification {
    private static final AiBotClassification NOT_A_BOT = new AiBotClassification(false, null, null, null);
    private final boolean mIsAiBot;
    private final String mBotName;
    private final String mProvider;
    private final String mCategory;

    private AiBotClassification(boolean isAiBot, String botName, String provider, String category) {
        mIsAiBot = isAiBot;
        mBotName = botName;
        mProvider = provider;
        mCategory = category;
    }

    static AiBotClassification match(String botName, String provider, String category) {
        return new AiBotClassification(true, botName, provider, category);
    }

    static AiBotClassification noMatch() { return NOT_A_BOT; }

    /** @return true if the user-agent was identified as an AI bot */
    public boolean isAiBot() { return mIsAiBot; }
    /** @return the bot name (e.g., "GPTBot"), or null if not an AI bot */
    public String getBotName() { return mBotName; }
    /** @return the bot provider (e.g., "OpenAI"), or null if not an AI bot */
    public String getProvider() { return mProvider; }
    /** @return the bot category ("indexing", "retrieval", or "agent"), or null if not an AI bot */
    public String getCategory() { return mCategory; }
}
