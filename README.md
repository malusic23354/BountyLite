<div align="center">

# BountyLite

**A lightweight bounty system for Paper servers, with a sorted bounty menu and bet dialogs.**

[![Version](https://img.shields.io/badge/version-1.0.4-2ea44f)](https://github.com/malusic23354/BountyLite/releases)
[![Paper](https://img.shields.io/badge/Paper-1.21.11-222222)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)

</div>

---

BountyLite lets players put money on each other's heads. Open `/bounty` to see every active bounty in a menu sorted from highest to lowest, bet on a player with one click, and place new bounties from a dialog. Whoever kills a player with a bounty collects it through Vault.

> **Paper plugin.** BountyLite uses `paper-plugin.yml` and Paper's Brigadier command API, not the legacy Bukkit/Spigot command system.

## Features

**Menu and dialogs**

- `/bounty` opens a menu listing every bounty from highest to lowest amount.
- The menu resizes to the number of bounties and paginates past `gui.max-rows`.
- Previous page, new bounty (bottom center) and next page buttons.
- Click a player's head to open the bet dialog for them.
- Dialogs fall back to a clickable chat message that fills in `/bounty bet <player> <amount>` on servers without the Dialog API.
- The menu refreshes live when a bounty is added, changed or claimed.

**Bounties**

- Set, add to, remove and check bounties.
- Kill a player with a bounty to claim it.
- Paginated leaderboard with `/bounty top`.
- Vault economy support with balance checks and clear error messages.

**Server control**

- SQLite by default, MySQL optional.
- Fully editable `messages.yml` with MiniMessage, HEX colors and gradients.
- Missing config and message keys are added automatically on update.
- Permission support and live reload with `/bounty reload`.
- `/bounty info` shows author, version and API version.

## Requirements

| | |
| --- | --- |
| **Server** | [Paper](https://papermc.io/) 1.21.11 – 26.x. Compiled against the 1.21.11 API with Java 21 bytecode. |
| **Required** | [Vault](https://www.spigotmc.org/resources/vault.34315/) |
| **Economy** | A Vault-compatible economy plugin, such as EssentialsX Economy |

Vault is used only as the economy bridge. All plugin code uses Paper APIs.

## Installation

1. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) and an economy plugin.
2. Drop `BountyLite-VERSION.jar` into your server's `plugins/` folder and start the server.
3. Edit the generated files in `plugins/BountyLite/`, then run `/bounty reload`.

Default files are only written when missing. Delete a file to regenerate it with the current defaults.

## Menu

`/bounty` opens the bounty menu.

| Button | Position | Action |
| --- | --- | --- |
| Player head | Top rows | Opens the bet dialog for that player |
| Previous page | Bottom row | Goes back one page |
| New bounty | Bottom center | Opens the new bounty dialog |
| Next page | Bottom row | Goes forward one page |

Menu options in `config.yml`:

```yaml
gui:
  max-rows: 6
  materials:
    filler: GRAY_STAINED_GLASS_PANE
    previous: ARROW
    next: ARROW
    new-bounty: EMERALD
    empty: BARRIER
```

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/bounty` | Open the bounty menu | `bounty.gui` |
| `/bounty check <player>` | Check a player's bounty | `bounty.check` |
| `/bounty bet <player> <amount>` | Add money to a bounty | `bounty.bet` |
| `/bounty set <player> <amount>` | Set a bounty without paying | `bounty.set` |
| `/bounty remove <player>` | Remove a bounty | `bounty.remove` |
| `/bounty top [page]` | Show the leaderboard | `bounty.top` |
| `/bounty info` | Show author, version and API version | `bounty.info` |
| `/bounty reload` | Reload messages and config | `bounty.reload` |

## Permissions

<details>
<summary><b>Show all permissions</b></summary>

| Permission | Default | Description |
| --- | --- | --- |
| `bounty.gui` | Everyone | Open the bounty menu |
| `bounty.check` | Everyone | Check player bounties |
| `bounty.bet` | Everyone | Add money to bounties |
| `bounty.top` | Everyone | View the leaderboard |
| `bounty.info` | Everyone | View plugin information |
| `bounty.set` | Operators | Set bounties |
| `bounty.remove` | Operators | Remove bounties |
| `bounty.reload` | Operators | Reload messages and config |

</details>

Commands and permissions are registered in code rather than in `paper-plugin.yml`.

## Storage

Bounties are stored in a SQL table. SQLite is the default:

```text
plugins/BountyLite/bounties.db
```

To use MySQL, edit `config.yml`:

```yaml
storage:
  type: mysql
  table-prefix: 'bounty_'
  mysql:
    host: 'localhost'
    port: 3306
    database: 'bounty'
    username: 'root'
    password: 'your-password'
    use-ssl: false
```

The database is created automatically if the account has permission; otherwise create it manually first. The table is created on startup.

## Configuration files

| File | Contents |
| --- | --- |
| `config.yml` | Storage settings and menu options |
| `messages.yml` | Every player-facing message and dialog text, using MiniMessage |

Missing options are appended automatically on start and `/bounty reload`; existing content is never changed.

## Building from source

```bash
git clone https://github.com/malusic23354/BountyLite.git
cd BountyLite
./gradlew build
```

The plugin JAR is written to `build/libs/`.

To run a local test server, place a Paper JAR at `run/paper.jar` and use:

```bash
./gradlew runServer
```

## Support

- **Issues:** https://github.com/malusic23354/BountyLite/issues
- **Changelog:** [CHANGELOG.md](CHANGELOG.md)

Bug reports and pull requests are welcome. When reporting a problem, include your server version, plugin version and the relevant console output.
