package net.malusic.bounty

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BetLimits(private val plugin: JavaPlugin) {

    val min: Long
        get() = EconomyService.toCents(plugin.config.getDouble("bets.min", 0.01))

    val max: Long
        get() = plugin.config.getDouble("bets.max", 0.0).let { if (it > 0.0) EconomyService.toCents(it) else Long.MAX_VALUE }

    val cooldownSeconds: Long
        get() = plugin.config.getLong("bets.cooldown", 0L).coerceAtLeast(0L)

    val confirmThreshold: Long
        get() = plugin.config.getDouble("bets.confirm-threshold", 0.0)
            .let { if (it > 0.0) EconomyService.toCents(it) else Long.MAX_VALUE }
}

class BetCooldown {

    private val lastBet = ConcurrentHashMap<UUID, Long>()

    fun remainingSeconds(uuid: UUID, cooldownSeconds: Long): Long {
        if (cooldownSeconds <= 0L) return 0L
        val last = lastBet[uuid] ?: return 0L
        val remainingMillis = cooldownSeconds * 1000L - (System.currentTimeMillis() - last)
        if (remainingMillis <= 0L) {
            lastBet.remove(uuid)
            return 0L
        }
        return (remainingMillis + 999L) / 1000L
    }

    fun mark(uuid: UUID, cooldownSeconds: Long) {
        val now = System.currentTimeMillis()
        if (lastBet.size >= MAX_ENTRIES) {
            val ttl = cooldownSeconds.coerceAtLeast(0L) * 1000L
            lastBet.entries.removeIf { now - it.value >= ttl }
        }
        lastBet[uuid] = now
    }

    private companion object {
        const val MAX_ENTRIES = 1024
    }
}

class BetExpiry(
    private val plugin: JavaPlugin,
    private val messages: MessageHandler,
    private val bountyManager: BountyManager,
    private val economy: EconomyService
) {

    fun run() {
        val hours = plugin.config.getDouble("bets.expiry-hours", 0.0)
        if (hours <= 0.0) return

        val refund = plugin.config.getBoolean("bets.refund-expired", true)
        val cutoff = System.currentTimeMillis() - (hours * 3_600_000L).toLong()

        val refunded = bountyManager.expiredContributions(cutoff).filter { contribution ->
            !refund || economy.deposit(Bukkit.getOfflinePlayer(contribution.bettor), contribution.cents)
        }

        bountyManager.expire(refunded)
        if (refund) refunded.forEach { notify(Bukkit.getOfflinePlayer(it.bettor), it) }
    }

    private fun notify(bettor: OfflinePlayer, contribution: Contribution) {
        val online = bettor.player ?: return
        val target = Bukkit.getOfflinePlayer(contribution.target).name ?: "a player"
        online.sendMessage(
            messages.message(
                "bet.expired",
                mapOf("amount" to economy.format(contribution.cents), "player" to target)
            )
        )
    }
}