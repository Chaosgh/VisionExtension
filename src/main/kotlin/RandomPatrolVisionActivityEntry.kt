package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.utils.point.distanceSqrt
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.entity.entries.activity.NavigationActivity
import com.typewritermc.roadnetwork.RoadNetwork
import com.typewritermc.roadnetwork.RoadNetworkEntry
import com.typewritermc.roadnetwork.RoadNetworkManager
import com.typewritermc.roadnetwork.gps.PointToPointGPS
import org.bukkit.Material
import org.koin.core.component.KoinComponent
import org.koin.java.KoinJavaComponent

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
    @Help("The maximum distance (in blocks) from the entity's current position to consider nodes for random selection.")
    @Default("100.0")
    val patrolRadius: Double = 100.0,
    @Help("Maximum distance in blocks the NPC can see")
    val visionRadius: Double = 5.0,
    @Help("Field of view in degrees (max 170)")
    val fov: Double = 90.0,
    @Help("Shape of the vision area")
    val shape: VisionShape = VisionShape.CONE,
    @Help("Display item displays to visualize the vision area")
    val showDisplays: Boolean = true,
    @Help("Material used when visualizing vision")
    val material: Material = Material.BARRIER,
    @Help("Size of the item displays")
    val displaySize: Float = 0.02f,
    @Help("Rotate NPC to face players inside the vision area")
    val lookAtPlayer: Boolean = true,
    @Help("Pause patrolling while a player is visible")
    val stopWhenLooking: Boolean = true,
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<ActivityContext> {
        val patrol = RandomPatrolActivity(roadNetwork, patrolRadius * patrolRadius, currentLocation)
        val vision = VisionActivity(visionRadius, fov, shape, showDisplays, material, displaySize, lookAtPlayer, currentLocation)
        return RandomPatrolVisionActivity(patrol, vision, stopWhenLooking)
    }
}

class RandomPatrolVisionActivity(
    private val patrol: RandomPatrolActivity,
    private val vision: VisionActivity,
    private val stopWhenLooking: Boolean,
) : EntityActivity<ActivityContext> {
    override var currentPosition: PositionProperty
        get() = patrol.currentPosition
        set(value) {
            patrol.currentPosition = value
            vision.currentPosition = value
        }

    override val currentProperties: List<EntityProperty>
        get() = patrol.currentProperties + vision.currentProperties

    override fun initialize(context: ActivityContext) {
        patrol.initialize(context)
        vision.initialize(context)
    }

    override fun tick(context: ActivityContext): TickResult {
        vision.currentPosition = patrol.currentPosition
        val visionResult = vision.tick(context)
        val patrolResult = if (stopWhenLooking && vision.isSeeingPlayer) {
            TickResult.IGNORED
        } else {
            patrol.tick(context)
        }
        patrol.currentPosition =
            patrol.currentPosition.withRotation(vision.currentPosition.yaw, vision.currentPosition.pitch)
        return if (patrolResult == TickResult.CONSUMED || visionResult == TickResult.CONSUMED) {
            TickResult.CONSUMED
        } else {
            TickResult.IGNORED
        }
    }

    override fun dispose(context: ActivityContext) {
        patrol.dispose(context)
        vision.dispose(context)
    }
}

class RandomPatrolActivity(
    private val roadNetwork: Ref<RoadNetworkEntry>,
    private val radiusSquared: Double,
    startLocation: PositionProperty,
) : EntityActivity<ActivityContext>, KoinComponent {
    private var network: RoadNetwork? = null
    private var activity: EntityActivity<in ActivityContext> = IdleActivity(startLocation)

    fun refreshActivity(context: ActivityContext, network: RoadNetwork): TickResult {
        val currentPos = currentPosition.toPosition()
        val nextNode = network.nodes
            .filter { (it.position.distanceSqrt(currentPos) ?: Double.MAX_VALUE) <= radiusSquared }
            .randomOrNull()
            ?: return TickResult.IGNORED

        activity.dispose(context)
        activity = NavigationActivity(
            PointToPointGPS(
                roadNetwork,
                { currentPosition.toPosition() }) {
                nextNode.position
            }, currentPosition
        )
        activity.initialize(context)
        return TickResult.CONSUMED
    }

    override fun initialize(context: ActivityContext) = setup(context)

    private fun setup(context: ActivityContext) {
        network =
            KoinJavaComponent.get<RoadNetworkManager>(RoadNetworkManager::class.java).getNetworkOrNull(roadNetwork)
                ?: return

        refreshActivity(context, network!!)
    }

    override fun tick(context: ActivityContext): TickResult {
        if (network == null) {
            setup(context)
            return TickResult.CONSUMED
        }

        val result = activity.tick(context)
        if (result == TickResult.IGNORED) {
            return refreshActivity(context, network!!)
        }

        return TickResult.CONSUMED
    }

    override fun dispose(context: ActivityContext) {
        val oldPosition = currentPosition
        activity.dispose(context)
        activity = IdleActivity(oldPosition)
    }

    override var currentPosition: PositionProperty
        get() = activity.currentPosition
        set(value) {
            activity.currentPosition = value
        }

    override val currentProperties: List<EntityProperty>
        get() = activity.currentProperties
}

