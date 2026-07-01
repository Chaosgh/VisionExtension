package de.chaos.vision

import org.bukkit.Material

data class VisionConfig(
    val radius: Double = VisionDefaults.VISION_RADIUS,
    val fovDegrees: Double = VisionDefaults.FOV_DEGREES,
    val shape: VisionShape = VisionDefaults.SHAPE,
    val showDisplays: Boolean = VisionDefaults.SHOW_DISPLAYS,
    val material: Material = VisionDefaults.MATERIAL,
    val displaySize: Float = VisionDefaults.DISPLAY_SIZE,
    val displayUpdateIntervalTicks: Int = VisionDefaults.DISPLAY_UPDATE_INTERVAL_TICKS,
    val lookAtPlayer: Boolean = VisionDefaults.LOOK_AT_PLAYER,
    val sneakProgressEnabled: Boolean = VisionDefaults.SNEAK_PROGRESS_ENABLED,
    val walkProgressEnabled: Boolean = VisionDefaults.WALK_PROGRESS_ENABLED,
    val sneakMinDetectSeconds: Double = VisionDefaults.SNEAK_MIN_DETECT_SECONDS,
    val sneakMaxDetectSeconds: Double = VisionDefaults.SNEAK_MAX_DETECT_SECONDS,
    val walkMinDetectSeconds: Double = VisionDefaults.WALK_MIN_DETECT_SECONDS,
    val walkMaxDetectSeconds: Double = VisionDefaults.WALK_MAX_DETECT_SECONDS,
    val visionDecayPerSecond: Double = VisionDefaults.VISION_DECAY_PER_SECOND,
    val lostDelayTicks: Int = VisionDefaults.LOST_DELAY_TICKS,
    val raycastIntervalTicks: Int = VisionDefaults.RAYCAST_INTERVAL_TICKS,
    val showDetectionIndicator: Boolean = VisionDefaults.SHOW_DETECTION_INDICATOR,
    val indicatorOffsetY: Double = VisionDefaults.INDICATOR_OFFSET_Y,
    val forcedLookEnabled: Boolean = VisionDefaults.FORCED_LOOK_ENABLED,
    val forcedYaw: Float = VisionDefaults.FORCED_LOOK_YAW,
    val forcedPitch: Float = VisionDefaults.FORCED_LOOK_PITCH,
)
