package net.malusic.bounty

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.malusic.bounty.gui.BountyGui
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import java.util.UUID

class BountyPlugin : JavaPlugin() {

    private val mini = MiniMessage.miniMessage()

    private lateinit var bountyManager: BountyManager
    private var betExpiryTask: BukkitTask? = null
    private var started = false

    override fun onLoad() {
        info(
            "<gradient:#a78bfa:#60a5fa><bold>BountyLite</bold></gradient> " +
                "<gray>loading plugin classes<dark_gray>..."
        )
    }

    override fun onEnable() {
        saveDefaultConfig()

        val storage = try {
            SqlStorage(this)
        } catch (exception: Exception) {
            return fail("Could not initialize bounty storage.", exception)
        }

        bountyManager = BountyManager(storage, logger)
        try {
            bountyManager.load()
        } catch (exception: Exception) {
            bountyManager.close()
            return fail("Could not load bounties from storage.", exception)
        }

        val messages = MessageHandler(this)
        val economy = EconomyService(this)
        val betLimits = BetLimits(this)
        val betCooldown = BetCooldown()
        val gui = BountyGui(this, bountyManager, messages, economy, BetDialog(this, messages))
        bountyManager.onChange = gui::refreshOpen

        registerPermissions()
        logBanner(storage)
        logVault(economy)
        registerPlaceholders(economy)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(BountyCommand(bountyManager, messages, economy, betLimits, betCooldown, gui, this).build())
        }

        server.pluginManager.registerEvents(DeathListener(this, bountyManager, messages, economy), this)
        server.pluginManager.registerEvents(gui, this)

        val betExpiry = BetExpiry(this, messages, bountyManager, economy)
        betExpiryTask = server.scheduler.runTaskTimer(this, Runnable { betExpiry.run() }, 1200L, 1200L)

        started = true
        info("<gradient:#a78bfa:#60a5fa><bold>BountyLite</bold></gradient> <green>enabled<dark_gray>. <gray>Ready for bounties.")
    }

    override fun onDisable() {
        betExpiryTask?.cancel()
        if (::bountyManager.isInitialized) bountyManager.close()

        if (started) {
            info("<gradient:#a78bfa:#60a5fa><bold>BountyLite</bold></gradient> <gray>disabled<dark_gray>.")
        }
    }

    private fun logBanner(storage: SqlStorage) {
        val version = pluginMeta.version
        val author = pluginMeta.authors.joinToString(", ").ifEmpty { "Malusic" }
        val bounties = bountyManager.count()

        info("<dark_gray>------------------------------")
        info("<gradient:#a78bfa:#60a5fa><bold>BountyLite</bold></gradient> <gray>v<white>$version <dark_gray>| <gray>by <white>$author")
        info("<dark_gray>» <gray>Storage: <white>${storage.description}")
        info("<dark_gray>» <gray>Active bounties: <white>$bounties")
        info("<dark_gray>------------------------------")
    }

    private fun logVault(economy: EconomyService, attempt: Int = 0) {
        val provider = economy.providerName()

        when {
            provider != null ->
                info("<dark_gray>» <gray>Vault: <green>hooked <dark_gray>(<white>$provider<dark_gray>)")

            attempt == 0 -> {
                warn("<dark_gray>» <gray>Vault: <yellow>no economy provider registered yet<dark_gray>, <gray>retrying in 2s<dark_gray>...")
                server.scheduler.runTaskLater(this, Runnable { logVault(economy, 1) }, 40L)
            }

            else -> warn(
                "<dark_gray>» <gray>Vault: <red>not hooked <dark_gray>- <gray>no economy provider was found. " +
                    "<red>Payouts and bets are disabled until an economy plugin registers."
            )
        }
    }

    private fun registerPlaceholders(economy: EconomyService) {
        if (!server.pluginManager.isPluginEnabled("PlaceholderAPI")) {
            warn("<dark_gray>» <gray>PlaceholderAPI: <yellow>not installed <dark_gray>(<gray>optional<dark_gray>)")
            return
        }

        runCatching { BountyExpansion(this, bountyManager, economy).register() }
            .onSuccess { registered ->
                if (registered) {
                    info("<dark_gray>» <gray>PlaceholderAPI: <green>hooked <dark_gray>(<white>bountylite<dark_gray>)")
                } else {
                    warn("<dark_gray>» <gray>PlaceholderAPI: <red>registration rejected <dark_gray>(<gray>identifier in use?<dark_gray>)")
                }
            }
            .onFailure {
                error("<dark_gray>» <gray>PlaceholderAPI: <red>failed to register expansion.", it)
            }
    }

    private fun fail(message: String, exception: Exception) {
        error("<red><bold>BountyLite failed to start</bold> <dark_gray>- <gray>$message", exception)
        server.pluginManager.disablePlugin(this)
    }

    private fun info(message: String) = componentLogger.info(mini.deserialize(message))

    private fun warn(message: String) = componentLogger.warn(mini.deserialize(message))

    private fun error(message: String, exception: Throwable? = null) {
        val component: Component = mini.deserialize(message)
        if (exception == null) componentLogger.error(component) else componentLogger.error(component, exception)
    }

    private fun registerPermissions() {
        listOf("set", "remove", "reload").forEach {
            server.pluginManager.addPermission(Permission("bounty.$it", PermissionDefault.OP))
        }
        listOf("bet", "check", "top", "gui", "info").forEach {
            server.pluginManager.addPermission(Permission("bounty.$it", PermissionDefault.TRUE))
        }
    }
}

class BountyExpansion(
    private val plugin: BountyPlugin,
    private val bounties: BountyManager,
    private val economy: EconomyService
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = IDENTIFIER

    override fun getAuthor(): String =
        plugin.pluginMeta.authors.joinToString(", ").ifEmpty { "Malusic" }

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val lower = params.lowercase(Locale.ROOT)

        if (lower == "total") return economy.format(bounties.total())
        if (lower.startsWith("bounty_")) return playerBounty(params.substring(7))

        val parts = lower.split('_')
        val position = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val field = parts.getOrNull(2) ?: return null

        return when (parts[0]) {
            "top" -> entry(bounties.getTop().getOrNull(position - 1), field)
            "topbettor", "topbettors" -> entry(bounties.topBettor(position), field)
            else -> null
        }
    }

    private fun entry(entry: Pair<UUID, Long>?, field: String): String? {
        if (entry == null) return ""

        val (uuid, cents) = entry
        return when (field) {
            "name" -> name(uuid)
            "amount" -> economy.format(cents)
            else -> null
        }
    }

    private fun playerBounty(name: String): String? {
        if (name.isEmpty()) return null

        val player = Bukkit.getOfflinePlayer(name)
        if (player.name == null && !player.hasPlayedBefore()) return ""

        return economy.format(bounties.getBounty(player.uniqueId))
    }

    private fun name(uuid: UUID): String =
        Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString().take(8)

    companion object {
        private const val IDENTIFIER = "bountylite"
    }
}
