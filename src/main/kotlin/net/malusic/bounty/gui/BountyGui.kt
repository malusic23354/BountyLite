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
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
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
) : Listener {

    private val blankName = Component.text(" ")

    fun open(player: Player, page: Int = 0, query: String? = null) {
        val search = query?.trim()?.takeIf { it.isNotEmpty() }
        val maxRows = plugin.config.getInt("gui.max-rows", 6).coerceIn(2, 6)
        val perPage = (maxRows - 1) * 9
        val top = bountyManager.getTop()
        val filtered = if (search == null) top else top.filter { (uuid, _) -> matches(uuid, search) }
        val pages = maxOf(1, (filtered.size + perPage - 1) / perPage)
        val current = page.coerceIn(0, pages - 1)
        val offset = current * perPage
        val slice = filtered.subList(offset, minOf(offset + perPage, filtered.size))
        val rows = maxOf(1, (slice.size + 8) / 9) + 1

        val holder = BountyGuiHolder(current, search, slice.size)
        val title = messages.message(
            if (search == null) "gui.title" else "gui.search-title",
            mapOf(
                "page" to (current + 1).toString(),
                "pages" to pages.toString(),
                "query" to (search ?: "")
            ),
            withPrefix = false
        )
        val inventory = Bukkit.createInventory(holder, rows * 9, title)
        holder.inv = inventory

        slice.forEachIndexed { index, (uuid, amount) ->
            holder.entries[index] = uuid
            inventory.setItem(index, head(uuid, amount, offset + index + 1))
        }

        if (slice.isEmpty()) {
            val key = if (top.isNotEmpty() && search != null) "no-results" else "empty"
            button(inventory, 4, key, Material.BARRIER, placeholders = mapOf("query" to (search ?: "")))
        }

        val base = (rows - 1) * 9
        val filler = item(material("filler", Material.GRAY_STAINED_GLASS_PANE), blankName)
        for (slot in base until base + 9) inventory.setItem(slot, filler)

        if (current > 0) {
            holder.previousSlot = base + 3
            button(
                inventory, holder.previousSlot, "previous", Material.ARROW,
                nameKey = "gui.previous", loreKey = null,
                placeholders = mapOf("page" to "$current")
            )
        }

        if (current < pages - 1) {
            holder.nextSlot = base + 5
            button(
                inventory, holder.nextSlot, "next", Material.ARROW,
                nameKey = "gui.next", loreKey = null,
                placeholders = mapOf("page" to "${current + 2}")
            )
        }

        if (player.hasPermission("bounty.bet")) {
            holder.newSlot = base + 4
            button(inventory, holder.newSlot, "new-bounty", Material.EMERALD)
        }

        holder.searchSlot = base
        button(
            inventory, holder.searchSlot, "search", Material.COMPASS,
            placeholders = mapOf("query" to (search ?: ""))
        )

        if (search != null) {
            holder.clearSlot = base + 8
            button(inventory, holder.clearSlot, "clear", Material.BARRIER)
        }

        player.openInventory(inventory)
    }

    fun bet(player: Player, target: UUID) {
        if (!checkPermission(player)) return

        val name = Bukkit.getOfflinePlayer(target).name ?: target.toString().take(8)
        val amount = economy.format(bountyManager.getBounty(target))

        if (dialog.openBet(player, name, amount)) return

        player.fallback("gui.bet-fallback", "/bounty bet $name ", mapOf("player" to name))
    }

    fun newBounty(player: Player) {
        if (!checkPermission(player)) return

        if (!dialog.openNew(player)) {
            player.fallback("gui.new-fallback", "/bounty bet ")
        }
    }

    fun search(player: Player) {
        if (!checkPermission(player, "bounty.gui")) return

        if (!dialog.openSearch(player)) {
            player.fallback("gui.search-fallback", "/bounty search ")
        }
    }

    fun refreshOpen() {
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.getHolder(false) as? BountyGuiHolder ?: continue
            open(player, holder.page, holder.query)
        }
    }

    fun reload() {
        plugin.reloadConfig()
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.getHolder(false) as? BountyGuiHolder ?: return
        event.isCancelled = true

        if (event.clickedInventory !== top) return
        val player = event.whoClicked as? Player ?: return

        val slot = event.slot
        val target = holder.entries.getOrNull(slot)

        val action = when {
            target != null -> Runnable { bet(player, target) }
            slot == holder.searchSlot -> Runnable { search(player) }
            slot == holder.clearSlot -> Runnable { open(player, 0) }
            slot == holder.previousSlot -> Runnable { open(player, holder.page - 1, holder.query) }
            slot == holder.nextSlot -> Runnable { open(player, holder.page + 1, holder.query) }
            slot == holder.newSlot -> Runnable { newBounty(player) }
            else -> null
        }
        if (action != null) plugin.server.scheduler.runTask(plugin, action)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is BountyGuiHolder) {
            event.isCancelled = true
        }
    }

    private fun matches(uuid: UUID, query: String): Boolean {
        val name = Bukkit.getOfflinePlayer(uuid).name
        return (name != null && name.contains(query, ignoreCase = true)) ||
                uuid.toString().startsWith(query, ignoreCase = true)
    }

    private fun checkPermission(player: Player, permission: String = "bounty.bet"): Boolean {
        val allowed = player.hasPermission(permission)
        if (!allowed) player.sendMessage(messages.message("no-permission"))
        return allowed
    }

    private fun Player.fallback(key: String, command: String, placeholders: Map<String, String> = emptyMap()) {
        sendMessage(
            messages.message(key, placeholders)
                .clickEvent(ClickEvent.suggestCommand(command))
        )
    }

    private fun button(
        inventory: Inventory,
        slot: Int,
        materialKey: String,
        default: Material,
        nameKey: String = "gui.$materialKey.name",
        loreKey: String? = "gui.$materialKey.lore",
        placeholders: Map<String, String> = emptyMap()
    ) {
        inventory.setItem(
            slot,
            item(
                material(materialKey, default),
                messages.message(nameKey, placeholders, withPrefix = false),
                loreKey?.let { messages.messageList(it, placeholders) } ?: emptyList()
            )
        )
    }

    private fun head(uuid: UUID, cents: Long, rank: Int): ItemStack {
        val owner = Bukkit.getOfflinePlayer(uuid)
        val placeholders = mapOf(
            "player" to (owner.name ?: uuid.toString().take(8)),
            "amount" to economy.format(cents),
            "rank" to rank.toString()
        )

        val stack = item(
            Material.PLAYER_HEAD,
            messages.message("gui.head.name", placeholders, withPrefix = false),
            messages.messageList("gui.head.lore", placeholders)
        )
        stack.editMeta(SkullMeta::class.java) { it.owningPlayer = owner }
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

private class BountyGuiHolder(
    val page: Int,
    val query: String? = null,
    size: Int = 0
) : InventoryHolder {

    lateinit var inv: Inventory

    val entries = arrayOfNulls<UUID>(size)

    var previousSlot = -1
    var nextSlot = -1
    var newSlot = -1
    var searchSlot = -1
    var clearSlot = -1

    override fun getInventory(): Inventory = inv
}
