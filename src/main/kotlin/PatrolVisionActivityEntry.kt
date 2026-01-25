package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.entity.entries.activity.PatrolActivity
import com.typewritermc.roadnetwork.RoadNetworkEntry
import com.typewritermc.roadnetwork.RoadNodeId
import org.bukkit.Material

@Entry(
    "patrol_vision_activity",
    "Patrol nodes while detecting players",
    Colors.BLUE,
    "mdi:eye-plus"
)
@Deprecated("Use Activity Vision Entry instead")
class PatrolVisionActivityEntry(
    override val id: String = "",
    override val name: String = "",
    val roadNetwork: Ref<RoadNetworkEntry> = emptyRef(),
    @Help("IDs of road nodes to patrol in sequence") val nodeIds: List<Int> = emptyList(),
    @Help("Maximum distance in blocks the NPC can see") override val visionRadius: Double = 5.0,
    @Help("Field of view in degrees (max 170)") override val fov: Double = 90.0,
    @Help("Shape of the vision area") override val shape: VisionShape = VisionShape.CONE,
    @Help("Display item displays to visualize the vision area")
    override val showDisplays: Boolean = true,
    @Help("Material used when visualizing vision") override val material: Material = Material.BARRIER,
    @Help("Size of the item displays") override val displaySize: Float = 0.02f,
    @Help("Rotate NPC to face players inside the vision area") override val lookAtPlayer: Boolean = true,
    @Help("Pause patrolling while a player is visible") val stopWhenLooking: Boolean = true,
    @Help("Ticks to wait before resuming patrol after losing sight")
    @Default("10")
    val resumeDelayTicks: Int = 10,
    @Help("Require progressive detection while the player is sneaking")
    @Default("true")
    override val sneakProgressEnabled: Boolean = true,
    @Help("Apply progressive detection while walking (non-sneak)")
    @Default("false")
    override val walkProgressEnabled: Boolean = false,
    @Help("Minimum seconds to detect a walking player at point-blank")
    @Default("0.3")
    override val walkMinDetectSeconds: Double = 0.3,
    @Help("Maximum seconds to detect a walking player at max radius distance")
    @Default("1.5")
    override val walkMaxDetectSeconds: Double = 1.5,
    @Help("Minimum seconds to detect a sneaking player at point-blank")
    @Default("0.6")
    override val sneakMinDetectSeconds: Double = 0.6,
    @Help("Maximum seconds to detect a sneaking player at max radius distance")
    @Default("2.5")
    override val sneakMaxDetectSeconds: Double = 2.5,
    @Help("Progress decay per second when not visible")
    @Default("1.2")
    override val visionDecayPerSecond: Double = 1.2,
    @Help("Show a detection indicator above the NPC (two text displays)")
    @Default("true")
    override val showDetectionIndicator: Boolean = true,
    @Help("Vertical offset for the detection indicator above head (blocks)")
    @Default("0.6")
    override val indicatorOffsetY: Double = 0.6,
) : GenericEntityActivityEntry, VisionConfigProvider {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<ActivityContext> {
        val patrolNodes = nodeIds.map { RoadNodeId(it) }
        val patrol = PausableActivity(
            PatrolActivity(
                roadNetwork,
                patrolNodes,
                currentLocation,
                nextNodeIndexFetcher = { nodes, currentIndex -> (currentIndex + 1) % nodes.size }
            )
        )
        val vision = VisionActivity(toVisionConfig(), currentLocation)
        return PatrolVisionActivity(patrol, vision, stopWhenLooking, resumeDelayTicks)
    }
}
