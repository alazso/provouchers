# ProVouchers

A feature-rich voucher plugin for Paper and Folia 26.1+ (Java 25).

ProVouchers gives server operators item vouchers and typeable codes with a
config-driven reward and condition system, persistent anti-dupe protection, a
preview GUI, and a migration path from CrazyVouchers.

## Requirements

- Paper or Folia 26.1+ (Java 25)
- [Strata](https://github.com/alazso/strata) installed on the server

ProVouchers builds on Strata for scheduling, storage, text rendering,
conditions, hooks, GUIs, and metrics. Install Strata first; it loads before
ProVouchers automatically.

## Installation

1. Download `Strata` and drop it into `plugins/`.
2. Download the latest `provouchers-<version>.jar` from [Releases](https://github.com/alazso/ProVouchers/releases).
3. Drop it into `plugins/` and restart the server.

## Building

```bash
./gradlew build
# Output: build/libs/provouchers-<version>.jar
```

The build compiles, runs tests, checks coverage, and produces the plugin jar.
Strata's API is a compile-only dependency, resolved from `https://repo.alaz.so/releases`.

## Releasing

1. Update `version` in `gradle.properties`.
2. Push a tag matching `v<version>`. CI validates the tag against the version,
   builds, and creates a GitHub release.

## License

[MIT](LICENSE)
