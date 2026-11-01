package net.malusic.bounty

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.malusic.bounty.gui.BountyGui
import net.malusic.bounty.gui.BountyGuiListener
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class BountyPlugin : JavaPlugin() {

    lateinit var bountyManager: BountyManager
        private set

    lateinit var messageManager: MessageHandler
        private set

    lateinit var economyService: EconomyService
        private set

    private lateinit var gui: BountyGui

    override fun onEnable() {
        if (server.pluginManager.getPlugin("Vault") == null) {
            logger.severe("Vault is not installed! Disabling BountyLite.")
            server.pluginManager.disablePlugin(this)
            return
        }

        saveDefaultConfig()

        economyService = EconomyService(this)
        messageManager = MessageHandler(this)

        val storage = try {
            SqlStorage(this)
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "Could not initialize bounty storage.", exception)
            server.pluginManager.disablePlugin(this)
            return
        }

        bountyManager = BountyManager(storage)
        try {
            bountyManager.load()
        } catch (exception: Exception) {
            logger.log(Level.SEVERE, "Could not load bounties from storage.", exception)
            bountyManager.close()
            server.pluginManager.disablePlugin(this)
            return
        }

        gui = BountyGui(
            this,
            bountyManager,
            messageManager,
            economyService,
            BetDialog(this, messageManager)
        )
        bountyManager.onChange = { gui.refreshOpen() }

        registerPermissions()
        registerCommands()

        server.pluginManager.registerEvents(
            DeathListener(bountyManager, messageManager, economyService),
            this
        )
        server.pluginManager.registerEvents(BountyGuiListener(this, gui), this)

        logger.info("BountyLite enabled (Paper + Brigadier).")
    }

    override fun onDisable() {
        if (::bountyManager.isInitialized) {
            bountyManager.close()
        }

        logger.info("BountyLite disabled.")
    }

    private fun registerCommands() {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val command = BountyCommand(bountyManager, messageManager, economyService, gui, this)
            event.registrar().register(command.build())
        }
    }

    private fun registerPermissions() {
        val permissions = listOf(
            Permission(
                "bounty.set",
                "Allows setting player bounties",
                PermissionDefault.OP
            ),
            Permission(
                "bounty.remove",
                "Allows removing player bounties",
                PermissionDefault.OP
            ),
            Permission(
                "bounty.reload",
                "Allows reloading bounty messages",
                PermissionDefault.OP
            ),
            Permission(
                "bounty.bet",
                "Allows adding money to bounties",
                PermissionDefault.TRUE
            ),
            Permission(
                "bounty.check",
                "Allows checking player bounties",
                PermissionDefault.TRUE
            ),
            Permission(
                "bounty.top",
                "Allows viewing the bounty leaderboard",
                PermissionDefault.TRUE
            ),
            Permission(
                "bounty.info",
                "Allows viewing plugin info",
                PermissionDefault.TRUE
            ),
            Permission(
                "bounty.gui",
                "Allows opening the bounty menu",
                PermissionDefault.TRUE
            )
        )

        permissions.forEach { server.pluginManager.addPermission(it) }
    }
}
