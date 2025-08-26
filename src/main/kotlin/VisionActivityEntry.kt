package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.engine.paper.utils.isLookable
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Entry(
    "vision_activity",
    "Detect players inside an NPC's field of view",
    Colors.GREEN,
    "mdi:eye"
)
class VisionActivityEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Maximum distance in blocks the NPC can see")
    val radius: Double = 5.0,
    @Help("Field of view in degrees")
    val fov: Double = 90.0,
    @Help("Show the raycast with particles")
    val showParticles: Boolean = false,
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<in ActivityContext> {
        return VisionActivity(radius, fov, showParticles, currentLocation)
    }
}

enum class VisionShape {
    CONE,
}

class VisionActivity(
    private val radius: Double,
    private val fov: Double,
    private val showParticles: Boolean,
    start: PositionProperty,
) : EntityActivity<ActivityContext> {

    override var currentPosition: PositionProperty = start

    override fun initialize(context: ActivityContext) {}

    override fun tick(context: ActivityContext): TickResult {
        val eyeX = currentPosition.x
        val eyeY = currentPosition.y + context.entityState.eyeHeight
        val eyeZ = currentPosition.z
        val forward = fromYawPitch(currentPosition.yaw, currentPosition.pitch)

        context.viewers.filter { it.isLookable }.forEach { player ->
            val origin = org.bukkit.Location(player.world, eyeX, eyeY, eyeZ)

            val dir = player.eyeLocation.toVector().subtract(Vector(eyeX, eyeY, eyeZ))
            val distance = dir.length()
            if (distance > radius) return@forEach

            val angle = Math.toDegrees(acos(forward.clone().normalize().dot(dir.clone().normalize())))
            if (angle > fov / 2) return@forEach

            val blocked = player.world.rayTraceBlocks(origin, dir.normalize(), distance) != null
            if (blocked) return@forEach

            if (showParticles) {
                spawnParticles(origin, dir.normalize(), distance, player)
            }

            // Player is inside the NPC's vision cone
        }

        return TickResult.IGNORED
    }

    private fun spawnParticles(origin: org.bukkit.Location, direction: Vector, distance: Double, viewer: Player) {
        val steps = (distance * 4).toInt()
        val step = direction.clone().multiply(distance / steps)
        var loc = origin.clone()
        repeat(steps) {
            viewer.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0)
            loc.add(step)
        }
    }

    override fun dispose(context: ActivityContext) {}

    private fun fromYawPitch(yaw: Float, pitch: Float): Vector {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val xz = cos(pitchRad)
        return Vector(-sin(yawRad) * xz, -sin(pitchRad), cos(yawRad) * xz)
    }
}

