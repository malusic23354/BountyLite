package net.malusic.bounty.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.plugin.java.JavaPlugin

class BountyGuiListener(
    private val plugin: JavaPlugin,
    private val gui: BountyGui
) : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val holder = top.getHolder(false) as? BountyGuiHolder ?: return
        event.isCancelled = true

        if (event.clickedInventory !== top) return
        val player = event.whoClicked as? Player ?: return

        val slot = event.slot
        val target = holder.entries[slot]

        plugin.server.scheduler.runTask(plugin, Runnable {
            when {
                target != null -> gui.bet(player, target)
                slot == holder.previousSlot -> gui.open(player, holder.page - 1)
                slot == holder.nextSlot -> gui.open(player, holder.page + 1)
                slot == holder.newSlot -> gui.newBounty(player)
            }
        })
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is BountyGuiHolder) {
            event.isCancelled = true
        }
    }
}
