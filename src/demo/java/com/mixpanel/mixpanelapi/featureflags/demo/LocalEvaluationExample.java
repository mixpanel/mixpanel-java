package com.mixpanel.mixpanelapi.featureflags.demo;

import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;

import java.util.HashMap;
import java.util.Map;

/**
 * Example demonstrating local feature flag evaluation.
 *
 * This example shows how to:
 * 1. Configure and initialize a local flags client
 * 2. Start polling for flag definitions
 * 3. Evaluate flags with different contexts
 * 4. Properly clean up resources
 */
public class LocalEvaluationExample {

    public static void main(String[] args) throws Exception {
        // Replace with your actual Mixpanel project token
        String projectToken = "YOUR_PROJECT_TOKEN";

        // 1. Configure local evaluation
        LocalFlagsConfig config = LocalFlagsConfig.builder()
            .projectToken(projectToken)
            .apiHost("api.mixpanel.com")       // Use "api-eu.mixpanel.com" for EU
            .pollingIntervalSeconds(60)        // Poll every 60 seconds
            .enablePolling(true)               // Enable background polling
            .requestTimeoutSeconds(10)         // 10 second timeout for HTTP requests
            .build();

        try (MixpanelAPI mixpanel = new MixpanelAPI(config)) {

            // 2. Start polling for flag definitions
            System.out.println("Starting flag polling...");
            mixpanel.getLocalFlags().startPollingForDefinitions();

            System.out.println("Waiting for flags to be ready...");
            int retries = 0;
            while (!mixpanel.getLocalFlags().areFlagsReady() && retries < 50) {
                Thread.sleep(100);
                retries++;
            }

            if (!mixpanel.getLocalFlags().areFlagsReady()) {
                System.err.println("Warning: Flags not ready after 5 seconds, will use fallback values");
            } else {
                System.out.println("Flags are ready!");
            }

            // 3. Example 1: Simple boolean flag check
            System.out.println("\n=== Example 1: Boolean Flag ===");
            Map<String, Object> context1 = new HashMap<>();
            context1.put("distinct_id", "user-123");

            boolean newFeatureEnabled = mixpanel.getLocalFlags().isEnabled(
                "new-checkout-flow",
                context1
            );

            System.out.println("New checkout flow enabled: " + newFeatureEnabled);

            // Example 2: String variant value
            System.out.println("\n=== Example 2: String Variant ===");
            String buttonColor = mixpanel.getLocalFlags().getVariantValue(
                "button-color",
                "blue",  // fallback value
                context1
            );

            System.out.println("Button color: " + buttonColor);

            // Example 3: With custom properties for targeting
            System.out.println("\n=== Example 3: Targeted Flag ===");
            Map<String, Object> context2 = new HashMap<>();
            context2.put("distinct_id", "user-456");

            // Add custom properties for runtime evaluation
            Map<String, Object> customProps = new HashMap<>();
            customProps.put("subscription_tier", "premium");
            customProps.put("country", "US");
            context2.put("custom_properties", customProps);

            boolean premiumFeatureEnabled = mixpanel.getLocalFlags().isEnabled(
                "premium-analytics-dashboard",
                context2
            );

            System.out.println("Premium analytics enabled: " + premiumFeatureEnabled);

            // Example 4: Get full variant information
            System.out.println("\n=== Example 4: Full Variant Info ===");
            SelectedVariant<Object> variant = mixpanel.getLocalFlags().getVariant(
                "recommendation-algorithm",
                new SelectedVariant<>("default-algorithm"),  // fallback
                context1
            );

            if (variant.isSuccess()) {
                System.out.println("Variant key: " + variant.getVariantKey());
                System.out.println("Variant value: " + variant.getVariantValue());
            } else {
                System.out.println("Using fallback variant");
            }

            // Example 5: Number variant
            System.out.println("\n=== Example 5: Number Variant ===");
            Integer maxItems = mixpanel.getLocalFlags().getVariantValue(
                "max-cart-items",
                10,  // fallback value
                context1
            );

            System.out.println("Max cart items: " + maxItems);

            System.out.println("\n=== Example Complete ===");
            System.out.println("MixpanelAPI will be automatically closed");

            // 4. Properly clean up resources
            mixpanel.close();

        }

        System.out.println("Resources cleaned up successfully");
    }
}
