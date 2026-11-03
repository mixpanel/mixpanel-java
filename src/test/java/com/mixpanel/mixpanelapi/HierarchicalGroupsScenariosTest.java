package com.mixpanel.mixpanelapi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

/**
 * One test per hierarchical groups scenario. Each builds its messages, delivers them through
 * {@link ClientDelivery} and {@link MixpanelAPI}, and asserts the endpoint and exact body sent.
 */
public class HierarchicalGroupsScenariosTest {

    private static final String TOKEN = "test-token";
    private static final String USER_ID = "my_user_id";
    private static final String COMPANY_ID = "my_company_id";
    private static final String TEAM_ID = "my_team_id";
    private static final String DEVICE_ID = "my_device_id";
    private static final String COMBINED_DISTINCT_ID = "$company:" + COMPANY_ID + "|$user:" + USER_ID;

    private static final String TRACK = "/track";
    private static final String ENGAGE = "/engage";
    private static final String GROUPS = "/groups";
    private static final String IMPORT = "/import";

    private static final Set<String> EVENT_KEYS = setOf("event", "properties");
    private static final Set<String> EVENT_PROPERTY_KEYS = setOf("token", "time", "mp_lib");
    private static final Set<String> IMPORT_PROPERTY_KEYS = setOf("token", "time", "mp_lib", "$insert_id");
    private static final Set<String> GROUP_MESSAGE_KEYS = setOf("$token", "$group_key", "$group_id", "$time",
            "$company_id");

    private static final MixpanelIdentity IDENTIFIED =
            MixpanelIdentity.builder().userId(USER_ID).companyId(COMPANY_ID).build();
    private static final MixpanelIdentity IDENTIFIED_WITH_DEVICE =
            MixpanelIdentity.builder().userId(USER_ID).companyId(COMPANY_ID).deviceId(DEVICE_ID).build();

    private static final class Sent {
        final String endpoint;
        final JSONObject body;

        Sent(String endpoint, JSONObject body) {
            this.endpoint = endpoint;
            this.body = body;
        }
    }

    private final List<Sent> mSent = new ArrayList<>();
    private MixpanelAPI mApi;
    private MessageBuilder mBuilder;
    private MessageBuilder mCompany;
    private MessageBuilder mLegacyBuilder;

    @Before
    public void setUp() {
        mSent.clear();
        mApi = new MixpanelAPI() {
            @Override
            boolean sendData(String dataString, String endpointUrl) {
                record(dataString, endpointUrl);
                return true;
            }

            @Override
            boolean sendImportData(String dataString, String endpointUrl, String token) {
                record(dataString, endpointUrl);
                return true;
            }
        };
        mBuilder = new MessageBuilder(TOKEN, true);
        mCompany = mBuilder.forCompany(COMPANY_ID);
        mLegacyBuilder = new MessageBuilder(TOKEN);
    }

    private void record(String dataString, String endpointUrl) {
        String path = endpointUrl.substring(Config.BASE_ENDPOINT.length(), endpointUrl.indexOf('?'));
        JSONArray messages = new JSONArray(dataString);
        for (int i = 0; i < messages.length(); i++) {
            mSent.add(new Sent(path, messages.getJSONObject(i)));
        }
    }

    private void send(JSONObject... messages) {
        ClientDelivery delivery = new ClientDelivery();
        for (JSONObject message : messages) {
            delivery.addMessage(message);
        }
        try {
            mApi.deliver(delivery);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertNothingSent() {
        send();
        assertEquals("Expected no requests", 0, mSent.size());
    }

    private Sent onlySent(String endpoint) {
        assertEquals("Expected exactly one request", 1, mSent.size());
        Sent sent = mSent.get(0);
        assertEquals(endpoint, sent.endpoint);
        return sent;
    }

    // Asserts the single event request and returns its properties, checking their exact key set
    private JSONObject onlyEvent(String endpoint, String eventName, Set<String> baseKeys, String... extraKeys) {
        JSONObject event = onlySent(endpoint).body;
        assertEquals(EVENT_KEYS, keys(event));
        assertEquals(eventName, event.getString("event"));
        JSONObject props = event.getJSONObject("properties");
        Set<String> expected = new HashSet<>(baseKeys);
        expected.addAll(Arrays.asList(extraKeys));
        assertEquals(expected, keys(props));
        assertEquals(TOKEN, props.getString("token"));
        return props;
    }

    private JSONObject onlyTrack(String eventName, String... extraKeys) {
        return onlyEvent(TRACK, eventName, EVENT_PROPERTY_KEYS, extraKeys);
    }

    // Asserts the single /groups request and returns the value of its operation
    private Object onlyGroupsUpdate(String groupKey, String groupId, String operation) {
        JSONObject message = onlySent(GROUPS).body;
        Set<String> expected = new HashSet<>(GROUP_MESSAGE_KEYS);
        expected.add(operation);
        assertEquals(expected, keys(message));
        assertEquals(TOKEN, message.getString("$token"));
        assertEquals(groupKey, message.getString("$group_key"));
        assertEquals(groupId, message.getString("$group_id"));
        assertEquals(COMPANY_ID, message.getString("$company_id"));
        return message.get(operation);
    }

    private Object onlyUserUpdate(String operation) {
        return onlyGroupsUpdate("$user_id", USER_ID, operation);
    }

    private Object onlyCompanyUpdate(String operation) {
        return onlyGroupsUpdate("$company_id", COMPANY_ID, operation);
    }

    private Object onlyCustomGroupUpdate(String operation) {
        return onlyGroupsUpdate("team_id", TEAM_ID, operation);
    }

    private static Set<String> keys(JSONObject object) {
        Set<String> keys = new HashSet<>();
        for (Iterator<String> iter = object.keys(); iter.hasNext(); ) {
            keys.add(iter.next());
        }
        return keys;
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private static JSONObject json(Object... keyValues) {
        JSONObject object = new JSONObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            object.put((String) keyValues[i], keyValues[i + 1]);
        }
        return object;
    }

    private static Map<String, JSONArray> unionOf(String key, Object... values) {
        Map<String, JSONArray> union = new HashMap<>();
        union.put(key, new JSONArray(Arrays.asList(values)));
        return union;
    }

    private static void assertJsonEquals(Object expected, Object actual) {
        assertEquals(expected.toString(), actual.toString());
    }

    // Track (Anonymous)

    @Test
    public void testTrackAnonymous() {
        send(mBuilder.event(MixpanelIdentity.builder().deviceId(DEVICE_ID).build(), "Page View", null));

        JSONObject props = onlyTrack("Page View", "$device_id");
        assertEquals(DEVICE_ID, props.getString("$device_id"));
    }

    // Track (Identified)

    @Test
    public void testTrackIdentified() {
        send(mBuilder.event(IDENTIFIED_WITH_DEVICE, "Signup", null));

        JSONObject props = onlyTrack("Signup", "$user_id", "$company_id", "$device_id");
        assertEquals(USER_ID, props.getString("$user_id"));
        assertEquals(COMPANY_ID, props.getString("$company_id"));
        assertEquals(DEVICE_ID, props.getString("$device_id"));
    }

    // Track (Identified Without Device)

    @Test
    public void testTrackIdentifiedWithoutDevice() {
        send(mBuilder.event(IDENTIFIED, "Signup", null));

        JSONObject props = onlyTrack("Signup", "$user_id", "$company_id");
        assertEquals(USER_ID, props.getString("$user_id"));
        assertEquals(COMPANY_ID, props.getString("$company_id"));
    }

    // Track With Custom Group

    @Test
    public void testTrackWithCustomGroup() {
        send(mBuilder.event(IDENTIFIED, "Signup", json("team_id", TEAM_ID)));

        JSONObject props = onlyTrack("Signup", "$user_id", "$company_id", "team_id");
        assertEquals(TEAM_ID, props.get("team_id"));
    }

    // Import (Identified)

    @Test
    public void testImportIdentified() {
        send(mBuilder.importEvent(IDENTIFIED_WITH_DEVICE, "Signup", null));

        JSONObject props = onlyEvent(IMPORT, "Signup", IMPORT_PROPERTY_KEYS, "$user_id", "$company_id", "$device_id");
        assertEquals(USER_ID, props.getString("$user_id"));
        assertEquals(COMPANY_ID, props.getString("$company_id"));
        assertEquals(DEVICE_ID, props.getString("$device_id"));
    }

    // Identity Overrides Properties

    @Test
    public void testIdentityOverridesProperties() {
        JSONObject caller = json("$user_id", "other_user", "$company_id", "other_company", "plan", "pro");
        send(mBuilder.event(IDENTIFIED, "Signup", caller));

        JSONObject props = onlyTrack("Signup", "$user_id", "$company_id", "plan");
        assertEquals(USER_ID, props.getString("$user_id"));
        assertEquals(COMPANY_ID, props.getString("$company_id"));
        assertEquals("pro", props.getString("plan"));
    }

    // Track With Combined Distinct ID

    @Test
    public void testTrackWithCombinedDistinctId() {
        send(mBuilder.event(COMBINED_DISTINCT_ID, "Signup", null));

        JSONObject props = onlyTrack("Signup", "distinct_id");
        assertEquals(COMBINED_DISTINCT_ID, props.getString("distinct_id"));
    }

    // Track With Combined Distinct ID Identity

    @Test
    public void testTrackWithCombinedDistinctIdIdentity() {
        MixpanelIdentity identity = MixpanelIdentity.builder().distinctId(COMBINED_DISTINCT_ID).build();
        send(mBuilder.event(identity, "Signup", null));

        JSONObject props = onlyTrack("Signup", "distinct_id");
        assertEquals(COMBINED_DISTINCT_ID, props.getString("distinct_id"));
    }

    // Set User Property

    @Test
    public void testSetUserProperty() {
        send(mBuilder.set(IDENTIFIED, json("plan", "pro")));

        assertJsonEquals(json("plan", "pro"), onlyUserUpdate("$set"));
    }

    // Set User Property With Combined Distinct ID

    @Test
    public void testSetUserPropertyWithCombinedDistinctId() {
        send(mBuilder.set(COMBINED_DISTINCT_ID, json("plan", "pro")));

        // $group_id and $company_id are parsed from the combined distinct id
        assertJsonEquals(json("plan", "pro"), onlyUserUpdate("$set"));
    }

    // Set User Property With Combined Distinct ID Identity

    @Test
    public void testSetUserPropertyWithCombinedDistinctIdIdentity() {
        MixpanelIdentity identity = MixpanelIdentity.builder().distinctId(COMBINED_DISTINCT_ID).build();
        send(mBuilder.set(identity, json("plan", "pro")));

        assertJsonEquals(json("plan", "pro"), onlyUserUpdate("$set"));
    }

    // Set User Property With Distinct ID (Unsupported)

    @Test
    public void testSetUserPropertyWithDistinctIdIsUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> mBuilder.set(USER_ID, json("plan", "pro")));
        assertNothingSent();
    }

    // Set User Properties

    @Test
    public void testSetUserProperties() {
        send(mBuilder.set(IDENTIFIED, json("plan", "pro", "seats", 5)));

        JSONObject set = (JSONObject) onlyUserUpdate("$set");
        assertEquals(setOf("plan", "seats"), keys(set));
        assertEquals("pro", set.getString("plan"));
        assertEquals(5, set.getInt("seats"));
    }

    // Set Once User Property

    @Test
    public void testSetOnceUserProperty() {
        send(mBuilder.setOnce(IDENTIFIED, json("first_seen", "today")));

        assertJsonEquals(json("first_seen", "today"), onlyUserUpdate("$set_once"));
    }

    // Union User Property

    @Test
    public void testUnionUserProperty() {
        send(mBuilder.union(IDENTIFIED, unionOf("roles", "admin")));

        assertJsonEquals(json("roles", new JSONArray().put("admin")), onlyUserUpdate("$union"));
    }

    // Remove User Property

    @Test
    public void testRemoveUserProperty() {
        send(mBuilder.remove(IDENTIFIED, json("roles", "admin")));

        assertJsonEquals(json("roles", "admin"), onlyUserUpdate("$remove"));
    }

    // Unset User Property

    @Test
    public void testUnsetUserProperty() {
        send(mBuilder.unset(IDENTIFIED, Collections.singletonList("plan")));

        assertJsonEquals(new JSONArray().put("plan"), onlyUserUpdate("$unset"));
    }

    // Delete User

    @Test
    public void testDeleteUser() {
        send(mBuilder.delete(IDENTIFIED));

        assertJsonEquals(new JSONObject(), onlyUserUpdate("$delete"));
    }

    // Increment User Property (Unsupported)

    @Test
    public void testIncrementUserPropertyIsUnsupported() {
        Map<String, Long> increment = new HashMap<>();
        increment.put("logins", 1L);

        assertThrows(UnsupportedOperationException.class, () -> mBuilder.increment(USER_ID, increment));
        assertNothingSent();
    }

    // Append User Property (Unsupported)

    @Test
    public void testAppendUserPropertyIsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> mBuilder.append(USER_ID, json("roles", "admin")));
        assertNothingSent();
    }

    // Track Charge (Unsupported)

    @Test
    public void testTrackChargeIsUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> mBuilder.trackCharge(USER_ID, 9.99, null));
        assertNothingSent();
    }

    // Set Company Property

    @Test
    public void testSetCompanyProperty() {
        send(mCompany.groupSet("$company_id", COMPANY_ID, json("tier", "enterprise")));

        assertJsonEquals(json("tier", "enterprise"), onlyCompanyUpdate("$set"));
    }

    // Set Company Properties

    @Test
    public void testSetCompanyProperties() {
        send(mCompany.groupSet("$company_id", COMPANY_ID, json("tier", "enterprise", "seats", 50)));

        JSONObject set = (JSONObject) onlyCompanyUpdate("$set");
        assertEquals(setOf("tier", "seats"), keys(set));
        assertEquals("enterprise", set.getString("tier"));
        assertEquals(50, set.getInt("seats"));
    }

    // Set Once Company Property

    @Test
    public void testSetOnceCompanyProperty() {
        send(mCompany.groupSetOnce("$company_id", COMPANY_ID, json("founded", "2020")));

        assertJsonEquals(json("founded", "2020"), onlyCompanyUpdate("$set_once"));
    }

    // Union Company Property

    @Test
    public void testUnionCompanyProperty() {
        send(mCompany.groupUnion("$company_id", COMPANY_ID, unionOf("regions", "us")));

        assertJsonEquals(json("regions", new JSONArray().put("us")), onlyCompanyUpdate("$union"));
    }

    // Remove Company Property

    @Test
    public void testRemoveCompanyProperty() {
        send(mCompany.groupRemove("$company_id", COMPANY_ID, json("regions", "us")));

        assertJsonEquals(json("regions", "us"), onlyCompanyUpdate("$remove"));
    }

    // Unset Company Property

    @Test
    public void testUnsetCompanyProperty() {
        send(mCompany.groupUnset("$company_id", COMPANY_ID, Collections.singletonList("tier")));

        assertJsonEquals(new JSONArray().put("tier"), onlyCompanyUpdate("$unset"));
    }

    // Set User's Custom Group

    @Test
    public void testSetUsersCustomGroup() {
        send(mCompany.set(IDENTIFIED, json("team_id", TEAM_ID)));

        JSONObject set = (JSONObject) onlyUserUpdate("$set");
        assertEquals(setOf("team_id"), keys(set));
        assertEquals(TEAM_ID, set.get("team_id"));
    }

    // Remove User's Custom Group

    @Test
    public void testRemoveUsersCustomGroup() {
        send(mCompany.unset(IDENTIFIED, Collections.singletonList("team_id")));

        assertJsonEquals(new JSONArray().put("team_id"), onlyUserUpdate("$unset"));
    }

    // Set Custom Group Property

    @Test
    public void testSetCustomGroupProperty() {
        send(mCompany.groupSet("team_id", TEAM_ID, json("name", "Platform")));

        assertJsonEquals(json("name", "Platform"), onlyCustomGroupUpdate("$set"));
    }

    // Set Custom Group Properties

    @Test
    public void testSetCustomGroupProperties() {
        send(mCompany.groupSet("team_id", TEAM_ID, json("name", "Platform", "size", 8)));

        JSONObject set = (JSONObject) onlyCustomGroupUpdate("$set");
        assertEquals(setOf("name", "size"), keys(set));
        assertEquals("Platform", set.getString("name"));
        assertEquals(8, set.getInt("size"));
    }

    // Set Once Custom Group Property

    @Test
    public void testSetOnceCustomGroupProperty() {
        send(mCompany.groupSetOnce("team_id", TEAM_ID, json("created", "today")));

        assertJsonEquals(json("created", "today"), onlyCustomGroupUpdate("$set_once"));
    }

    // Union Custom Group Property

    @Test
    public void testUnionCustomGroupProperty() {
        send(mCompany.groupUnion("team_id", TEAM_ID, unionOf("tools", "jira")));

        assertJsonEquals(json("tools", new JSONArray().put("jira")), onlyCustomGroupUpdate("$union"));
    }

    // Remove Custom Group Property

    @Test
    public void testRemoveCustomGroupProperty() {
        send(mCompany.groupRemove("team_id", TEAM_ID, json("tools", "jira")));

        assertJsonEquals(json("tools", "jira"), onlyCustomGroupUpdate("$remove"));
    }

    // Unset Custom Group Property

    @Test
    public void testUnsetCustomGroupProperty() {
        send(mCompany.groupUnset("team_id", TEAM_ID, Collections.singletonList("name")));

        assertJsonEquals(new JSONArray().put("name"), onlyCustomGroupUpdate("$unset"));
    }

    // Delete Company

    @Test
    public void testDeleteCompany() {
        send(mCompany.groupDelete("$company_id", COMPANY_ID));

        assertJsonEquals(new JSONObject(), onlyCompanyUpdate("$delete"));
    }

    // Delete Custom Group

    @Test
    public void testDeleteCustomGroup() {
        send(mCompany.groupDelete("team_id", TEAM_ID));

        assertJsonEquals(new JSONObject(), onlyCustomGroupUpdate("$delete"));
    }

    // Identify Without Company

    @Test
    public void testIdentifyWithoutCompany() {
        MixpanelIdentity userOnly = MixpanelIdentity.builder().userId(USER_ID).build();

        // The event is sent as given; a user profile needs the company.
        assertThrows(IllegalStateException.class, () -> mBuilder.set(userOnly, json("plan", "pro")));
        assertNothingSent();

        send(mBuilder.event(userOnly, "Signup", null));
        JSONObject props = onlyTrack("Signup", "$user_id");
        assertEquals(USER_ID, props.getString("$user_id"));
    }

    // Set User Property Without User (Unsupported)

    @Test
    public void testSetUserPropertyWithoutUserIsUnsupported() {
        MixpanelIdentity companyOnly = MixpanelIdentity.builder().companyId(COMPANY_ID).build();

        assertThrows(IllegalArgumentException.class, () -> mBuilder.set(companyOnly, json("plan", "pro")));
        assertNothingSent();
    }

    // Set Company Property Without Company (Unsupported)

    @Test
    public void testSetCompanyPropertyWithoutCompanyIsUnsupported() {
        assertThrows(IllegalStateException.class,
                () -> mBuilder.groupSet("$company_id", COMPANY_ID, json("tier", "enterprise")));
        assertNothingSent();
    }

    // Set Custom Group Property Without Company (Unsupported)

    @Test
    public void testSetCustomGroupPropertyWithoutCompanyIsUnsupported() {
        assertThrows(IllegalStateException.class,
                () -> mBuilder.groupSet("team_id", TEAM_ID, json("name", "Platform")));
        assertNothingSent();
    }

    // Company Identity Without Hierarchical Groups (Unsupported)

    @Test
    public void testCompanyIdentityWithoutHierarchicalGroupsIsUnsupported() {
        assertThrows(IllegalArgumentException.class, () -> mLegacyBuilder.event(IDENTIFIED, "Signup", null));
        assertThrows(IllegalArgumentException.class, () -> mLegacyBuilder.importEvent(IDENTIFIED, "Signup", null));
        assertThrows(IllegalArgumentException.class, () -> mLegacyBuilder.set(IDENTIFIED, json("plan", "pro")));
        assertNothingSent();
    }

    // Track With Distinct ID (without hierarchical groups)

    @Test
    public void testTrackWithDistinctId() {
        send(mLegacyBuilder.event(USER_ID, "Signup", null));

        JSONObject props = onlyTrack("Signup", "distinct_id");
        assertEquals(USER_ID, props.getString("distinct_id"));
    }

    // Set User Property With Distinct ID (without hierarchical groups)

    @Test
    public void testSetUserPropertyWithDistinctId() {
        send(mLegacyBuilder.set(USER_ID, json("plan", "pro")));

        JSONObject message = onlySent(ENGAGE).body;
        assertEquals(setOf("$token", "$distinct_id", "$time", "$set"), keys(message));
        assertEquals(TOKEN, message.getString("$token"));
        assertEquals(USER_ID, message.getString("$distinct_id"));
        assertJsonEquals(json("plan", "pro"), message.get("$set"));
    }

    // Set Group Property (without hierarchical groups)

    @Test
    public void testSetGroupProperty() {
        send(mLegacyBuilder.groupSet("team_id", TEAM_ID, json("name", "Platform")));

        JSONObject message = onlySent(GROUPS).body;
        assertEquals(setOf("$token", "$group_key", "$group_id", "$time", "$set"), keys(message));
        assertEquals("team_id", message.getString("$group_key"));
        assertEquals(TEAM_ID, message.getString("$group_id"));
        assertFalse(message.has("$company_id"));
        assertJsonEquals(json("name", "Platform"), message.get("$set"));
    }
}
