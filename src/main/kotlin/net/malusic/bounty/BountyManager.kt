package net.malusic.bounty

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.logging.Logger

class BountyManager(
    private val storage: SqlStorage,
    private val logger: Logger
) {

    private val bounties = ConcurrentHashMap<UUID, Long>()
    private val lock = Any()

    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "BountyLite-Storage").apply { isDaemon = true }
    }

    private var sorted: List<Pair<UUID, Long>>? = null
    private var totalCache: Long? = null
    private var closed = false

    @Volatile
    private var topBettorsCache: List<Pair<UUID, Long>> = emptyList()

    private val pendingRefreshes = AtomicInteger()

    var onChange: (() -> Unit)? = null

    fun load() {
        synchronized(lock) {
            bounties.clear()
            storage.loadAll().forEach { (id, cents) -> if (cents > 0L) bounties[id] = cents }
            invalidate()
        }
        refreshTopBettors()
    }

    fun getBounty(uuid: UUID): Long = bounties[uuid] ?: 0L

    fun count(): Int = bounties.size

    fun total(): Long = synchronized(lock) {
        totalCache ?: bounties.values.sum().also { totalCache = it }
    }

    fun setBounty(uuid: UUID, cents: Long) {
        if (cents <= 0L) return removeBounty(uuid)

        synchronized(lock) {
            bounties[uuid] = cents
            invalidate()
            enqueue { storage.clearContributions(uuid) }
            enqueue { storage.set(uuid, cents) }
        }
        changed()
    }

    fun addBounty(uuid: UUID, cents: Long, bettor: UUID): Long {
        val placedAt = System.currentTimeMillis()
        val total = synchronized(lock) {
            val total = bounties.merge(uuid, cents) { a, b -> a + b }!!
            invalidate()
            enqueue { storage.set(uuid, total) }
            enqueue { storage.addContribution(Contribution(uuid, bettor, placedAt, cents)) }
            total
        }
        changed()
        return total
    }

    fun removeBounty(uuid: UUID) {
        synchronized(lock) {
            bounties.remove(uuid)
            invalidate()
            enqueue { storage.clearContributions(uuid) }
            enqueue { storage.remove(uuid) }
        }
        changed()
    }

    fun expiredContributions(cutoff: Long): List<Contribution> = storage.expiredContributions(cutoff)

    fun expire(contributions: List<Contribution>) {
        if (contributions.isEmpty()) return

        synchronized(lock) {
            for (contribution in contributions) {
                val remaining = (bounties[contribution.target] ?: 0L) - contribution.cents
                if (remaining > 0L) {
                    bounties[contribution.target] = remaining
                    enqueue { storage.set(contribution.target, remaining) }
                } else {
                    bounties.remove(contribution.target)
                    enqueue { storage.remove(contribution.target) }
                }
                enqueue { storage.deleteContribution(contribution) }
            }
            invalidate()
        }
        changed()
    }

    fun topBettor(position: Int): Pair<UUID, Long>? = topBettorsCache.getOrNull(position - 1)

    fun refreshTopBettors(limit: Int = 50) {
        // Every change queues a refresh, but only the last one in a burst has to
        // hit the database; earlier tasks just retire themselves.
        pendingRefreshes.incrementAndGet()
        enqueue {
            if (pendingRefreshes.decrementAndGet() > 0) return@enqueue
            topBettorsCache = storage.topBettors(limit)
        }
    }

    private fun changed() {
        refreshTopBettors()
        onChange?.invoke()
    }

    fun getTop(): List<Pair<UUID, Long>> = synchronized(lock) {
        sorted ?: bounties.entries
            .filter { it.value > 0L }
            .sortedWith(compareByDescending<Map.Entry<UUID, Long>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
            .also { sorted = it }
    }

    private fun invalidate() {
        sorted = null
        totalCache = null
    }

    fun close() {
        if (closed) return
        closed = true
        writer.shutdown()
        writer.awaitTermination(5, TimeUnit.SECONDS)
        storage.close()
    }

    private fun enqueue(task: () -> Unit) {
        if (writer.isShutdown) return
        writer.execute {
            try {
                task()
            } catch (exception: Exception) {
                logger.log(Level.SEVERE, "Could not update bounty data.", exception)
            }
        }
    }
}