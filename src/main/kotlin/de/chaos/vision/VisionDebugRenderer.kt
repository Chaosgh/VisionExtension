package de.chaos.vision

import de.chaos.display.VisionDebugDisplaySink

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import com.typewritermc.engine.paper.entry.entity.PositionProperty

internal class VisionDebugRenderer(
    config: NormalizedVisionConfig,
    private val displayManager: VisionDebugDisplaySink,
) {
    private val radius = config.radius
    private val fov = config.fovDegrees
    private val halfFov = fov / 2.0
    private val shape = config.shape

    fun render(origin: Location, viewer: Player, position: PositionProperty) {
        when (shape) {
            VisionShape.CONE -> drawCone(origin, viewer, position)
            VisionShape.LINE -> drawLine(origin, viewer, position)
            VisionShape.SPHERE -> drawSphere(origin, viewer)
        }
    }

    private fun drawCone(origin: Location, viewer: Player, position: PositionProperty) {
        val basis = basis(position)
        val halfFovRadians = Math.toRadians(halfFov)
        val baseRadius = radius * sin(halfFovRadians)
        val center = origin.clone().add(basis.forward.clone().multiply(radius * cos(halfFovRadians)))

        val steps = (fov / 5).toInt().coerceAtLeast(8).coerceAtMost(72)
        for (i in 0 until steps) {
            val angle = 2 * PI * i / steps
            val point =
                center.clone()
                    .add(basis.right.clone().multiply(cos(angle) * baseRadius))
                    .add(basis.up.clone().multiply(sin(angle) * baseRadius))
            displayManager.updatePointDisplay(point, viewer)
        }

        val edgeAngles = listOf(0.0, PI / 2, PI, 3 * PI / 2)
        edgeAngles.forEach { angle ->
            val point =
                center.clone()
                    .add(basis.right.clone().multiply(cos(angle) * baseRadius))
                    .add(basis.up.clone().multiply(sin(angle) * baseRadius))
            displayManager.updateLineDisplay(origin, point, viewer)
        }
    }

    private fun drawLine(origin: Location, viewer: Player, position: PositionProperty) {
        val basis = basis(position)
        val end = origin.clone().add(basis.forward.clone().multiply(radius))

        val startCorners =
            arrayOf(
                origin.clone().add(basis.right.clone().multiply(halfFov)).add(basis.up.clone().multiply(halfFov)),
                origin.clone().add(basis.right.clone().multiply(halfFov)).add(basis.up.clone().multiply(-halfFov)),
                origin.clone().add(basis.right.clone().multiply(-halfFov)).add(basis.up.clone().multiply(halfFov)),
                origin.clone().add(basis.right.clone().multiply(-halfFov)).add(basis.up.clone().multiply(-halfFov))
            )
        val endCorners =
            arrayOf(
                end.clone().add(basis.right.clone().multiply(halfFov)).add(basis.up.clone().multiply(halfFov)),
                end.clone().add(basis.right.clone().multiply(halfFov)).add(basis.up.clone().multiply(-halfFov)),
                end.clone().add(basis.right.clone().multiply(-halfFov)).add(basis.up.clone().multiply(halfFov)),
                end.clone().add(basis.right.clone().multiply(-halfFov)).add(basis.up.clone().multiply(-halfFov))
            )

        for (i in 0 until 4) {
            displayManager.updateLineDisplay(startCorners[i], startCorners[(i + 1) % 4], viewer)
            displayManager.updateLineDisplay(endCorners[i], endCorners[(i + 1) % 4], viewer)
            displayManager.updateLineDisplay(startCorners[i], endCorners[i], viewer)
        }
    }

    private fun drawSphere(origin: Location, viewer: Player) {
        val points = (radius * 8).toInt().coerceAtLeast(16).coerceAtMost(200)
        for (i in 0 until points) {
            val angle = 2 * PI * i / points
            displayManager.updatePointDisplay(
                origin.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius),
                viewer
            )
            displayManager.updatePointDisplay(
                origin.clone().add(0.0, cos(angle) * radius, sin(angle) * radius),
                viewer
            )
            displayManager.updatePointDisplay(
                origin.clone().add(cos(angle) * radius, sin(angle) * radius, 0.0),
                viewer
            )
        }
    }

    private fun basis(position: PositionProperty): Basis {
        val forward = VisionMath.fromYawPitch(position.yaw, position.pitch).normalize()
        val rightSource =
            if (kotlin.math.abs(forward.y) > 0.999) {
                Vector(1.0, 0.0, 0.0)
            } else {
                Vector(0.0, 1.0, 0.0)
            }
        val right = forward.clone().crossProduct(rightSource).normalize()
        val up = right.clone().crossProduct(forward).normalize()
        return Basis(forward, right, up)
    }

    private data class Basis(
        val forward: Vector,
        val right: Vector,
        val up: Vector,
    )
}
