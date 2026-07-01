package de.chaos

import de.chaos.vision.VisionTargetSelector
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class VisionTargetSelectorTest {
    @Test
    fun `undetected players are ignored`() {
        val selector = VisionTargetSelector()
        val player = fakePlayer()

        selector.consider(detected = false, player = player, distanceSquared = 1.0)

        assertNull(selector.player)
    }

    @Test
    fun `nearest detected player wins regardless of consideration order`() {
        val selector = VisionTargetSelector()
        val far = fakePlayer()
        val near = fakePlayer()
        val middle = fakePlayer()

        selector.consider(detected = true, player = far, distanceSquared = 100.0)
        selector.consider(detected = true, player = near, distanceSquared = 9.0)
        selector.consider(detected = true, player = middle, distanceSquared = 25.0)

        assertSame(near, selector.player)
    }

    @Test
    fun `reset clears selected player`() {
        val selector = VisionTargetSelector()
        val player = fakePlayer()

        selector.consider(detected = true, player = player, distanceSquared = 1.0)
        selector.reset()

        assertNull(selector.player)
    }
}
