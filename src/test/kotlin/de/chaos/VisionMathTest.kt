package de.chaos

import de.chaos.vision.VisionMath
import kotlin.test.Test
import kotlin.test.assertEquals
import org.bukkit.Location
import org.bukkit.util.Vector

class VisionMathTest {
    @Test
    fun `normalizeYaw wraps negative and overflowing angles`() {
        assertFloatEquals(0f, VisionMath.normalizeYaw(0f))
        assertFloatEquals(0f, VisionMath.normalizeYaw(360f))
        assertFloatEquals(270f, VisionMath.normalizeYaw(-90f))
        assertFloatEquals(45f, VisionMath.normalizeYaw(765f))
    }

    @Test
    fun `fromYawPitch follows Bukkit yaw and pitch directions`() {
        assertVectorEquals(0.0, 0.0, 1.0, VisionMath.fromYawPitch(0f, 0f))
        assertVectorEquals(-1.0, 0.0, 0.0, VisionMath.fromYawPitch(90f, 0f))
        assertVectorEquals(0.0, 0.0, -1.0, VisionMath.fromYawPitch(180f, 0f))
        assertVectorEquals(0.0, -1.0, 0.0, VisionMath.fromYawPitch(0f, 90f))
        assertVectorEquals(0.0, 1.0, 0.0, VisionMath.fromYawPitch(0f, -90f))
    }

    @Test
    fun `normalizeInto uses forward fallback for near zero vectors`() {
        val target = Vector()

        VisionMath.normalizeInto(Vector(0.0, 0.0, 0.0), target, 0.0)

        assertVectorEquals(0.0, 0.0, 1.0, target)
    }

    @Test
    fun `yawPitchTo keeps current rotation when target overlaps origin`() {
        val player = fakePlayer(eyeLocation = Location(null, 4.0, 5.0, 6.0))

        val rotation = VisionMath.yawPitchTo(player, Vector(4.0, 5.0, 6.0), 123f, -17f)

        assertFloatEquals(123f, rotation.first)
        assertFloatEquals(-17f, rotation.second)
    }

    @Test
    fun `smoothRotate takes shortest yaw path and clamps pitch`() {
        val wrappedPositive = VisionMath.smoothRotate(350f, 0f, 10f, 0f)
        assertFloatEquals(2f, wrappedPositive.first)
        assertFloatEquals(0f, wrappedPositive.second)

        val wrappedNegative = VisionMath.smoothRotate(10f, 0f, 350f, 0f)
        assertFloatEquals(358f, wrappedNegative.first)
        assertFloatEquals(0f, wrappedNegative.second)

        val clampedPitch = VisionMath.smoothRotate(0f, 85f, 0f, 120f)
        assertFloatEquals(0f, clampedPitch.first)
        assertFloatEquals(89.9f, clampedPitch.second)
    }

    private fun assertVectorEquals(expectedX: Double, expectedY: Double, expectedZ: Double, actual: Vector) {
        assertEquals(expectedX, actual.x, EPSILON)
        assertEquals(expectedY, actual.y, EPSILON)
        assertEquals(expectedZ, actual.z, EPSILON)
    }

    private fun assertFloatEquals(expected: Float, actual: Float) {
        assertEquals(expected.toDouble(), actual.toDouble(), EPSILON)
    }

    private companion object {
        const val EPSILON = 1.0E-5
    }
}
