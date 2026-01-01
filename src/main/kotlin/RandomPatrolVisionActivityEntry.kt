package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.utils.point.distanceSqrt
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.roadnetwork.RoadNetwork
import com.typewritermc.roadnetwork.RoadNetworkEntry
import org.bukkit.Material

@Entry(
        "random_patrol_vision_activity",
        "Randomly patrol nodes while detecting players",
        Colors.BLUE,
        "mdi:eye-plus"
)
class RandomPatrolVisionActivityEntry(
        override val id: String = "",
        override val name: String = "",
        val roadNetwork: Ref<RoadNetworkEntry> = emptyRef(),
        @Help(
                "The maximum distance (in blocks) from the entity's current position to consider nodes for random selection."
        )
        @Default("100.0")
        val patrolRadius: Double = 100.0,
        @Help("Maximum distance in blocks the NPC can see") val visionRadius: Double = 5.0,
        @Help("Field of view in degrees (max 170)") val fov: Double = 90.0,
        @Help("Shape of the vision area") val shape: VisionShape = VisionShape.CONE,
        @Help("Display item displays to visualize the vision area")
        val showDisplays: Boolean = true,
        @Help("Material used when visualizing vision") val material: Material = Material.BARRIER,
        @Help("Size of the item displays") val displaySize: Float = 0.02f,
        @Help("Rotate NPC to face players inside the vision area") val lookAtPlayer: Boolean = true,
        @Help("Pause patrolling while a player is visible") val stopWhenLooking: Boolean = true,
        @Help("Ticks to wait before resuming patrol after losing sight")
        @Default("10")
        val resumeDelayTicks: Int = 10,
        @Help("Require progressive detection while the player is sneaking")
        @Default("true")
        val sneakProgressEnabled: Boolean = true,
        @Help("Apply progressive detection while walking (non-sneak)")
        @Default("false")
        val walkProgressEnabled: Boolean = false,
        @Help("Minimum seconds to detect a walking player at point-blank")
        @Default("0.3")
        val walkMinDetectSeconds: Double = 0.3,
        @Help("Maximum seconds to detect a walking player at max radius distance")
        @Default("1.5")
        val walkMaxDetectSeconds: Double = 1.5,
        @Help("Minimum seconds to detect a sneaking player at point-blank")
        @Default("0.6")
        val sneakMinDetectSeconds: Double = 0.6,
        @Help("Maximum seconds to detect a sneaking player at max radius distance")
        @Default("2.5")
        val sneakMaxDetectSeconds: Double = 2.5,
        @Help("Progress decay per second when not visible")
        @Default("1.2")
        val visionDecayPerSecond: Double = 1.2,
        @Help("Show a detection indicator above the NPC (two text displays)")
        @Default("true")
        val showDetectionIndicator: Boolean = true,
        @Help("Vertical offset for the detection indicator above head (blocks)")
        @Default("0.6")
        val indicatorOffsetY: Double = 0.6,
) : GenericEntityActivityEntry {
    override fun create(
            context: ActivityContext,
            currentLocation: PositionProperty
    ): EntityActivity<ActivityContext> {
        val patrol = RandomPatrolActivity(roadNetwork, patrolRadius * patrolRadius, currentLocation)
        val vision =
                VisionActivity(
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
                        forcedLookEnabled = false,
                        forcedYaw = 0f,
                        forcedPitch = 0f,
                        start = currentLocation
                )
        return RandomPatrolVisionActivity(patrol, vision, stopWhenLooking, resumeDelayTicks)
    }
}

/** Random patrol activity that picks random nodes within a radius. */
class RandomPatrolActivity(
        roadNetwork: Ref<RoadNetworkEntry>,
        private val radiusSquared: Double,
        startLocation: PositionProperty,
) : BasePatrolActivity(roadNetwork, startLocation) {

    override fun refreshActivity(context: ActivityContext, network: RoadNetwork): TickResult {
        val currentPos = currentPosition.toPosition()
        val nextNode =
                network.nodes
                        .filter {
                            (it.position.distanceSqrt(currentPos)
                                    ?: Double.MAX_VALUE) <= radiusSquared
                        }
                        .randomOrNull()
                        ?: return TickResult.IGNORED

        return navigateTo(context, nextNode.position)
    }
}

/** Combined random patrol + vision activity. */
class RandomPatrolVisionActivity(
        patrol: RandomPatrolActivity,
        vision: VisionActivity,
        stopWhenLooking: Boolean,
        resumeDelayTicks: Int,
) :
        BasePatrolVisionActivity<RandomPatrolActivity>(
                patrol,
                vision,
                stopWhenLooking,
                resumeDelayTicks
        )
