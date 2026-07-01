package de.chaos

import de.chaos.vision.DetectionState
import de.chaos.vision.VisionConfig
import de.chaos.vision.VisionSensor
import de.chaos.vision.VisionShape
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionSensorTest {
    @Test
    fun `cone detects targets inside radius and fov`() {
        val state = DetectionState()
        val normalizedDirection = Vector()
        val result =
            sensor(VisionShape.CONE).check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(0.0, 0.0, 5.0),
                normalizedDirection = normalizedDirection,
                state = state,
                tickIndex = 1L,
            )

        assertTrue(result.visible)
        assertEquals(5.0, result.distance, EPSILON)
        assertEquals(25.0, result.distanceSquared, EPSILON)
        assertVectorEquals(0.0, 0.0, 1.0, normalizedDirection)
    }

    @Test
    fun `cone rejects targets outside radius or fov`() {
        val byRadius =
            sensor(VisionShape.CONE).check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(0.0, 0.0, 10.1),
                normalizedDirection = Vector(),
                state = DetectionState(),
                tickIndex = 1L,
            )
        assertFalse(byRadius.visible)

        val byFov =
            sensor(VisionShape.CONE).check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(10.0, 0.0, 0.0),
                normalizedDirection = Vector(),
                state = DetectionState(),
                tickIndex = 1L,
            )
        assertFalse(byFov.visible)
    }

    @Test
    fun `sphere ignores facing direction inside radius`() {
        val result =
            sensor(VisionShape.SPHERE, fovDegrees = 1.0).check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(3.0, 0.0, 0.0),
                normalizedDirection = Vector(),
                state = DetectionState(),
                tickIndex = 1L,
            )

        assertTrue(result.visible)
    }

    @Test
    fun `line shape uses forward projection and lateral width`() {
        val lineSensor = sensor(VisionShape.LINE, fovDegrees = 4.0)

        val inside =
            lineSensor.check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(1.5, 0.0, 5.0),
                normalizedDirection = Vector(),
                state = DetectionState(),
                tickIndex = 1L,
            )
        assertTrue(inside.visible)

        val tooWide =
            lineSensor.check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(2.5, 0.0, 5.0),
                normalizedDirection = Vector(),
                state = DetectionState(),
                tickIndex = 1L,
            )
        assertFalse(tooWide.visible)

        val behind =
            lineSensor.check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(0.0, 0.0, -1.0),
                normalizedDirection = Vector(),
                state = DetectionState(),
                tickIndex = 1L,
            )
        assertFalse(behind.visible)
    }

    @Test
    fun `raycast result is cached until configured interval expires`() {
        val state = DetectionState()
        val cachedSensor = sensor(VisionShape.CONE, raycastIntervalTicks = 5)

        val first =
            cachedSensor.check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(0.0, 0.0, 5.0),
                normalizedDirection = Vector(),
                state = state,
                tickIndex = 1L,
            )
        assertTrue(first.visible)
        assertEquals(1L, state.lastRaycastTick)

        state.lastLineOfSight = false
        val cached =
            cachedSensor.check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(0.0, 0.0, 5.0),
                normalizedDirection = Vector(),
                state = state,
                tickIndex = 2L,
            )
        assertFalse(cached.visible)
        assertEquals(1L, state.lastRaycastTick)

        val refreshed =
            cachedSensor.check(
                origin = ORIGIN,
                forward = FORWARD,
                direction = Vector(0.0, 0.0, 5.0),
                normalizedDirection = Vector(),
                state = state,
                tickIndex = 6L,
            )
        assertTrue(refreshed.visible)
        assertEquals(6L, state.lastRaycastTick)
    }

    private fun sensor(
        shape: VisionShape,
        radius: Double = 10.0,
        fovDegrees: Double = 90.0,
        raycastIntervalTicks: Int = 1,
    ): VisionSensor {
        return VisionSensor(
            VisionConfig(
                radius = radius,
                fovDegrees = fovDegrees,
                shape = shape,
                raycastIntervalTicks = raycastIntervalTicks,
            ),
        )
    }

    private fun assertVectorEquals(
        expectedX: Double,
        expectedY: Double,
        expectedZ: Double,
        actual: Vector,
    ) {
        assertEquals(expectedX, actual.x, EPSILON)
        assertEquals(expectedY, actual.y, EPSILON)
        assertEquals(expectedZ, actual.z, EPSILON)
    }

    private companion object {
        val ORIGIN = Location(null, 0.0, 0.0, 0.0)
        val FORWARD = Vector(0.0, 0.0, 1.0)
        const val EPSILON = 1.0E-5
    }
}
