# Changelog

All notable changes to ProVouchers are documented here. This project follows
[Semantic Versioning](https://semver.org). Releases before 0.5.0 are listed on the
[GitHub releases](https://github.com/alazso/provouchers/releases) page.

## [1.1.0] - 2026-06-11

Localization, admin quality of life, and richer voucher text. Every player-facing
message is now translatable per player, admins get a diagnostic command and a
single-file reload, and vouchers gain two-step confirmation, a redeem sound, and
new placeholders.

### Added
- **Localization**: every command, redeem, and condition message now lives in
  `lang/<code>.yml`. A player sees their Minecraft client language when a matching
  file exists, otherwise the configured `locale.default`, with English as a per-key
  fallback so partial translations never blank out.
- **Two-step confirmation** (`two-step-authentication`): redeeming takes a second
  click within `redeem.confirm-window-seconds`, guarding costly or irreversible
  vouchers. The prompt is customizable per voucher.
- **Reward preview GUI**: left-click a held voucher to see its loot (guaranteed
  items, and each random set with its chance) before redeeming, controlled by
  `redeem.left-click-preview`.
- **`/voucher doctor`** (`provouchers.doctor`): a health report covering version,
  server flavor, Java runtime, storage backend and pool state, content counts,
  detected integrations, locale, and metrics.
- **`/voucher reload [id]`**: reload a single voucher and/or code file by id,
  leaving everything else loaded. A file that fails to parse keeps its previously
  loaded version instead of dropping out.
- **Station blocking**: vouchers can no longer be used as crafting ingredients or
  placed into anvils, grindstones, smithing tables, looms, cartography tables,
  stonecutters, or brewing stands.
- **Per-voucher redeem sound** (`effects.sound`): a `"key [volume] [pitch]"` sound
  played to the redeemer on success. Batch-open redemptions stay quiet.
- **Named random rolls** (`%random:min-max:name%`): the first use in a redeem rolls
  once and every later use of the same name reuses the value, so a reward can give
  an amount and announce that same amount.
- **`%expiry%` placeholder**: shows a voucher's expiry in its name or lore as
  `in 30d`, `on 2026-12-31`, or `never`.
- **Flexible expiry formats**: `expiry` now also accepts a plain date
  (`2026-12-31`, valid through the end of that day in the server's time zone) and a
  zone-less date-time, alongside relative durations and ISO-8601 instants.
- **Batch-open quiet mode** (`redeem.batch-open-quiet`): opening a stack skips the
  per-item feedback rewards (messages, titles, sounds) so chat is not spammed.
- **Staff duplicate-alert toggle** (`anti-dupe.notify.enabled`), and the alert
  message gains a `{world}` placeholder.

### Changed
- **Placeholders now use percent syntax**: `%player%`, `%arg%`, and
  `%random:min-max%`, matching the common PlaceholderAPI convention. The curly
  forms (`{player}`, `{arg}`, `{random:..}`) still work but are deprecated, and
  `/voucher reload` warns when a file uses them.
- Updated FastStats to 0.25.2.

### Fixed
- Stackable vouchers given on separate occasions now stack with each other. The
  give time is stamped onto an item only when a relative expiry needs it as an
  anchor.

## [1.0.0] - 2026-06-10

The first standalone release. ProVouchers no longer needs the Strata library:
scheduling, text rendering, storage, the GUI, conditions, metrics, and every
integration (Vault, LuckPerms, ItemsAdder, Oraxen, Nexo, HeadDatabase, WorldGuard)
now ship inside the plugin. It installs as a single jar and runs on Paper, Folia,
and Purpur 26.1+. 0.6.0 was never published; its work is included here.

### Added
- Standalone install: one jar, no Strata. The connection pool and JDBC drivers are
  downloaded by Paper's library loader on first start.
- Database storage on SQLite out of the box, or MySQL, MariaDB, or PostgreSQL for
  shared and networked setups.
- Admin preview GUI (`/voucher preview`): a paginated browser of every loaded
  voucher. Right-click to give yourself one, left-click for a per-voucher info menu.
- Batch open (`batch-open: true`): shift-right-click a stack to redeem all of it at
  once. Needs a stackable voucher with no cooldown.
- Full-fidelity custom items: a voucher or reward item can be a serialized item that
  preserves enchants, name, lore, model data, attributes, and other components exactly.
- Duplicate warning lore (`anti-dupe.warning.enabled` / `anti-dupe.warning.text`):
  mark a returned duplicate with a lore line to deter resale scams.
- Dynamic tokens (`%player%`, `{player}`, `{arg}`, `{random:min-max}`) now resolve in
  a voucher's display name and lore, not just in rewards.

### Changed
- Anti-dupe is now opt-in per voucher through a `stackable` flag (default `true`). A
  stackable voucher stacks freely and is not dupe-tracked. Setting `stackable: false`
  stamps each item with a unique id, recorded once on redeem, so duplicates are caught
  and those items do not stack. This replaces the old always-on batch/nonce scheme and
  fixes the stacking complaints.
- The redeem event exposes a single `uid` in place of `batchId` and `nonce`.
- Custom item references resolve strictly: a `provider:id` reference is served only by
  that provider, and an unknown provider prefix now fails at load instead of silently
  falling through.

### Migration
- The schema upgrades in place on first start: a used-voucher table is added and the
  old stamp table is dropped. A pre-1.0.0 install is detected and its recorded schema
  version carried over, so migrations are not re-run. Vouchers minted before the
  upgrade redeem as ungoverned (they carry the old data, not a `uid`).

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

[1.1.0]: https://github.com/alazso/provouchers/releases/tag/v1.1.0
[1.0.0]: https://github.com/alazso/provouchers/releases/tag/v1.0.0
[0.5.0]: https://github.com/alazso/provouchers/releases/tag/v0.5.0
