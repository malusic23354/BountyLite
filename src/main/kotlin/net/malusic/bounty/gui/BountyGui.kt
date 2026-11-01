package net.malusic.bounty.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextDecoration
import net.malusic.bounty.BetDialog
import net.malusic.bounty.BountyManager
import net.malusic.bounty.EconomyService
import net.malusic.bounty.MessageHandler
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

class BountyGui(
    private val plugin: JavaPlugin,
    private val bountyManager: BountyManager,
    private val messages: MessageHandler,
    private val economy: EconomyService,
    private val dialog: BetDialog
) {

    fun open(player: Player, page: Int = 0) {
        val maxRows = plugin.config.getInt("gui.max-rows", 6).coerceIn(2, 6)
        val perPage = (maxRows - 1) * 9
        val top = bountyManager.getTop()
        val pages = maxOf(1, (top.size + perPage - 1) / perPage)
        val current = page.coerceIn(0, pages - 1)
        val slice = top.drop(current * perPage).take(perPage)
        val rows = maxOf(1, (slice.size + 8) / 9) + 1

        val holder = BountyGuiHolder(current)
        val title = messages.message(
            "gui.title",
            mapOf("page" to (current + 1).toString(), "pages" to pages.toString()),
            withPrefix = false
        )
        val inventory = Bukkit.createInventory(holder, rows * 9, title)
        holder.inv = inventory

        val offset = current * perPage
        slice.forEachIndexed { index, (uuid, amount) ->
            holder.entries[index] = uuid
            inventory.setItem(index, head(uuid, amount, offset + index + 1))
        }

        if (slice.isEmpty()) {
            inventory.setItem(
                4,
                item(
                    material("empty", Material.BARRIER),
                    messages.message("gui.empty.name", withPrefix = false),
                    messages.messageList("gui.empty.lore")
                )
            )
        }

        val base = (rows - 1) * 9
        val filler = item(material("filler", Material.GRAY_STAINED_GLASS_PANE), Component.text(" "))
        for (slot in base until base + 9) inventory.setItem(slot, filler)

        if (current > 0) {
            holder.previousSlot = base + 3
            inventory.setItem(
                holder.previousSlot,
                item(
                    material("previous", Material.ARROW),
                    messages.message("gui.previous", mapOf("page" to current.toString()), withPrefix = false)
                )
            )
        }

        if (current < pages - 1) {
            holder.nextSlot = base + 5
            inventory.setItem(
                holder.nextSlot,
                item(
                    material("next", Material.ARROW),
                    messages.message("gui.next", mapOf("page" to (current + 2).toString()), withPrefix = false)
                )
            )
        }

        if (player.hasPermission("bounty.bet")) {
            holder.newSlot = base + 4
            inventory.setItem(
                holder.newSlot,
                item(
                    material("new-bounty", Material.EMERALD),
                    messages.message("gui.new-bounty.name", withPrefix = false),
                    messages.messageList("gui.new-bounty.lore")
                )
            )
        }

        player.openInventory(inventory)
    }

    fun bet(player: Player, target: UUID) {
        if (!player.hasPermission("bounty.bet")) {
            player.sendMessage(messages.message("no-permission"))
            return
        }

        val name = Bukkit.getOfflinePlayer(target).name ?: target.toString().take(8)
        val amount = economy.format(bountyManager.getBounty(target))

        if (dialog.openBet(player, name, amount)) return

        player.sendMessage(
            messages.message("gui.bet-fallback", mapOf("player" to name))
                .clickEvent(ClickEvent.suggestCommand("/bounty bet $name "))
        )
    }

    fun newBounty(player: Player) {
        if (!player.hasPermission("bounty.bet")) {
            player.sendMessage(messages.message("no-permission"))
            return
        }

        if (dialog.openNew(player)) return

        player.sendMessage(
            messages.message("gui.new-fallback")
                .clickEvent(ClickEvent.suggestCommand("/bounty bet "))
        )
    }

    fun refreshOpen() {
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.getHolder(false) as? BountyGuiHolder ?: continue
            open(player, holder.page)
        }
    }

    fun reload() {
        plugin.reloadConfig()
    }

    private fun head(uuid: UUID, amount: Double, rank: Int): ItemStack {
        val owner = Bukkit.getOfflinePlayer(uuid)
        val placeholders = mapOf(
            "player" to (owner.name ?: uuid.toString().take(8)),
            "amount" to economy.format(amount),
            "rank" to rank.toString()
        )

        val stack = item(
            Material.PLAYER_HEAD,
            messages.message("gui.head.name", placeholders, withPrefix = false),
            messages.messageList("gui.head.lore", placeholders)
        )
        stack.editMeta(SkullMeta::class.java) { it.setOwningPlayer(owner) }
        return stack
    }

    private fun item(
        material: Material,
        name: Component,
        lore: List<Component> = emptyList()
    ): ItemStack {
        val stack = ItemStack(material)
        stack.editMeta { meta ->
            meta.displayName(name.plain())
            if (lore.isNotEmpty()) meta.lore(lore.map { it.plain() })
        }
        return stack
    }

    private fun material(key: String, default: Material): Material =
        Material.matchMaterial(plugin.config.getString("gui.materials.$key") ?: "") ?: default

    private fun Component.plain(): Component =
        decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)
}
