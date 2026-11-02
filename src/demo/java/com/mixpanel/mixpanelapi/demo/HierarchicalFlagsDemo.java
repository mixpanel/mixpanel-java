package com.mixpanel.mixpanelapi.demo;

import com.mixpanel.mixpanelapi.MixpanelAPI;
import com.mixpanel.mixpanelapi.MixpanelIdentity;
import com.mixpanel.mixpanelapi.ServiceAccountCredential;
import com.mixpanel.mixpanelapi.featureflags.FlagContext;
import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.config.RemoteFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;
import com.mixpanel.mixpanelapi.featureflags.provider.BaseFlagsProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Evaluates one feature flag against a real Mixpanel project, once per identity shape,
 * so the hierarchical groups changes can be seen end to end rather than only in tests.
 *
 * Reads credentials from the environment so nothing lands in the shell history:
 *
 * <pre>
 *   MIXPANEL_TOKEN          required, the project token
 *   MIXPANEL_FLAG_KEY       optional, defaults to hierchical-group-testing
 *   MIXPANEL_COMPANY_ID     optional, defaults to acme
 *   MIXPANEL_USER_ID        optional, defaults to user_123
 *   MIXPANEL_FLAGS_MODE     local or remote, defaults to remote
 *   MIXPANEL_SA_USERNAME    service account, optional for local evaluation
 *   MIXPANEL_SA_SECRET      service account
 *   MIXPANEL_PROJECT_ID     service account
 * </pre>
 *
 * With a service account it evaluates locally; with only a token it evaluates remotely.
 */
public class HierarchicalFlagsDemo {

    public static void main(String[] args) throws Exception {
        String token = env("MIXPANEL_TOKEN", null);
        if (token == null) {
            System.err.println("Set MIXPANEL_TOKEN to the project token first.");
            System.exit(2);
        }
        String flagKey = env("MIXPANEL_FLAG_KEY", "hierchical-group-testing");
        String companyId = env("MIXPANEL_COMPANY_ID", "acme");
        String userId = env("MIXPANEL_USER_ID", "user_123");
        boolean local = "local".equalsIgnoreCase(env("MIXPANEL_FLAGS_MODE", "remote"));

        showSdkWarnings();

        System.out.println("flag        " + flagKey);
        System.out.println("evaluation  " + (local ? "local" : "remote"));
        System.out.println("company     " + companyId);
        System.out.println("user        " + userId);
        System.out.println();

        MixpanelAPI mixpanel = build(token, local);
        BaseFlagsProvider<?> flags = local ? mixpanel.getLocalFlags() : mixpanel.getRemoteFlags();
        if (local) {
            mixpanel.getLocalFlags().startPollingForDefinitions();
            Thread.sleep(3000); // let the first definition fetch land
        }

        evaluate(flags, flagKey, "hierarchical: user + company",
                FlagContext.builder()
                        .identity(MixpanelIdentity.builder().userId(userId).companyId(companyId).build())
                        .build());

        evaluate(flags, flagKey, "hierarchical: company only",
                FlagContext.builder()
                        .identity(MixpanelIdentity.builder().companyId(companyId).build())
                        .build());

        evaluate(flags, flagKey, "anonymous: device only",
                FlagContext.builder()
                        .identity(MixpanelIdentity.builder().deviceId("device_abc").build())
                        .build());

        evaluate(flags, flagKey, "combined distinct id",
                FlagContext.builder()
                        .identity(MixpanelIdentity.builder()
                                .distinctId("$company:" + companyId + "|$user:" + userId).build())
                        .build());

        evaluate(flags, flagKey, "combined distinct id + hierarchical keys",
                FlagContext.builder()
                        .identity(MixpanelIdentity.builder()
                                .distinctId("$company:" + companyId + "|$user:" + userId)
                                .userId(userId).companyId(companyId).build())
                        .build());

        mixpanel.close();
    }

    private static MixpanelAPI build(String token, boolean local) {
        if (!local) {
            return new MixpanelAPI(RemoteFlagsConfig.builder()
                    .projectToken(token)
                    .hierarchicalGroups(true)
                    .build());
        }
        LocalFlagsConfig config = LocalFlagsConfig.builder()
                .projectToken(token)
                .hierarchicalGroups(true)
                .enablePolling(false)
                .build();
        MixpanelAPI.Builder builder = new MixpanelAPI.Builder().flagsConfig(config);
        // The definitions endpoint accepts the project token, so a service account is optional.
        if (env("MIXPANEL_SA_USERNAME", null) != null) {
            builder.credentials(new ServiceAccountCredential(
                    Long.parseLong(env("MIXPANEL_PROJECT_ID", "0")),
                    env("MIXPANEL_SA_USERNAME", null),
                    env("MIXPANEL_SA_SECRET", null)));
        }
        return builder.build();
    }

    private static void evaluate(BaseFlagsProvider<?> flags, String flagKey, String label,
                                 Map<String, Object> context) {
        System.out.println(label);
        System.out.println("    context  " + new LinkedHashMap<>(context));
        try {
            SelectedVariant<Object> variant =
                    flags.getVariant(flagKey, new SelectedVariant<Object>((Object) false),
                            context);
            System.out.println("    variant  " + variant.getVariantKey()
                    + "   value=" + variant.getVariantValue()
                    + "   source=" + variant.getSource());
        } catch (Throwable t) {
            System.out.println("    THREW    " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
        System.out.println();
    }

    /** The SDK reports dropped exposures and missing context through java.util.logging. */
    private static void showSdkWarnings() {
        Logger root = Logger.getLogger("com.mixpanel");
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        root.addHandler(handler);
        root.setLevel(Level.ALL);
        root.setUseParentHandlers(false);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
