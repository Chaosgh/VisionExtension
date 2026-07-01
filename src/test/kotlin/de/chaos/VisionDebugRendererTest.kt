package de.chaos

import de.chaos.display.VisionDebugDisplaySink
import de.chaos.vision.NormalizedVisionConfig
import de.chaos.vision.VisionConfig
import de.chaos.vision.VisionDebugRenderer
import de.chaos.vision.VisionMath
import de.chaos.vision.VisionShape
import de.chaos.vision.normalized
import org.bukkit.Location
import org.bukkit.entity.Player
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertTrue

class VisionDebugRendererTest {
    @Test
    fun `cone debug geometry stays inside sensor cone`() {
        val config =
            VisionConfig(
                radius = RADIUS,
                fovDegrees = FOV_DEGREES,
                shape = VisionShape.CONE,
            ).normalized()
        val sink = RecordingDebugDisplaySink()
        val renderer = VisionDebugRenderer(config, sink)
        val origin = Location(null, 0.0, 1.0, 0.0)
        val position = testPosition(y = 1.0, yaw = 35f, pitch = -12f)

        renderer.render(origin, fakePlayer(), position)

        val renderedLocations = sink.points + sink.lines.map { it.end }
        assertTrue(renderedLocations.isNotEmpty())
        renderedLocations.forEach { location ->
            assertInsideCone(config, origin, position.yaw, position.pitch, location)
        }
    }

    private fun assertInsideCone(
        config: NormalizedVisionConfig,
        origin: Location,
        yaw: Float,
        pitch: Float,
        location: Location,
    ) {
        val direction = location.clone().subtract(origin).toVector()
        val distance = direction.length()
        val dot = VisionMath.fromYawPitch(yaw, pitch).normalize().dot(direction.clone().normalize())
        val threshold = cos(Math.toRadians(config.fovDegrees / 2.0))

        assertTrue(distance <= config.radius + EPSILON, "Debug point exceeded radius: $distance")
        assertTrue(dot + EPSILON >= threshold, "Debug point exceeded FOV: dot=$dot threshold=$threshold")
    }

    private data class DebugLine(
        val start: Location,
        val end: Location,
    )

    private class RecordingDebugDisplaySink : VisionDebugDisplaySink {
        val points = mutableListOf<Location>()
        val lines = mutableListOf<DebugLine>()

        override fun updatePointDisplay(
            location: Location,
            viewer: Player,
        ) {
            points += location
        }

        override fun updateLineDisplay(
            start: Location,
            end: Location,
            viewer: Player,
        ) {
            lines += DebugLine(start, end)
        }
    }

    private companion object {
        const val RADIUS = 8.0
        const val FOV_DEGREES = 130.0
        const val EPSILON = 1.0E-6
    }
}
