package com.mixpanel.mixpanelapi.featureflags.model;

/**
 * Where a {@link SelectedVariant} came from.
 *
 * <p>The flags providers tag every variant they return so callers — especially
 * the OpenFeature wrapper — can distinguish a real evaluation from each of the
 * distinct fallback paths. Discriminated union (abstract class + nested static
 * finals) rather than a string so the per-case data (the {@link Fallback.Reason}
 * tag) lives on the variant it describes; that way invalid states like
 * "successful evaluation with a fallback reason" are unrepresentable.</p>
 *
 * <p>Java 8 source level, so no {@code sealed} keyword — the package-private
 * constructor is what makes this closed. Construct via {@link #local()},
 * {@link #remote()}, or {@link #fallback(Fallback.Reason)}.</p>
 */
public abstract class Source {
    Source() {}

    /** Singleton {@link Local} — every call returns the same instance. */
    public static Local local() {
        return Local.INSTANCE;
    }

    /** Singleton {@link Remote} — every call returns the same instance. */
    public static Remote remote() {
        return Remote.INSTANCE;
    }

    /**
     * Returns a {@link Fallback} tagged with the given reason.
     *
     * <p>The SDK uses this to explain why a fallback was returned (flag missing,
     * required context absent, no rollout matched, network error, not ready) so
     * the OpenFeature wrapper can map to the correct user-facing error code
     * instead of collapsing every fallback to FLAG_NOT_FOUND.</p>
     */
    public static Fallback fallback(Fallback.Reason reason) {
        return new Fallback(reason);
    }

    /** Variant produced by local rule evaluation against cached flag definitions. */
    public static final class Local extends Source {
        // Held inside the subclass so the outer class's <clinit> does not reference it,
        // sidestepping the "subclass referenced from superclass initializer" deadlock pattern.
        static final Local INSTANCE = new Local();

        Local() {}
    }

    /** Variant returned by a remote /flags evaluation call. */
    public static final class Remote extends Source {
        static final Remote INSTANCE = new Remote();

        Remote() {}
    }

    /** Developer-supplied fallback returned because the SDK had no value to serve. */
    public static final class Fallback extends Source {
        /**
         * Why the SDK returned the developer fallback. Matches the set of reasons
         * used by the other Mixpanel SDKs (mixpanel-php in particular).
         */
        public enum Reason {
            /** Flag key is not in the local definitions or the remote response. */
            FLAG_NOT_FOUND,
            /** A property the flag's rollout is keyed on was absent from the evaluation context. */
            MISSING_CONTEXT_KEY,
            /** Flag exists, but no rollout in its ruleset matched the supplied context. */
            NO_ROLLOUT_MATCH,
            /** Remote evaluation failed (network error, HTTP error, parse error). */
            BACKEND_ERROR,
        }
        // Note: the wrapper handles PROVIDER_NOT_READY by short-circuiting before
        // invoking the provider (see MixpanelProvider.areFlagsReady check), so
        // there is no NOT_READY constant here — no producer would ever construct it.

        /** Reason the SDK returned this fallback. */
        public final Reason reason;

        Fallback(Reason reason) {
            this.reason = reason;
        }
    }
}
