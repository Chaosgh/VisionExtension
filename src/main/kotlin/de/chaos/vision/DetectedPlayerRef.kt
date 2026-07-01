package de.chaos.vision

import org.bukkit.entity.Player
import java.util.UUID

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
