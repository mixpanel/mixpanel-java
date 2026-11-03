package com.mixpanel.mixpanelapi;

import com.mixpanel.mixpanelapi.featureflags.FlagContext;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
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
    public void testUserWithoutCompanyOrDeviceIsRejectedForHierarchicalGroups() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> hgBuilder.event(MixpanelIdentity.builder().userId("user_123").build(), "Signup", null));

        assertTrue(e.getMessage(), e.getMessage().contains("cannot be merged"));
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
    public void testCombinedDistinctIdCompanyWinsOverForCompany() {
        JSONObject envelope = hgBuilder.forCompany("other").set("$company:acme|$user:user_123",
                new JSONObject().put("a", 1));

        assertUserGroupMessage(envelope, "acme", "user_123");
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
    public void testIdentityCompanyWinsOverForCompany() {
        JSONObject envelope = hgBuilder.forCompany("other")
            .set(MixpanelIdentity.builder().userId("user_123").companyId("acme").build(), new JSONObject().put("role", "admin"));

        assertEquals("acme", envelope.getJSONObject("message").getString("$company_id"));
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

        assertTrue(e.getMessage(), e.getMessage().contains("groupSet"));
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
    public void testFlagContextFromLegacyIdentity() {
        Map<String, Object> context = FlagContext.builder()
            .identity(MixpanelIdentity.builder().distinctId("user_123").build())
            .build();

        assertEquals("user_123", context.get("distinct_id"));
        assertFalse(context.containsKey("$company_id"));
        assertFalse(context.containsKey("custom_properties"));
    }

    // #endregion
}
