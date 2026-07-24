# Changelog

## [v1.10.0](https://github.com/mixpanel/mixpanel-java/tree/v1.10.0) (2026-07-24)

### Features
- add Source discriminated union with Fallback.Reason (SDK-79) ([#89](https://github.com/mixpanel/mixpanel-java/pull/89))

### Fixes
- guard polling start so concurrent callers don't leak executors (SDK-81) ([#91](https://github.com/mixpanel/mixpanel-java/pull/91))
- keep polling alive when fetch throws a Throwable (SDK-86) ([#93](https://github.com/mixpanel/mixpanel-java/pull/93))
- surface dropped exposure when distinct_id missing from context (SDK-84) ([#90](https://github.com/mixpanel/mixpanel-java/pull/90))

[Full Changelog](https://github.com/mixpanel/mixpanel-java/compare/v1.9.0...v1.10.0)

## [v1.9.0](https://github.com/mixpanel/mixpanel-java/tree/v1.9.0) (2026-05-29)

### Fixes
- allow capability to offload reportExposure to async thread ([#85](https://github.com/mixpanel/mixpanel-java/pull/85))

[Full Changelog](https://github.com/mixpanel/mixpanel-java/compare/v1.8.1...v1.9.0)

## [v1.8.1](https://github.com/mixpanel/mixpanel-java/tree/v1.8.1) (2026-04-29)

### Features
- Add getAllVariantsByFlag ([#70](https://github.com/mixpanel/mixpanel-java/pull/70))

[Full Changelog](https://github.com/mixpanel/mixpanel-java/compare/v1.8.0...v1.8.1)

## [v1.8.0](https://github.com/mixpanel/mixpanel-java/tree/v1.8.0) (2026-04-09)

See the [v1.8.0 release notes](https://github.com/mixpanel/mixpanel-java/releases/tag/v1.8.0)
and prior tags on the [releases page](https://github.com/mixpanel/mixpanel-java/releases)
for the full history.
