package net.malusic.bounty

import java.util.UUID

interface BountyStorage : AutoCloseable {

    fun loadAll(): Map<UUID, Double>

    fun set(uuid: UUID, amount: Double)

    fun remove(uuid: UUID)

    override fun close() {}
}
