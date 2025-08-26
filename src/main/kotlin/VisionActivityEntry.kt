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
import kotlin.math.PI
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
    @Help("Display particles to visualize the vision area")
    val showParticles: Boolean = true,
    @Help("Particle type used when visualizing vision")
    val particle: Particle = Particle.CRIT,
    @Help("Rotate NPC to face players inside the vision area")
    val lookAtPlayer: Boolean = true,
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<in ActivityContext> {
        return VisionActivity(radius, fov, shape, showParticles, particle, lookAtPlayer, currentLocation)
    }
}

enum class VisionShape {
    CONE,
    SPHERE,
}

class VisionActivity(
    private val radius: Double,
    private val fov: Double,
    private val shape: VisionShape,
    private val showParticles: Boolean,
    private val particle: Particle,
    private val lookAtPlayer: Boolean,
    start: PositionProperty,
) : EntityActivity<ActivityContext> {

    override var currentPosition: PositionProperty = start

    override fun initialize(context: ActivityContext) {}

    override fun tick(context: ActivityContext): TickResult {
        if (!context.isViewed) return TickResult.IGNORED

        val eyeX = currentPosition.x
        val eyeY = currentPosition.y + context.entityState.eyeHeight
        val eyeZ = currentPosition.z

        val yaw = currentPosition.yaw
        val pitch = currentPosition.pitch
        val forward = fromYawPitch(yaw, pitch)

        context.viewers.filter { it.isLookable }.forEach { player ->
            val origin = org.bukkit.Location(player.world, eyeX, eyeY, eyeZ)
            if (showParticles) {
                spawnShapeParticles(origin, yaw, pitch, player)
            }

            val dir = player.eyeLocation.toVector().subtract(Vector(eyeX, eyeY, eyeZ))
            val distance = dir.length()
            val forwardNorm = forward.clone().normalize()

            val inside = when (shape) {
                VisionShape.CONE -> {
                    val dot = forwardNorm.dot(dir.clone().normalize()).coerceIn(-1.0, 1.0)
                    val angle = Math.toDegrees(acos(dot))
                    distance <= radius && angle <= fov / 2
                }
                VisionShape.SPHERE -> distance <= radius
            }
            if (!inside) return@forEach

            val blocked = origin.world.rayTraceBlocks(origin, dir.clone().normalize(), distance) != null
            if (blocked) return@forEach

            if (lookAtPlayer) {
                val dirNorm = dir.clone().normalize()
                val lookYaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
                val lookPitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()
                currentPosition = currentPosition.withRotation(lookYaw, lookPitch)
            }
        }

        return TickResult.IGNORED
    }

    private fun spawnShapeParticles(
        origin: org.bukkit.Location,
        yaw: Float,
        pitch: Float,
        viewer: Player
    ) {
        when (shape) {
            VisionShape.CONE -> spawnCone(origin, radius, yaw, pitch, viewer)
            VisionShape.SPHERE -> spawnDisk(origin, radius, viewer)
        }
    }

    private fun spawnCone(
        origin: org.bukkit.Location,
        radius: Double,
        yaw: Float,
        pitch: Float,
        viewer: Player
    ) {
        val yawSteps = (fov / 10).toInt().coerceAtLeast(1).coerceAtMost(36)
        val pitchSteps = (fov / 10).toInt().coerceAtLeast(1).coerceAtMost(36)
        val dist = radius
        for (yStep in 0..yawSteps) {
            val yawOffset = -fov / 2 + fov * yStep / yawSteps
            for (pStep in 0..pitchSteps) {
                val pitchOffset = -fov / 2 + fov * pStep / pitchSteps
                val dir = fromYawPitch(
                    (yaw + yawOffset).toFloat(),
                    (pitch + pitchOffset).toFloat()
                ).normalize().multiply(dist)
                val loc = origin.clone().add(dir)
                viewer.spawnParticle(particle, loc, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    private fun spawnDisk(origin: org.bukkit.Location, radius: Double, viewer: Player) {
        val points = (radius * 8).toInt().coerceAtLeast(8).coerceAtMost(200)
        for (i in 0 until points) {
            val angle = 2 * PI * i / points
            val x = origin.x + cos(angle) * radius
            val z = origin.z + sin(angle) * radius
            viewer.spawnParticle(particle, x, origin.y, z, 1, 0.0, 0.0, 0.0, 0.0)
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
