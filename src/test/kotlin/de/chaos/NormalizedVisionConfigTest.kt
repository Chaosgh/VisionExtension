package de.chaos

import de.chaos.vision.VisionConfig
import de.chaos.vision.normalized
import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizedVisionConfigTest {
    @Test
    fun `normalization clamps invalid runtime tuning once`() {
        val normalized =
            VisionConfig(
                radius = -5.0,
                fovDegrees = 400.0,
                displaySize = -1f,
                displayUpdateIntervalTicks = 0,
                sneakMinDetectSeconds = -1.0,
                sneakMaxDetectSeconds = -2.0,
                walkMinDetectSeconds = 0.0,
                walkMaxDetectSeconds = -5.0,
                visionDecayPerSecond = -10.0,
                lostDelayTicks = -4,
                raycastIntervalTicks = 0,
                forcedYaw = -90f,
                forcedPitch = 200f,
            ).normalized()

        assertEquals(0.05, normalized.radius)
        assertEquals(170.0, normalized.fovDegrees)
        assertEquals(0.001f, normalized.displaySize)
        assertEquals(1, normalized.displayUpdateIntervalTicks)
        assertEquals(0.05, normalized.sneakMinDetectSeconds)
        assertEquals(0.05, normalized.sneakMaxDetectSeconds)
        assertEquals(0.05, normalized.walkMinDetectSeconds)
        assertEquals(0.05, normalized.walkMaxDetectSeconds)
        assertEquals(0.0, normalized.visionDecayPerSecond)
        assertEquals(0.0, normalized.decayPerTick)
        assertEquals(0, normalized.lostDelayTicks)
        assertEquals(1, normalized.raycastIntervalTicks)
        assertEquals(270f, normalized.forcedYaw)
        assertEquals(89.9f, normalized.forcedPitch)
    }
}
