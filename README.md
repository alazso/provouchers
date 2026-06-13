<div align="center">

<img src="https://raw.githubusercontent.com/alazso/provouchers/main/.github/assets/banner.png" alt="ProVouchers: a powerful ecosystem of integrations for your server" width="720">

[![Build](https://img.shields.io/github/actions/workflow/status/alazso/provouchers/ci.yml?branch=main&style=for-the-badge&label=build)](https://github.com/alazso/provouchers/actions)
[![Total downloads](https://img.shields.io/endpoint?url=https%3A%2F%2Ffaststats.dev%2Fapi%2Fshields%2Fprovouchers%3Fmetric%3Ddownloads&style=for-the-badge)](https://faststats.dev/project/provouchers)
[![Minecraft](https://img.shields.io/badge/Paper%20·%20Folia%20·%20Purpur-26.1%2B-2b2d31?style=for-the-badge)](https://papermc.io/)
[![License](https://img.shields.io/badge/license-MIT-2b2d31?style=for-the-badge)](https://github.com/alazso/provouchers/blob/main/LICENSE)

[![Documentation](https://img.shields.io/badge/Documentation-FF8A00?style=for-the-badge&logo=readthedocs&logoColor=white)](https://alaz.so/provouchers/docs)
[![Source](https://img.shields.io/badge/Source-0f0f0f?style=for-the-badge&logo=github&logoColor=white)](https://github.com/alazso/provouchers)
[![Support Discord](https://img.shields.io/discord/1510890328943628350?style=for-the-badge&logo=discord&logoColor=white&label=discord&color=5865F2)](https://discord.gg/m3AKQfrMS5)

</div>

<br>

Create vouchers and redeemable codes backed by a **powerful ecosystem of integrations**, built to help your server stand out. Hand out rewards as **items players right-click** or **codes players type**, define everything in plain YAML, and reload it live, with persistent anti-dupe and cooldowns that survive restarts.

<br>

## At a glance

|   |   |
|---|---|
| 🌍 **Per-player localization** | Every player sees messages in their own Minecraft client language. Ships in **7 languages**. |
| 🔒 **Soulbound vouchers** | Cannot be dropped, stored, or traded (each toggle configurable), with optional bind-on-pickup. |
| 🎁 **Vouchers and codes** | One reward system, two ways to deliver it. |
| 🧩 **Typed rewards** | Items, currency, XP, ranks, permissions, commands, titles, sounds, and weighted random sets. |
| 🧱 **Layered conditions** | Gate redemptions on playtime, rank, region, economy, advancements, PlaceholderAPI, and more. |
| 🛡️ **Anti-dupe that holds** | Per-item stamps in persistent storage. Clones are rejected; creative, crafting, and item frames are blocked. |
| ⏳ **Persistent limits** | Cooldowns and usage caps survive restarts and apply network-wide on a shared database. |
| 🔌 **Drop-in integrations** | ItemsAdder, Oraxen, Nexo, Head Database, Vault, LuckPerms, PlaceholderAPI, MiniPlaceholders, WorldGuard. |

<br>

## Every feature

**Delivery and rewards**
- **Vouchers** (physical items players right-click) and **codes** (typed with `/voucher redeem`).
- Reward types: **items, currency, XP, ranks, permissions, console/player commands, broadcasts, titles, action bars, sounds**.
- **Weighted random reward sets**, on top of always-run rewards.
- **Named random rolls** (`%random:1-100:loot%`): give a roll and announce the exact same number.
- **Decorated reward items**: define an item once (name, lore, glow, custom/head) and grant it by reference.
- **Shift + right-click to mass-open** a whole stack at once.

**Conditions** (all must pass before a redeem)
- permission, rank (LuckPerms), world, region (WorldGuard), gamemode, economy balance (Vault), experience level, **playtime**, **item in inventory**, **advancement completed**, **PlaceholderAPI comparison**, player statistic.

**Limits and lifecycle**
- **Global usage limits** per voucher or code (the first N redemptions server-wide).
- **Per-player usage limits** (each player may redeem it N times, ever).
- **Per-player cooldowns** that survive restarts, with **permission-based tiers** so VIPs wait less.
- **Expiry**: a fixed date (`2026-07-01`), a date-time, or relative (`30d` from when it was given).
- **Active-from**: not redeemable until a chosen date.

**Security and control**
- **Soulbound**: block dropping, storing, and trading independently; optionally bind to whoever picks it up.
- **Owner-only** vouchers: only the player it was given to may redeem it.
- **Two-step confirmation**: require a second click, as a chat prompt or a GUI dialog.
- Blocked in creative and spectator, and as a crafting ingredient or in anvils, grindstones, looms, item frames, and more.

**Localization**
- **Per-player locale**: Player A sees Polish while Player B sees French, from their client language, no commands needed.
- Bundled in **English, German, French, Spanish, Polish, Danish, and Dutch**. Untranslated lines fall back to English, never blank.

**Appearance**
- **Custom items and heads**: ItemsAdder, Oraxen, Nexo, Head Database, vanilla player heads, and custom model data.
- **Redeem effects**: play a sound and/or a firework on a successful redeem.

**Admin and developer tools**
- **Live reload** of everything, or a single file by id.
- **`/voucher fromhand`**: capture the item in your hand as a new voucher, behind a confirm GUI.
- **Import from CrazyVouchers** (`/voucher import`): vouchers and codes, with a full report of anything that needs review.
- **`/voucher doctor`** diagnostics and a reward **preview GUI**.
- **Published developer API** with cancellable pre-redeem and post-redeem events.
- **Folia-ready**: off-thread storage, region-safe rewards.

<br>

## A complete voucher

```yaml
# plugins/ProVouchers/vouchers/daily.yml
id: daily
display-name: "<gradient:#FFD700:#FF8A00>Daily Reward</gradient>"
item:
  material: SUNFLOWER
  glow: true
lore:
  - "<gray>Right-click to claim today's reward"
cooldown: 86400          # once per day, survives restarts
rewards:
  - "currency: give 250"
  - "item: DIAMOND 1"
  - "message: <green>Thanks for playing today!"
```

That is a working voucher, top to bottom. The **[documentation](https://alaz.so/provouchers/docs)** covers every reward, condition, and option, with examples.

<br>

## Migrating from CrazyVouchers?

Drop ProVouchers in beside it and run **`/voucher import crazyvouchers`**. It converts your vouchers and codes (both the modern and legacy layouts), translates color codes and placeholders, and prints a complete report of anything that has no equivalent so nothing changes behind your back. Your existing files are never touched.

<br>

## Requirements

|   |   |
|---|---|
| **Server** | Paper, Folia, or Purpur |
| **Minecraft** | 26.1 or newer |
| **Java** | 25 |

**Optional integrations:** Vault (economy), LuckPerms, PlaceholderAPI, MiniPlaceholders, WorldGuard, ItemsAdder, Oraxen, Nexo, Head Database. Each is detected automatically when installed; nothing breaks when it is absent.

<br>

## Help translate

ProVouchers ships in seven languages, and we would love more. The message files are plain YAML ([`lang/`](https://github.com/alazso/provouchers/tree/main/src/main/resources/lang)): copy `en.yml` to your language code, translate the values, and open a pull request or share it in [Discord](https://discord.gg/m3AKQfrMS5). Native speakers reviewing the existing translations (Polish and Danish especially) are very welcome too.

<br>

## Found a bug? Have an idea?

Bug reports and feature ideas are welcome. Open a [GitHub issue](https://github.com/alazso/provouchers/issues) with your server software and version, your ProVouchers version, and the relevant config plus any console output. For quick questions or to help test, join the [Discord](https://discord.gg/m3AKQfrMS5).

<br>

<div align="center">

Released under the [MIT License](https://github.com/alazso/provouchers/blob/main/LICENSE).

</div>
