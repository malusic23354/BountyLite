package net.malusic.bounty

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

class BountyManager(
    private val storage: SqlStorage,
    private val logger: Logger
) {

    private val bounties = ConcurrentHashMap<UUID, Double>()

    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "BountyLite-Storage").apply { isDaemon = true }
    }

    @Volatile
    private var sorted: List<Pair<UUID, Double>>? = null

    var onChange: (() -> Unit)? = null

    fun load() {
        bounties.clear()
        bounties.putAll(storage.loadAll())
        sorted = null
    }

    fun getBounty(uuid: UUID): Double = bounties[uuid] ?: 0.0

    fun setBounty(uuid: UUID, amount: Double) {
        if (amount <= 0.0) return removeBounty(uuid)

        bounties[uuid] = amount
        persist { storage.set(uuid, amount) }
    }

    fun addBounty(uuid: UUID, amount: Double): Double {
        val total = bounties.merge(uuid, amount) { a, b -> a + b }!!
        persist { storage.set(uuid, total) }
        return total
    }

    fun removeBounty(uuid: UUID) {
        bounties.remove(uuid)
        persist { storage.remove(uuid) }
    }

    fun getTop(): List<Pair<UUID, Double>> =
        sorted ?: bounties.entries
            .filter { it.value > 0.0 }
            .sortedWith(compareByDescending<Map.Entry<UUID, Double>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
            .also { sorted = it }

    fun close() {
        writer.shutdown()
        writer.awaitTermination(5, TimeUnit.SECONDS)
        storage.close()
    }

    private fun persist(write: () -> Unit) {
        sorted = null
        writer.execute {
            try {
                write()
            } catch (exception: Exception) {
                logger.log(Level.SEVERE, "Could not save bounty data.", exception)
            }
        }
        onChange?.invoke()
    }
}
