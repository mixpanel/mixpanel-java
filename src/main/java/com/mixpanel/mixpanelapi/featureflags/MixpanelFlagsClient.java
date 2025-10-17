package com.mixpanel.mixpanelapi.featureflags;

import com.mixpanel.mixpanelapi.MessageBuilder;
import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.provider.LocalFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.RemoteFlagsProvider;

import org.json.JSONObject;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main client for Mixpanel feature flags functionality.
 * <p>
 * This client provides access to both local and remote feature flag evaluation modes.
 * It integrates with the main Mixpanel SDK to automatically track exposure events.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create client with local evaluation
 * LocalFlagsConfig localConfig = LocalFlagsConfig.builder()
 *     .pollingIntervalSeconds(60)
 *     .build();
 *
 * MixpanelFlagsClient flagsClient = new MixpanelFlagsClient(
 *     "YOUR_PROJECT_TOKEN",
 *     localConfig,
 *     null
 * );
 *
 * // Start polling for flag definitions
 * flagsClient.getLocalFlags().startPollingForDefinitions();
 *
 * // Evaluate a flag
 * Map<String, Object> context = new HashMap<>();
 * context.put("distinct_id", "user-123");
 * boolean enabled = flagsClient.getLocalFlags().isEnabled("new-feature", context);
 *
 * // Cleanup
 * flagsClient.close();
 * }</pre>
 * </p>
 * <p>
 * This class is thread-safe and implements AutoCloseable.
 * </p>
 */
public class MixpanelFlagsClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(MixpanelFlagsClient.class.getName());
    private static final String SDK_VERSION = "1.6.0-flags"; // Version with flags support

    private final String projectToken;
    private final LocalFlagsProvider localFlags;
    private final RemoteFlagsProvider remoteFlags;
    private final MessageBuilder messageBuilder;
    private final MixpanelAPI mixpanelAPI;

    /**
     * Creates a new MixpanelFlagsClient with both local and remote evaluation support.
     *
     * @param projectToken the Mixpanel project token
     * @param localFlagsConfig optional configuration for local evaluation (can be null)
     * @param remoteFlagsConfig optional configuration for remote evaluation (can be null)
     */
    public MixpanelFlagsClient(String projectToken, LocalFlagsConfig localFlagsConfig, RemoteFlagsConfig remoteFlagsConfig) {
        this(projectToken, localFlagsConfig, remoteFlagsConfig, new MixpanelAPI(), new MessageBuilder(projectToken));
    }

    /**
     * Creates a new MixpanelFlagsClient with custom MixpanelAPI and MessageBuilder instances.
     * <p>
     * This constructor is useful for testing or when you need to use custom endpoints.
     * </p>
     *
     * @param projectToken the Mixpanel project token
     * @param localFlagsConfig optional configuration for local evaluation (can be null)
     * @param remoteFlagsConfig optional configuration for remote evaluation (can be null)
     * @param mixpanelAPI the MixpanelAPI instance to use for sending events
     * @param messageBuilder the MessageBuilder instance to use for constructing events
     */
    public MixpanelFlagsClient(String projectToken, LocalFlagsConfig localFlagsConfig, RemoteFlagsConfig remoteFlagsConfig,
                               MixpanelAPI mixpanelAPI, MessageBuilder messageBuilder) {
        this.projectToken = projectToken;
        this.messageBuilder = messageBuilder;
        this.mixpanelAPI = mixpanelAPI;

        // Create local flags provider if config provided
        if (localFlagsConfig != null) {
            this.localFlags = new LocalFlagsProvider(
                projectToken,
                localFlagsConfig,
                SDK_VERSION,
                this::trackLocalExposure
            );
        } else {
            this.localFlags = null;
        }

        // Create remote flags provider if config provided
        if (remoteFlagsConfig != null) {
            this.remoteFlags = new RemoteFlagsProvider(
                projectToken,
                remoteFlagsConfig,
                SDK_VERSION,
                this::trackRemoteExposure
            );
        } else {
            this.remoteFlags = null;
        }
    }

    /**
     * Gets the local flags provider.
     *
     * @return the local flags provider, or null if not configured
     */
    public LocalFlagsProvider getLocalFlags() {
        return localFlags;
    }

    /**
     * Gets the remote flags provider.
     *
     * @return the remote flags provider, or null if not configured
     */
    public RemoteFlagsProvider getRemoteFlags() {
        return remoteFlags;
    }

    /**
     * Tracks an exposure event for local evaluation.
     */
    private void trackLocalExposure(String distinctId, String flagKey, String variantKey, String evaluationMode, long latencyMs) {
        try {
            JSONObject properties = new JSONObject();
            properties.put("Experiment name", flagKey);
            properties.put("Variant name", variantKey);
            properties.put("$experiment_type", "feature_flag");
            properties.put("Flag evaluation mode", evaluationMode);
            properties.put("Variant fetch latency (ms)", latencyMs);

            JSONObject event = messageBuilder.event(distinctId, "$experiment_started", properties);
            mixpanelAPI.sendMessage(event);

            logger.fine("Tracked exposure event for flag: " + flagKey + ", variant: " + variantKey);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to send exposure event", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error tracking exposure event", e);
        }
    }

    /**
     * Tracks an exposure event for remote evaluation.
     */
    private void trackRemoteExposure(String distinctId, String flagKey, String variantKey, String evaluationMode,
                                     String startTime, String completeTime) {
        try {
            JSONObject properties = new JSONObject();
            properties.put("Experiment name", flagKey);
            properties.put("Variant name", variantKey);
            properties.put("$experiment_type", "feature_flag");
            properties.put("Flag evaluation mode", evaluationMode);
            properties.put("Variant fetch start time", startTime);
            properties.put("Variant fetch complete time", completeTime);

            JSONObject event = messageBuilder.event(distinctId, "$experiment_started", properties);
            mixpanelAPI.sendMessage(event);

            logger.fine("Tracked exposure event for flag: " + flagKey + ", variant: " + variantKey);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to send exposure event", e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error tracking exposure event", e);
        }
    }

    @Override
    public void close() {
        if (localFlags != null) {
            try {
                localFlags.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing local flags provider", e);
            }
        }

        if (remoteFlags != null) {
            try {
                remoteFlags.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error closing remote flags provider", e);
            }
        }
    }
}
