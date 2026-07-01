package de.chaos.vision

import kotlin.math.cos
import kotlin.math.sqrt
import org.bukkit.Location
import org.bukkit.util.Vector

internal data class VisionCheckResult(
    val visible: Boolean,
    val distance: Double = 0.0,
    val distanceSquared: Double = Double.MAX_VALUE,
)

internal class VisionSensor(config: NormalizedVisionConfig) {
    constructor(config: VisionConfig) : this(config.normalized())

    private val radius = config.radius
    private val radiusSquared = radius * radius
    private val fov = config.fovDegrees
    private val halfFov = fov / 2.0
    private val halfFovSquared = halfFov * halfFov
    private val coneDotThreshold = cos(Math.toRadians(halfFov))
    private val shape = config.shape
    private val raycastIntervalTicks = config.raycastIntervalTicks

    fun check(
        origin: Location,
        forward: Vector,
        direction: Vector,
        normalizedDirection: Vector,
        state: DetectionState,
        tickIndex: Long,
    ): VisionCheckResult {
        val distanceSquared = direction.lengthSquared()
        if (!isWithinBroadphase(distanceSquared)) {
            return VisionCheckResult(visible = false, distanceSquared = distanceSquared)
        }

        val distance = sqrt(distanceSquared)
        VisionMath.normalizeInto(direction, normalizedDirection, distance)

        val visible =
            checkInsideVision(forward, direction, normalizedDirection, distanceSquared) &&
                hasLineOfSight(state, origin, normalizedDirection, distance, tickIndex)

        return VisionCheckResult(visible, distance, distanceSquared)
    }

    fun centerFactor(forward: Vector, normalizedDirection: Vector, distance: Double): Double {
        return when (shape) {
            VisionShape.CONE -> {
                val dot = forward.dot(normalizedDirection).coerceIn(coneDotThreshold, 1.0)
                ((dot - coneDotThreshold) / (1.0 - coneDotThreshold)).coerceIn(0.0, 1.0)
            }
            VisionShape.LINE -> {
                val projection = forward.dot(normalizedDirection) * distance
                val latX = normalizedDirection.x * distance - forward.x * projection
                val latY = normalizedDirection.y * distance - forward.y * projection
                val latZ = normalizedDirection.z * distance - forward.z * projection
                val lateralLen = sqrt(latX * latX + latY * latY + latZ * latZ)
                (1.0 - (lateralLen / halfFov)).coerceIn(0.0, 1.0)
            }
            VisionShape.SPHERE -> 1.0
        }
    }

    private fun isWithinBroadphase(distanceSquared: Double): Boolean {
        return when (shape) {
            VisionShape.CONE,
            VisionShape.SPHERE -> distanceSquared <= radiusSquared
            VisionShape.LINE -> distanceSquared <= radiusSquared + halfFovSquared
        }
    }

    private fun checkInsideVision(
        forward: Vector,
        direction: Vector,
        normalizedDirection: Vector,
        distanceSquared: Double
    ): Boolean {
        return when (shape) {
            VisionShape.CONE -> {
                if (distanceSquared > radiusSquared) return false
                forward.dot(normalizedDirection) >= coneDotThreshold
            }
            VisionShape.LINE -> {
                val projection = forward.dot(direction)
                if (projection !in 0.0..radius) return false
                val latX = direction.x - forward.x * projection
                val latY = direction.y - forward.y * projection
                val latZ = direction.z - forward.z * projection
                val lateralSquared = latX * latX + latY * latY + latZ * latZ
                lateralSquared <= halfFovSquared
            }
            VisionShape.SPHERE -> distanceSquared <= radiusSquared
        }
    }

    private fun hasLineOfSight(
        state: DetectionState,
        origin: Location,
        direction: Vector,
        distance: Double,
        tickIndex: Long
    ): Boolean {
        if (tickIndex - state.lastRaycastTick >= raycastIntervalTicks) {
            state.lastLineOfSight =
                origin.world?.rayTraceBlocks(origin, direction, distance) == null
            state.lastRaycastTick = tickIndex
        }
        return state.lastLineOfSight
    }
}
