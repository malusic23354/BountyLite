package net.malusic.bounty

import org.bukkit.event.Listener as Listner
import org.bukkit.plugin.java.JavaPlugin


class PluginLoader : JavaPlugin(), Listner {

    lateinit var bountyManager: BountyManager
    lateinit var messageManager: MessageHandler

    override fun onEnable() {

        messageManager = MessageHandler(this)

        bountyManager = BountyManager(this)
        bountyManager.load()

        val commandHandler = CommandHandler(
            bountyManager,
            messageManager
        )

        getCommand("bounty")?.setExecutor(commandHandler)
        getCommand("bounty")?.tabCompleter = commandHandler

        logger.info("Plugin enabled!")
    }

    override fun onDisable() {
        bountyManager.save()

    }

}