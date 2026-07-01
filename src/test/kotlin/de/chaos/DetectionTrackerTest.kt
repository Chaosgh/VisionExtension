package de.chaos

import de.chaos.vision.DetectionTracker
import de.chaos.vision.VisionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetectionTrackerTest {
    @Test
    fun `hidden seen player remains detected during lost delay and fires lost once afterwards`() {
        val events = RecordingVisionEventSink()
        val tracker = tracker(events, lostDelayTicks = 2)
        val context = fakeActivityContext()
        val player = fakePlayer()
        val state = tracker.stateFor(player)

        assertTrue(tracker.handleVisible(player, context, state, 0.0, 1.0, 0.0, 1.0, 1.0))
        assertEquals(listOf(player.uniqueId), events.seen)

        assertTrue(tracker.handleHidden(player, context, state, 0.0, 1.0, 0.0))
        assertTrue(tracker.handleHidden(player, context, state, 0.0, 1.0, 0.0))
        assertTrue(state.seen)

        assertFalse(tracker.handleHidden(player, context, state, 0.0, 1.0, 0.0))
        assertFalse(state.seen)
        assertEquals(listOf(player.uniqueId), events.lost)

        assertFalse(tracker.handleHidden(player, context, state, 0.0, 1.0, 0.0))
        assertEquals(listOf(player.uniqueId), events.lost)
    }

    @Test
    fun `cleanupMissingPlayers uses cached last seen player for lost event`() {
        val events = RecordingVisionEventSink()
        val tracker = tracker(events)
        val context = fakeActivityContext()
        val player = fakePlayer()
        val state = tracker.stateFor(player)

        assertTrue(tracker.handleVisible(player, context, state, 0.0, 1.0, 0.0, 1.0, 1.0))

        tracker.cleanupMissingPlayers(context, emptySet())

        assertEquals(listOf(player.uniqueId), events.seen)
        assertEquals(listOf(player.uniqueId), events.lost)
        assertNull(state.lastSeenPlayer)
    }

    @Test
    fun `unseen players do not fire lost during cleanup`() {
        val events = RecordingVisionEventSink()
        val tracker = tracker(events)
        val context = fakeActivityContext()
        val player = fakePlayer()

        tracker.stateFor(player)
        tracker.cleanupMissingPlayers(context, emptySet())

        assertEquals(emptyList(), events.seen)
        assertEquals(emptyList(), events.lost)
    }

    private fun tracker(
        events: RecordingVisionEventSink,
        lostDelayTicks: Int = 3,
    ): DetectionTracker {
        return DetectionTracker(
            VisionConfig(
                showDetectionIndicator = false,
                sneakProgressEnabled = false,
                walkProgressEnabled = false,
                lostDelayTicks = lostDelayTicks
            ),
            NoopDetectionDisplaySink,
            events
        )
    }
}
