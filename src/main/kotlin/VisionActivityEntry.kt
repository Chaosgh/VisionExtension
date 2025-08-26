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
import kotlin.math.asin
import kotlin.math.atan2
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
    @Help("Shape of the vision area")
    val shape: VisionShape = VisionShape.CONE,
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<in ActivityContext> {
        return VisionActivity(radius, fov, shape, currentLocation)
    }
}

enum class VisionShape {
    CONE,
    LINE,
    SPHERE,
}

class VisionActivity(
    private val radius: Double,
    private val fov: Double,
    private val shape: VisionShape,
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
            spawnShapeParticles(origin, forward, player)

            val dir = player.eyeLocation.toVector().subtract(Vector(eyeX, eyeY, eyeZ))
            val distance = dir.length()

            val angle = Math.toDegrees(acos(forward.clone().normalize().dot(dir.clone().normalize())))

            val inside = when (shape) {
                VisionShape.CONE -> distance <= radius && angle <= fov / 2
                VisionShape.LINE -> distance <= radius && angle <= 1.0
                VisionShape.SPHERE -> distance <= radius
            }
            if (!inside) return@forEach

            val blocked = player.world.rayTraceBlocks(origin, dir.clone().normalize(), distance) != null
            if (blocked) return@forEach

            val dirNorm = dir.clone().normalize()
            val lookYaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
            val lookPitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()
            currentPosition = currentPosition.withRotation(lookYaw, lookPitch)
        }

        return TickResult.IGNORED
    }

    private fun spawnShapeParticles(origin: org.bukkit.Location, forward: Vector, viewer: Player) {
        when (shape) {
            VisionShape.LINE -> spawnLine(origin, forward, radius, viewer)
            VisionShape.CONE -> {
                val rays = 5
                for (i in 0 until rays) {
                    val angle = -fov / 2 + (fov / (rays - 1)) * i
                    val dir = rotateY(forward, angle)
                    spawnLine(origin, dir, radius, viewer)
                }
            }
            VisionShape.SPHERE -> spawnCircle(origin, radius, viewer)
        }
    }

    private fun spawnLine(origin: org.bukkit.Location, direction: Vector, distance: Double, viewer: Player) {
        val steps = (distance * 4).toInt()
        val step = direction.clone().normalize().multiply(distance / steps)
        var loc = origin.clone()
        repeat(steps) {
            viewer.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.0)
            loc.add(step)
        }
    }

    private fun spawnCircle(origin: org.bukkit.Location, radius: Double, viewer: Player) {
        val points = 24
        for (i in 0 until points) {
            val angle = 2 * Math.PI * i / points
            val x = origin.x + cos(angle) * radius
            val z = origin.z + sin(angle) * radius
            viewer.spawnParticle(Particle.END_ROD, x, origin.y, z, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun rotateY(vec: Vector, angleDeg: Double): Vector {
        val rad = Math.toRadians(angleDeg)
        val cos = cos(rad)
        val sin = sin(rad)
        return Vector(
            vec.x * cos - vec.z * sin,
            vec.y,
            vec.x * sin + vec.z * cos
        )
    }

    override fun dispose(context: ActivityContext) {}

    private fun fromYawPitch(yaw: Float, pitch: Float): Vector {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val xz = cos(pitchRad)
        return Vector(-sin(yawRad) * xz, -sin(pitchRad), cos(yawRad) * xz)
    }
}