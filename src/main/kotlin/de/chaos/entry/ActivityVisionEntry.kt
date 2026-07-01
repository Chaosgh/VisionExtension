package de.chaos.entry

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entity.ActivityContext
import com.typewritermc.engine.paper.entry.entity.EntityActivity
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import de.chaos.vision.VisionDefaults
import de.chaos.vision.VisionShape
import org.bukkit.Material

@Entry(
    "activity_vision",
    "Add vision detection to any activity",
    Colors.BLUE,
    "mdi:eye-plus-outline"
)
class ActivityVisionEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("The base activity to wrap with vision detection")
    val baseActivity: Ref<GenericEntityActivityEntry> = emptyRef(),
    @Help("Maximum distance in blocks the NPC can see")
    override val visionRadius: Double = VisionDefaults.VISION_RADIUS,
    @Help(VisionEntryHelp.FOV_OR_WIDTH)
    override val fov: Double = VisionDefaults.FOV_DEGREES,
    @Help("Shape of the vision area")
    override val shape: VisionShape = VisionDefaults.SHAPE,
    @Help("Display item displays to visualize the vision area")
    override val showDisplays: Boolean = VisionDefaults.SHOW_DISPLAYS,
    @Help("Ticks between vision display refreshes")
    @Default(VisionDefaults.DISPLAY_UPDATE_INTERVAL_TICKS_TEXT)
    override val displayUpdateIntervalTicks: Int = VisionDefaults.DISPLAY_UPDATE_INTERVAL_TICKS,
    @Help("Material used when visualizing vision")
    override val material: Material = VisionDefaults.MATERIAL,
    @Help("Size of the item displays")
    override val displaySize: Float = VisionDefaults.DISPLAY_SIZE,
    @Help("Rotate NPC to face players inside the vision area")
    override val lookAtPlayer: Boolean = VisionDefaults.LOOK_AT_PLAYER,
    @Help("Pause base activity while a player is visible")
    val stopWhenLooking: Boolean = VisionDefaults.STOP_WHEN_LOOKING,
    @Help("Ticks to wait before resuming activity after losing sight")
    @Default(VisionDefaults.RESUME_DELAY_TICKS_TEXT)
    val resumeDelayTicks: Int = VisionDefaults.RESUME_DELAY_TICKS,
    @Help("Require progressive detection while the player is sneaking")
    @Default(VisionDefaults.SNEAK_PROGRESS_ENABLED_TEXT)
    override val sneakProgressEnabled: Boolean = VisionDefaults.SNEAK_PROGRESS_ENABLED,
    @Help("Apply progressive detection while walking (non-sneak)")
    @Default(VisionDefaults.WALK_PROGRESS_ENABLED_TEXT)
    override val walkProgressEnabled: Boolean = VisionDefaults.WALK_PROGRESS_ENABLED,
    @Help("Minimum seconds to detect a walking player at point-blank")
    @Default(VisionDefaults.WALK_MIN_DETECT_SECONDS_TEXT)
    override val walkMinDetectSeconds: Double = VisionDefaults.WALK_MIN_DETECT_SECONDS,
    @Help("Maximum seconds to detect a walking player at max radius distance")
    @Default(VisionDefaults.WALK_MAX_DETECT_SECONDS_TEXT)
    override val walkMaxDetectSeconds: Double = VisionDefaults.WALK_MAX_DETECT_SECONDS,
    @Help("Minimum seconds to detect a sneaking player at point-blank")
    @Default(VisionDefaults.SNEAK_MIN_DETECT_SECONDS_TEXT)
    override val sneakMinDetectSeconds: Double = VisionDefaults.SNEAK_MIN_DETECT_SECONDS,
    @Help("Maximum seconds to detect a sneaking player at max radius distance")
    @Default(VisionDefaults.SNEAK_MAX_DETECT_SECONDS_TEXT)
    override val sneakMaxDetectSeconds: Double = VisionDefaults.SNEAK_MAX_DETECT_SECONDS,
    @Help("Progress decay per second when not visible")
    @Default(VisionDefaults.VISION_DECAY_PER_SECOND_TEXT)
    override val visionDecayPerSecond: Double = VisionDefaults.VISION_DECAY_PER_SECOND,
    @Help("Ticks a detected player can be briefly hidden before being lost")
    @Default(VisionDefaults.LOST_DELAY_TICKS_TEXT)
    override val lostDelayTicks: Int = VisionDefaults.LOST_DELAY_TICKS,
    @Help("Ticks between line-of-sight raycasts per player")
    @Default(VisionDefaults.RAYCAST_INTERVAL_TICKS_TEXT)
    override val raycastIntervalTicks: Int = VisionDefaults.RAYCAST_INTERVAL_TICKS,
    @Help(VisionEntryHelp.DETECTION_INDICATOR)
    @Default(VisionDefaults.SHOW_DETECTION_INDICATOR_TEXT)
    override val showDetectionIndicator: Boolean = VisionDefaults.SHOW_DETECTION_INDICATOR,
    @Help("Vertical offset for the detection indicator above head (blocks)")
    @Default(VisionDefaults.INDICATOR_OFFSET_Y_TEXT)
    override val indicatorOffsetY: Double = VisionDefaults.INDICATOR_OFFSET_Y,
    @Help("Always face a specific yaw/pitch while this activity runs")
    override val forcedLookEnabled: Boolean = VisionDefaults.FORCED_LOOK_ENABLED,
    @Help("Forced yaw (degrees, 0-360)")
    override val forcedLookYaw: Float = VisionDefaults.FORCED_LOOK_YAW,
    @Help("Forced pitch (degrees, -90 to 90)")
    override val forcedLookPitch: Float = VisionDefaults.FORCED_LOOK_PITCH,
) : GenericEntityActivityEntry, VisionConfigProvider {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<ActivityContext> {
        val base = baseActivity.get()?.create(context, currentLocation)
        return wrapActivityWithVision(base, currentLocation, stopWhenLooking, resumeDelayTicks)
    }
}
