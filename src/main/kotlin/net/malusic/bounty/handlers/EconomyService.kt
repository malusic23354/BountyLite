package net.malusic.bounty

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.util.Locale

class EconomyService(private val plugin: JavaPlugin) {

    private fun economy(): Economy? =
        plugin.server.servicesManager.getRegistration(Economy::class.java)?.provider

    fun isAvailable(): Boolean = economy() != null

    fun providerName(): String? = economy()?.name

    fun format(cents: Long): String =
        economy()?.format(toDollars(cents)) ?: toDollars(cents).toString()

    fun raw(cents: Long): String = String.format(Locale.US, "%.2f", toDollars(cents))

    fun has(player: OfflinePlayer, cents: Long): Boolean =
        economy()?.has(player, toDollars(cents)) ?: false

    fun withdraw(player: OfflinePlayer, cents: Long): Boolean =
        economy()?.withdrawPlayer(player, toDollars(cents))?.transactionSuccess() ?: false

    fun deposit(player: OfflinePlayer, cents: Long): Boolean =
        economy()?.depositPlayer(player, toDollars(cents))?.transactionSuccess() ?: false

    companion object {
        fun toCents(amount: Double): Long = Math.round(amount * 100.0)

        private fun toDollars(cents: Long): Double = cents / 100.0
    }
}