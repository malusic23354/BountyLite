package net.malusic.bounty

data class BountyData(
    val bounties: MutableMap<String, Double> = mutableMapOf()
)