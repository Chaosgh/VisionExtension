package de.chaos.vision

import org.bukkit.entity.Player

internal class DetectionProgressCalculator(
    private val config: NormalizedVisionConfig,
) {
    fun usesProgressDetection(player: Player): Boolean {
        return (player.isSneaking && config.sneakProgressEnabled) ||
            (!player.isSneaking && config.walkProgressEnabled)
    }

    fun visibleProgress(
        player: Player,
        currentProgress: Double,
        distance: Double,
        centerFactor: Double,
    ): Double {
        val minSeconds = if (player.isSneaking) config.sneakMinDetectSeconds else config.walkMinDetectSeconds
        val maxSeconds = if (player.isSneaking) config.sneakMaxDetectSeconds else config.walkMaxDetectSeconds
        val distanceFactor = (distance / config.radius).coerceIn(0.0, 1.0)
        val centerMultiplier = (1.0 - 0.5 * centerFactor).coerceIn(0.5, 1.0)
        val detectSeconds = (minSeconds + (maxSeconds - minSeconds) * distanceFactor) * centerMultiplier

        return (currentProgress + 1.0 / (detectSeconds * 20.0)).coerceAtMost(1.0)
    }

    fun hiddenProgress(currentProgress: Double): Double {
        return (currentProgress - config.decayPerTick).coerceAtLeast(0.0)
    }
}
