package net.malusic.bounty

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver
import net.malusic.bounty.gui.BountyGui
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

private const val PER_PAGE = 10

private typealias Context = CommandContext<CommandSourceStack>

class BountyCommand(
    private val bounties: BountyManager,
    private val messages: MessageHandler,
    private val economy: EconomyService,
    private val gui: BountyGui,
    private val plugin: JavaPlugin
) {

    private class Target(val id: UUID, val name: String)

    fun build(): LiteralCommandNode<CommandSourceStack> =
        Commands.literal("bounty")
            .executes(exec { root(it.source.sender) })
            .then(node("check", "bounty.check").then(player().executes(exec { check(it) })))
            .then(node("set", "bounty.set").then(player().then(amount(0.0).executes(exec { set(it) }))))
            .then(node("remove", "bounty.remove").then(player().executes(exec { remove(it) })))
            .then(node("bet", "bounty.bet").then(player().then(amount(0.01).executes(exec { bet(it) }))))
            .then(
                node("top", "bounty.top")
                    .executes(exec { top(it, 1) })
                    .then(
                        Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(exec { top(it, IntegerArgumentType.getInteger(it, "page")) })
                    )
            )
            .then(node("info", "bounty.info").executes(exec { info(it.source.sender) }))
            .then(node("reload", "bounty.reload").executes(exec { reload(it.source.sender) }))
            .build()

    private fun exec(block: (Context) -> Unit): Command<CommandSourceStack> =
        Command<CommandSourceStack> { context ->
            block(context)
            Command.SINGLE_SUCCESS
        }

    private fun node(name: String, permission: String): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal(name).requires { it.sender.hasPermission(permission) }

    private fun player() = Commands.argument("player", ArgumentTypes.playerProfiles())

    private fun amount(min: Double) = Commands.argument("amount", DoubleArgumentType.doubleArg(min))

    private fun root(sender: CommandSender) {
        if (sender is Player && sender.hasPermission("bounty.gui")) gui.open(sender) else sender.say("usage")
    }

    private fun check(context: Context) = withTarget(context) { sender, target ->
        sender.say("check.result", target.placeholders(bounties.getBounty(target.id)))
    }

    private fun set(context: Context) = withTarget(context) { sender, target ->
        val amount = DoubleArgumentType.getDouble(context, "amount")
        bounties.setBounty(target.id, amount)
        sender.say("set.success", target.placeholders(amount))
    }

    private fun remove(context: Context) = withTarget(context) { sender, target ->
        bounties.removeBounty(target.id)
        sender.say("remove.success", mapOf("player" to target.name))
    }

    private fun bet(context: Context) {
        val sender = context.source.sender as? Player ?: return context.source.sender.say("player-only")

        withTarget(context) { _, target ->
            val amount = DoubleArgumentType.getDouble(context, "amount")
            val formatted = mapOf("amount" to economy.format(amount))

            when {
                target.id == sender.uniqueId -> sender.say("bet.self")
                !economy.isAvailable() -> sender.say("bet.payment-failed")
                !economy.has(sender, amount) -> sender.say("bet.insufficient-funds", formatted)
                !economy.withdraw(sender, amount) -> sender.say("bet.payment-failed")
                else -> {
                    val total = bounties.addBounty(target.id, amount)
                    sender.say("bet.success", formatted + ("player" to target.name))
                    sender.say("bet.new-total", mapOf("amount" to economy.format(total)))
                }
            }
        }
    }

    private fun top(context: Context, page: Int) {
        val sender = context.source.sender
        val top = bounties.getTop()
        if (top.isEmpty()) return sender.say("top.empty")

        val pages = (top.size + PER_PAGE - 1) / PER_PAGE
        if (page > pages) return sender.say("top.invalid-page")

        sender.sendMessage(
            messages.message("top.header", mapOf("page" to "$page", "pages" to "$pages"), withPrefix = false)
        )

        top.drop((page - 1) * PER_PAGE).take(PER_PAGE).forEachIndexed { index, (uuid, amount) ->
            val placeholders = mapOf(
                "rank" to "${(page - 1) * PER_PAGE + index + 1}",
                "player" to (Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString().take(8)),
                "amount" to economy.format(amount)
            )
            sender.sendMessage(messages.message("top.entry", placeholders, withPrefix = false))
        }

        if (page < pages) {
            sender.sendMessage(messages.message("top.footer", mapOf("next" to "${page + 1}"), withPrefix = false))
        }
    }

    private fun info(sender: CommandSender) {
        val meta = plugin.pluginMeta
        val placeholders = mapOf(
            "author" to meta.authors.joinToString(", ").ifEmpty { "Unknown" },
            "version" to meta.version,
            "api" to (meta.apiVersion ?: "Unknown")
        )
        sender.sendMessage(messages.message("info.message", placeholders, withPrefix = false))
    }

    private fun reload(sender: CommandSender) {
        messages.load()
        gui.reload()
        sender.say("reload.success")
    }

    private fun withTarget(context: Context, block: (CommandSender, Target) -> Unit) {
        val sender = context.source.sender
        val target = resolveTarget(context)

        if (target == null) sender.say("unknown-player") else block(sender, target)
    }

    private fun resolveTarget(context: Context): Target? {
        val resolver = context.getArgument("player", PlayerProfileListResolver::class.java)
        val profile = runCatching { resolver.resolve(context.source) }.getOrNull()?.singleOrNull() ?: return null
        val id = profile.id ?: return null

        val player = Bukkit.getOfflinePlayer(id)
        if (!player.isOnline && !player.hasPlayedBefore()) return null

        return Target(id, profile.name ?: player.name ?: id.toString().take(8))
    }

    private fun Target.placeholders(amount: Double) =
        mapOf("player" to name, "amount" to economy.format(amount))

    private fun CommandSender.say(key: String, placeholders: Map<String, String> = emptyMap()) =
        sendMessage(messages.message(key, placeholders))
}
