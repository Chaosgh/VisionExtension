package de.chaos.vision

import kotlin.math.max
import org.bukkit.Material

internal data class NormalizedVisionConfig(
    val radius: Double,
    val fovDegrees: Double,
    val shape: VisionShape,
    val showDisplays: Boolean,
    val material: Material,
    val displaySize: Float,
    val displayUpdateIntervalTicks: Int,
    val lookAtPlayer: Boolean,
    val sneakProgressEnabled: Boolean,
    val walkProgressEnabled: Boolean,
    val sneakMinDetectSeconds: Double,
    val sneakMaxDetectSeconds: Double,
    val walkMinDetectSeconds: Double,
    val walkMaxDetectSeconds: Double,
    val visionDecayPerSecond: Double,
    val decayPerTick: Double,
    val lostDelayTicks: Int,
    val raycastIntervalTicks: Int,
    val showDetectionIndicator: Boolean,
    val indicatorOffsetY: Double,
    val forcedLookEnabled: Boolean,
    val forcedYaw: Float,
    val forcedPitch: Float,
) {
    companion object {
        private const val MIN_RADIUS = 0.05
        private const val MIN_DETECT_SECONDS = 0.05

        fun from(config: VisionConfig): NormalizedVisionConfig {
            val sneakMin = max(MIN_DETECT_SECONDS, config.sneakMinDetectSeconds)
            val walkMin = max(MIN_DETECT_SECONDS, config.walkMinDetectSeconds)
            val decayPerSecond = config.visionDecayPerSecond.coerceAtLeast(0.0)

            return NormalizedVisionConfig(
                radius = config.radius.coerceAtLeast(MIN_RADIUS),
                fovDegrees = config.fovDegrees.coerceIn(1.0, 170.0),
                shape = config.shape,
                showDisplays = config.showDisplays,
                material = config.material,
                displaySize = config.displaySize.coerceAtLeast(0.001f),
                displayUpdateIntervalTicks = config.displayUpdateIntervalTicks.coerceAtLeast(1),
                lookAtPlayer = config.lookAtPlayer,
                sneakProgressEnabled = config.sneakProgressEnabled,
                walkProgressEnabled = config.walkProgressEnabled,
                sneakMinDetectSeconds = sneakMin,
                sneakMaxDetectSeconds = max(sneakMin, config.sneakMaxDetectSeconds),
                walkMinDetectSeconds = walkMin,
                walkMaxDetectSeconds = max(walkMin, config.walkMaxDetectSeconds),
                visionDecayPerSecond = decayPerSecond,
                decayPerTick = decayPerSecond / 20.0,
                lostDelayTicks = config.lostDelayTicks.coerceAtLeast(0),
                raycastIntervalTicks = config.raycastIntervalTicks.coerceAtLeast(1),
                showDetectionIndicator = config.showDetectionIndicator,
                indicatorOffsetY = config.indicatorOffsetY,
                forcedLookEnabled = config.forcedLookEnabled,
                forcedYaw = VisionMath.normalizeYaw(config.forcedYaw),
                forcedPitch = config.forcedPitch.coerceIn(-89.9f, 89.9f),
            )
        }
    }
}

internal fun VisionConfig.normalized(): NormalizedVisionConfig {
    return NormalizedVisionConfig.from(this)
}
