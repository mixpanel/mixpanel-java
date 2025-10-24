package com.mixpanel.mixpanelapi.featureflags;

import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.provider.LocalFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.provider.RemoteFlagsProvider;
import com.mixpanel.mixpanelapi.featureflags.util.VersionUtil;

import org.json.JSONObject;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main client for Mixpanel feature flags functionality.
 * <p>
 * This client provides access to both local and remote feature flag evaluation modes.
 * It uses an EventSender to track exposure events.
 * </p>
 * <p>
 * Most users should use MixpanelAPI constructors which handle EventSender creation:
 * </p>
 * <pre>{@code
 * LocalFlagsConfig config = LocalFlagsConfig.builder()
 *     .projectToken("YOUR_PROJECT_TOKEN")
 *     .pollingIntervalSeconds(60)
 *     .build();
 *
 * MixpanelAPI mixpanel = new MixpanelAPI(config);
 * mixpanel.getLocalFlags().startPollingForDefinitions();
 *
 * boolean enabled = mixpanel.getLocalFlags().isEnabled("new-feature", context);
 * mixpanel.close();
 * }</pre>
 * <p>
 * Advanced users can create MixpanelFlagsClient directly with a custom EventSender:
 * </p>
 * <pre>{@code
 * EventSender customSender = (distinctId, eventName, properties) -> {
 *     // Custom event handling
 * };
 *
 * MixpanelFlagsClient client = new MixpanelFlagsClient(config, customSender);
 * }</pre>
 * <p>
 * This class is thread-safe and implements AutoCloseable.
 * </p>
 */
public class MixpanelFlagsClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(MixpanelFlagsClient.class.getName());

    private final String projectToken;
    private final LocalFlagsProvider localFlags;
    private final RemoteFlagsProvider remoteFlags;
    private final EventSender eventSender;

    /**
     * Creates a new MixpanelFlagsClient with local evaluation.
     *
     * @param localFlagsConfig configuration for local evaluation
     * @param eventSender the EventSender implementation for tracking exposure events
     */
    public MixpanelFlagsClient(LocalFlagsConfig localFlagsConfig, EventSender eventSender) {
        this(localFlagsConfig, null, eventSender);
    }

    /**
     * Creates a new MixpanelFlagsClient with remote evaluation.
     *
     * @param remoteFlagsConfig configuration for remote evaluation
     * @param eventSender the EventSender implementation for tracking exposure events
     */
    public MixpanelFlagsClient(RemoteFlagsConfig remoteFlagsConfig, EventSender eventSender) {
        this(null, remoteFlagsConfig, eventSender);
    }

    /**
     * Main constructor for MixpanelFlagsClient.
     * <p>
     * Either localFlagsConfig or remoteFlagsConfig must be non-null.
     * Most users should use the single-config constructors instead.
     * </p>
     *
     * @param localFlagsConfig optional configuration for local evaluation (can be null)
     * @param remoteFlagsConfig optional configuration for remote evaluation (can be null)
     * @param eventSender the EventSender implementation for tracking exposure events
     * @throws IllegalArgumentException if both configs are null or both are non-null
     */
    public MixpanelFlagsClient(LocalFlagsConfig localFlagsConfig, RemoteFlagsConfig remoteFlagsConfig,
                               EventSender eventSender) {
        if (localFlagsConfig == null && remoteFlagsConfig == null) {
            throw new IllegalArgumentException("Either localFlagsConfig or remoteFlagsConfig must be provided");
        }

        // Get project token from whichever config is non-null
        this.projectToken = localFlagsConfig != null ? localFlagsConfig.getProjectToken()
                                                      : remoteFlagsConfig.getProjectToken();
        this.eventSender = eventSender;

        // Create local flags provider if config provided
        if (localFlagsConfig != null) {
            this.localFlags = new LocalFlagsProvider(
                localFlagsConfig,
                VersionUtil.getVersion(),
                this::trackLocalExposure
            );
        } else {
            this.localFlags = null;
        }

        // Create remote flags provider if config provided
        if (remoteFlagsConfig != null) {
            this.remoteFlags = new RemoteFlagsProvider(
                remoteFlagsConfig,
                VersionUtil.getVersion(),
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
     * Common helper method for tracking exposure events.
     */
    private void trackExposure(String distinctId, String flagKey, String variantKey,
                              String evaluationMode, Consumer<JSONObject> addTimingProperties,
                              UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        try {
            JSONObject properties = new JSONObject();
            properties.put("Experiment name", flagKey);
            properties.put("Variant name", variantKey);
            properties.put("$experiment_type", "feature_flag");
            properties.put("Flag evaluation mode", evaluationMode);

            // Add experiment metadata
            if (experimentId != null) {
                properties.put("$experiment_id", experimentId.toString());
            }
            if (isExperimentActive != null) {
                properties.put("$is_experiment_active", isExperimentActive);
            }
            if (isQaTester != null) {
                properties.put("$is_qa_tester", isQaTester);
            }

            // Add timing-specific properties
            addTimingProperties.accept(properties);

            // Send via EventSender interface
            eventSender.sendEvent(distinctId, "$experiment_started", properties);

            logger.log(Level.FINE, "Tracked exposure event for flag: " + flagKey + ", variant: " + variantKey);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error tracking exposure event for flag: " + flagKey + ", variant: " + variantKey + " - " + e.getMessage(), e);
        }
    }

    /**
     * Tracks an exposure event for local evaluation.
     */
    private void trackLocalExposure(String distinctId, String flagKey, String variantKey, String evaluationMode, long latencyMs, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        trackExposure(distinctId, flagKey, variantKey, evaluationMode, properties -> {
            properties.put("Variant fetch latency (ms)", latencyMs);
        }, experimentId, isExperimentActive, isQaTester);
    }

    /**
     * Tracks an exposure event for remote evaluation.
     */
    private void trackRemoteExposure(String distinctId, String flagKey, String variantKey, String evaluationMode,
                                     String startTime, String completeTime, UUID experimentId, Boolean isExperimentActive, Boolean isQaTester) {
        trackExposure(distinctId, flagKey, variantKey, evaluationMode, properties -> {
            properties.put("Variant fetch start time", startTime);
            properties.put("Variant fetch complete time", completeTime);
        }, experimentId, isExperimentActive, isQaTester);
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
    }
}
