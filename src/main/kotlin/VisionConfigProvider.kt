package de.chaos

import org.bukkit.Material

/**
 * Interface for entries that provide vision configuration.
 * Provides a default implementation of toVisionConfig() to avoid duplication.
 */
interface VisionConfigProvider {
    val visionRadius: Double
    val fov: Double
    val shape: VisionShape
    val showDisplays: Boolean
    val material: Material
    val displaySize: Float
    val lookAtPlayer: Boolean
    val sneakProgressEnabled: Boolean
    val walkProgressEnabled: Boolean
    val walkMinDetectSeconds: Double
    val walkMaxDetectSeconds: Double
    val sneakMinDetectSeconds: Double
    val sneakMaxDetectSeconds: Double
    val visionDecayPerSecond: Double
    val showDetectionIndicator: Boolean
    val indicatorOffsetY: Double
    val forcedLookEnabled: Boolean get() = false
    val forcedLookYaw: Float get() = 0f
    val forcedLookPitch: Float get() = 0f

    fun toVisionConfig() = VisionConfig(
        radius = visionRadius,
        fovDegrees = fov,
        shape = shape,
        showDisplays = showDisplays,
        material = material,
        displaySize = displaySize,
        lookAtPlayer = lookAtPlayer,
        sneakProgressEnabled = sneakProgressEnabled,
        walkProgressEnabled = walkProgressEnabled,
        sneakMinDetectSeconds = sneakMinDetectSeconds,
        sneakMaxDetectSeconds = sneakMaxDetectSeconds,
        walkMinDetectSeconds = walkMinDetectSeconds,
        walkMaxDetectSeconds = walkMaxDetectSeconds,
        visionDecayPerSecond = visionDecayPerSecond,
        showDetectionIndicator = showDetectionIndicator,
        indicatorOffsetY = indicatorOffsetY,
        forcedLookEnabled = forcedLookEnabled,
        forcedYaw = forcedLookYaw,
        forcedPitch = forcedLookPitch,
    )
}
