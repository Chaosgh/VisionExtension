package de.chaos.vision

import org.bukkit.Material

object VisionDefaults {
    const val VISION_RADIUS = 5.0
    const val FOV_DEGREES = 90.0
    val SHAPE = VisionShape.CONE
    const val SHOW_DISPLAYS = true
    const val DISPLAY_UPDATE_INTERVAL_TICKS = 2
    const val DISPLAY_UPDATE_INTERVAL_TICKS_TEXT = "2"
    val MATERIAL: Material = Material.BARRIER
    const val DISPLAY_SIZE = 0.02f
    const val LOOK_AT_PLAYER = true
    const val STOP_WHEN_LOOKING = true
    const val RESUME_DELAY_TICKS = 10
    const val RESUME_DELAY_TICKS_TEXT = "10"
    const val SNEAK_PROGRESS_ENABLED = true
    const val SNEAK_PROGRESS_ENABLED_TEXT = "true"
    const val WALK_PROGRESS_ENABLED = false
    const val WALK_PROGRESS_ENABLED_TEXT = "false"
    const val WALK_MIN_DETECT_SECONDS = 0.3
    const val WALK_MIN_DETECT_SECONDS_TEXT = "0.3"
    const val WALK_MAX_DETECT_SECONDS = 1.5
    const val WALK_MAX_DETECT_SECONDS_TEXT = "1.5"
    const val SNEAK_MIN_DETECT_SECONDS = 0.6
    const val SNEAK_MIN_DETECT_SECONDS_TEXT = "0.6"
    const val SNEAK_MAX_DETECT_SECONDS = 2.5
    const val SNEAK_MAX_DETECT_SECONDS_TEXT = "2.5"
    const val VISION_DECAY_PER_SECOND = 1.2
    const val VISION_DECAY_PER_SECOND_TEXT = "1.2"
    const val LOST_DELAY_TICKS = 3
    const val LOST_DELAY_TICKS_TEXT = "3"
    const val RAYCAST_INTERVAL_TICKS = 1
    const val RAYCAST_INTERVAL_TICKS_TEXT = "1"
    const val SHOW_DETECTION_INDICATOR = true
    const val SHOW_DETECTION_INDICATOR_TEXT = "true"
    const val INDICATOR_OFFSET_Y = 0.6
    const val INDICATOR_OFFSET_Y_TEXT = "0.6"
    const val FORCED_LOOK_ENABLED = false
    const val FORCED_LOOK_YAW = 0f
    const val FORCED_LOOK_PITCH = 0f
}
