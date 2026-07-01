package de.chaos

import de.chaos.vision.DetectionProgressCalculator
import de.chaos.vision.VisionConfig
import de.chaos.vision.normalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectionProgressCalculatorTest {
    @Test
    fun `uses progressive detection according to movement state`() {
        val sneakingOnly =
            DetectionProgressCalculator(
                VisionConfig(sneakProgressEnabled = true, walkProgressEnabled = false).normalized(),
            )
        val walkingOnly =
            DetectionProgressCalculator(
                VisionConfig(sneakProgressEnabled = false, walkProgressEnabled = true).normalized(),
            )

        assertTrue(sneakingOnly.usesProgressDetection(fakePlayer(sneaking = true)))
        assertFalse(sneakingOnly.usesProgressDetection(fakePlayer(sneaking = false)))
        assertFalse(walkingOnly.usesProgressDetection(fakePlayer(sneaking = true)))
        assertTrue(walkingOnly.usesProgressDetection(fakePlayer(sneaking = false)))
    }

    @Test
    fun `visible progress increases faster near center and hidden progress decays`() {
        val calculator =
            DetectionProgressCalculator(
                VisionConfig(
                    radius = 10.0,
                    walkProgressEnabled = true,
                    walkMinDetectSeconds = 1.0,
                    walkMaxDetectSeconds = 3.0,
                    visionDecayPerSecond = 2.0,
                ).normalized(),
            )
        val player = fakePlayer(sneaking = false)

        val nearCenter = calculator.visibleProgress(player, currentProgress = 0.0, distance = 1.0, centerFactor = 1.0)
        val farEdge = calculator.visibleProgress(player, currentProgress = 0.0, distance = 10.0, centerFactor = 0.0)

        assertTrue(nearCenter > farEdge)
        assertEquals(0.4, calculator.hiddenProgress(0.5), 1.0E-8)
    }
}
