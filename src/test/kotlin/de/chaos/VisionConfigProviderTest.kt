@file:Suppress("DEPRECATION")

package de.chaos

import de.chaos.entry.ActivityVisionEntry
import de.chaos.entry.PatrolVisionActivityEntry
import de.chaos.entry.RandomPatrolVisionActivityEntry
import de.chaos.entry.VisionActivityEntry
import de.chaos.entry.VisionConfigProvider
import de.chaos.vision.VisionConfig
import de.chaos.vision.VisionDefaults
import de.chaos.vision.VisionShape
import org.bukkit.Material
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionConfigProviderTest {
    @Test
    fun `toVisionConfig preserves shared entry tuning fields`() {
        val provider =
            TestVisionConfigProvider(
                visionRadius = 12.5,
                fov = 70.0,
                shape = VisionShape.LINE,
                displayUpdateIntervalTicks = 4,
                lostDelayTicks = 8,
                raycastIntervalTicks = 3,
                forcedLookEnabled = true,
                forcedLookYaw = -90f,
                forcedLookPitch = 130f,
            )

        val config = provider.toVisionConfig()

        assertEquals(12.5, config.radius)
        assertEquals(70.0, config.fovDegrees)
        assertEquals(VisionShape.LINE, config.shape)
        assertEquals(4, config.displayUpdateIntervalTicks)
        assertEquals(8, config.lostDelayTicks)
        assertEquals(3, config.raycastIntervalTicks)
        assertTrue(config.forcedLookEnabled)
        assertEquals(-90f, config.forcedYaw)
        assertEquals(130f, config.forcedPitch)
    }

    @Test
    fun `toVisionConfig uses compatibility defaults for newly added fields`() {
        val config = TestVisionConfigProvider().toVisionConfig()

        assertEquals(VisionDefaults.DISPLAY_UPDATE_INTERVAL_TICKS, config.displayUpdateIntervalTicks)
        assertEquals(VisionDefaults.LOST_DELAY_TICKS, config.lostDelayTicks)
        assertEquals(VisionDefaults.RAYCAST_INTERVAL_TICKS, config.raycastIntervalTicks)
        assertFalse(config.forcedLookEnabled)
        assertEquals(VisionDefaults.FORCED_LOOK_YAW, config.forcedYaw)
        assertEquals(VisionDefaults.FORCED_LOOK_PITCH, config.forcedPitch)
    }

    @Test
    fun `entry defaults map to the same vision config`() {
        val expected = VisionConfig()

        assertEquals(expected, VisionActivityEntry().toVisionConfig())
        assertEquals(expected, ActivityVisionEntry().toVisionConfig())
        assertEquals(expected, PatrolVisionActivityEntry().toVisionConfig())
        assertEquals(expected, RandomPatrolVisionActivityEntry().toVisionConfig())
    }

    private class TestVisionConfigProvider(
        override val visionRadius: Double = 5.0,
        override val fov: Double = 90.0,
        override val shape: VisionShape = VisionShape.CONE,
        override val showDisplays: Boolean = true,
        override val displayUpdateIntervalTicks: Int = 2,
        override val material: Material = Material.BARRIER,
        override val displaySize: Float = 0.02f,
        override val lookAtPlayer: Boolean = true,
        override val sneakProgressEnabled: Boolean = true,
        override val walkProgressEnabled: Boolean = false,
        override val walkMinDetectSeconds: Double = 0.3,
        override val walkMaxDetectSeconds: Double = 1.5,
        override val sneakMinDetectSeconds: Double = 0.6,
        override val sneakMaxDetectSeconds: Double = 2.5,
        override val visionDecayPerSecond: Double = 1.2,
        override val lostDelayTicks: Int = 3,
        override val raycastIntervalTicks: Int = 1,
        override val showDetectionIndicator: Boolean = true,
        override val indicatorOffsetY: Double = 0.6,
        override val forcedLookEnabled: Boolean = false,
        override val forcedLookYaw: Float = 0f,
        override val forcedLookPitch: Float = 0f,
    ) : VisionConfigProvider
}
