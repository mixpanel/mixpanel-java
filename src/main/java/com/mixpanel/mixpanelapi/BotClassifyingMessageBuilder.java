package com.mixpanel.mixpanelapi;

import java.util.Collection;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Wrapper around {@link MessageBuilder} that enriches event properties with AI bot
 * classification data when a {@code $user_agent} property is present.
 *
 * <p>When creating event or import event messages with a {@code $user_agent} key,
 * the wrapper classifies the user-agent and injects:</p>
 * <ul>
 *   <li>{@code $is_ai_bot} (boolean) — always set when $user_agent is present</li>
 *   <li>{@code $ai_bot_name}, {@code $ai_bot_provider}, {@code $ai_bot_category} — set only for matches</li>
 * </ul>
 *
 * <p>If {@code $user_agent} is absent, the event passes through unchanged.
 * Requires zero modifications to existing SDK code.</p>
 *
 * @see AiBotClassifier
 * @see MessageBuilder
 */
public class BotClassifyingMessageBuilder {
    private static final String USER_AGENT_PROPERTY = "$user_agent";
    private final MessageBuilder mDelegate;
    private final AiBotClassifier mClassifier;

    /** Wraps the given MessageBuilder using the default AI bot database. */
    public BotClassifyingMessageBuilder(MessageBuilder delegate) { this(delegate, null); }

    /** Wraps the given MessageBuilder using a custom AiBotClassifier. */
    public BotClassifyingMessageBuilder(MessageBuilder delegate, AiBotClassifier classifier) {
        if (delegate == null) throw new IllegalArgumentException("delegate must not be null");
        mDelegate = delegate;
        mClassifier = classifier;
    }

    /** Creates an event message with AI bot classification enrichment. */
    public JSONObject event(String distinctId, String eventName, JSONObject properties) {
        return mDelegate.event(distinctId, eventName, enrichProperties(properties));
    }

    /** Creates an import event message with AI bot classification enrichment. */
    public JSONObject importEvent(String distinctId, String eventName, JSONObject properties) {
        return mDelegate.importEvent(distinctId, eventName, enrichProperties(properties));
    }

    // === Delegated People Profile Methods ===
    public JSONObject set(String distinctId, JSONObject properties) { return mDelegate.set(distinctId, properties); }
    public JSONObject set(String distinctId, JSONObject properties, JSONObject modifiers) { return mDelegate.set(distinctId, properties, modifiers); }
    public JSONObject setOnce(String distinctId, JSONObject properties) { return mDelegate.setOnce(distinctId, properties); }
    public JSONObject setOnce(String distinctId, JSONObject properties, JSONObject modifiers) { return mDelegate.setOnce(distinctId, properties, modifiers); }
    public JSONObject delete(String distinctId) { return mDelegate.delete(distinctId); }
    public JSONObject delete(String distinctId, JSONObject modifiers) { return mDelegate.delete(distinctId, modifiers); }
    public JSONObject increment(String distinctId, Map<String, Long> properties) { return mDelegate.increment(distinctId, properties); }
    public JSONObject increment(String distinctId, Map<String, Long> properties, JSONObject modifiers) { return mDelegate.increment(distinctId, properties, modifiers); }
    public JSONObject append(String distinctId, JSONObject properties) { return mDelegate.append(distinctId, properties); }
    public JSONObject append(String distinctId, JSONObject properties, JSONObject modifiers) { return mDelegate.append(distinctId, properties, modifiers); }
    public JSONObject remove(String distinctId, JSONObject properties) { return mDelegate.remove(distinctId, properties); }
    public JSONObject remove(String distinctId, JSONObject properties, JSONObject modifiers) { return mDelegate.remove(distinctId, properties, modifiers); }
    public JSONObject union(String distinctId, Map<String, JSONArray> properties) { return mDelegate.union(distinctId, properties); }
    public JSONObject union(String distinctId, Map<String, JSONArray> properties, JSONObject modifiers) { return mDelegate.union(distinctId, properties, modifiers); }
    public JSONObject unset(String distinctId, Collection<String> propertyNames) { return mDelegate.unset(distinctId, propertyNames); }
    public JSONObject unset(String distinctId, Collection<String> propertyNames, JSONObject modifiers) { return mDelegate.unset(distinctId, propertyNames, modifiers); }
    public JSONObject trackCharge(String distinctId, double amount, JSONObject properties) { return mDelegate.trackCharge(distinctId, amount, properties); }
    public JSONObject trackCharge(String distinctId, double amount, JSONObject properties, JSONObject modifiers) { return mDelegate.trackCharge(distinctId, amount, properties, modifiers); }

    // === Delegated Group Profile Methods ===
    public JSONObject groupSet(String groupKey, String groupId, JSONObject properties) { return mDelegate.groupSet(groupKey, groupId, properties); }
    public JSONObject groupSet(String groupKey, String groupId, JSONObject properties, JSONObject modifiers) { return mDelegate.groupSet(groupKey, groupId, properties, modifiers); }
    public JSONObject groupSetOnce(String groupKey, String groupId, JSONObject properties) { return mDelegate.groupSetOnce(groupKey, groupId, properties); }
    public JSONObject groupSetOnce(String groupKey, String groupId, JSONObject properties, JSONObject modifiers) { return mDelegate.groupSetOnce(groupKey, groupId, properties, modifiers); }
    public JSONObject groupDelete(String groupKey, String groupId) { return mDelegate.groupDelete(groupKey, groupId); }
    public JSONObject groupDelete(String groupKey, String groupId, JSONObject modifiers) { return mDelegate.groupDelete(groupKey, groupId, modifiers); }
    public JSONObject groupRemove(String groupKey, String groupId, JSONObject properties) { return mDelegate.groupRemove(groupKey, groupId, properties); }
    public JSONObject groupRemove(String groupKey, String groupId, JSONObject properties, JSONObject modifiers) { return mDelegate.groupRemove(groupKey, groupId, properties, modifiers); }
    public JSONObject groupUnion(String groupKey, String groupId, Map<String, JSONArray> properties) { return mDelegate.groupUnion(groupKey, groupId, properties); }
    public JSONObject groupUnion(String groupKey, String groupId, Map<String, JSONArray> properties, JSONObject modifiers) { return mDelegate.groupUnion(groupKey, groupId, properties, modifiers); }
    public JSONObject groupUnset(String groupKey, String groupId, Collection<String> propertyNames) { return mDelegate.groupUnset(groupKey, groupId, propertyNames); }
    public JSONObject groupUnset(String groupKey, String groupId, Collection<String> propertyNames, JSONObject modifiers) { return mDelegate.groupUnset(groupKey, groupId, propertyNames, modifiers); }

    // === Private Helpers ===

    private JSONObject enrichProperties(JSONObject properties) {
        if (properties == null || !properties.has(USER_AGENT_PROPERTY)) return properties;
        try {
            JSONObject enriched = new JSONObject(properties.toString());
            String userAgent = enriched.optString(USER_AGENT_PROPERTY, null);
            if (userAgent == null || userAgent.isEmpty()) return properties;
            AiBotClassification classification = (mClassifier != null)
                ? mClassifier.classifyUserAgent(userAgent) : AiBotClassifier.classify(userAgent);
            enriched.put("$is_ai_bot", classification.isAiBot());
            if (classification.isAiBot()) {
                enriched.put("$ai_bot_name", classification.getBotName());
                enriched.put("$ai_bot_provider", classification.getProvider());
                enriched.put("$ai_bot_category", classification.getCategory());
            }
            return enriched;
        } catch (JSONException e) {
            return properties;
        }
    }
}
