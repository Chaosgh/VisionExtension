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
    @Help("Field of view in degrees (line width in blocks when using LINE shape)")
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
    LINE,
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
        val eyeX = currentPosition.x
        val eyeY = currentPosition.y + context.entityState.eyeHeight
        val eyeZ = currentPosition.z
        val forward = fromYawPitch(currentPosition.yaw, currentPosition.pitch)

        context.viewers.filter { it.isLookable }.forEach { player ->
            val origin = org.bukkit.Location(player.world, eyeX, eyeY, eyeZ)
            if (showParticles) {
                spawnShapeParticles(origin, forward, currentPosition.yaw, currentPosition.pitch, player)
            }

            val dir = player.eyeLocation.toVector().subtract(Vector(eyeX, eyeY, eyeZ))
            val distance = dir.length()
            val forwardNorm = forward.clone().normalize()

            val inside = when (shape) {
                VisionShape.CONE -> {
                    val angle = Math.toDegrees(acos(forwardNorm.dot(dir.clone().normalize())))
                    distance <= radius && angle <= fov / 2
                }
                VisionShape.LINE -> {
                    val along = dir.dot(forwardNorm)
                    if (along < 0 || along > radius) false
                    else {
                        val perpendicular = dir.clone().subtract(forwardNorm.clone().multiply(along))
                        perpendicular.length() <= fov / 2
                    }
                }
                VisionShape.SPHERE -> distance <= radius
            }
            if (!inside) return@forEach

            val blocked = player.world.rayTraceBlocks(origin, dir.clone().normalize(), distance) != null
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
        forward: Vector,
        yaw: Float,
        pitch: Float,
        viewer: Player
    ) {
        when (shape) {
            VisionShape.LINE -> spawnLine(origin, forward, fov, radius, viewer)
            VisionShape.CONE -> spawnCone(origin, radius, yaw, pitch, viewer)
            VisionShape.SPHERE -> spawnDisk(origin, radius, viewer)
        }
    }

    private fun spawnLine(
        origin: org.bukkit.Location,
        direction: Vector,
        width: Double,
        distance: Double,
        viewer: Player
    ) {
        val steps = (distance * 2).toInt().coerceAtLeast(1).coerceAtMost(40)
        val widthSteps = (width * 2).toInt().coerceAtLeast(1).coerceAtMost(40)
        val forwardStep = direction.clone().normalize().multiply(distance / steps)
        var base = origin.clone()
        val right = direction.clone().normalize().crossProduct(Vector(0.0, 1.0, 0.0)).apply {
            if (lengthSquared() == 0.0) {
                x = 1.0; y = 0.0; z = 0.0
            }
            normalize()
        }
        val sideStep = right.clone().multiply(width / widthSteps)
        repeat(steps) {
            var side = base.clone().add(right.clone().multiply(-width / 2))
            repeat(widthSteps + 1) {
                viewer.spawnParticle(particle, side, 1, 0.0, 0.0, 0.0, 0.0)
                side.add(sideStep)
            }
            base.add(forwardStep)
        }
    }

    private fun spawnCone(
        origin: org.bukkit.Location,
        radius: Double,
        yaw: Float,
        pitch: Float,
        viewer: Player
    ) {
        val radialSteps = (radius * 2).toInt().coerceAtLeast(1).coerceAtMost(40)
        val yawSteps = (fov / 10).toInt().coerceAtLeast(1).coerceAtMost(36)
        val pitchSteps = (fov / 10).toInt().coerceAtLeast(1).coerceAtMost(36)
        for (r in 0..radialSteps) {
            val dist = radius * r / radialSteps
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
    }

    private fun spawnDisk(origin: org.bukkit.Location, radius: Double, viewer: Player) {
        val radialSteps = radius.toInt().coerceAtLeast(1).coerceAtMost(40)
        for (r in 0..radialSteps) {
            val dist = radius * r / radialSteps
            val points = (dist * 4).toInt().coerceAtLeast(1).coerceAtMost(200)
            for (i in 0 until points) {
                val angle = 2 * PI * i / points
                val x = origin.x + cos(angle) * dist
                val z = origin.z + sin(angle) * dist
                viewer.spawnParticle(particle, x, origin.y, z, 1, 0.0, 0.0, 0.0, 0.0)
            }
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

