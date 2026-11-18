package com.mixpanel.mixpanelapi;

import com.mixpanel.mixpanelapi.featureflags.EventSender;
import com.mixpanel.mixpanelapi.featureflags.FlagContext;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.provider.BaseFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.RemoteFlagsProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.*;

public class HierarchicalGroupsTest {

    private static final String TOKEN = "test-token";

    private MessageBuilder hgBuilder;
    private MessageBuilder legacyBuilder;

    @Before
    public void setUp() {
        hgBuilder = new MessageBuilder(TOKEN, true);
        legacyBuilder = new MessageBuilder(TOKEN);
    }

    private static JSONObject eventProperties(JSONObject envelope) {
        return envelope.getJSONObject("message").getJSONObject("properties");
    }

    // #region Events

    @Test
    public void testHierarchicalEventSendsUserAndCompanyWithoutDistinctId() {
        JSONObject props = eventProperties(hgBuilder.event(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(), "Signup", null));

        assertEquals("user_123", props.getString("$user_id"));
        assertEquals("acme", props.getString("$company_id"));
        assertFalse(props.has("distinct_id"));
        assertFalse(props.has("$device_id"));
        assertEquals(TOKEN, props.getString("token"));
    }

    @Test
    public void testHierarchicalEventWithDeviceId() {
        JSONObject props = eventProperties(
            hgBuilder.event(MixpanelIdentity.builder().userId("user_123").companyId("acme").deviceId("device_abc").build(), "Signup", null));

        assertEquals("device_abc", props.getString("$device_id"));
        assertFalse(props.has("distinct_id"));
    }

    @Test
    public void testCompanyOnlyEvent() {
        JSONObject props = eventProperties(hgBuilder.event(MixpanelIdentity.builder().companyId("acme").build(), "Renewed", null));

        assertEquals("acme", props.getString("$company_id"));
        assertFalse(props.has("$user_id"));
        assertFalse(props.has("distinct_id"));
    }

    @Test
    public void testLegacyIdentitySendsDistinctId() {
        JSONObject props = eventProperties(legacyBuilder.event(MixpanelIdentity.builder().distinctId("user_123").build(), "Signup", null));

        assertEquals("user_123", props.getString("distinct_id"));
        assertFalse(props.has("$user_id"));
        assertFalse(props.has("$company_id"));
    }

    @Test
    public void testIdentityOverridesCallerSuppliedKeysAndKeepsOtherProperties() {
        JSONObject caller = new JSONObject();
        caller.put("$company_id", "wrong");
        caller.put("plan", "enterprise");

        JSONObject props = eventProperties(hgBuilder.event(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(), "Signup", caller));

        assertEquals("acme", props.getString("$company_id"));
        assertEquals("enterprise", props.getString("plan"));
        assertEquals("wrong", caller.getString("$company_id"));
    }

    @Test
    public void testIdentityKeepsCallerSuppliedKeysItLeavesUnset() {
        JSONObject caller = new JSONObject();
        caller.put("distinct_id", "caller");
        caller.put("$device_id", "caller_device");
        caller.put("$user_id", "caller_user");
        caller.put("$company_id", "wrong");
        caller.put("plan", "enterprise");

        JSONObject props = eventProperties(hgBuilder.event(MixpanelIdentity.builder().companyId("acme").build(), "Renewed", caller));

        assertEquals("acme", props.getString("$company_id"));
        assertEquals("caller", props.getString("distinct_id"));
        assertEquals("caller_device", props.getString("$device_id"));
        assertEquals("caller_user", props.getString("$user_id"));
        assertEquals("enterprise", props.getString("plan"));
        assertEquals("wrong", caller.getString("$company_id"));

        JSONObject imported = eventProperties(hgBuilder.importEvent(
                MixpanelIdentity.builder().userId("user_123").companyId("acme").build(), "Renewed",
                new JSONObject().put("distinct_id", "caller")));
        assertEquals("caller", imported.getString("distinct_id"));
        assertEquals("user_123", imported.getString("$user_id"));
    }

    @Test
    public void testHierarchicalImportEvent() {
        JSONObject envelope = hgBuilder.importEvent(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(), "Signup", null);

        assertEquals("import", envelope.getString("message_type"));
        JSONObject props = eventProperties(envelope);
        assertEquals("user_123", props.getString("$user_id"));
        assertEquals("acme", props.getString("$company_id"));
        assertFalse(props.has("distinct_id"));
        assertTrue(props.has("$insert_id"));
    }

    @Test
    public void testIdentityRejectsEmptyValues() {
        // An empty string is a caller error; null simply leaves the value unset.
        assertThrows(IllegalArgumentException.class, () -> MixpanelIdentity.builder().userId(""));
        assertThrows(IllegalArgumentException.class, () -> MixpanelIdentity.builder().companyId(""));
        assertThrows(IllegalArgumentException.class, () -> MixpanelIdentity.builder().deviceId(""));
        assertThrows(IllegalArgumentException.class, () -> MixpanelIdentity.builder().distinctId("").build());
    }

    @Test
    public void testIdentityNeedsAtLeastOneValue() {
        assertThrows(IllegalArgumentException.class, () -> MixpanelIdentity.builder().build());
        assertThrows(IllegalArgumentException.class,
                () -> MixpanelIdentity.builder().userId(null).companyId(null).build());
    }

    @Test
    public void testNullLeavesAValueUnset() {
        MixpanelIdentity identity = MixpanelIdentity.builder().userId("user_123").deviceId(null).build();

        assertEquals("user_123", identity.getUserId());
        assertNull(identity.getDeviceId());
    }

    @Test
    public void testUserAndDeviceWithoutCompanyIsValidForSimplifiedIdMerge() {
        // $user_id with $device_id is the standard non-hierarchical server-side identity.
        JSONObject props = eventProperties(legacyBuilder.event(
                MixpanelIdentity.builder().userId("user_123").deviceId("device_abc").build(), "Signup", null));

        assertEquals("user_123", props.getString("$user_id"));
        assertEquals("device_abc", props.getString("$device_id"));
        assertFalse(props.has("distinct_id"));
        assertFalse(props.has("$company_id"));
    }

    @Test
    public void testUserWithoutCompanyIsSentAsGivenForHierarchicalGroups() {
        JSONObject props = eventProperties(hgBuilder.event(
                MixpanelIdentity.builder().userId("user_123").build(), "Signup", null));

        assertEquals("user_123", props.getString("$user_id"));
        assertFalse(props.has("$company_id"));
    }

    @Test
    public void testUserWithDeviceButNoCompanyIsAllowedForHierarchicalGroups() {
        // Mixpanel ignores the user id and keeps the event under the device, which later links to
        // the company-scoped user. Recoverable, so the library does not refuse it.
        JSONObject props = eventProperties(hgBuilder.event(
                MixpanelIdentity.builder().userId("user_123").deviceId("device_abc").build(), "Signup", null));

        assertEquals("device_abc", props.getString("$device_id"));
        assertFalse(props.has("$company_id"));
    }

    @Test
    public void testAnonymousDeviceEventIsValidForHierarchicalGroups() {
        JSONObject props = eventProperties(hgBuilder.event(
                MixpanelIdentity.builder().deviceId("device_abc").build(), "Page View", null));

        assertEquals("device_abc", props.getString("$device_id"));
        assertFalse(props.has("distinct_id"));
        assertFalse(props.has("$user_id"));
    }

    @Test
    public void testCombinedDistinctIdEventPassesThrough() {
        // Ingestion parses $company:{company}|$user:{user} back into $company_id and $user_id,
        // so a pipeline that can only carry one id resolves to the same user as the identity form.
        String combined = "$company:acme|$user:user_123";

        for (JSONObject envelope : Arrays.asList(
                hgBuilder.event(combined, "Signup", null),
                hgBuilder.event(MixpanelIdentity.builder().distinctId(combined).build(), "Signup", null))) {
            JSONObject props = eventProperties(envelope);
            assertEquals(combined, props.getString("distinct_id"));
            assertFalse(props.has("$user_id"));
        }
    }

    @Test
    public void testRawDistinctIdEventsAreNotRefused() {
        // Not the recommended form, but it is valid data that Mixpanel accepts, so the library
        // documents the consequence rather than refusing to build the message.
        assertEquals("user_123", eventProperties(hgBuilder.event("user_123", "Signup", null))
                .getString("distinct_id"));
        assertEquals("user_123", eventProperties(hgBuilder.importEvent("user_123", "Signup", null))
                .getString("distinct_id"));
        assertEquals("user_123", eventProperties(legacyBuilder.event("user_123", "Signup", null))
                .getString("distinct_id"));
    }

    @Test
    public void testAnonymousDeviceEvent() {
        JSONObject props = eventProperties(hgBuilder.event(MixpanelIdentity.builder().deviceId("device_abc").build(), "Page View", null));

        assertEquals("device_abc", props.getString("$device_id"));
        assertFalse(props.has("distinct_id"));
        assertFalse(props.has("$user_id"));
        assertFalse(props.has("$company_id"));
    }

    @Test
    public void testAnonymousDeviceEventWorksForLegacyProjects() {
        JSONObject props = eventProperties(legacyBuilder.event(MixpanelIdentity.builder().deviceId("device_abc").build(), "Page View", null));

        assertEquals("device_abc", props.getString("$device_id"));
        assertFalse(props.has("distinct_id"));
    }

    @Test
    public void testDeviceIdentityCannotIdentifyAProfile() {
        JSONObject props = new JSONObject().put("plan", "pro");

        assertThrows(IllegalArgumentException.class, () -> hgBuilder.set(MixpanelIdentity.builder().deviceId("device_abc").build(), props));
        assertThrows(IllegalArgumentException.class,
                () -> legacyBuilder.set(MixpanelIdentity.builder().deviceId("device_abc").build(), props));
    }

    // #endregion
    // #region User profiles

    @Test
    public void testAddAndAppendThrowForHierarchicalGroups() {
        // A hierarchical groups user profile is a group profile, and /groups has no $add or $append,
        // so these have no valid path at all.
        JSONObject props = new JSONObject().put("plan", "pro");
        Class<UnsupportedOperationException> expected = UnsupportedOperationException.class;

        assertThrows(expected, () -> hgBuilder.increment("user_123", Collections.singletonMap("logins", 1L)));
        assertThrows(expected, () -> hgBuilder.append("user_123", props));
        assertThrows(expected, () -> hgBuilder.trackCharge("user_123", 9.99, null));
        assertThrows(expected, () -> hgBuilder.peopleMessage("user_123", "$add", props, null));
        assertThrows(expected, () -> hgBuilder.peopleMessage("user_123", "$append", props, null));
    }

    @Test
    public void testSupportedPeopleMethodsWorkForHierarchicalGroups() {
        // A combined distinct id is parsed into its company and user and sent to /groups.
        JSONObject props = new JSONObject().put("plan", "pro");
        String id = "$company:acme|$user:user_123";

        JSONObject[] envelopes = {
            hgBuilder.set(id, props),
            hgBuilder.setOnce(id, props),
            hgBuilder.delete(id),
            hgBuilder.remove(id, props),
            hgBuilder.union(id, Collections.singletonMap("tags", new JSONArray().put("a"))),
            hgBuilder.unset(id, Arrays.asList("plan")),
        };
        String[] operations = {"$set", "$set_once", "$delete", "$remove", "$union", "$unset"};
        for (int i = 0; i < envelopes.length; i++) {
            assertUserGroupMessage(envelopes[i], "acme", "user_123");
            assertTrue(operations[i], envelopes[i].getJSONObject("message").has(operations[i]));
        }
    }

    private static void assertUserGroupMessage(JSONObject envelope, String companyId, String userId) {
        assertEquals("group", envelope.getString("message_type"));
        JSONObject message = envelope.getJSONObject("message");
        assertEquals(TOKEN, message.getString("$token"));
        assertEquals("$user_id", message.getString("$group_key"));
        assertEquals(userId, message.getString("$group_id"));
        assertEquals(companyId, message.getString("$company_id"));
        assertFalse(message.has("$distinct_id"));
    }

    @Test
    public void testRawDistinctIdProfileUpdateIsRejectedForHierarchicalGroups() {
        JSONObject props = new JSONObject().put("plan", "pro");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.set("user_123", props));
        assertTrue(e.getMessage(), e.getMessage().contains("$company:{company}|$user:{user}"));
        assertTrue(e.getMessage(), e.getMessage().contains("MixpanelIdentity"));

        assertThrows(IllegalArgumentException.class, () -> hgBuilder.setOnce("user_123", props));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.delete("user_123"));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.remove("user_123", props));
        assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.union("user_123", Collections.singletonMap("tags", new JSONArray().put("a"))));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.unset("user_123", Arrays.asList("plan")));
        assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.set(MixpanelIdentity.builder().distinctId("user_123").build(), props));
        assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.forCompany("acme").set("user_123", props));
    }

    @Test
    public void testMalformedCombinedDistinctIdIsRejected() {
        JSONObject props = new JSONObject().put("plan", "pro");
        String[] malformed = {
            "$company:acme|$user:",       // missing user
            "$company:|$user:user_123",   // missing company
            "$company:acme",              // no user part
            "$org:acme|$user:user_123",   // wrong prefix
            "company:acme|$user:user_123",
            "$user:user_123|$company:acme",
            "",
        };
        for (String id : malformed) {
            assertThrows(id, IllegalArgumentException.class, () -> hgBuilder.set(id, props));
            assertThrows(id, IllegalArgumentException.class,
                    () -> hgBuilder.peopleMessage(id, "$set", props, null));
        }
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.set((String) null, props));
    }

    @Test
    public void testCombinedDistinctIdSplitsOnFirstUserSeparator() {
        JSONObject envelope = hgBuilder.set("$company:acme|$user:user|$user:123", new JSONObject().put("a", 1));

        assertUserGroupMessage(envelope, "acme", "user|$user:123");
    }

    @Test
    public void testForCompanyRefusesACombinedDistinctIdFromAnotherCompany() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> hgBuilder.forCompany("other").set("$company:acme|$user:user_123", new JSONObject().put("a", 1)));

        assertTrue(e.getMessage().contains("belongs to company \"acme\""));
    }

    @Test
    public void testGenericPeopleMessageRewritesCombinedDistinctId() {
        JSONObject modifiers = new JSONObject().put("$ip", "1.2.3.4").put("$ignore_time", true);
        JSONObject envelope = hgBuilder.peopleMessage("$company:acme|$user:user_123", "$set",
                new JSONObject().put("plan", "pro"), modifiers);

        assertUserGroupMessage(envelope, "acme", "user_123");
        JSONObject message = envelope.getJSONObject("message");
        assertEquals("pro", message.getJSONObject("$set").getString("plan"));
        assertEquals("1.2.3.4", message.getString("$ip"));
        assertTrue(message.getBoolean("$ignore_time"));
        assertTrue(message.has("$time"));
    }

    @Test
    public void testGenericPeopleMessageAppliesRuleToDistinctIdModifier() {
        JSONObject props = new JSONObject().put("plan", "pro");
        JSONObject modifiers = new JSONObject().put("$distinct_id", "$company:acme|$user:user_123");

        JSONObject envelope = hgBuilder.peopleMessage("ignored", "$set", props, modifiers);
        assertUserGroupMessage(envelope, "acme", "user_123");
        // The caller's modifiers are not changed
        assertEquals("$company:acme|$user:user_123", modifiers.getString("$distinct_id"));

        assertThrows(IllegalArgumentException.class, () -> hgBuilder.peopleMessage("$company:acme|$user:user_123",
                "$set", props, new JSONObject().put("$distinct_id", "user_123")));
    }

    @Test
    public void testUnsupportedOperationIsReportedBeforeDistinctId() {
        JSONObject props = new JSONObject().put("plan", "pro");

        assertThrows(UnsupportedOperationException.class,
                () -> hgBuilder.peopleMessage("$company:acme|$user:user_123", "$add", props, null));
        assertThrows(UnsupportedOperationException.class,
                () -> hgBuilder.peopleMessage("not_combined", "$append", props, null));
    }

    @Test
    public void testUnsupportedModifierOperationThrowsOnHierarchicalUserProfile() {
        JSONObject props = new JSONObject().put("plan", "pro");
        for (String operation : Arrays.asList("$add", "$append", "$merge", "$foo")) {
            JSONObject modifiers = new JSONObject().put(operation, new JSONObject().put("logins", 1));
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                    () -> hgBuilder.peopleMessage("$company:acme|$user:user_123", "$set", props, modifiers));
            assertTrue(e.getMessage(), e.getMessage().startsWith(operation + " is not supported"));
            // Before the distinct id is checked, as for the actionType
            assertThrows(UnsupportedOperationException.class,
                    () -> hgBuilder.peopleMessage("not_combined", "$set", props, modifiers));
            assertThrows(UnsupportedOperationException.class,
                    () -> hgBuilder.set(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(),
                            props, modifiers));
        }
    }

    @Test
    public void testNonOperationAndSupportedModifiersBuildOnHierarchicalUserProfile() {
        JSONObject modifiers = new JSONObject()
                .put("$token", TOKEN).put("$time", 1L).put("$ip", "1.2.3.4").put("$ignore_time", true)
                .put("$ignore_alias", true).put("$mp_metadata", new JSONObject()).put("$company_id", "acme")
                .put("$group_key", "$user_id").put("$group_id", "user_123")
                .put("$unset", new JSONArray().put("old")).put("custom", 1);
        JSONObject envelope = hgBuilder.peopleMessage("$company:acme|$user:user_123", "$set",
                new JSONObject().put("plan", "pro"), modifiers);

        assertUserGroupMessage(envelope, "acme", "user_123");
        JSONObject message = envelope.getJSONObject("message");
        assertEquals("pro", message.getJSONObject("$set").getString("plan"));
        assertEquals("old", message.getJSONArray("$unset").getString(0));
    }

    @Test
    public void testLegacyPeopleMessageCopiesAnyModifier() {
        JSONObject envelope = legacyBuilder.peopleMessage("user_123", "$set", new JSONObject().put("plan", "pro"),
                new JSONObject().put("$add", new JSONObject().put("logins", 1)).put("$foo", 1));

        JSONObject message = envelope.getJSONObject("message");
        assertEquals("people", envelope.getString("message_type"));
        assertEquals(1, message.getJSONObject("$add").getInt("logins"));
        assertEquals(1, message.getInt("$foo"));
    }

    @Test
    public void testLegacyGenericPeopleMessageKeepsDistinctId() {
        JSONObject envelope = legacyBuilder.peopleMessage("$company:acme|$user:user_123", "$set",
                new JSONObject().put("plan", "pro"), null);

        assertEquals("people", envelope.getString("message_type"));
        JSONObject message = envelope.getJSONObject("message");
        assertEquals("$company:acme|$user:user_123", message.getString("$distinct_id"));
        assertFalse(message.has("$group_key"));
        assertFalse(message.has("$company_id"));
    }

    @Test
    public void testAddAndAppendErrorNamesWhatIsSupported() {
        UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                () -> hgBuilder.increment("user_123", Collections.singletonMap("logins", 1L)));

        // The whole point of throwing is that the message reaches whoever wrote the call.
        assertTrue(e.getMessage(), e.getMessage().contains("$add"));
        assertTrue(e.getMessage(), e.getMessage().contains("increment, append and trackCharge"));
    }

    @Test
    public void testPeopleMethodsUnchangedForLegacy() {
        JSONObject envelope = legacyBuilder.set("user_123", new JSONObject().put("plan", "pro"));

        assertNotNull(envelope);
        assertEquals("people", envelope.getString("message_type"));
        assertEquals("user_123", envelope.getJSONObject("message").getString("$distinct_id"));
    }

    @Test
    public void testDeliveryRejectsNullMessages() {
        ClientDelivery delivery = new ClientDelivery();

        assertFalse(delivery.isValidMessage(null));
        assertThrows(MixpanelMessageException.class, () -> delivery.addMessage(null));

        assertTrue(delivery.getPeopleMessages().isEmpty());
        assertTrue(delivery.getEventsMessages().isEmpty());
    }

    @Test
    public void testIdentitySetReroutesToUserGroupProfile() {
        JSONObject envelope = hgBuilder.set(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(), new JSONObject().put("role", "admin"));

        assertEquals("group", envelope.getString("message_type"));
        JSONObject message = envelope.getJSONObject("message");
        assertEquals("$user_id", message.getString("$group_key"));
        assertEquals("user_123", message.getString("$group_id"));
        assertEquals("acme", message.getString("$company_id"));
        assertEquals("admin", message.getJSONObject("$set").getString("role"));
        assertFalse(message.has("$distinct_id"));
    }

    @Test
    public void testEveryIdentityProfileOperationReroutes() {
        MixpanelIdentity identity = MixpanelIdentity.builder().userId("user_123").companyId("acme").build();
        JSONObject props = new JSONObject().put("plan", "pro");

        JSONObject[] envelopes = {
            hgBuilder.set(identity, props),
            hgBuilder.setOnce(identity, props),
            hgBuilder.delete(identity),
            hgBuilder.remove(identity, props),
            hgBuilder.union(identity, Collections.singletonMap("tags", new JSONArray().put("a"))),
            hgBuilder.unset(identity, Arrays.asList("plan")),
        };
        String[] operations = {"$set", "$set_once", "$delete", "$remove", "$union", "$unset"};
        for (int i = 0; i < envelopes.length; i++) {
            assertEquals("group", envelopes[i].getString("message_type"));
            JSONObject message = envelopes[i].getJSONObject("message");
            assertTrue(operations[i], message.has(operations[i]));
            assertEquals("acme", message.getString("$company_id"));
            assertEquals("user_123", message.getString("$group_id"));
        }
    }

    @Test
    public void testHierarchicalIdentityRequiresHierarchicalBuilder() {
        MixpanelIdentity identity = MixpanelIdentity.builder().userId("user_123").companyId("acme").build();
        JSONObject props = new JSONObject().put("role", "admin");

        assertThrows(IllegalArgumentException.class, () -> legacyBuilder.set(identity, props));
        assertThrows(IllegalArgumentException.class, () -> legacyBuilder.event(identity, "Signup", null));
        assertThrows(IllegalArgumentException.class, () -> legacyBuilder.importEvent(identity, "Signup", null));
        assertThrows(IllegalArgumentException.class,
            () -> legacyBuilder.event(MixpanelIdentity.builder().companyId("acme").build(), "Renewed", null));
    }

    @Test
    public void testForCompanyAcceptsAUserFromTheBoundCompany() {
        JSONObject envelope = hgBuilder.forCompany("acme")
            .set(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(), new JSONObject().put("role", "admin"));

        assertEquals("acme", envelope.getJSONObject("message").getString("$company_id"));
    }

    @Test
    public void testForCompanyRefusesAUserFromAnotherCompany() {
        MixpanelIdentity user = MixpanelIdentity.builder().userId("user_123").companyId("acme").build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> hgBuilder.forCompany("other").set(user, new JSONObject().put("role", "admin")));

        assertTrue(e.getMessage().contains("bound to company \"other\""));
    }

    @Test
    public void testForCompanyUpdatesAUserOnlyIdentityWithinTheBoundCompany() {
        MixpanelIdentity userOnly = MixpanelIdentity.builder().userId("user_123").build();
        MessageBuilder acme = hgBuilder.forCompany("acme");
        JSONObject props = new JSONObject().put("role", "admin");

        assertUserGroupMessage(acme.set(userOnly, props), "acme", "user_123");
        assertUserGroupMessage(acme.set(userOnly, props, new JSONObject().put("$company_id", "globex")),
                "acme", "user_123");
    }

    @Test
    public void testUserOnlyIdentityUsesCompanyIdModifierWithoutBoundCompany() {
        MixpanelIdentity userOnly = MixpanelIdentity.builder().userId("user_123").build();
        JSONObject props = new JSONObject().put("role", "admin");

        assertUserGroupMessage(hgBuilder.set(userOnly, props, new JSONObject().put("$company_id", "acme")),
                "acme", "user_123");
        assertThrows(IllegalStateException.class, () -> hgBuilder.set(userOnly, props));
    }

    @Test
    public void testIdentityCompanyWinsOverCompanyIdModifier() {
        MixpanelIdentity user = MixpanelIdentity.builder().userId("user_123").companyId("acme").build();

        assertUserGroupMessage(hgBuilder.set(user, new JSONObject().put("role", "admin"),
                new JSONObject().put("$company_id", "globex")), "acme", "user_123");
    }

    @Test
    public void testEmptyCompanyIdModifierIsNotACompany() {
        MixpanelIdentity userOnly = MixpanelIdentity.builder().userId("user_123").build();
        JSONObject props = new JSONObject().put("role", "admin");

        for (Object empty : Arrays.asList("", JSONObject.NULL)) {
            JSONObject modifiers = new JSONObject().put("$company_id", empty);
            assertThrows(IllegalStateException.class, () -> hgBuilder.set(userOnly, props, modifiers));
            assertThrows(IllegalStateException.class, () -> hgBuilder.groupSet("department", "finance", props, modifiers));
        }
    }

    @Test
    public void testLegacyIdentityUsesEngage() {
        JSONObject envelope = legacyBuilder.set(MixpanelIdentity.builder().distinctId("user_123").build(), new JSONObject().put("role", "admin"));

        assertEquals("people", envelope.getString("message_type"));
        assertEquals("user_123", envelope.getJSONObject("message").getString("$distinct_id"));
    }

    @Test
    public void testCombinedDistinctIdIdentityProfileUpdateGoesToGroups() {
        JSONObject envelope = hgBuilder.set(MixpanelIdentity.builder().distinctId("$company:acme|$user:user_123").build(),
                new JSONObject().put("role", "admin"));

        assertUserGroupMessage(envelope, "acme", "user_123");
        assertEquals("admin", envelope.getJSONObject("message").getJSONObject("$set").getString("role"));
    }

    @Test
    public void testCompanyOnlyIdentityProfileUpdateIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.set(MixpanelIdentity.builder().companyId("acme").build(), new JSONObject().put("tier", "ent")));

        assertTrue(e.getMessage(), e.getMessage().contains("companySet"));
    }

    @Test
    public void testIdentityProfileUpdateRejectsNullIdentity() {
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.set((MixpanelIdentity) null, new JSONObject()));
    }

    // #endregion
    // #region Group profiles

    @Test
    public void testForCompanyAddsTopLevelCompanyId() {
        JSONObject message = hgBuilder.forCompany("acme")
            .groupSet("department", "finance", new JSONObject().put("plan", "enterprise"))
            .getJSONObject("message");

        assertEquals("acme", message.getString("$company_id"));
        assertEquals("department", message.getString("$group_key"));
        assertEquals("finance", message.getString("$group_id"));
        assertFalse(message.getJSONObject("$set").has("$company_id"));
    }

    @Test
    public void testUserAndCompanyProfilesHaveBareGroupIds() {
        MessageBuilder acme = hgBuilder.forCompany("acme");

        JSONObject user = acme.groupSet("$user_id", "user_123", new JSONObject().put("role", "admin"))
            .getJSONObject("message");
        assertEquals("$user_id", user.getString("$group_key"));
        assertEquals("user_123", user.getString("$group_id"));
        assertEquals("acme", user.getString("$company_id"));

        JSONObject company = acme.groupSet("$company_id", "acme", new JSONObject().put("tier", "ent"))
            .getJSONObject("message");
        assertEquals("$company_id", company.getString("$group_key"));
        assertEquals("acme", company.getString("$group_id"));
        assertEquals("acme", company.getString("$company_id"));
    }

    @Test
    public void testEveryGroupOperationCarriesCompany() {
        MessageBuilder acme = hgBuilder.forCompany("acme");
        JSONObject props = new JSONObject().put("plan", "enterprise");

        JSONObject[] messages = {
            acme.groupSet("department", "finance", props),
            acme.groupSetOnce("department", "finance", props),
            acme.groupDelete("department", "finance"),
            acme.groupRemove("department", "finance", props),
            acme.groupUnion("department", "finance", Collections.singletonMap("tags", new JSONArray().put("a"))),
            acme.groupUnset("department", "finance", Arrays.asList("plan")),
        };
        for (JSONObject envelope : messages) {
            assertEquals("acme", envelope.getJSONObject("message").getString("$company_id"));
        }
    }

    @Test
    public void testHierarchicalGroupWithoutCompanyIsRejected() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> hgBuilder.groupSet("department", "finance", new JSONObject().put("plan", "enterprise")));

        assertTrue(e.getMessage(), e.getMessage().contains("forCompany"));
    }

    @Test
    public void testCompanyIdModifierIsAccepted() {
        JSONObject modifiers = new JSONObject().put("$company_id", "acme");
        JSONObject envelope = hgBuilder.groupSet("department", "finance", new JSONObject().put("plan", "x"), modifiers);

        assertNotNull(envelope);
        assertEquals("acme", envelope.getJSONObject("message").getString("$company_id"));
    }

    @Test
    public void testForCompanyWinsOverModifier() {
        JSONObject modifiers = new JSONObject().put("$company_id", "other");
        JSONObject envelope = hgBuilder.forCompany("acme")
            .groupSet("department", "finance", new JSONObject().put("plan", "x"), modifiers);

        assertEquals("acme", envelope.getJSONObject("message").getString("$company_id"));
    }

    @Test
    public void testLegacyGroupUnchanged() {
        JSONObject message = legacyBuilder.groupSet("company", "Acme Inc.", new JSONObject().put("plan", "x"))
            .getJSONObject("message");

        assertFalse(message.has("$company_id"));
        assertEquals("company", message.getString("$group_key"));
    }

    @Test
    public void testUnsupportedGroupOperationStillBuildsMessage() {
        JSONObject envelope = legacyBuilder.groupMessage("company", "Acme Inc.", "$add",
            new JSONObject().put("seats", 1), null);

        assertNotNull(envelope);
        assertTrue(envelope.getJSONObject("message").has("$add"));
    }

    @Test
    public void testForCompanyRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.forCompany(""));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.forCompany(null));
    }

    @Test
    public void testForCompanyKeepsAddAndAppendRejected() {
        assertThrows(UnsupportedOperationException.class,
                () -> hgBuilder.forCompany("acme").increment("user_123", Collections.singletonMap("logins", 1L)));
    }

    @Test
    public void testUnsupportedGroupOperationThrows() {
        MessageBuilder acme = hgBuilder.forCompany("acme");
        for (String operation : Arrays.asList("$add", "$append", "$merge")) {
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                    () -> acme.groupMessage("company", "acme", operation, new JSONObject().put("seats", 1), null));
            assertTrue(e.getMessage(), e.getMessage().startsWith(operation + " is not supported"));
        }
    }

    @Test
    public void testUnsupportedGroupModifierOperationThrows() {
        MessageBuilder acme = hgBuilder.forCompany("acme");
        JSONObject properties = new JSONObject().put("seats", 1);
        for (String operation : Arrays.asList("$add", "$append", "$merge", "$foo")) {
            JSONObject modifiers = new JSONObject().put(operation, properties);
            UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
                    () -> acme.groupMessage("company", "acme", "$set", properties, modifiers));
            assertTrue(e.getMessage(), e.getMessage().startsWith(operation + " is not supported"));
            assertThrows(UnsupportedOperationException.class,
                    () -> acme.groupSet("company", "acme", properties, modifiers));
            // Before the company check, as for the actionType
            assertThrows(UnsupportedOperationException.class,
                    () -> hgBuilder.groupMessage("company", "acme", "$set", properties, modifiers));
        }
    }

    @Test
    public void testNonOperationGroupModifiersBuild() {
        JSONObject modifiers = new JSONObject()
                .put("$token", TOKEN).put("$time", 1L).put("$ip", "1.2.3.4").put("$ignore_time", true)
                .put("$ignore_alias", true).put("$mp_metadata", new JSONObject()).put("$distinct_id", "x")
                .put("$company_id", "acme").put("$remove", new JSONObject().put("tags", "a"));
        JSONObject envelope = hgBuilder.groupMessage("company", "acme", "$set", new JSONObject().put("seats", 1),
                modifiers);

        JSONObject message = envelope.getJSONObject("message");
        assertEquals("acme", message.getString("$company_id"));
        assertEquals(1, message.getJSONObject("$set").getInt("seats"));
        assertEquals("a", message.getJSONObject("$remove").getString("tags"));
    }

    @Test
    public void testLegacyGroupMessageCopiesAnyModifier() {
        JSONObject envelope = legacyBuilder.groupMessage("company", "acme", "$set", new JSONObject().put("seats", 1),
                new JSONObject().put("$add", new JSONObject().put("seats", 1)).put("$foo", 1));

        JSONObject message = envelope.getJSONObject("message");
        assertEquals(1, message.getJSONObject("$add").getInt("seats"));
        assertEquals(1, message.getInt("$foo"));
    }

    @Test
    public void testUnsupportedGroupOperationThrowsBeforeCompanyCheck() {
        assertThrows(UnsupportedOperationException.class,
                () -> hgBuilder.groupMessage("company", "acme", "$add", new JSONObject().put("seats", 1), null));
    }

    @Test
    public void testSupportedGroupOperationsBuild() {
        JSONObject properties = new JSONObject().put("seats", 1);
        MessageBuilder acme = hgBuilder.forCompany("acme");

        assertNotNull(acme.groupSet("department", "finance", properties));
        assertNotNull(acme.groupSetOnce("department", "finance", properties));
        assertNotNull(acme.groupUnset("department", "finance", Arrays.asList("seats")));
        assertNotNull(acme.groupUnion("department", "finance",
                Collections.singletonMap("tags", new JSONArray().put("a"))));
        assertNotNull(acme.groupRemove("department", "finance", properties));
        assertNotNull(acme.groupDelete("department", "finance"));
    }

    /** A company method call, followed by the equivalent group method call with the company named twice. */
    private static List<Function<MessageBuilder, JSONObject>> companyAndGroupCalls(JSONObject modifiers) {
        JSONObject props = new JSONObject().put("plan", "enterprise");
        Map<String, JSONArray> union = Collections.singletonMap("tags", new JSONArray().put("a"));
        List<String> unset = Arrays.asList("plan");
        if (modifiers == null) {
            return Arrays.asList(
                b -> b.companySet(props), b -> b.groupSet("$company_id", "acme", props),
                b -> b.companySetOnce(props), b -> b.groupSetOnce("$company_id", "acme", props),
                b -> b.companyDelete(), b -> b.groupDelete("$company_id", "acme"),
                b -> b.companyRemove(props), b -> b.groupRemove("$company_id", "acme", props),
                b -> b.companyUnion(union), b -> b.groupUnion("$company_id", "acme", union),
                b -> b.companyUnset(unset), b -> b.groupUnset("$company_id", "acme", unset));
        }
        return Arrays.asList(
            b -> b.companySet(props, modifiers), b -> b.groupSet("$company_id", "acme", props, modifiers),
            b -> b.companySetOnce(props, modifiers), b -> b.groupSetOnce("$company_id", "acme", props, modifiers),
            b -> b.companyDelete(modifiers), b -> b.groupDelete("$company_id", "acme", modifiers),
            b -> b.companyRemove(props, modifiers), b -> b.groupRemove("$company_id", "acme", props, modifiers),
            b -> b.companyUnion(union, modifiers), b -> b.groupUnion("$company_id", "acme", union, modifiers),
            b -> b.companyUnset(unset, modifiers), b -> b.groupUnset("$company_id", "acme", unset, modifiers));
    }

    private static void assertCompanyMethodsMatchGroupMethods(MessageBuilder acme, JSONObject modifiers) {
        List<Function<MessageBuilder, JSONObject>> calls = companyAndGroupCalls(modifiers);
        for (int i = 0; i < calls.size(); i += 2) {
            JSONObject company = calls.get(i).apply(acme);
            JSONObject group = calls.get(i + 1).apply(acme);
            if (modifiers == null) {
                // $time is the build time, so it can differ between the two calls.
                company.getJSONObject("message").remove("$time");
                group.getJSONObject("message").remove("$time");
            }
            assertEquals(group.toString(), company.toString());

            JSONObject message = company.getJSONObject("message");
            assertEquals("$company_id", message.getString("$group_key"));
            assertEquals("acme", message.getString("$group_id"));
            assertEquals("acme", message.getString("$company_id"));
        }
    }

    @Test
    public void testCompanyMethodsMatchGroupMethodsWithBoundCompany() {
        assertCompanyMethodsMatchGroupMethods(hgBuilder.forCompany("acme"), null);
        assertCompanyMethodsMatchGroupMethods(legacyBuilder.forCompany("acme"), null);
    }

    @Test
    public void testCompanyMethodsWithModifiersMatchGroupMethodsWithBoundCompany() {
        JSONObject modifiers = new JSONObject().put("$time", 1234567890L).put("$ignore_time", true);
        assertCompanyMethodsMatchGroupMethods(hgBuilder.forCompany("acme"), modifiers);
        assertCompanyMethodsMatchGroupMethods(legacyBuilder.forCompany("acme"), modifiers);
    }

    @Test
    public void testCompanyMethodsWithoutBoundCompanyAreRejected() {
        String[] names = {"companySet", "companySetOnce", "companyDelete", "companyRemove", "companyUnion",
            "companyUnset"};
        for (JSONObject modifiers : Arrays.asList(null, new JSONObject().put("$company_id", "acme"))) {
            List<Function<MessageBuilder, JSONObject>> calls = companyAndGroupCalls(modifiers);
            for (MessageBuilder unbound : Arrays.asList(hgBuilder, legacyBuilder)) {
                for (int i = 0; i < calls.size(); i += 2) {
                    Function<MessageBuilder, JSONObject> call = calls.get(i);
                    IllegalStateException e = assertThrows(IllegalStateException.class, () -> call.apply(unbound));
                    assertTrue(e.getMessage(), e.getMessage().contains("forCompany"));
                    assertTrue(e.getMessage(), e.getMessage().startsWith(names[i / 2]));
                }
            }
        }
    }

    /** Every group method with key $company_id and group id globex, in the order set, setOnce, delete,
     *  remove, union, unset, then the generic groupMessage. */
    private static List<Function<MessageBuilder, JSONObject>> globexCompanyGroupCalls(JSONObject modifiers) {
        JSONObject props = new JSONObject().put("plan", "pro");
        Map<String, JSONArray> union = Collections.singletonMap("tags", new JSONArray().put("a"));
        List<String> unset = Arrays.asList("plan");
        return Arrays.asList(
            b -> b.groupSet("$company_id", "globex", props, modifiers),
            b -> b.groupSetOnce("$company_id", "globex", props, modifiers),
            b -> b.groupDelete("$company_id", "globex", modifiers),
            b -> b.groupRemove("$company_id", "globex", props, modifiers),
            b -> b.groupUnion("$company_id", "globex", union, modifiers),
            b -> b.groupUnset("$company_id", "globex", unset, modifiers),
            b -> b.groupMessage("$company_id", "globex", "$set", props, modifiers));
    }

    private static void assertCompanyGroupMismatchRejected(MessageBuilder builder, JSONObject modifiers,
                                                           String company) {
        for (Function<MessageBuilder, JSONObject> call : globexCompanyGroupCalls(modifiers)) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> call.apply(builder));
            assertTrue(e.getMessage(), e.getMessage().contains("$company_id=globex"));
            assertTrue(e.getMessage(), e.getMessage().contains("company " + company));
            assertTrue(e.getMessage(), e.getMessage().contains("forCompany(globex)"));
            assertTrue(e.getMessage(), e.getMessage().endsWith("Nothing sent"));
        }
    }

    @Test
    public void testCompanyGroupWithAnotherBoundCompanyIsRejected() {
        // The service writes a $company_id group update to the top-level $company_id's profile.
        assertCompanyGroupMismatchRejected(hgBuilder.forCompany("acme"), null, "acme");
        assertCompanyGroupMismatchRejected(hgBuilder.forCompany("acme"),
            new JSONObject().put("$company_id", "globex"), "acme");
    }

    @Test
    public void testCompanyGroupWithAnotherCompanyIdModifierIsRejected() {
        assertCompanyGroupMismatchRejected(hgBuilder, new JSONObject().put("$company_id", "acme"), "acme");
    }

    @Test
    public void testGenericCompanyGroupMessageWithAnotherCompanyIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> hgBuilder.forCompany("acme").groupMessage("$company_id", "globex", "$set_once",
                new JSONObject().put("plan", "pro"), null));
        assertEquals("Group $company_id=globex does not match company acme. A company profile is updated "
            + "through forCompany(globex).companySet and the other company methods. Nothing sent", e.getMessage());
    }

    @Test
    public void testCompanyGroupMatchingBoundCompanyIsSentUnchanged() {
        JSONObject modifiers = new JSONObject().put("$time", 1234567890L);
        JSONObject envelope = hgBuilder.forCompany("globex")
            .groupSet("$company_id", "globex", new JSONObject().put("plan", "pro"), modifiers);

        JSONObject expected = new JSONObject()
            .put("envelope_version", 1)
            .put("message_type", "group")
            .put("message", new JSONObject()
                .put("$set", new JSONObject().put("plan", "pro"))
                .put("$token", TOKEN)
                .put("$group_key", "$company_id")
                .put("$group_id", "globex")
                .put("$time", 1234567890L)
                .put("$company_id", "globex"));
        assertTrue(envelope.toString(), expected.similar(envelope));
    }

    @Test
    public void testCompanyGroupMatchingCompanyIdModifierIsSent() {
        JSONObject message = hgBuilder.groupSet("$company_id", "globex", new JSONObject().put("plan", "pro"),
            new JSONObject().put("$company_id", "globex")).getJSONObject("message");

        assertEquals("globex", message.getString("$group_id"));
        assertEquals("globex", message.getString("$company_id"));
    }

    @Test
    public void testCompanyGroupComparesNumericCompanyIdModifierAsString() {
        JSONObject message = hgBuilder.groupSet("$company_id", "7", new JSONObject().put("plan", "pro"),
            new JSONObject().put("$company_id", 7)).getJSONObject("message");

        assertEquals("7", message.getString("$group_id"));
        // Written as a string, as a bound company is
        assertEquals("7", message.get("$company_id"));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.groupSet("$company_id", "8",
            new JSONObject().put("plan", "pro"), new JSONObject().put("$company_id", 7)));
    }

    @Test
    public void testCompanyGroupMismatchUnchangedForLegacy() {
        for (Function<MessageBuilder, JSONObject> call : globexCompanyGroupCalls(null)) {
            JSONObject message = call.apply(legacyBuilder.forCompany("acme")).getJSONObject("message");
            assertEquals("globex", message.getString("$group_id"));
            assertEquals("acme", message.getString("$company_id"));
        }
    }

    // #endregion
    // #region Retargeting modifiers

    @Test
    public void testGroupKeyModifierCannotRetargetUserProfileDeletion() {
        MixpanelIdentity user = MixpanelIdentity.builder().userId("user_123").companyId("acme").build();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.delete(user, new JSONObject().put("$group_key", "$company_id")));
        assertTrue(e.getMessage(), e.getMessage().startsWith("$group_key modifier"));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.forCompany("acme")
                .delete(MixpanelIdentity.builder().userId("user_123").build(),
                        new JSONObject().put("$group_key", "$company_id")));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.delete(COMBINED,
                new JSONObject().put("$group_key", "$company_id")));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.peopleMessage(COMBINED, "$delete",
                new JSONObject(), new JSONObject().put("$group_key", "$company_id")));
    }

    @Test
    public void testRetargetingModifiersAreRefusedOnEveryHierarchicalProfilePath() {
        MixpanelIdentity user = MixpanelIdentity.builder().userId("user_123").companyId("acme").build();
        MessageBuilder acme = hgBuilder.forCompany("acme");
        JSONObject props = new JSONObject().put("plan", "pro");
        List<JSONObject> retargets = Arrays.asList(
                new JSONObject().put("$group_id", "user_456"),
                new JSONObject().put("$group_key", "team"),
                new JSONObject().put("$token", "other-token"));
        for (JSONObject modifiers : retargets) {
            String key = JSONObject.getNames(modifiers)[0];
            List<ThrowingRunnable> calls = Arrays.asList(
                    () -> hgBuilder.set(user, props, modifiers),
                    () -> hgBuilder.set(COMBINED, props, modifiers),
                    () -> hgBuilder.peopleMessage(COMBINED, "$set", props, modifiers),
                    () -> acme.groupSet("department", "finance", props, modifiers),
                    () -> acme.groupMessage("department", "finance", "$set", props, modifiers),
                    () -> acme.companySet(props, modifiers),
                    () -> acme.companyDelete(modifiers));
            for (ThrowingRunnable call : calls) {
                IllegalArgumentException e = assertThrows(key + " call " + calls.indexOf(call),
                        IllegalArgumentException.class, call);
                assertTrue(e.getMessage(), e.getMessage().startsWith(key + " modifier"));
            }
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.set(user, props, new JSONObject().put("$distinct_id", COMBINED)));
        assertTrue(e.getMessage(), e.getMessage().startsWith("$distinct_id modifier"));
        MixpanelIdentity combined = MixpanelIdentity.builder().distinctId(COMBINED).build();
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.set(combined, props,
                new JSONObject().put("$distinct_id", "$company:acme|$user:user_456")));
    }

    @Test
    public void testOrdinaryModifiersStillBuildOnHierarchicalProfiles() {
        MixpanelIdentity user = MixpanelIdentity.builder().userId("user_123").companyId("acme").build();
        JSONObject modifiers = new JSONObject().put("$ip", "1.2.3.4").put("$time", 1L).put("$ignore_time", true)
                .put("$company_id", "acme");
        JSONObject envelope = hgBuilder.delete(user, modifiers);
        assertUserGroupMessage(envelope, "acme", "user_123");
        JSONObject message = envelope.getJSONObject("message");
        assertEquals("1.2.3.4", message.getString("$ip"));
        assertEquals(1L, message.getLong("$time"));
        assertTrue(message.getBoolean("$ignore_time"));
        assertUserGroupMessage(hgBuilder.set(COMBINED, new JSONObject().put("plan", "pro"), modifiers),
                "acme", "user_123");

        JSONObject company = hgBuilder.forCompany("acme").companySet(new JSONObject().put("plan", "pro"),
                modifiers).getJSONObject("message");
        assertEquals("$company_id", company.getString("$group_key"));
        assertEquals("acme", company.getString("$group_id"));
        assertEquals("1.2.3.4", company.getString("$ip"));
    }

    @Test
    public void testRetargetingModifiersUnchangedForLegacy() {
        JSONObject message = legacyBuilder.groupSet("company", "acme", new JSONObject().put("plan", "pro"),
                new JSONObject().put("$group_key", "other").put("$group_id", "globex").put("$token", "t2"))
                .getJSONObject("message");
        assertEquals("other", message.getString("$group_key"));
        assertEquals("globex", message.getString("$group_id"));
        assertEquals("t2", message.getString("$token"));
        JSONObject people = legacyBuilder.delete("user_123", new JSONObject().put("$distinct_id", "user_456"))
                .getJSONObject("message");
        assertEquals("user_456", people.getString("$distinct_id"));
    }

    // #endregion
    // #region Id validation found by service testing

    private static final String COMBINED = "$company:acme|$user:user_123";

    @Test
    public void testCompanyIdModifierOfAnotherTypeIsRejected() {
        MixpanelIdentity userOnly = MixpanelIdentity.builder().userId("user_123").build();
        JSONObject props = new JSONObject().put("plan", "pro");
        for (Object bad : Arrays.asList(new JSONObject().put("x", 1), new JSONArray().put("acme"), true, 1.5)) {
            JSONObject modifiers = new JSONObject().put("$company_id", bad);
            assertThrows(String.valueOf(bad), IllegalArgumentException.class,
                    () -> hgBuilder.groupSet("department", "finance", props, modifiers));
            assertThrows(String.valueOf(bad), IllegalArgumentException.class,
                    () -> hgBuilder.groupMessage("department", "finance", "$set", props, modifiers));
            assertThrows(String.valueOf(bad), IllegalArgumentException.class,
                    () -> hgBuilder.set(userOnly, props, modifiers));
            assertThrows(String.valueOf(bad), IllegalArgumentException.class,
                    () -> hgBuilder.set(COMBINED, props, modifiers));
        }
    }

    @Test
    public void testEmptyCompanyIdModifierStillMeansNoCompany() {
        JSONObject modifiers = new JSONObject().put("$company_id", "");
        assertThrows(IllegalStateException.class,
                () -> hgBuilder.groupSet("department", "finance", new JSONObject().put("plan", "x"), modifiers));
        // On a combined distinct id the parsed company is used
        assertUserGroupMessage(hgBuilder.set(COMBINED, new JSONObject().put("plan", "x"), modifiers),
                "acme", "user_123");
    }

    @Test
    public void testWhitespaceCompanyIdModifierIsRejected() {
        MixpanelIdentity userOnly = MixpanelIdentity.builder().userId("user_123").build();
        JSONObject props = new JSONObject().put("plan", "pro");
        for (String blank : Arrays.asList(" ", "  ", "\t\n")) {
            JSONObject modifiers = new JSONObject().put("$company_id", blank);
            assertThrows(IllegalArgumentException.class,
                    () -> hgBuilder.groupSet("department", "finance", props, modifiers));
            assertThrows(IllegalArgumentException.class, () -> hgBuilder.set(userOnly, props, modifiers));
        }
    }

    @Test
    public void testValidCompanyIdModifiersAreSent() {
        JSONObject props = new JSONObject().put("plan", "pro");
        for (Object valid : Arrays.asList("acme", " acme ", 7, 3190317L, new java.math.BigInteger("12345678901234567890"))) {
            JSONObject message = hgBuilder.groupSet("department", "finance", props,
                    new JSONObject().put("$company_id", valid)).getJSONObject("message");
            assertEquals(String.valueOf(valid), message.get("$company_id"));
            assertEquals("finance", message.getString("$group_id"));
        }
    }

    @Test
    public void testCompanyIdModifierIsNotValidatedForLegacy() {
        for (Object any : Arrays.asList("", " ", new JSONObject().put("x", 1), true, 7)) {
            JSONObject message = legacyBuilder.groupSet("company", "Acme", new JSONObject().put("plan", "x"),
                    new JSONObject().put("$company_id", any)).getJSONObject("message");
            assertEquals(any.toString(), message.get("$company_id").toString());
            assertEquals(any.getClass(), message.get("$company_id").getClass());
        }
    }

    @Test
    public void testCombinedDistinctIdEventWithConflictingCompanyIsRejected() {
        JSONObject props = new JSONObject().put("$company_id", "zz6");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.event(COMBINED, "Signup", props));
        assertEquals("distinct_id " + COMBINED + " names company acme, but $company_id is zz6. Nothing sent",
                e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.importEvent(COMBINED, "Signup", props));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.event(COMBINED, "Signup",
                new JSONObject().put("$company_id", 7)));
    }

    @Test
    public void testCombinedDistinctIdEventWithConflictingUserIsRejected() {
        JSONObject props = new JSONObject().put("$user_id", "other");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.event(COMBINED, "Signup", props));
        assertEquals("distinct_id " + COMBINED + " names user user_123, but $user_id is other. Nothing sent",
                e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.importEvent(COMBINED, "Signup", props));
    }

    @Test
    public void testCombinedDistinctIdEventWithMatchingIdsIsSentUnchanged() {
        String numeric = "$company:3190317|$user:3190316";
        JSONObject props = new JSONObject().put("$company_id", 3190317).put("$user_id", "3190316").put("plan", "x");

        for (JSONObject envelope : Arrays.asList(hgBuilder.event(numeric, "Signup", props),
                hgBuilder.importEvent(numeric, "Signup", props))) {
            JSONObject sent = eventProperties(envelope);
            assertEquals(numeric, sent.getString("distinct_id"));
            assertEquals(3190317, sent.get("$company_id"));
            assertEquals("3190316", sent.get("$user_id"));
            assertEquals("x", sent.get("plan"));
        }
    }

    @Test
    public void testNonCombinedDistinctIdEventKeepsAnyCompany() {
        JSONObject props = new JSONObject().put("$company_id", "zz6").put("$user_id", "other");
        for (String id : Arrays.asList("user_123", "$company:acme")) {
            JSONObject sent = eventProperties(hgBuilder.event(id, "Signup", props));
            assertEquals(id, sent.getString("distinct_id"));
            assertEquals("zz6", sent.getString("$company_id"));
        }
    }

    @Test
    public void testCombinedDistinctIdIdentityEventIsUnchanged() {
        JSONObject sent = eventProperties(hgBuilder.event(MixpanelIdentity.builder().distinctId(COMBINED).build(),
                "Signup", new JSONObject().put("$company_id", "zz6")));
        assertEquals(COMBINED, sent.getString("distinct_id"));
        assertEquals("zz6", sent.getString("$company_id"));
    }

    @Test
    public void testCombinedDistinctIdConflictIsNotCheckedForLegacy() {
        JSONObject props = new JSONObject().put("$company_id", "zz6").put("$user_id", "other");
        JSONObject sent = eventProperties(legacyBuilder.event(COMBINED, "Signup", props));
        assertEquals(COMBINED, sent.getString("distinct_id"));
        assertEquals("zz6", sent.getString("$company_id"));
        assertEquals("$company: |$user:u", eventProperties(legacyBuilder.event("$company: |$user:u", "Signup", null))
                .getString("distinct_id"));
        assertEquals("$company: |$user:u", legacyBuilder.set("$company: |$user:u", new JSONObject().put("a", 1))
                .getJSONObject("message").getString("$distinct_id"));
    }

    @Test
    public void testCombinedDistinctIdProfileWithConflictingCompanyModifierIsRejected() {
        JSONObject props = new JSONObject().put("plan", "pro");
        JSONObject modifiers = new JSONObject().put("$company_id", "zz6");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.set(COMBINED, props, modifiers));
        assertEquals("distinct_id " + COMBINED + " names company acme, but $company_id is zz6. Nothing sent",
                e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.peopleMessage(COMBINED, "$set", props, modifiers));
        assertThrows(IllegalArgumentException.class, () -> hgBuilder.peopleMessage("ignored", "$set", props,
                new JSONObject().put("$distinct_id", COMBINED).put("$company_id", "zz6")));
    }

    @Test
    public void testCombinedDistinctIdProfileWithMatchingCompanyModifierIsSent() {
        assertUserGroupMessage(hgBuilder.set(COMBINED, new JSONObject().put("plan", "pro"),
                new JSONObject().put("$company_id", "acme")), "acme", "user_123");
        assertUserGroupMessage(hgBuilder.set("$company:7|$user:u", new JSONObject().put("plan", "pro"),
                new JSONObject().put("$company_id", 7)), "7", "u");
    }

    @Test
    public void testCombinedDistinctIdIdentityProfileIsUnchanged() {
        assertUserGroupMessage(hgBuilder.set(MixpanelIdentity.builder().distinctId(COMBINED).build(),
                new JSONObject().put("plan", "pro"), new JSONObject().put("$company_id", "zz6")),
                "acme", "user_123");
    }

    @Test
    public void testIdentityRejectsWhitespaceOnlyUserAndCompany() {
        for (String blank : Arrays.asList(" ", "  ", "\t", "\u00a0")) {
            assertThrows(IllegalArgumentException.class, () -> MixpanelIdentity.builder().userId(blank));
            assertThrows(IllegalArgumentException.class, () -> MixpanelIdentity.builder().companyId(blank));
        }
        MixpanelIdentity padded = MixpanelIdentity.builder().userId(" u ").companyId(" c ").build();
        assertEquals(" u ", padded.getUserId());
        assertEquals(" c ", padded.getCompanyId());
        assertEquals(" ", MixpanelIdentity.builder().distinctId(" ").build().getDistinctId());
        assertEquals(" ", MixpanelIdentity.builder().deviceId(" ").build().getDeviceId());
    }

    @Test
    public void testForCompanyRejectsWhitespaceOnly() {
        for (MessageBuilder builder : Arrays.asList(hgBuilder, legacyBuilder)) {
            assertThrows(IllegalArgumentException.class, () -> builder.forCompany(" "));
            assertThrows(IllegalArgumentException.class, () -> builder.forCompany("\t "));
        }
        assertEquals(" acme ", hgBuilder.forCompany(" acme ")
                .companySet(new JSONObject().put("a", 1)).getJSONObject("message").getString("$company_id"));
    }

    @Test
    public void testCombinedDistinctIdWithBlankPartIsRejected() {
        JSONObject props = new JSONObject().put("plan", "pro");
        String[][] cases = {
            {"$company:|$user:u", "company"},
            {"$company:c|$user:", "user"},
            {"$company:|$user:", "company"},
            {"$company: |$user:", "company"},
            {"$company: |$user:u", "company"},
            {"$company:c|$user:  ", "user"},
        };
        for (String[] c : cases) {
            String id = c[0];
            String expected = "distinct_id " + id + " names a " + c[1]
                    + " that is empty or only whitespace. Nothing sent";
            MixpanelIdentity identity = MixpanelIdentity.builder().distinctId(id).build();
            List<ThrowingRunnable> calls = Arrays.asList(
                () -> hgBuilder.event(id, "Signup", null),
                () -> hgBuilder.event(id, "Signup", new JSONObject().put("plan", "pro")),
                () -> hgBuilder.importEvent(id, "Signup", null),
                () -> hgBuilder.event(identity, "Signup", null),
                () -> hgBuilder.importEvent(identity, "Signup", null),
                () -> hgBuilder.set(id, props),
                () -> hgBuilder.peopleMessage(id, "$set", props, null),
                () -> hgBuilder.set(identity, props));
            for (ThrowingRunnable call : calls) {
                IllegalArgumentException e = assertThrows(id, IllegalArgumentException.class, call);
                assertEquals(expected, e.getMessage());
            }
        }
    }

    @Test
    public void testBlankPartCheckLeavesOtherDistinctIdsAlone() {
        // A valid combined id and a non-combined id are still sent as given.
        for (String id : Arrays.asList("$company:acme|$user:user_123", "user_123", "$device:abc", "$company:acme")) {
            assertEquals(id, eventProperties(hgBuilder.event(id, "Signup", null)).getString("distinct_id"));
            assertEquals(id, eventProperties(hgBuilder.event(MixpanelIdentity.builder().distinctId(id).build(),
                    "Signup", null)).getString("distinct_id"));
        }
        // Non-blank parts are not trimmed.
        assertEquals("$company: acme |$user: u ", eventProperties(hgBuilder.event("$company: acme |$user: u ",
                "Signup", null)).getString("distinct_id"));
        // Without hierarchical groups a blank part is sent as given.
        for (String id : Arrays.asList("$company:|$user:", "$company: |$user:u", "$company:c|$user:  ")) {
            assertEquals(id, eventProperties(legacyBuilder.event(id, "Signup", null)).getString("distinct_id"));
            assertEquals(id, eventProperties(legacyBuilder.event(MixpanelIdentity.builder().distinctId(id).build(),
                    "Signup", null)).getString("distinct_id"));
            assertEquals(id, legacyBuilder.set(id, new JSONObject().put("plan", "pro"))
                    .getJSONObject("message").getString("$distinct_id"));
        }
    }

    @Test
    public void testIdentityWithCombinedDistinctIdNamingAnotherUserIsRejected() {
        String id = "$company:acme|$user:old";
        MixpanelIdentity identity = MixpanelIdentity.builder().distinctId(id).userId("new").companyId("acme").build();
        String expected = "distinct_id " + id + " names user old, but user_id is new. Nothing sent";
        JSONObject props = new JSONObject().put("plan", "pro");
        List<ThrowingRunnable> calls = Arrays.asList(
            () -> hgBuilder.set(identity, props),
            () -> hgBuilder.setOnce(identity, props),
            () -> hgBuilder.unset(identity, Collections.singletonList("plan")),
            () -> hgBuilder.remove(identity, props),
            () -> hgBuilder.union(identity, Collections.singletonMap("tags", new JSONArray().put("a"))),
            () -> hgBuilder.delete(identity),
            () -> hgBuilder.event(identity, "Signup", null),
            () -> hgBuilder.importEvent(identity, "Signup", null));
        for (ThrowingRunnable call : calls) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void testIdentityWithCombinedDistinctIdNamingAnotherCompanyIsRejected() {
        String expected = "distinct_id " + COMBINED + " names company acme, but company_id is zz6. Nothing sent";
        for (MixpanelIdentity identity : Arrays.asList(
                MixpanelIdentity.builder().distinctId(COMBINED).userId("user_123").companyId("zz6").build(),
                MixpanelIdentity.builder().distinctId(COMBINED).companyId("zz6").build())) {
            List<ThrowingRunnable> calls = Arrays.asList(
                () -> hgBuilder.set(identity, new JSONObject().put("plan", "pro")),
                () -> hgBuilder.delete(identity),
                () -> hgBuilder.event(identity, "Signup", null),
                () -> hgBuilder.importEvent(identity, "Signup", null));
            for (ThrowingRunnable call : calls) {
                IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
                assertEquals(expected, e.getMessage());
            }
        }
    }

    @Test
    public void testIdentityWithAgreeingCombinedDistinctIdIsSent() {
        String numeric = "$company:3190317|$user:3190316";
        for (MixpanelIdentity identity : Arrays.asList(
                MixpanelIdentity.builder().distinctId(numeric).userId("3190316").companyId("3190317").build(),
                MixpanelIdentity.builder().distinctId(numeric).userId("3190316").build(),
                MixpanelIdentity.builder().distinctId(numeric).companyId("3190317").build())) {
            assertUserGroupMessage(hgBuilder.set(identity, new JSONObject().put("plan", "pro")), "3190317", "3190316");
            assertUserGroupMessage(hgBuilder.delete(identity), "3190317", "3190316");
            JSONObject sent = eventProperties(hgBuilder.event(identity, "Signup", null));
            assertEquals(numeric, sent.getString("distinct_id"));
            assertEquals(numeric, eventProperties(hgBuilder.importEvent(identity, "Signup", null))
                    .getString("distinct_id"));
        }
        // A plain distinct id beside a user and company is not a combined id, so nothing is compared.
        MixpanelIdentity plain = MixpanelIdentity.builder().distinctId("old").userId("new").companyId("acme").build();
        assertEquals("old", eventProperties(hgBuilder.event(plain, "Signup", null)).getString("distinct_id"));
    }

    @Test
    public void testIdentityDistinctIdConflictIsNotCheckedForLegacy() {
        String id = "$company:acme|$user:old";
        MixpanelIdentity identity = MixpanelIdentity.builder().distinctId(id).userId("new").build();
        assertEquals(id, legacyBuilder.set(identity, new JSONObject().put("plan", "pro"))
                .getJSONObject("message").getString("$distinct_id"));
        assertEquals(id, legacyBuilder.delete(identity).getJSONObject("message").getString("$distinct_id"));
        JSONObject sent = eventProperties(legacyBuilder.event(identity, "Signup", null));
        assertEquals(id, sent.getString("distinct_id"));
        assertEquals("new", sent.getString("$user_id"));
    }

    // #endregion
    // #region Flag context

    @Test
    public void testFlagContextFromHierarchicalIdentity() {
        Map<String, Object> context = FlagContext.builder()
            .identity(MixpanelIdentity.builder().userId("user_123").companyId("acme").deviceId("device_abc").build())
            .group("department", "finance")
            .customProperty("plan", "enterprise")
            .build();

        assertEquals("user_123", context.get("$user_id"));
        assertEquals("acme", context.get("$company_id"));
        assertEquals("device_abc", context.get("device_id"));
        assertEquals("finance", context.get("department"));
        assertFalse(context.containsKey("distinct_id"));
        assertEquals("enterprise", ((Map<?, ?>) context.get("custom_properties")).get("plan"));
    }

    @Test
    public void testFlagContextIdentityWinsOverGroupsWhateverTheOrder() {
        MixpanelIdentity identity = MixpanelIdentity.builder().distinctId("d1").userId("user_123")
                .companyId("acme").deviceId("device_abc").build();

        Map<String, Object> groupsFirst = FlagContext.builder()
            .group("distinct_id", "g").group("$user_id", "g").group("$company_id", "g").group("device_id", "g")
            .group("custom_properties", "g")
            .customProperty("plan", "enterprise")
            .identity(identity)
            .build();
        Map<String, Object> identityFirst = FlagContext.builder()
            .identity(identity)
            .customProperty("plan", "enterprise")
            .group("distinct_id", "g").group("$user_id", "g").group("$company_id", "g").group("device_id", "g")
            .build();

        for (Map<String, Object> context : Arrays.asList(groupsFirst, identityFirst)) {
            assertEquals("d1", context.get("distinct_id"));
            assertEquals("user_123", context.get("$user_id"));
            assertEquals("acme", context.get("$company_id"));
            assertEquals("device_abc", context.get("device_id"));
            assertEquals("enterprise", ((Map<?, ?>) context.get("custom_properties")).get("plan"));
        }
    }

    @Test
    public void testFlagContextExplicitSettersWinOverGroupsWhateverTheOrder() {
        Map<String, Object> settersFirst = FlagContext.builder()
            .distinctId("d1").userId("user_123").companyId("acme").deviceId("device_abc")
            .group("distinct_id", "g").group("$user_id", "g").group("$company_id", "g").group("device_id", "g")
            .build();
        Map<String, Object> groupsFirst = FlagContext.builder()
            .group("distinct_id", "g").group("$user_id", "g").group("$company_id", "g").group("device_id", "g")
            .distinctId("d1").userId("user_123").companyId("acme").deviceId("device_abc")
            .build();

        for (Map<String, Object> context : Arrays.asList(settersFirst, groupsFirst)) {
            assertEquals("d1", context.get("distinct_id"));
            assertEquals("user_123", context.get("$user_id"));
            assertEquals("acme", context.get("$company_id"));
            assertEquals("device_abc", context.get("device_id"));
        }
    }

    @Test
    public void testFlagContextLaterIdentitySourceWins() {
        Map<String, Object> setterLast = FlagContext.builder()
            .identity(MixpanelIdentity.builder().userId("from_identity").companyId("acme").build())
            .userId("from_setter")
            .build();
        assertEquals("from_setter", setterLast.get("$user_id"));
        assertEquals("acme", setterLast.get("$company_id"));

        Map<String, Object> identityLast = FlagContext.builder()
            .userId("from_setter")
            .companyId("other")
            .identity(MixpanelIdentity.builder().userId("from_identity").companyId("acme").build())
            .build();
        assertEquals("from_identity", identityLast.get("$user_id"));
        assertEquals("acme", identityLast.get("$company_id"));
    }

    @Test
    public void testFlagContextFromLegacyIdentity() {
        Map<String, Object> context = FlagContext.builder()
            .identity(MixpanelIdentity.builder().distinctId("user_123").build())
            .build();

        assertEquals("user_123", context.get("distinct_id"));
        assertFalse(context.containsKey("$company_id"));
        assertFalse(context.containsKey("custom_properties"));
    }

    // #endregion

    // #region Flag exposures

    @Test
    public void testHierarchicalExposureWithoutDistinctIdIsSentWithUserAndCompany() throws Exception {
        // Drives a flag exposure through the event sender MixpanelAPI wires up, so the message
        // is built by MessageBuilder.event with a null distinct id and handed to sendData.
        RemoteFlagsConfig config = RemoteFlagsConfig.builder()
            .projectToken(TOKEN)
            .hierarchicalGroups(true)
            .build();
        final List<String> sent = new ArrayList<>();
        MixpanelAPI api = new MixpanelAPI(config) {
            @Override
            boolean sendData(String dataString, String endpointUrl) {
                sent.add(dataString);
                return true;
            }
        };
        java.lang.reflect.Field senderField = BaseFlagsProvider.class.getDeclaredField("eventSender");
        senderField.setAccessible(true);
        EventSender apiSender = (EventSender) senderField.get(api.mRemoteFlags);

        RemoteFlagsProvider provider = new RemoteFlagsProvider(config, "test", apiSender) {
            @Override
            protected String httpGet(String urlString) {
                return "{\"flags\":{\"test-flag\":{\"variant_key\":\"variant-a\",\"variant_value\":\"value-a\"}}}";
            }
        };
        Map<String, Object> context = new HashMap<>();
        context.put("$user_id", "user_123");
        context.put("$company_id", "acme");

        assertEquals("value-a", provider.getVariantValue("test-flag", "fallback", context));

        assertEquals(1, sent.size());
        JSONArray messages = new JSONArray(sent.get(0));
        assertEquals(1, messages.length());
        JSONObject event = messages.getJSONObject(0);
        assertEquals("$experiment_started", event.getString("event"));
        JSONObject props = event.getJSONObject("properties");
        assertFalse(props.has("distinct_id"));
        assertEquals("user_123", props.getString("$user_id"));
        assertEquals("acme", props.getString("$company_id"));
        assertEquals("test-flag", props.getString("Experiment name"));
    }

    // #endregion
}
