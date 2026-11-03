<div align="center">

# BountyLite

**A lightweight bounty system for Paper servers, with a sorted bounty menu and bet dialogs.**

[![Version](https://img.shields.io/badge/version-1.0.5-2ea44f)](https://github.com/malusic23354/BountyLite/releases)
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
- Previous page, new bounty (bottom center), next page and search buttons.
- Click a player's head to open the bet dialog for them.
- Search for a player by name without leaving the menu.
- Dialogs fall back to a clickable chat message that fills in `/bounty bet <player> <amount>` on servers without the Dialog API.
- The menu refreshes live when a bounty is added, changed or claimed.

**Bounties**

- Set, add to, remove and check bounties.
- Kill a player with a bounty to claim it, with an on-screen title and sound.
- Paginated leaderboard with `/bounty top`; entries are clickable and page links work from chat.
- Tab completion suggests online players only; offline player names still resolve when typed.
- Vault economy support with balance checks and clear error messages.
- Per-player bet cooldown, minimum/maximum bets, and optional expiry with refunds.
- The victim is notified when a new bounty is placed on them.
- Same-IP claims are blocked by default to deter alt farming.

**Integrations**

- Optional PlaceholderAPI placeholders for the top bounties, top bettors and individual bounties.

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
| **Optional** | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholders |

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
| Search | Bottom left | Opens the search dialog |
| Clear search | Bottom right | Shows all bounties again (only while searching) |
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
    search: COMPASS
    clear: BARRIER
```

Bounty, notification and claim behaviour in `config.yml`:

```yaml
anti-abuse:
  # Block claims when the killer and victim share an IP (deters alt farming).
  block-same-ip: true

notifications:
  # Tell players when a bounty is placed on them.
  notify-target: true

claim:
  # Show a title and play a sound when a bounty is claimed.
  title: true
  sound: true
```

## Commands

<details>
<summary><b>Show all commands</b></summary>

| Command | Description | Permission |
| --- | --- | --- |
| `/bounty` | Open the bounty menu | `bounty.gui` |
| `/bounty check <player>` | Check a player's bounty | `bounty.check` |
| `/bounty bet <player> <amount>` | Add money to a bounty | `bounty.bet` |
| `/bounty set <player> <amount>` | Set a bounty without paying | `bounty.set` |
| `/bounty remove <player>` | Remove a bounty | `bounty.remove` |
| `/bounty top [page]` | Show the leaderboard | `bounty.top` |
| `/bounty search <query>` | Open the menu filtered by player name | `bounty.gui` |
| `/bounty info` | Show author, version and API version | `bounty.info` |
| `/bounty reload` | Reload messages and config | `bounty.reload` |

</details>

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

## PlaceholderAPI

If PlaceholderAPI is installed, BountyLite registers a `bountylite` expansion. `<n>` is a 1-based position.

| Placeholder | Description |
| --- | --- |
| `%bountylite_top_<n>_name%` | Name of the player at position `<n>` on the bounty leaderboard |
| `%bountylite_top_<n>_amount%` | Bounty amount at position `<n>` |
| `%bountylite_topbettor_<n>_name%` | Name of the player at position `<n>` on the top bettors list |
| `%bountylite_topbettor_<n>_amount%` | Total amount bet by that player |
| `%bountylite_bounty_<player>%` | Current bounty on a player |
| `%bountylite_total%` | Total of all active bounties |

Positions outside the list return an empty string.

## Console output

On startup BountyLite prints a colored banner with the version, author, storage backend and number of active bounties, followed by the status of each hook:

- `Vault: hooked (<provider>)` when an economy provider is available. If none is registered yet, it retries once after 2 seconds and warns if it still cannot find one.
- `PlaceholderAPI: hooked`, `not installed (optional)`, `registration rejected` or `failed to register expansion`.
- Startup failures are printed in red with the full stack trace, and the plugin disables itself cleanly.

## Storage

<details>
<summary><b>Show all storage info</b></summary>

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

</details>

## Configuration files

| File | Contents |
| --- | --- |
| `config.yml` | Storage, bet limits, anti-abuse, notifications, claim effects and menu options |
| `messages.yml` | Every player-facing message and dialog text, using MiniMessage |

Missing options are appended automatically on start and `/bounty reload`; existing content is never changed.

<details>
<summary><b>Show building from source info</b></summary>

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

</details>

## Support

- **Issues:** https://github.com/malusic23354/BountyLite/issues

Bug reports and pull requests are welcome. When reporting a problem, include your server version, plugin version and the relevant console output.