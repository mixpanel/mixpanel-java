package com.mixpanel.mixpanelapi.featureflags.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.semver4j.Semver;

import io.github.jamsesso.jsonlogic.JsonLogic;

public final class CustomOperators {

    private CustomOperators() {}

    // Using the official semantic versioning 2.0.0 regular expression to handle cross-platform validation
    // differences on other SDK's. For example, some platforms allow leading zeros even though it is not valid
    // as part of the Semver 2.0.0 spec. See https://semver.org/
    private static final Pattern SEMVER = Pattern.compile(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
            + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
            + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    // Strict RFC3339 guard for datetime strings.
    private static final Pattern RFC3339 = Pattern.compile(
        "^\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?([Zz]|[+-]\\d{2}:\\d{2})$");

    // SemVer 2.0.0 requires major.minor.patch; partial versions are zero-padded to this.
    private static final int SEMVER_PARTS = 3;

    // Epoch milliseconds are compared as a long, so anything at or beyond this is out of range.
    private static final double MAX_EPOCH_MS = (double) Long.MAX_VALUE;

    // Registers custom operators into the JSONLogic engine.
    // 1. Semantic Versioning 2.0.0 comparison
    // 2. RFC3339 datetime comparison
    public static void register(JsonLogic jsonLogic) {
        jsonLogic.addOperation("semver_compare", CustomOperators::semverCompare);
        jsonLogic.addOperation("datetime_compare", CustomOperators::datetimeCompare);
    }

    // Implements a custom operation for semantic versioning comparison that conforms to the semver
    // 2.0.0 standard. Prior to comparison, any leading version prefix is stripped.
    private static Object semverCompare(Object[] args) {
        if (!validShape(args)) {
            return false;
        }
        if (!(args[0] instanceof String) || !(args[2] instanceof String)) {
            return false;
        }
        String actualVersion = normalizeSemver((String) args[0]);
        String targetVersion = normalizeSemver((String) args[2]);
        if (!SEMVER.matcher(actualVersion).matches() || !SEMVER.matcher(targetVersion).matches()) {
            return false;
        }
        Semver actual = Semver.parse(actualVersion);
        Semver target = Semver.parse(targetVersion);
        if (actual == null || target == null) {
            return false;
        }
        long cmp = actual.compareTo(target);
        return comparatorMatches(cmp, (String) args[1]);
    }

    // Implements a custom operation for datetime comparison. The target value stored on the feature
    // flag is the millisecond epoch, whereas the actual value provided at evaluation time must be
    // RFC-3339 formatted.
    private static Object datetimeCompare(Object[] args) {
        if (!validShape(args)) {
            return false;
        }
        Long actual = convertRfc3339ToUnixSeconds(args[0]);
        Long target = convertUnixMillisecondsToSeconds(args[2]);
        if (actual == null || target == null) {
            return false;
        }
        long cmp = actual - target;
        return comparatorMatches(cmp, (String) args[1]);
    }

    private static boolean validShape(Object[] args) {
        return args.length == 3 && args[1] instanceof String;
    }

    private static boolean comparatorMatches(long cmp, String symbol) {
        switch (symbol) {
            case "=":
                return cmp == 0;
            case "!=":
                return cmp != 0;
            case "<":
                return cmp < 0;
            case "<=":
                return cmp <= 0;
            case ">":
                return cmp > 0;
            case ">=":
                return cmp >= 0;
            default:
                return false;
        }
    }

    private static String normalizeSemver(String raw) {
        String stripped = raw.trim();
        if (stripped.startsWith("v") || stripped.startsWith("V")) {
            stripped = stripped.substring(1);
        }

        int suffixStart = stripped.length();
        for (String separator : new String[] {"-", "+"}) {
            int index = stripped.indexOf(separator);
            if (index != -1 && index < suffixStart) {
                suffixStart = index;
            }
        }

        String core = stripped.substring(0, suffixStart);
        String suffix = stripped.substring(suffixStart);

        String[] segments = core.split("\\.", -1);
        // Reject anything that is not 1-3 all-digit segments, so an empty or malformed core is never
        // padded into a real version such as "0.0.0".
        if (core.isEmpty() || segments.length > SEMVER_PARTS) {
            return stripped;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || !isAllDigits(segment)) {
                return stripped;
            }
        }
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < SEMVER_PARTS; i++) {
            if (i > 0) {
                normalized.append('.');
            }
            normalized.append(i < segments.length ? segments[i] : "0");
        }
        return normalized + suffix;
    }

    private static boolean isAllDigits(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }


    private static Long convertRfc3339ToUnixSeconds(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String normalized = ((String) value).trim().toUpperCase();
        if (!RFC3339.matcher(normalized).matches()) {
            return null;
        }
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(normalized);
            return parsed.toEpochSecond();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Long convertUnixMillisecondsToSeconds(Object value) {
        if (!(value instanceof Number)) {
            return null;
        }
        double millis = ((Number) value).doubleValue();
        // A value long cannot represent is not a real timestamp; narrowing one would saturate into a
        // finite bound and let a nonsense target define a rollout window.
        if (Double.isNaN(millis) || millis >= MAX_EPOCH_MS || millis <= -MAX_EPOCH_MS) {
            return null;
        }
        return (long) millis / 1000L;
    }
}
