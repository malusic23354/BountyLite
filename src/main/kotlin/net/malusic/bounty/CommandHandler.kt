package net.malusic.bounty

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class CommandHandler(
    private val bountyManager: BountyManager,
    private val messageManager: MessageHandler,
    private val economyProvider: () -> Economy?
) : CommandExecutor, TabCompleter {

    private fun format(amount: Double): String =
        economyProvider()?.format(amount) ?: amount.toString()

    private fun findPlayer(name: String): OfflinePlayer? {
        val player = Bukkit.getOfflinePlayer(name)
        return if (player.isOnline || player.hasPlayedBefore()) player else null
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (args.isEmpty()) {
            sender.sendMessage(messageManager.message("usage"))
            return true
        }

        when (args[0].lowercase()) {

            "check" -> {
                if (args.size != 2) {
                    sender.sendMessage(messageManager.message("usage-check"))
                    return true
                }

                val player = findPlayer(args[1]) ?: run {
                    sender.sendMessage(messageManager.message("unknown-player"))
                    return true
                }

                sender.sendMessage(
                    messageManager.message(
                        "check.result",
                        mapOf(
                            "player" to (player.name ?: args[1]),
                            "amount" to format(bountyManager.getBounty(player.uniqueId))
                        )
                    )
                )
            }

            "set" -> {
                if (!sender.hasPermission("bounty.set")) {
                    sender.sendMessage(messageManager.message("no-permission"))
                    return true
                }

                if (args.size != 3) {
                    sender.sendMessage(messageManager.message("usage-set"))
                    return true
                }

                val player = findPlayer(args[1]) ?: run {
                    sender.sendMessage(messageManager.message("unknown-player"))
                    return true
                }

                val amount = args[2].toDoubleOrNull()
                if (amount == null || amount < 0) {
                    sender.sendMessage(messageManager.message("invalid-amount"))
                    return true
                }

                bountyManager.setBounty(player.uniqueId, amount)

                sender.sendMessage(
                    messageManager.message(
                        "set.success",
                        mapOf(
                            "player" to (player.name ?: args[1]),
                            "amount" to format(amount)
                        )
                    )
                )
            }

            "remove" -> {
                if (!sender.hasPermission("bounty.remove")) {
                    sender.sendMessage(messageManager.message("no-permission"))
                    return true
                }

                if (args.size != 2) {
                    sender.sendMessage(messageManager.message("usage-remove"))
                    return true
                }

                val player = findPlayer(args[1]) ?: run {
                    sender.sendMessage(messageManager.message("unknown-player"))
                    return true
                }

                bountyManager.removeBounty(player.uniqueId)

                sender.sendMessage(
                    messageManager.message(
                        "remove.success",
                        mapOf("player" to (player.name ?: args[1]))
                    )
                )
            }

            "bet" -> {
                if (!sender.hasPermission("bounty.bet")) {
                    sender.sendMessage(messageManager.message("no-permission"))
                    return true
                }

                if (sender !is Player) {
                    sender.sendMessage(messageManager.message("player-only"))
                    return true
                }

                if (args.size != 3) {
                    sender.sendMessage(messageManager.message("usage-bet"))
                    return true
                }

                val player = findPlayer(args[1]) ?: run {
                    sender.sendMessage(messageManager.message("unknown-player"))
                    return true
                }

                if (player.uniqueId == sender.uniqueId) {
                    sender.sendMessage(messageManager.message("bet.self"))
                    return true
                }

                val amount = args[2].toDoubleOrNull()
                if (amount == null || amount <= 0.0) {
                    sender.sendMessage(messageManager.message("invalid-amount"))
                    return true
                }

                val economy = economyProvider() ?: run {
                    sender.sendMessage(messageManager.message("bet.payment-failed"))
                    return true
                }

                if (!economy.has(sender, amount)) {
                    sender.sendMessage(
                        messageManager.message(
                            "bet.insufficient-funds",
                            mapOf("amount" to format(amount))
                        )
                    )
                    return true
                }

                if (!economy.withdrawPlayer(sender, amount).transactionSuccess()) {
                    sender.sendMessage(messageManager.message("bet.payment-failed"))
                    return true
                }

                val newBounty = bountyManager.getBounty(player.uniqueId) + amount
                bountyManager.setBounty(player.uniqueId, newBounty)

                sender.sendMessage(
                    messageManager.message(
                        "bet.success",
                        mapOf(
                            "player" to (player.name ?: args[1]),
                            "amount" to format(amount)
                        )
                    )
                )

                sender.sendMessage(
                    messageManager.message(
                        "bet.new-total",
                        mapOf("amount" to format(newBounty))
                    )
                )
            }

            "reload" -> {
                if (!sender.hasPermission("bounty.reload")) {
                    sender.sendMessage(messageManager.message("no-permission"))
                    return true
                }

                messageManager.reload()
                sender.sendMessage(messageManager.message("reload.success"))
            }

            else -> {
                sender.sendMessage(messageManager.message("unknown-command"))
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {

        if (args.size == 1) {
            return listOf("check", "set", "remove", "bet", "reload")
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2 && args[0].lowercase() in listOf("check", "set", "remove", "bet")) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }

        if (args.size == 3 && args[0].lowercase() in listOf("set", "bet")) {
            return listOf("<amount>")
        }

        return emptyList()
    }
}