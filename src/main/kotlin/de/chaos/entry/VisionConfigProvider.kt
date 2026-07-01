package de.chaos.entry

import com.typewritermc.engine.paper.entry.entity.ActivityContext
import com.typewritermc.engine.paper.entry.entity.EntityActivity
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import de.chaos.activity.PatrolVisionActivity
import de.chaos.activity.PausableActivity
import de.chaos.vision.VisionActivity
import de.chaos.vision.VisionConfig
import de.chaos.vision.VisionDefaults
import de.chaos.vision.VisionShape
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
    val displayUpdateIntervalTicks: Int get() = VisionDefaults.DISPLAY_UPDATE_INTERVAL_TICKS
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
    val lostDelayTicks: Int get() = VisionDefaults.LOST_DELAY_TICKS
    val raycastIntervalTicks: Int get() = VisionDefaults.RAYCAST_INTERVAL_TICKS
    val showDetectionIndicator: Boolean
    val indicatorOffsetY: Double
    val forcedLookEnabled: Boolean get() = VisionDefaults.FORCED_LOOK_ENABLED
    val forcedLookYaw: Float get() = VisionDefaults.FORCED_LOOK_YAW
    val forcedLookPitch: Float get() = VisionDefaults.FORCED_LOOK_PITCH

    fun toVisionConfig() = VisionConfig(
        radius = visionRadius,
        fovDegrees = fov,
        shape = shape,
        showDisplays = showDisplays,
        displayUpdateIntervalTicks = displayUpdateIntervalTicks,
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
        lostDelayTicks = lostDelayTicks,
        raycastIntervalTicks = raycastIntervalTicks,
        showDetectionIndicator = showDetectionIndicator,
        indicatorOffsetY = indicatorOffsetY,
        forcedLookEnabled = forcedLookEnabled,
        forcedYaw = forcedLookYaw,
        forcedPitch = forcedLookPitch,
    )

    fun createVisionActivity(currentLocation: PositionProperty): VisionActivity {
        return VisionActivity(toVisionConfig(), currentLocation)
    }

    fun wrapActivityWithVision(
        baseActivity: EntityActivity<in ActivityContext>?,
        currentLocation: PositionProperty,
        stopWhenLooking: Boolean,
        resumeDelayTicks: Int,
    ): EntityActivity<ActivityContext> {
        val vision = createVisionActivity(currentLocation)
        if (baseActivity == null) return vision

        return PatrolVisionActivity(
            PausableActivity(baseActivity),
            vision,
            stopWhenLooking,
            resumeDelayTicks
        )
    }
}
