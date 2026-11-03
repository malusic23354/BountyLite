package net.malusic.bounty

import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

class DeathListener(
    private val plugin: JavaPlugin,
    private val bountyManager: BountyManager,
    private val messageManager: MessageHandler,
    private val economy: EconomyService
) : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        if (killer.uniqueId == victim.uniqueId) return

        val cents = bountyManager.getBounty(victim.uniqueId)
        if (cents <= 0L) return

        val placeholders = mapOf(
            "killer" to killer.name,
            "victim" to victim.name,
            "amount" to economy.format(cents)
        )

        if (plugin.config.getBoolean("anti-abuse.block-same-ip", true) && sharesAddress(killer, victim)) {
            killer.sendMessage(messageManager.message("claim.same-ip", placeholders))
            return
        }

        if (!economy.isAvailable() || !economy.deposit(killer, cents)) {
            plugin.logger.log(Level.WARNING, "Could not pay bounty of $cents cents to ${killer.name} for killing ${victim.name}.")
            killer.sendMessage(messageManager.message("claim.failed", placeholders))
            return
        }

        bountyManager.removeBounty(victim.uniqueId)

        victim.server.broadcast(messageManager.message("claim.broadcast", placeholders))
        celebrate(killer, placeholders)
    }

    private fun sharesAddress(first: Player, second: Player): Boolean {
        val firstHost = first.address?.address?.hostAddress ?: return false
        val secondHost = second.address?.address?.hostAddress ?: return false
        return firstHost == secondHost
    }

    private fun celebrate(killer: Player, placeholders: Map<String, String>) {
        if (plugin.config.getBoolean("claim.title", true)) {
            killer.showTitle(
                Title.title(
                    messageManager.message("claim.title", placeholders, withPrefix = false),
                    messageManager.message("claim.subtitle", placeholders, withPrefix = false)
                )
            )
        }

        if (plugin.config.getBoolean("claim.sound", true)) {
            killer.playSound(killer.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        }
    }
}
