package net.malusic.bounty

import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.java.JavaPlugin

class PluginLoader : JavaPlugin() {

    lateinit var bountyManager: BountyManager
    lateinit var messageManager: MessageHandler

    private fun economy(): Economy? =
        server.servicesManager.getRegistration(Economy::class.java)?.provider

    override fun onEnable() {
        if (server.pluginManager.getPlugin("Vault") == null) {
            logger.severe("Vault is not installed!")
            server.pluginManager.disablePlugin(this)
            return
        }

        messageManager = MessageHandler(this)

        bountyManager = BountyManager(this)
        bountyManager.load()

        val commandHandler = CommandHandler(
            bountyManager,
            messageManager
        ) { economy() }

        getCommand("bounty")?.setExecutor(commandHandler)
        getCommand("bounty")?.tabCompleter = commandHandler

        server.pluginManager.registerEvents(
            DeathListener(bountyManager, messageManager) { economy() },
            this
        )

        logger.info("Plugin enabled!")
    }

    override fun onDisable() {
        if (::bountyManager.isInitialized) {
            bountyManager.save()
        }
    }
}