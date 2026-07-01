package de.chaos

import de.chaos.display.PacketEventsDisplayRuntime
import de.chaos.vision.DetectionState
import de.chaos.vision.DetectionTracker
import de.chaos.vision.VisionConfig
import de.chaos.vision.VisionSensor
import de.chaos.vision.VisionShape
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

open class BukkitIntegrationTest {
    private lateinit var server: ServerMock

    @BeforeTest
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterTest
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `cleanupMissingPlayers can fall back to Bukkit online player lookup`() {
        val events = RecordingVisionEventSink()
        val tracker =
            DetectionTracker(
                VisionConfig(
                    showDetectionIndicator = false,
                    sneakProgressEnabled = false,
                    walkProgressEnabled = false,
                ),
                NoopDetectionDisplaySink,
                events,
            )
        val context = fakeActivityContext()
        val player = server.addPlayer("SeenPlayer")
        val state = tracker.stateFor(player)

        assertTrue(tracker.handleVisible(player, context, state, 0.0, 1.0, 0.0, 1.0, 1.0))
        state.lastSeenPlayer = null

        assertSame(player, Bukkit.getPlayer(player.uniqueId))
        tracker.cleanupMissingPlayers(context, emptySet())

        assertEquals(listOf(player.uniqueId), events.seen)
        assertEquals(listOf(player.uniqueId), events.lost)
    }

    @Test
    fun `sensor can evaluate a Bukkit world and player eye location`() {
        val world = server.addSimpleWorld("vision-world")
        val player = server.addPlayer("Target")
        player.teleport(Location(world, 0.0, 0.0, 5.0))

        val origin = Location(world, 0.0, player.eyeLocation.y, 0.0)
        val direction = player.eyeLocation.toVector().subtract(origin.toVector())
        val state =
            DetectionState(
                lastRaycastTick = 1L,
                lastLineOfSight = true,
            )
        val result =
            VisionSensor(
                VisionConfig(
                    radius = 10.0,
                    fovDegrees = 90.0,
                    shape = VisionShape.CONE,
                    raycastIntervalTicks = 5,
                ),
            ).check(
                origin = origin,
                forward = Vector(0.0, 0.0, 1.0),
                direction = direction,
                normalizedDirection = Vector(),
                state = state,
                tickIndex = 2L,
            )

        assertTrue(result.visible)
        assertEquals(5.0, result.distance, 1.0E-5)
        assertEquals(1L, state.lastRaycastTick)
    }

    @Test
    fun `packet events display runtime rejects off primary thread usage`() {
        val failure = AtomicReference<Throwable?>()
        val thread =
            Thread {
                try {
                    PacketEventsDisplayRuntime.itemStack(Material.BARRIER)
                } catch (throwable: Throwable) {
                    failure.set(throwable)
                }
            }

        thread.start()
        thread.join()

        val throwable = failure.get()
        assertTrue(throwable is IllegalStateException)
        assertEquals(
            "PacketEvents display runtime must be used from the Bukkit primary thread.",
            throwable.message,
        )
    }
}
