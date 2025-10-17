package com.mixpanel.mixpanelapi.featureflags;

import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for MixpanelFlagsClient.
 */
public class MixpanelFlagsClientTest {

    private static final EventSender NO_OP_SENDER = (distinctId, eventName, properties) -> {};

    @Test
    public void testLocalFlagsClientCreation() {
        LocalFlagsConfig config = LocalFlagsConfig.builder()
            .projectToken("test-token")
            .enablePolling(false)
            .build();

        MixpanelFlagsClient client = new MixpanelFlagsClient(config, NO_OP_SENDER);

        assertNotNull(client.getLocalFlags());
        assertNull(client.getRemoteFlags());
    }

    @Test
    public void testRemoteFlagsClientCreation() {
        RemoteFlagsConfig config = RemoteFlagsConfig.builder()
            .projectToken("test-token")
            .build();

        MixpanelFlagsClient client = new MixpanelFlagsClient(config, NO_OP_SENDER);

        assertNull(client.getLocalFlags());
        assertNotNull(client.getRemoteFlags());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoConfigsThrowsException() {
        new MixpanelFlagsClient(null, null, NO_OP_SENDER);
    }
}
