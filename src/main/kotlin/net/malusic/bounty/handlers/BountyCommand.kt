package net.malusic.bounty

import com.destroystokyo.paper.profile.PlayerProfile
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import net.malusic.bounty.gui.BountyGui
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class BountyCommand(
    private val bountyManager: BountyManager,
    private val messageManager: MessageHandler,
    private val economy: EconomyService,
    private val gui: BountyGui,
    private val plugin: JavaPlugin
) {

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("bounty")
            .executes { context -> executeRoot(context.source.sender) }
            .then(checkCommand())
            .then(setCommand())
            .then(removeCommand())
            .then(betCommand())
            .then(topCommand())
            .then(infoCommand())
            .then(reloadCommand())
            .build()

    private fun checkCommand() =
        Commands.literal("check")
            .then(
                Commands.argument("player", ArgumentTypes.playerProfiles())
                    .executes { context -> executeCheck(context) }
            )

    private fun setCommand() =
        Commands.literal("set")
            .then(
                Commands.argument("player", ArgumentTypes.playerProfiles())
                    .then(
                        Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                            .executes { context -> executeSet(context) }
                    )
            )

    private fun removeCommand() =
        Commands.literal("remove")
            .then(
                Commands.argument("player", ArgumentTypes.playerProfiles())
                    .executes { context -> executeRemove(context) }
            )

    private fun betCommand() =
        Commands.literal("bet")
            .then(
                Commands.argument("player", ArgumentTypes.playerProfiles())
                    .then(
                        Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                            .executes { context -> executeBet(context) }
                    )
            )

    private fun topCommand() =
        Commands.literal("top")
            .executes { context -> executeTop(context, 1) }
            .then(
                Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes { context ->
                        executeTop(context, IntegerArgumentType.getInteger(context, "page"))
                    }
            )

    private fun infoCommand() =
        Commands.literal("info")
            .executes { context -> executeInfo(context.source.sender) }

    private fun reloadCommand() =
        Commands.literal("reload")
            .executes { context -> executeReload(context) }

    private fun executeCheck(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        if (!requirePermission(sender, "bounty.check")) return Command.SINGLE_SUCCESS

        val target = resolveTarget(context) ?: return unknownPlayer(sender)
        val amount = bountyManager.getBounty(target.player.uniqueId)

        sender.sendMessage(
            messageManager.message(
                "check.result",
                mapOf(
                    "player" to target.name,
                    "amount" to economy.format(amount)
                )
            )
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeSet(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        if (!requirePermission(sender, "bounty.set")) return Command.SINGLE_SUCCESS

        val target = resolveTarget(context) ?: return unknownPlayer(sender)
        val amount = DoubleArgumentType.getDouble(context, "amount")

        bountyManager.setBounty(target.player.uniqueId, amount)

        sender.sendMessage(
            messageManager.message(
                "set.success",
                mapOf(
                    "player" to target.name,
                    "amount" to economy.format(amount)
                )
            )
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeRemove(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        if (!requirePermission(sender, "bounty.remove")) return Command.SINGLE_SUCCESS

        val target = resolveTarget(context) ?: return unknownPlayer(sender)
        bountyManager.removeBounty(target.player.uniqueId)

        sender.sendMessage(
            messageManager.message(
                "remove.success",
                mapOf("player" to target.name)
            )
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeBet(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        if (!requirePermission(sender, "bounty.bet")) return Command.SINGLE_SUCCESS

        if (sender !is Player) {
            sender.sendMessage(messageManager.message("player-only"))
            return Command.SINGLE_SUCCESS
        }

        val target = resolveTarget(context) ?: return unknownPlayer(sender)
        if (target.player.uniqueId == sender.uniqueId) {
            sender.sendMessage(messageManager.message("bet.self"))
            return Command.SINGLE_SUCCESS
        }

        val amount = DoubleArgumentType.getDouble(context, "amount")
        if (amount <= 0.0) {
            sender.sendMessage(messageManager.message("invalid-amount"))
            return Command.SINGLE_SUCCESS
        }

        if (!economy.isAvailable()) {
            sender.sendMessage(messageManager.message("bet.payment-failed"))
            return Command.SINGLE_SUCCESS
        }

        if (!economy.has(sender, amount)) {
            sender.sendMessage(
                messageManager.message(
                    "bet.insufficient-funds",
                    mapOf("amount" to economy.format(amount))
                )
            )
            return Command.SINGLE_SUCCESS
        }

        if (!economy.withdraw(sender, amount)) {
            sender.sendMessage(messageManager.message("bet.payment-failed"))
            return Command.SINGLE_SUCCESS
        }

        val newBounty = bountyManager.getBounty(target.player.uniqueId) + amount
        bountyManager.setBounty(target.player.uniqueId, newBounty)

        sender.sendMessage(
            messageManager.message(
                "bet.success",
                mapOf(
                    "player" to target.name,
                    "amount" to economy.format(amount)
                )
            )
        )
        sender.sendMessage(
            messageManager.message(
                "bet.new-total",
                mapOf("amount" to economy.format(newBounty))
            )
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeTop(
        context: CommandContext<CommandSourceStack>,
        page: Int
    ): Int {
        val sender = context.source.sender
        if (!requirePermission(sender, "bounty.top")) return Command.SINGLE_SUCCESS

        val top = bountyManager.getTop()
        if (top.isEmpty()) {
            sender.sendMessage(messageManager.message("top.empty"))
            return Command.SINGLE_SUCCESS
        }

        val perPage = 10
        val pages = (top.size + perPage - 1) / perPage
        if (page > pages) {
            sender.sendMessage(messageManager.message("top.invalid-page"))
            return Command.SINGLE_SUCCESS
        }

        sender.sendMessage(
            messageManager.message(
                "top.header",
                mapOf("page" to page.toString(), "pages" to pages.toString()),
                withPrefix = false
            )
        )

        top.drop((page - 1) * perPage)
            .take(perPage)
            .forEachIndexed { index, (uuid, amount) ->
                val name = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString().take(8)
                sender.sendMessage(
                    messageManager.message(
                        "top.entry",
                        mapOf(
                            "rank" to ((page - 1) * perPage + index + 1).toString(),
                            "player" to name,
                            "amount" to economy.format(amount)
                        ),
                        withPrefix = false
                    )
                )
            }

        if (page < pages) {
            sender.sendMessage(
                messageManager.message(
                    "top.footer",
                    mapOf("next" to (page + 1).toString()),
                    withPrefix = false
                )
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun executeReload(context: CommandContext<CommandSourceStack>): Int {
        val sender = context.source.sender
        if (!requirePermission(sender, "bounty.reload")) return Command.SINGLE_SUCCESS

        messageManager.reload()
        gui.reload()
        sender.sendMessage(messageManager.message("reload.success"))
        return Command.SINGLE_SUCCESS
    }

    private fun executeInfo(sender: CommandSender): Int {
        if (!requirePermission(sender, "bounty.info")) return Command.SINGLE_SUCCESS

        val meta = plugin.pluginMeta
        sender.sendMessage(
            messageManager.message(
                "info.message",
                mapOf(
                    "author" to meta.authors.joinToString(", ").ifEmpty { "Unknown" },
                    "version" to meta.version,
                    "api" to (meta.apiVersion ?: "Unknown")
                ),
                withPrefix = false
            )
        )
        return Command.SINGLE_SUCCESS
    }

    private fun executeRoot(sender: CommandSender): Int {
        if (sender !is Player || !sender.hasPermission("bounty.gui")) return usage(sender)

        gui.open(sender)
        return Command.SINGLE_SUCCESS
    }

    private fun usage(sender: CommandSender): Int {
        sender.sendMessage(messageManager.message("usage"))
        return Command.SINGLE_SUCCESS
    }

    private fun requirePermission(sender: CommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true

        sender.sendMessage(messageManager.message("no-permission"))
        return false
    }

    private fun unknownPlayer(sender: CommandSender): Int {
        sender.sendMessage(messageManager.message("unknown-player"))
        return Command.SINGLE_SUCCESS
    }

    private fun resolveTarget(context: CommandContext<CommandSourceStack>): Target? {
        val resolver = context.getArgument("player", PlayerProfileListResolver::class.java)
        val profiles: Collection<PlayerProfile> = try {
            resolver.resolve(context.source)
        } catch (_: CommandSyntaxException) {
            return null
        }

        if (profiles.size != 1) return null

        val profile = profiles.first()
        val id = profile.id ?: return null
        val player = Bukkit.getOfflinePlayer(id)
        if (!player.isOnline && !player.hasPlayedBefore()) return null

        return Target(
            player = player,
            name = profile.name ?: player.name ?: id.toString().take(8)
        )
    }

    private data class Target(
        val player: OfflinePlayer,
        val name: String
    )
}
