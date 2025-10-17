package com.mixpanel.mixpanelapi.featureflags.demo;

import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;

import java.util.HashMap;
import java.util.Map;

/**
 * Example demonstrating remote feature flag evaluation.
 *
 * Remote evaluation makes an API call for each flag check, providing
 * real-time flag updates but with higher latency.
 */
public class RemoteEvaluationExample {

    public static void main(String[] args) {
        // Replace with your actual Mixpanel project token
        String projectToken = "YOUR_PROJECT_TOKEN";

        // 1. Configure remote evaluation
        RemoteFlagsConfig config = RemoteFlagsConfig.builder()
            .projectToken(projectToken)
            .apiHost("api.mixpanel.com")       // Use "api-eu.mixpanel.com" for EU
            .requestTimeoutSeconds(5)          // 5 second timeout
            .build();

        // 2. Create MixpanelAPI with flags support
        try (MixpanelAPI mixpanel = new MixpanelAPI(config)) {

            System.out.println("Remote flags initialized");

            // 3. Example 1: Simple flag check
            System.out.println("\n=== Example 1: Simple Flag Check ===");
            Map<String, Object> context1 = new HashMap<>();
            context1.put("distinct_id", "user-789");

            // Each call makes an API request
            boolean featureEnabled = mixpanel.getRemoteFlags().isEnabled(
                "experimental-feature",
                context1
            );

            System.out.println("Feature enabled: " + featureEnabled);

            // 4. Example 2: Admin access check with targeting
            System.out.println("\n=== Example 2: Admin Access Check ===");
            Map<String, Object> adminContext = new HashMap<>();
            adminContext.put("distinct_id", "admin-user-1");

            Map<String, Object> customProps = new HashMap<>();
            customProps.put("role", "admin");
            customProps.put("department", "engineering");
            adminContext.put("custom_properties", customProps);

            boolean hasAdminAccess = mixpanel.getRemoteFlags().isEnabled(
                "admin-panel-access",
                adminContext
            );

            System.out.println("Admin access granted: " + hasAdminAccess);

            // 5. Example 3: Get variant value for A/B test
            System.out.println("\n=== Example 3: A/B Test Variant ===");
            Map<String, Object> context2 = new HashMap<>();
            context2.put("distinct_id", "user-456");

            String landingPageVariant = mixpanel.getRemoteFlags().getVariantValue(
                "landing-page-test",
                "control",  // fallback to control variant
                context2
            );

            System.out.println("Landing page variant: " + landingPageVariant);

            // 6. Example 4: Full variant information
            System.out.println("\n=== Example 4: Full Variant Info ===");
            SelectedVariant<Object> variant = mixpanel.getRemoteFlags().getVariant(
                "pricing-tier-experiment",
                new SelectedVariant<>(null),
                context1
            );

            if (variant.isSuccess()) {
                System.out.println("Assigned to variant: " + variant.getVariantKey());
                System.out.println("Pricing tier: " + variant.getVariantValue());
            } else {
                System.out.println("Using default pricing");
            }

            // 7. Example 5: Dynamic configuration value
            System.out.println("\n=== Example 5: Dynamic Config ===");
            Integer apiRateLimit = mixpanel.getRemoteFlags().getVariantValue(
                "api-rate-limit",
                1000,  // default rate limit
                context1
            );

            System.out.println("API rate limit: " + apiRateLimit + " requests/hour");

            // 8. Example 6: Batch checking multiple users
            System.out.println("\n=== Example 6: Check Multiple Users ===");
            for (int i = 0; i < 3; i++) {
                Map<String, Object> userContext = new HashMap<>();
                userContext.put("distinct_id", "user-beta-" + i);

                boolean betaAccess = mixpanel.getRemoteFlags().isEnabled(
                    "beta-program",
                    userContext
                );

                System.out.println("User beta-" + i + " has beta access: " + betaAccess);
            }

            System.out.println("\n=== Example Complete ===");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("Resources cleaned up successfully");
    }
}
