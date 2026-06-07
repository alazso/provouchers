<div align="center">

# 🎟️ ProVouchers

#### Item vouchers and redeemable codes for Paper and Folia

[![Build](https://img.shields.io/github/actions/workflow/status/alazso/provouchers/ci.yml?branch=main&style=for-the-badge&label=build)](https://github.com/alazso/provouchers/actions)
[![Downloads](https://img.shields.io/modrinth/dt/iOogVoaR?style=for-the-badge&logo=modrinth&label=downloads)](https://modrinth.com/plugin/provouchers)
[![Minecraft](https://img.shields.io/badge/Paper%20·%20Folia%20·%20Purpur-26.1%2B-2b2d31?style=for-the-badge)](https://papermc.io/)
[![License](https://img.shields.io/github/license/alazso/provouchers?style=for-the-badge&label=license)](LICENSE)

**[📖 Documentation](https://alaz.so/provouchers/docs)**  ·  **[💻 Source](https://github.com/alazso/provouchers)**

</div>

<br>

> ⚠️ **Requires [Strata](https://github.com/alazso/strata) v0.9.0+.** Strata is the shared library ProVouchers builds on (scheduling, storage, integrations, conditions, text). It is currently under review on Modrinth, so for now grab **Strata API v0.9.0** from [GitHub releases](https://github.com/alazso/strata/releases/tag/v0.9.0).

<br>

Hand out rewards as **items players right-click** or **codes players type**. Define everything in plain YAML, reload it live, and let ProVouchers handle the rest: persistent anti-dupe, cooldowns that survive restarts, and clean integrations with the plugins you already run.

<br>

## Features

|   |   |
|---|---|
| 🎁 **Vouchers and codes** | One reward system, two ways to deliver it. |
| 🧩 **Typed rewards** | Items, currency, ranks, permissions, commands, titles, sounds, and weighted random sets. |
| 🛡️ **Anti-dupe that holds** | Per-item stamps in persistent storage. Clones are rejected; creative and item frames are blocked. |
| ⏳ **Persistent limits** | Cooldowns and code uses survive restarts, and apply network-wide on a shared database. |
| 🖼️ **Custom items and heads** | ItemsAdder, Oraxen, Nexo, Head Database, and vanilla player heads. |
| 🔌 **Drop-in integrations** | Vault economy, LuckPerms ranks, PlaceholderAPI, MiniPlaceholders, WorldGuard regions. |
| 🧰 **Developer API** | A published API and redeem events for other plugins. |
| 🪶 **Folia-ready** | Off-thread storage, region-safe rewards. |

<br>

## 🚀 Quick start

1. Drop **Strata** and **ProVouchers** into `plugins/`.
2. Start the server. Example `vouchers/` and `codes/` files are created for you.
3. Edit a file, run `/voucher reload`, then `/voucher give <id>`.

<br>

## 📝 A complete voucher

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

## 📦 Requirements

|   |   |
|---|---|
| **Server** | Paper, Folia, or Purpur |
| **Minecraft** | 26.1 or newer |
| **Java** | 25 |
| **Dependency** | Strata 0.9.0+ |

<br>

## 🗺️ Roadmap

* A paginated preview GUI
* An offline-give queue
* `/voucher fromhand` serializer.

<br>

<div align="center">

Made with care for the Minecraft server community.  ·  Released under the [MIT License](LICENSE).

</div>
