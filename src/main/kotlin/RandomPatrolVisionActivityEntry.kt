package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import com.typewritermc.entity.entries.activity.RandomPatrolActivity
import com.typewritermc.roadnetwork.RoadNetworkEntry
import org.bukkit.Material

/**
 * An activity that combines random patrolling along a road network
 * with the player detection capabilities of [VisionActivity].
 */
@Entry(
    "random_patrol_vision_activity",
    "Randomly patrol nodes while detecting players",
    Colors.GREEN,
    "mdi:eye"
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
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<ActivityContext> {
        return RandomPatrolVisionActivity(
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

class RandomPatrolVisionActivity(
    roadNetwork: Ref<RoadNetworkEntry>,
    radiusSquared: Double,
    visionRadius: Double,
    fov: Double,
    shape: VisionShape,
    showDisplays: Boolean,
    material: Material,
    displaySize: Float,
    lookAtPlayer: Boolean,
    startLocation: PositionProperty,
) : EntityActivity<ActivityContext> {

    private val patrol = RandomPatrolActivity(roadNetwork, radiusSquared, startLocation)
    private val vision = VisionActivity(visionRadius, fov, shape, showDisplays, material, displaySize, lookAtPlayer, startLocation)

    override fun initialize(context: ActivityContext) {
        patrol.initialize(context)
        vision.currentPosition = patrol.currentPosition
        vision.initialize(context)
    }

    override fun tick(context: ActivityContext): TickResult {
        val patrolResult = patrol.tick(context)
        vision.currentPosition = patrol.currentPosition
        val visionResult = vision.tick(context)
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

    override val currentPosition: PositionProperty
        get() = vision.currentPosition

    override val currentProperties: List<EntityProperty>
        get() = patrol.currentProperties
}

