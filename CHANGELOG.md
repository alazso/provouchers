# Changelog

All notable changes to ProVouchers are documented here. This project follows
[Semantic Versioning](https://semver.org). Releases before 0.5.0 are listed on the
[GitHub releases](https://github.com/alazso/provouchers/releases) page.

## [0.5.0] - 2026-06-07

### Added
- **Command help menu**: `/voucher` and `/voucher help` show a clickable,
  permission-filtered list of every subcommand with its syntax.
- **Comprehensive tab completion** across `/voucher`: voucher ids on
  `give`/`giveall`, predefined amounts (1, 16, 32, 64), and online players. Codes
  are intentionally never suggested, so loaded code values are not leaked.
- **Usage metrics**: configuration-shape charts (reward, condition, and
  item-provider distributions, count buckets, feature adoption, and which
  integrations are installed) and runtime counters (redemptions, voucher vs. code
  split, duplicates blocked, condition denials, and the most-granted reward),
  reported through bStats and FastStats and governed by the existing
  `metrics.enabled` switch.
- **Token amounts for item rewards**: `item: GOLD_INGOT {random:1-3}` is resolved
  at redeem time, the same way `currency` already worked.
- **Optional integration dependencies** declared in the plugin manifest (Vault,
  MiniPlaceholders, Nexo, Head Database) so server load order reflects every
  integration ProVouchers can use.

### Changed
- **Standardized command output**: a consistent `ProVouchers »` prefix, a single
  palette, and uniform error, success, and usage messages across `/voucher`.
- **Requires Strata 0.10.0+** (previously 0.9.0+) for the new command-suggestion
  API.

### Fixed
- Item reward amounts containing a token (such as `{random:1-3}`) no longer fail
  to load; they are now accepted and resolved at redeem time.
- Giving a voucher to a target that matches no online player now reports a clear
  error instead of throwing.
- Removed two no-op trailing argument slots from `/voucher give` and
  `/voucher giveall`.

[0.5.0]: https://github.com/alazso/provouchers/releases/tag/v0.5.0
