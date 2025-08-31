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
import com.typewritermc.roadnetwork.RoadNetwork
import com.typewritermc.roadnetwork.RoadNetworkEntry
import com.typewritermc.roadnetwork.RoadNetworkManager
import com.typewritermc.roadnetwork.gps.PointToPointGPS
import org.bukkit.Material
import org.koin.core.component.KoinComponent
import org.koin.java.KoinJavaComponent

@Entry(
    "vision_random_patrol_activity",
    "Randomly patrol nodes while detecting players",
    Colors.GREEN,
    "mdi:eye"
)
class VisionRandomPatrolActivityEntry(
    override val id: String = "",
    override val name: String = "",
    val roadNetwork: Ref<RoadNetworkEntry> = emptyRef(),
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
    @Help("The maximum distance (in blocks) from the entity's current position to consider nodes for random selection.")
    @Default("100.0")
    val patrolRadius: Double = 100.0,
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<ActivityContext> {
        return VisionRandomPatrolActivity(
            roadNetwork,
            patrolRadius * patrolRadius,
            visionRadius,
            fov,
            shape,
            showDisplays,
            material,
            displaySize,
            lookAtPlayer,
            currentLocation
        )
    }
}

class VisionRandomPatrolActivity(
    private val roadNetwork: Ref<RoadNetworkEntry>,
    private val patrolRadiusSquared: Double,
    visionRadius: Double,
    fov: Double,
    shape: VisionShape,
    showDisplays: Boolean,
    material: Material,
    displaySize: Float,
    lookAtPlayer: Boolean,
    startLocation: PositionProperty,
) : EntityActivity<ActivityContext>, KoinComponent {
    private var network: RoadNetwork? = null
    private var activity: EntityActivity<in ActivityContext> = IdleActivity(startLocation)
    private val vision = VisionActivity(visionRadius, fov, shape, showDisplays, material, displaySize, lookAtPlayer, startLocation)

    override var currentPosition: PositionProperty = startLocation

    private fun refreshActivity(context: ActivityContext, network: RoadNetwork): TickResult {
        val currentPos = currentPosition.toPosition()
        val nextNode = network.nodes
            .filter { (it.position.distanceSqrt(currentPos) ?: Double.MAX_VALUE) <= patrolRadiusSquared }
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
        currentPosition = activity.currentPosition
        return TickResult.CONSUMED
    }

    override fun initialize(context: ActivityContext) {
        vision.initialize(context)
        setup(context)
    }

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

        val patrolResult = activity.tick(context)
        currentPosition = activity.currentPosition

        vision.currentPosition = currentPosition
        vision.tick(context)

        if (patrolResult == TickResult.IGNORED) {
            return refreshActivity(context, network!!)
        }

        return TickResult.CONSUMED
    }

    override fun dispose(context: ActivityContext) {
        vision.dispose(context)
        activity.dispose(context)
        activity = IdleActivity(currentPosition)
    }

    override val currentProperties: List<EntityProperty>
        get() = activity.currentProperties + vision.currentProperties
}

