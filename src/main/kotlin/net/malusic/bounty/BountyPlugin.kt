package net.malusic.bounty

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.malusic.bounty.gui.BountyGui
import net.malusic.bounty.gui.BountyGuiListener
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class BountyPlugin : JavaPlugin() {

    private lateinit var bountyManager: BountyManager

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
        val gui = BountyGui(this, bountyManager, messages, economy, BetDialog(this, messages))
        bountyManager.onChange = gui::refreshOpen

        registerPermissions()

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(BountyCommand(bountyManager, messages, economy, gui, this).build())
        }

        server.pluginManager.registerEvents(DeathListener(bountyManager, messages, economy), this)
        server.pluginManager.registerEvents(BountyGuiListener(this, gui), this)
    }

    override fun onDisable() {
        if (::bountyManager.isInitialized) bountyManager.close()
    }

    private fun fail(message: String, exception: Exception) {
        logger.log(Level.SEVERE, message, exception)
        server.pluginManager.disablePlugin(this)
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
