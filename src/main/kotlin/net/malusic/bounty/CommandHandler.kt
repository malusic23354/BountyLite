package net.malusic.bounty

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class CommandHandler(
    private val bountyManager: BountyManager,
    private val messageManager: MessageHandler
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {

        if (args.isEmpty()) {
            sender.sendMessage(
                messageManager.message("usage")
            )
            return true
        }

        when (args[0].lowercase()) {

            "check" -> {

                if (args.size != 2) {
                    sender.sendMessage(
                        messageManager.message("usage-check")
                    )
                    return true
                }

                val player = Bukkit.getOfflinePlayer(args[1])
                val bounty = bountyManager.getBounty(player.uniqueId)

                sender.sendMessage(
                    messageManager.message(
                        "check.result",
                        mapOf(
                            "player" to (player.name ?: args[1]),
                            "amount" to bounty.toString()
                        )
                    )
                )
            }

            "set" -> {

                if (!sender.hasPermission("bounty.set")) {
                    sender.sendMessage(
                        messageManager.message("no-permission")
                    )
                    return true
                }

                if (args.size != 3) {
                    sender.sendMessage(
                        messageManager.message("usage-set")
                    )
                    return true
                }

                val player = Bukkit.getOfflinePlayer(args[1])

                val amount = args[2].toDoubleOrNull()

                if (amount == null || amount < 0) {
                    sender.sendMessage(
                        messageManager.message("invalid-amount")
                    )
                    return true
                }

                bountyManager.setBounty(
                    player.uniqueId,
                    amount
                )

                sender.sendMessage(
                    messageManager.message(
                        "set.success",
                        mapOf(
                            "player" to (player.name ?: args[1]),
                            "amount" to amount.toString()
                        )
                    )
                )
            }

            "remove" -> {

                if (!sender.hasPermission("bounty.remove")) {
                    sender.sendMessage(
                        messageManager.message("no-permission")
                    )
                    return true
                }

                if (args.size != 2) {
                    sender.sendMessage(
                        messageManager.message("usage-remove")
                    )
                    return true
                }

                val player = Bukkit.getOfflinePlayer(args[1])

                bountyManager.removeBounty(
                    player.uniqueId
                )

                sender.sendMessage(
                    messageManager.message(
                        "remove.success",
                        mapOf(
                            "player" to (player.name ?: args[1])
                        )
                    )
                )
            }

            "bet" -> {

                if (args.size != 3) {
                    sender.sendMessage(
                        messageManager.message("usage-bet")
                    )
                    return true
                }

                val player = Bukkit.getOfflinePlayer(args[1])

                val amount = args[2].toDoubleOrNull()

                if (amount == null || amount <= 0) {
                    sender.sendMessage(
                        messageManager.message("invalid-amount")
                    )
                    return true
                }

                val currentBounty =
                    bountyManager.getBounty(player.uniqueId)

                val newBounty =
                    currentBounty + amount

                bountyManager.setBounty(
                    player.uniqueId,
                    newBounty
                )

                sender.sendMessage(
                    messageManager.message(
                        "bet.success",
                        mapOf(
                            "player" to (player.name ?: args[1]),
                            "amount" to amount.toString()
                        )
                    )
                )

                sender.sendMessage(
                    messageManager.message(
                        "bet.new-total",
                        mapOf(
                            "amount" to newBounty.toString()
                        )
                    )
                )
            }

            "reload" -> {

                if (!sender.hasPermission("bounty.reload")) {
                    sender.sendMessage(
                        messageManager.message("no-permission")
                    )
                    return true
                }

                messageManager.reload()

                sender.sendMessage(
                    messageManager.message("reload.success")
                )
            }

            else -> {
                sender.sendMessage(
                    messageManager.message("unknown-command")
                )
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

            val options = listOf(
                "check",
                "set",
                "remove",
                "bet",
                "reload"
            )

            return options.filter {
                it.startsWith(
                    args[0],
                    ignoreCase = true
                )
            }
        }

        if (args.size == 2) {

            when (args[0].lowercase()) {

                "check",
                "set",
                "remove",
                "bet" -> {

                    return Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter {
                            it.startsWith(
                                args[1],
                                ignoreCase = true
                            )
                        }
                }
            }
        }

        if (args.size == 3) {

            when (args[0].lowercase()) {

                "set",
                "bet" -> {
                    return listOf("<amount>")
                }
            }
        }

        return emptyList()
    }
}
