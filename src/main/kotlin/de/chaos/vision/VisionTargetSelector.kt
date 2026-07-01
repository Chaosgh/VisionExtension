package de.chaos.vision

import org.bukkit.entity.Player

internal class VisionTargetSelector {
    private var target: VisionLookTarget? = null

    val player: Player?
        get() = target?.player

    fun reset() {
        target = null
    }

    fun consider(detected: Boolean, player: Player, distanceSquared: Double) {
        if (!detected) return
        val current = target
        if (current == null || distanceSquared < current.distanceSquared) {
            target = VisionLookTarget(player, distanceSquared)
        }
    }

    private data class VisionLookTarget(val player: Player, val distanceSquared: Double)
}
