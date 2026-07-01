package de.chaos.vision

import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object VisionMath {
    fun normalizeYaw(yaw: Float): Float {
        var value = yaw % 360f
        if (value < 0f) value += 360f
        return value
    }

    fun fromYawPitch(
        yaw: Float,
        pitch: Float,
    ): Vector {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val xz = cos(pitchRad)
        return Vector(-sin(yawRad) * xz, -sin(pitchRad), cos(yawRad) * xz)
    }

    fun normalizeInto(
        source: Vector,
        target: Vector,
        distance: Double,
    ) {
        if (distance > 0.0001) {
            val invDist = 1.0 / distance
            target.setX(source.x * invDist)
            target.setY(source.y * invDist)
            target.setZ(source.z * invDist)
        } else {
            target.setX(0.0)
            target.setY(0.0)
            target.setZ(1.0)
        }
    }

    fun yawPitchTo(
        player: Player,
        origin: Vector,
        fallbackYaw: Float,
        fallbackPitch: Float,
    ): Pair<Float, Float> {
        val direction = player.eyeLocation.toVector().subtract(origin)
        if (direction.lengthSquared() < EPSILON) {
            return fallbackYaw to fallbackPitch
        }
        direction.normalize()
        val yaw = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
        val pitch = Math.toDegrees(-asin(direction.y)).toFloat()
        return yaw to pitch
    }

    fun smoothRotate(
        currentYaw: Float,
        currentPitch: Float,
        targetYaw: Float,
        targetPitch: Float,
    ): Pair<Float, Float> {
        val maxYawStep = 12f
        val maxPitchStep = 12f
        val eps = 0.2f

        fun wrapDelta(
            a: Float,
            b: Float,
        ): Float {
            var delta = b - a
            while (delta <= -180f) delta += 360f
            while (delta > 180f) delta -= 360f
            return delta
        }

        val yawDelta = wrapDelta(currentYaw, targetYaw)
        val pitchDelta = targetPitch - currentPitch

        val yawStep =
            when {
                abs(yawDelta) <= eps -> 0f
                yawDelta > 0 -> minOf(yawDelta, maxYawStep)
                else -> -minOf(-yawDelta, maxYawStep)
            }
        val pitchStep =
            when {
                abs(pitchDelta) <= eps -> 0f
                pitchDelta > 0 -> minOf(pitchDelta, maxPitchStep)
                else -> -minOf(-pitchDelta, maxPitchStep)
            }

        var newYaw = currentYaw + yawStep
        if (newYaw < 0f) newYaw = (newYaw % 360f + 360f) % 360f
        if (newYaw >= 360f) newYaw %= 360f

        val newPitch = (currentPitch + pitchStep).coerceIn(-89.9f, 89.9f)
        return newYaw to newPitch
    }

    private const val EPSILON = 1.0E-8
}
