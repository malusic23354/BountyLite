package net.malusic.bounty

import com.google.gson.GsonBuilder
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID


class BountyManager(private val plugin: JavaPlugin) {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val file = File(plugin.dataFolder, "bounties.json")

    var data = BountyData()

    fun load() {
        if (!file.exists()) {
            file.parentFile.mkdirs()
            save()
            return
        }

        data = gson.fromJson(
            file.readText(),
            BountyData::class.java
        )
    }

    fun save() {
        file.writeText(
            gson.toJson(data)
        )
    }

    fun setBounty(uuid: UUID, amount: Double) {
        data.bounties[uuid.toString()] = amount
        save()
    }

    fun getBounty(uuid: UUID): Double {
        return data.bounties[uuid.toString()] ?: 0.0
    }

    fun removeBounty(uuid: UUID) {
        data.bounties.remove(uuid.toString())
        save()
    }
}