package de.chaos

import com.github.retrooper.packetevents.util.Vector3f
import de.chaos.display.ClientSideDisplayManager
import de.chaos.display.DisplayEntity
import de.chaos.display.DisplayItem
import de.chaos.display.DisplayRuntime
import de.chaos.display.DisplayViewer
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.github.retrooper.packetevents.protocol.world.Location as PELocation

class ClientSideDisplayManagerTest {
    @Test
    fun `indicator display is reused for the same viewer`() {
        val runtime = FakeDisplayRuntime()
        val player = fakePlayer()
        runtime.connect(player)
        val manager = ClientSideDisplayManager(Material.BARRIER, 0.05f, runtime)

        manager.updateIndicator(player, location(1.0), Component.text("first"))
        manager.updateIndicator(player, location(2.0), Component.text("second"))

        val entity = runtime.textDisplays.single()
        assertEquals(Component.text("second"), entity.text)
        assertEquals(2.0, entity.teleports.single().x)
        assertEquals(1, entity.viewers.size)
        assertTrue(entity.billboardCenter)
    }

    @Test
    fun `indicator display is removed when viewer user disappears`() {
        val runtime = FakeDisplayRuntime()
        val player = fakePlayer()
        runtime.connect(player)
        val manager = ClientSideDisplayManager(Material.BARRIER, 0.05f, runtime)

        manager.updateIndicator(player, location(1.0), Component.text("first"))
        val firstEntity = runtime.textDisplays.single()

        runtime.disconnect(player)
        manager.updateIndicator(player, location(2.0), Component.text("stale"))

        assertTrue(firstEntity.removed)

        runtime.connect(player)
        manager.updateIndicator(player, location(3.0), Component.text("fresh"))

        assertEquals(2, runtime.textDisplays.size)
        assertEquals(Component.text("fresh"), runtime.textDisplays.last().text)
    }

    @Test
    fun `frame cleanup removes unused debug item displays`() {
        val runtime = FakeDisplayRuntime()
        val player = fakePlayer()
        runtime.connect(player)
        val manager = ClientSideDisplayManager(Material.BARRIER, 0.05f, runtime)

        manager.prepareFrame(player)
        manager.updatePointDisplay(location(1.0), player)
        manager.updatePointDisplay(location(2.0), player)
        manager.finishFrame(player)

        assertEquals(2, runtime.itemDisplays.size)

        manager.prepareFrame(player)
        manager.updatePointDisplay(location(3.0), player)
        manager.finishFrame(player)

        assertFalse(runtime.itemDisplays[0].removed)
        assertTrue(runtime.itemDisplays[1].removed)
        assertEquals(3.0, runtime.itemDisplays[0].teleports.single().x)
    }

    @Test
    fun `missing viewer cleanup removes only displays for missing viewers`() {
        val runtime = FakeDisplayRuntime()
        val keptPlayer = fakePlayer()
        val removedPlayer = fakePlayer()
        runtime.connect(keptPlayer)
        runtime.connect(removedPlayer)
        val manager = ClientSideDisplayManager(Material.BARRIER, 0.05f, runtime)

        manager.updateIndicator(keptPlayer, location(1.0), Component.text("kept"))
        manager.updateIndicator(removedPlayer, location(2.0), Component.text("removed"))

        val keptEntity = runtime.textDisplays[0]
        val removedEntity = runtime.textDisplays[1]
        manager.cleanupMissingViewers(setOf(keptPlayer.uniqueId))

        assertFalse(keptEntity.removed)
        assertTrue(removedEntity.removed)
    }

    @Test
    fun `concurrent indicator updates create one display for the same viewer`() {
        val runtime =
            FakeDisplayRuntime().apply {
                textDisplayDelayMillis = 25
            }
        val player = fakePlayer()
        runtime.connect(player)
        val manager = ClientSideDisplayManager(Material.BARRIER, 0.05f, runtime)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(2) { index ->
            Thread {
                try {
                    start.await()
                    manager.updateIndicator(player, location(index.toDouble()), Component.text("text-$index"))
                } catch (throwable: Throwable) {
                    failures += throwable
                } finally {
                    done.countDown()
                }
            }.start()
        }

        start.countDown()

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(emptyList(), failures)
        assertEquals(1, runtime.textDisplays.size)
        assertEquals(1, runtime.textDisplays.single().viewers.size)
        assertTrue(runtime.textDisplays.single().billboardCenter)
    }

    private fun location(x: Double): Location {
        return Location(null, x, 0.0, 0.0)
    }

    private class FakeDisplayRuntime : DisplayRuntime {
        private val viewers = HashMap<UUID, FakeDisplayViewer>()
        private var nextEntityId = 1

        val itemDisplays = mutableListOf<FakeDisplayEntity>()
        val textDisplays = mutableListOf<FakeDisplayEntity>()
        var textDisplayDelayMillis = 0L

        fun connect(player: Player) {
            viewers[player.uniqueId] = FakeDisplayViewer(player.uniqueId)
        }

        fun disconnect(player: Player) {
            viewers.remove(player.uniqueId)
        }

        override fun user(player: Player): DisplayViewer? {
            return viewers[player.uniqueId]
        }

        override fun itemStack(material: Material): DisplayItem {
            return FakeDisplayItem(material)
        }

        @Synchronized
        override fun itemDisplay(): DisplayEntity {
            return FakeDisplayEntity(nextEntityId++).also(itemDisplays::add)
        }

        @Synchronized
        override fun textDisplay(): DisplayEntity {
            if (textDisplayDelayMillis > 0) {
                Thread.sleep(textDisplayDelayMillis)
            }
            return FakeDisplayEntity(nextEntityId++).also(textDisplays::add)
        }
    }

    private data class FakeDisplayViewer(val playerId: UUID) : DisplayViewer

    private data class FakeDisplayItem(val material: Material) : DisplayItem

    private class FakeDisplayEntity(
        override val entityId: Int,
    ) : DisplayEntity {
        val viewers = mutableListOf<DisplayViewer>()
        val teleports = mutableListOf<PELocation>()

        var text: Component? = null
            private set
        var removed = false
            private set
        var billboardCenter = false
            private set
        private var item: DisplayItem? = null
        private var scale: Vector3f? = null
        private var spawnedAt: PELocation? = null
        private var shadow = false

        override fun addViewer(viewer: DisplayViewer) {
            viewers += viewer
        }

        override fun spawn(location: PELocation) {
            spawnedAt = location
        }

        override fun teleport(location: PELocation) {
            teleports += location
        }

        override fun rotateHead(
            yaw: Float,
            pitch: Float,
        ) = Unit

        override fun remove() {
            removed = true
        }

        override fun configureItem(
            item: DisplayItem,
            scale: Vector3f,
        ) {
            this.item = item
            this.scale = scale
        }

        override fun updateItemScale(scale: Vector3f) {
            this.scale = scale
        }

        override fun configureText(
            text: Component,
            shadow: Boolean,
            billboardCenter: Boolean,
        ) {
            this.text = text
            this.shadow = shadow
            this.billboardCenter = billboardCenter
        }

        override fun updateText(text: Component) {
            this.text = text
        }
    }
}
