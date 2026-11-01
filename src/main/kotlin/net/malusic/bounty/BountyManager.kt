package net.malusic.bounty

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BountyManager(private val storage: BountyStorage) {

    private val bounties = ConcurrentHashMap<UUID, Double>()

    var onChange: (() -> Unit)? = null

    @Volatile
    private var sortedCache: List<Pair<UUID, Double>>? = null

    fun load() {
        bounties.clear()
        bounties.putAll(storage.loadAll())
        sortedCache = null
    }

    fun setBounty(uuid: UUID, amount: Double) {
        storage.set(uuid, amount)
        bounties[uuid] = amount
        sortedCache = null
        onChange?.invoke()
    }

    fun getBounty(uuid: UUID): Double = bounties[uuid] ?: 0.0

    fun removeBounty(uuid: UUID) {
        storage.remove(uuid)
        bounties.remove(uuid)
        sortedCache = null
        onChange?.invoke()
    }

    fun getTop(): List<Pair<UUID, Double>> =
        sortedCache ?: bounties.entries
            .asSequence()
            .filter { it.value > 0.0 }
            .sortedWith(compareByDescending<Map.Entry<UUID, Double>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
            .toList()
            .also { sortedCache = it }

    fun close() {
        storage.close()
    }
}
