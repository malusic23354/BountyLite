package net.malusic.bounty

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class MessageHandler(private val plugin: JavaPlugin) {

    private val miniMessage = MiniMessage.miniMessage()
    private val file = File(plugin.dataFolder, "messages.yml")
    private var config = YamlConfiguration()

    init {
        load()
    }

    fun load() {
        if (!file.exists()) plugin.saveResource("messages.yml", false)

        config = YamlConfiguration.loadConfiguration(file)
        addMissingKeys()
    }

    fun message(
        path: String,
        placeholders: Map<String, String> = emptyMap(),
        withPrefix: Boolean = true
    ): Component {
        val text = fill(config.getString(path) ?: "<red>Missing message: $path", placeholders)
        val prefix = if (withPrefix) config.getString("prefix") ?: "" else ""
        return miniMessage.deserialize(prefix + text)
    }

    fun messageList(path: String, placeholders: Map<String, String> = emptyMap()): List<Component> =
        config.getStringList(path)
            .ifEmpty { listOf(config.getString(path) ?: "<red>Missing message: $path") }
            .map { miniMessage.deserialize(fill(it, placeholders)) }

    private fun fill(text: String, placeholders: Map<String, String>): String =
        placeholders.entries.fold(text) { result, (key, value) -> result.replace("{$key}", value) }

    private fun addMissingKeys() {
        val stream = plugin.getResource("messages.yml") ?: return
        val defaults = stream.reader(Charsets.UTF_8).use { YamlConfiguration.loadConfiguration(it) }

        val missing = defaults.getKeys(true).filter {
            !defaults.isConfigurationSection(it) && !config.contains(it, true)
        }
        if (missing.isEmpty()) return

        for (key in missing) {
            config.set(key, defaults.get(key))
            config.setComments(key, defaults.getComments(key))
        }

        config.save(file)
        plugin.logger.info("Added ${missing.size} new message(s) to messages.yml: ${missing.joinToString()}")
    }
}
