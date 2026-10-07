package net.malusic.bounty

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

class DeathListener(
    private val bountyManager: BountyManager,
    private val messageManager: MessageHandler,
    private val economyProvider: () -> Economy?
) : Listener {

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return
        if (killer.uniqueId == victim.uniqueId) return

        val amount = bountyManager.getBounty(victim.uniqueId)
        if (amount <= 0.0) return

        val economy = economyProvider() ?: return
        if (!economy.depositPlayer(killer, amount).transactionSuccess()) return

        bountyManager.removeBounty(victim.uniqueId)

        Bukkit.broadcast(
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