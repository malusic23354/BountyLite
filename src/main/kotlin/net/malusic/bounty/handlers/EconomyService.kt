package net.malusic.bounty

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin

class EconomyService(private val plugin: JavaPlugin) {

    private fun economy(): Economy? =
        plugin.server.servicesManager.getRegistration(Economy::class.java)?.provider

    fun isAvailable(): Boolean = economy() != null

    fun format(amount: Double): String =
        economy()?.format(amount) ?: amount.toString()

    fun has(player: OfflinePlayer, amount: Double): Boolean =
        economy()?.has(player, amount) ?: false

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean =
        economy()?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false

    fun deposit(player: OfflinePlayer, amount: Double): Boolean =
        economy()?.depositPlayer(player, amount)?.transactionSuccess() ?: false
}
