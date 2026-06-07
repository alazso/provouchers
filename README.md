# ProVouchers

Item vouchers and redeemable codes for Paper and Folia 26.1+ (Java 25).

ProVouchers lets server operators hand out rewards as items players right-click
(vouchers) or words players type (codes). Both are defined in plain YAML, share a
typed reward and condition system, and are backed by persistent storage with
anti-dupe protection.

## Requirements

- Paper or Folia 26.1+ (Java 25)
- [Strata](https://github.com/alazso/strata) 0.9.0+ installed on the server

ProVouchers builds on Strata for scheduling, storage, text rendering, conditions,
integrations, and metrics. Install Strata first; it loads before ProVouchers
automatically.

## Installation

1. Download Strata and drop it into `plugins/`.
2. Download the latest `provouchers-<version>.jar` from [Releases](https://github.com/alazso/provouchers/releases).
3. Drop it into `plugins/` and restart the server.

## Documentation

Full documentation is at https://alaz.so/provouchers/docs, including a
[developer API](https://alaz.so/provouchers/docs/developers) for other plugins.

## Building

```bash
./gradlew build
# Output: build/libs/provouchers-<version>.jar
```

The build compiles, runs tests, checks coverage, and produces the plugin jar.
The public API module is published as `so.alaz.provouchers:provouchers-api`.

## Releasing

1. Update `version` in `gradle.properties`.
2. Push a tag matching `v<version>`. CI validates the tag against the version,
   builds, creates a GitHub release, and publishes the API artifact.

## License

[MIT](LICENSE)
