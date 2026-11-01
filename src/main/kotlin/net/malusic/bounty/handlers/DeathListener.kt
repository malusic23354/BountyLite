package net.malusic.bounty

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class DeathListener(
    private val bountyManager: BountyManager,
    private val messageManager: MessageHandler,
    private val economy: EconomyService
) : Listener {

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        if (killer.uniqueId == victim.uniqueId) return

        val amount = bountyManager.getBounty(victim.uniqueId)
        if (amount <= 0.0) return

        if (!economy.deposit(killer, amount)) return

        bountyManager.removeBounty(victim.uniqueId)

        victim.server.broadcast(
            messageManager.message(
                "claim.broadcast",
                mapOf(
                    "killer" to killer.name,
                    "victim" to victim.name,
                    "amount" to economy.format(amount)
                )
            )
        )
    }
}
