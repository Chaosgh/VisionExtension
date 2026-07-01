package de.chaos.entry

import com.typewritermc.core.books.pages.Colors
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

@Entry("vision_activity", "Detect players inside an NPC's field of view", Colors.GREEN, "mdi:eye")
class VisionActivityEntry(
    override val id: String = "",
    override val name: String = "",
    @Help(VisionEntryHelp.VISION_RADIUS) override val visionRadius: Double = VisionDefaults.VISION_RADIUS,
    @Help(VisionEntryHelp.FOV_OR_WIDTH)
    override val fov: Double = VisionDefaults.FOV_DEGREES,
    @Help(VisionEntryHelp.SHAPE) override val shape: VisionShape = VisionDefaults.SHAPE,
    @Help(VisionEntryHelp.SHOW_DISPLAYS)
    override val showDisplays: Boolean = VisionDefaults.SHOW_DISPLAYS,
    @Help(VisionEntryHelp.DISPLAY_UPDATE_INTERVAL)
    @Default(VisionDefaults.DISPLAY_UPDATE_INTERVAL_TICKS_TEXT)
    override val displayUpdateIntervalTicks: Int = VisionDefaults.DISPLAY_UPDATE_INTERVAL_TICKS,
    @Help(VisionEntryHelp.MATERIAL) override val material: Material = VisionDefaults.MATERIAL,
    @Help(VisionEntryHelp.DISPLAY_SIZE) override val displaySize: Float = VisionDefaults.DISPLAY_SIZE,
    @Help(VisionEntryHelp.LOOK_AT_PLAYER) override val lookAtPlayer: Boolean = VisionDefaults.LOOK_AT_PLAYER,
    @Help(VisionEntryHelp.SNEAK_PROGRESS)
    @Default(VisionDefaults.SNEAK_PROGRESS_ENABLED_TEXT)
    override val sneakProgressEnabled: Boolean = VisionDefaults.SNEAK_PROGRESS_ENABLED,
    @Help(VisionEntryHelp.WALK_PROGRESS)
    @Default(VisionDefaults.WALK_PROGRESS_ENABLED_TEXT)
    override val walkProgressEnabled: Boolean = VisionDefaults.WALK_PROGRESS_ENABLED,
    @Help(VisionEntryHelp.WALK_MIN_SECONDS)
    @Default(VisionDefaults.WALK_MIN_DETECT_SECONDS_TEXT)
    override val walkMinDetectSeconds: Double = VisionDefaults.WALK_MIN_DETECT_SECONDS,
    @Help(VisionEntryHelp.WALK_MAX_SECONDS)
    @Default(VisionDefaults.WALK_MAX_DETECT_SECONDS_TEXT)
    override val walkMaxDetectSeconds: Double = VisionDefaults.WALK_MAX_DETECT_SECONDS,
    @Help(VisionEntryHelp.SNEAK_MIN_SECONDS)
    @Default(VisionDefaults.SNEAK_MIN_DETECT_SECONDS_TEXT)
    override val sneakMinDetectSeconds: Double = VisionDefaults.SNEAK_MIN_DETECT_SECONDS,
    @Help(VisionEntryHelp.SNEAK_MAX_SECONDS)
    @Default(VisionDefaults.SNEAK_MAX_DETECT_SECONDS_TEXT)
    override val sneakMaxDetectSeconds: Double = VisionDefaults.SNEAK_MAX_DETECT_SECONDS,
    @Help(VisionEntryHelp.DECAY_PER_SECOND)
    @Default(VisionDefaults.VISION_DECAY_PER_SECOND_TEXT)
    override val visionDecayPerSecond: Double = VisionDefaults.VISION_DECAY_PER_SECOND,
    @Help(VisionEntryHelp.LOST_DELAY)
    @Default(VisionDefaults.LOST_DELAY_TICKS_TEXT)
    override val lostDelayTicks: Int = VisionDefaults.LOST_DELAY_TICKS,
    @Help(VisionEntryHelp.RAYCAST_INTERVAL)
    @Default(VisionDefaults.RAYCAST_INTERVAL_TICKS_TEXT)
    override val raycastIntervalTicks: Int = VisionDefaults.RAYCAST_INTERVAL_TICKS,
    @Help(VisionEntryHelp.DETECTION_INDICATOR)
    @Default(VisionDefaults.SHOW_DETECTION_INDICATOR_TEXT)
    override val showDetectionIndicator: Boolean = VisionDefaults.SHOW_DETECTION_INDICATOR,
    @Help(VisionEntryHelp.INDICATOR_OFFSET_Y)
    @Default(VisionDefaults.INDICATOR_OFFSET_Y_TEXT)
    override val indicatorOffsetY: Double = VisionDefaults.INDICATOR_OFFSET_Y,
    @Help(VisionEntryHelp.FORCED_LOOK_ENABLED)
    override val forcedLookEnabled: Boolean = VisionDefaults.FORCED_LOOK_ENABLED,
    @Help(VisionEntryHelp.FORCED_LOOK_YAW) override val forcedLookYaw: Float = VisionDefaults.FORCED_LOOK_YAW,
    @Help(VisionEntryHelp.FORCED_LOOK_PITCH) override val forcedLookPitch: Float = VisionDefaults.FORCED_LOOK_PITCH,
) : GenericEntityActivityEntry, VisionConfigProvider {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty,
    ): EntityActivity<in ActivityContext> {
        return createVisionActivity(currentLocation)
    }
}
