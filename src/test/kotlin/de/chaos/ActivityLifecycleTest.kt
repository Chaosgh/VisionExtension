package de.chaos

import com.typewritermc.engine.paper.entry.entity.ActivityContext
import com.typewritermc.engine.paper.entry.entity.EntityState
import com.typewritermc.engine.paper.entry.entity.EntityActivity
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import com.typewritermc.engine.paper.entry.entity.TickResult
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import de.chaos.activity.PausableActivity
import de.chaos.activity.PatrolVisionActivity
import de.chaos.vision.DetectionTracker
import de.chaos.vision.VisionActivity
import de.chaos.vision.VisionActivityDependencies
import de.chaos.vision.VisionConfig
import de.chaos.vision.VisionDebugRenderer
import de.chaos.vision.VisionSensor
import de.chaos.vision.VisionTargetSelector
import de.chaos.vision.normalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.bukkit.Location

class ActivityLifecycleTest {
    @Test
    fun `pausable activity disposes paused delegate once and reinitializes before resume tick`() {
        val context = fakeActivityContext()
        val start = testPosition(x = 1.0, yaw = 15f)
        val delegate = RecordingActivity(start)
        val pausable = PausableActivity(delegate)

        pausable.initialize(context, start)
        pausable.pause(context)
        pausable.resume(context)
        pausable.tick(context)

        assertFalse(pausable.isPaused)
        assertEquals(2, delegate.initializeCalls)
        assertEquals(1, delegate.disposeCalls)
        assertEquals(1, delegate.tickCalls)
        assertEquals(start, delegate.initializedPositions.last())
    }

    @Test
    fun `pausable activity can pause again immediately after resume`() {
        val context = fakeActivityContext()
        val start = testPosition()
        val delegate = RecordingActivity(start)
        val pausable = PausableActivity(delegate)

        pausable.initialize(context, start)
        pausable.pause(context)
        pausable.resume(context)
        pausable.pause(context)
        pausable.dispose(context)

        assertFalse(pausable.isPaused)
        assertEquals(2, delegate.initializeCalls)
        assertEquals(2, delegate.disposeCalls)
    }

    @Test
    fun `pausable activity does not dispose delegate twice when disposed while paused`() {
        val context = fakeActivityContext()
        val start = testPosition()
        val delegate = RecordingActivity(start)
        val pausable = PausableActivity(delegate)

        pausable.initialize(context, start)
        pausable.pause(context)
        pausable.dispose(context)

        assertFalse(pausable.isPaused)
        assertEquals(1, delegate.disposeCalls)
    }

    @Test
    fun `vision activity applies forced look on initialize and no-viewer ticks`() {
        val start = testPosition(yaw = 45f, pitch = 5f)
        val activity =
            VisionActivity(
                VisionConfig(
                    showDisplays = false,
                    showDetectionIndicator = false,
                    forcedLookEnabled = true,
                    forcedYaw = -90f,
                    forcedPitch = 200f
                ),
                start
            )

        activity.initialize(fakeActivityContext(), start)
        assertEquals(270f, activity.currentPosition.yaw)
        assertEquals(89.9f, activity.currentPosition.pitch)

        activity.currentPosition = activity.currentPosition.withRotation(10f, -10f)
        activity.tick(fakeActivityContext(viewed = false))

        assertEquals(270f, activity.currentPosition.yaw)
        assertEquals(89.9f, activity.currentPosition.pitch)
    }

    @Test
    fun `vision activity applies forced look before scanning viewed players`() {
        val config =
            VisionConfig(
                fovDegrees = 30.0,
                showDisplays = false,
                showDetectionIndicator = false,
                lookAtPlayer = false,
                sneakProgressEnabled = false,
                walkProgressEnabled = false,
                forcedLookEnabled = true,
                forcedYaw = 0f,
                forcedPitch = 0f
            )
        val events = RecordingVisionEventSink()
        val activity = visionActivity(config, events, testPosition(yaw = 90f))
        val entityState = EntityState()
        val player = fakePlayer(eyeLocation = Location(null, 0.0, entityState.eyeHeight, 4.0))
        val context = fakeActivityContext(viewed = true, viewers = listOf(player), entityState = entityState)

        activity.initialize(context, activity.currentPosition)
        activity.currentPosition = activity.currentPosition.withRotation(90f, 0f)
        activity.tick(context)

        assertTrue(activity.isSeeingPlayer)
        assertEquals(listOf(player.uniqueId), events.seen)
        assertEquals(0f, activity.currentPosition.yaw)
    }

    @Test
    fun `patrol vision activity exposes forced vision rotation while no player is seen`() {
        val context = fakeActivityContext(viewed = false)
        val patrolStart = testPosition(yaw = 90f)
        val patrol = PausableActivity(RecordingActivity(patrolStart))
        val vision =
            VisionActivity(
                VisionConfig(
                    showDisplays = false,
                    showDetectionIndicator = false,
                    forcedLookEnabled = true,
                    forcedYaw = 180f,
                    forcedPitch = -20f
                ),
                patrolStart
            )
        val activity = PatrolVisionActivity(patrol, vision, stopWhenLooking = true)

        activity.initialize(context, patrolStart)
        activity.tick(context)

        assertEquals(180f, activity.currentPosition.yaw)
        assertEquals(-20f, activity.currentPosition.pitch)
    }

    private fun visionActivity(
        config: VisionConfig,
        events: RecordingVisionEventSink,
        start: PositionProperty,
    ): VisionActivity {
        val normalized = config.normalized()
        return VisionActivity(
            config,
            start,
            VisionActivityDependencies(
                displayManager = NoopVisionDisplayManager,
                sensor = VisionSensor(normalized),
                tracker = DetectionTracker(normalized, NoopVisionDisplayManager, events),
                targetSelector = VisionTargetSelector(),
                debugRenderer = VisionDebugRenderer(normalized, NoopVisionDisplayManager),
            )
        )
    }

    private class RecordingActivity(start: PositionProperty) : EntityActivity<ActivityContext> {
        override var currentPosition: PositionProperty = start

        override val currentProperties: List<EntityProperty>
            get() = listOf(currentPosition)

        var initializeCalls = 0
            private set
        var disposeCalls = 0
            private set
        var tickCalls = 0
            private set
        val initializedPositions = mutableListOf<PositionProperty>()

        override fun initialize(context: ActivityContext, position: PositionProperty) {
            initializeCalls++
            currentPosition = position
            initializedPositions += position
        }

        override fun tick(context: ActivityContext): TickResult {
            tickCalls++
            return TickResult.IGNORED
        }

        override fun dispose(context: ActivityContext) {
            disposeCalls++
        }
    }
}
