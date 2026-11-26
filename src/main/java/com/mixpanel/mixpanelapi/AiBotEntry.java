package com.mixpanel.mixpanelapi;

import java.util.regex.Pattern;

/**
 * Immutable entry in the AI bot database mapping a user-agent regex pattern
 * to a bot name, provider, category, and description.
 *
 * @see AiBotClassifier
 */
public class AiBotEntry {
    private final Pattern mPattern;
    private final String mName;
    private final String mProvider;
    private final String mCategory;
    private final String mDescription;

    /**
     * @param pattern compiled regex pattern to match against user-agent strings
     * @param name human-readable bot name (e.g., "GPTBot")
     * @param provider the organization operating the bot (e.g., "OpenAI")
     * @param category bot category: "indexing", "retrieval", or "agent"
     * @param description human-readable description of the bot's purpose
     */
    public AiBotEntry(Pattern pattern, String name, String provider, String category, String description) {
        if (pattern == null) throw new IllegalArgumentException("pattern must not be null");
        if (name == null) throw new IllegalArgumentException("name must not be null");
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        if (category == null) throw new IllegalArgumentException("category must not be null");
        mPattern = pattern;
        mName = name;
        mProvider = provider;
        mCategory = category;
        mDescription = description != null ? description : "";
    }

    public Pattern getPattern() { return mPattern; }
    public String getName() { return mName; }
    public String getProvider() { return mProvider; }
    public String getCategory() { return mCategory; }
    public String getDescription() { return mDescription; }

    /** Tests whether the given user-agent string matches this bot's pattern. */
    public boolean matches(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        return mPattern.matcher(userAgent).find();
    }
}
