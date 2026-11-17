package com.mixpanel.mixpanelapi.featureflags.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Known values for the multi-experiment hashes, matching FnvHashMixed in the flags Go service.
 */
public class HashUtilsTest {

    @Test
    public void allocationBucketMatchesKnownValues() {
        assertEquals(37, HashUtils.allocationBucket("abc", ""));
        assertEquals(32, HashUtils.allocationBucket("user123", "checkoutsalt1"));
        assertEquals(58, HashUtils.allocationBucket("user456", "checkoutsalt1"));
    }

    @Test
    public void mixedNormalizedHashMatchesKnownValue() {
        assertEquals(0.37f, HashUtils.mixedNormalizedHash("abc", ""), 0.0f);
    }
}
