package de.chaos.vision

import java.util.UUID
import org.bukkit.entity.Player

internal data class DetectedPlayerRef(
    val uuid: UUID,
    val player: Player?,
) {
    companion object {
        fun from(player: Player): DetectedPlayerRef {
            return DetectedPlayerRef(player.uniqueId, player)
        }
    }
}
