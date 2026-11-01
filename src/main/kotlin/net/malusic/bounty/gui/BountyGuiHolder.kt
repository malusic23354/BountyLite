package net.malusic.bounty.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

class BountyGuiHolder(val page: Int) : InventoryHolder {

    lateinit var inv: Inventory

    val entries = HashMap<Int, UUID>()

    var previousSlot = -1
    var nextSlot = -1
    var newSlot = -1

    override fun getInventory(): Inventory = inv
}
