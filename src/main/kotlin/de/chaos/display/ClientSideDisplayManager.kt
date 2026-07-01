package de.chaos.display

import com.github.retrooper.packetevents.util.Vector3f
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.atan2
import com.github.retrooper.packetevents.protocol.world.Location as PELocation

internal interface DetectionDisplaySink {
    fun updateIndicator(
        viewer: Player,
        location: Location,
        text: Component,
    )

    fun removeIndicator(viewer: Player)
}

internal interface VisionDebugDisplaySink {
    fun updatePointDisplay(
        location: Location,
        viewer: Player,
    )

    fun updateLineDisplay(
        start: Location,
        end: Location,
        viewer: Player,
    )
}

internal interface VisionDisplayManager : DetectionDisplaySink, VisionDebugDisplaySink {
    fun prepareFrame(viewer: Player)

    fun finishFrame(viewer: Player)

    fun cleanupMissingViewers(currentViewerIds: Set<UUID>)

    fun dispose()
}

/**
 * Manages client-side display entities using PacketEvents/EntityLib. These entities only exist as
 * packets - no server-side entities are created.
 *
 * This provides significantly better performance than Bukkit entities:
 * - No server memory usage for entities
 * - No world persistence
 * - No entity tick processing
 * - Pure client-side rendering
 */
internal class ClientSideDisplayManager(
    private val material: Material,
    private val displaySize: Float,
    private val runtime: DisplayRuntime = PacketEventsDisplayRuntime,
) : VisionDisplayManager {
    // Pool of wrapper entities per viewer.
    private val viewerDisplays = ConcurrentHashMap<UUID, ViewerDisplayPool>()
    private val indicatorDisplays = ConcurrentHashMap<UUID, DisplayEntity>()
    private val knownViewers = ConcurrentHashMap.newKeySet<UUID>()

    // Cached item for ItemDisplayMeta
    private val cachedItem: DisplayItem by lazy {
        runtime.itemStack(material)
    }

    override fun prepareFrame(viewer: Player) {
        val uuid = viewer.uniqueId
        val pool = viewerDisplays.computeIfAbsent(uuid) { ViewerDisplayPool() }
        pool.prepareFrame()
        knownViewers.add(uuid)
    }

    /** Spawn or update a point display for this viewer. */
    override fun updatePointDisplay(
        location: Location,
        viewer: Player,
    ) {
        val uuid = viewer.uniqueId
        val user =
            runtime.user(viewer) ?: run {
                removeViewer(uuid)
                return
            }
        val pool = viewerDisplays.computeIfAbsent(uuid) { ViewerDisplayPool() }
        knownViewers.add(uuid)

        val peLocation = toPELocation(location)
        pool.updatePoint(peLocation, user, runtime, cachedItem, displaySize)
    }

    /** Spawn or update a line display (stretched in Z direction). */
    override fun updateLineDisplay(
        start: Location,
        end: Location,
        viewer: Player,
    ) {
        val uuid = viewer.uniqueId
        val user =
            runtime.user(viewer) ?: run {
                removeViewer(uuid)
                return
            }
        val pool = viewerDisplays.computeIfAbsent(uuid) { ViewerDisplayPool() }
        knownViewers.add(uuid)

        val dir = end.clone().subtract(start).toVector()
        val length = dir.length()
        if (length <= MIN_LINE_LENGTH) {
            updatePointDisplay(start, viewer)
            return
        }
        val mid = start.clone().add(dir.clone().multiply(0.5))
        val dirNorm = dir.clone().normalize()
        val yaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
        val pitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()

        val peLocation = PELocation(mid.x, mid.y, mid.z, yaw, pitch)
        pool.updateLine(peLocation, yaw, pitch, user, runtime, cachedItem, displaySize, length.toFloat())
    }

    /** Update or create text indicator display. */
    override fun updateIndicator(
        viewer: Player,
        location: Location,
        text: Component,
    ) {
        val uuid = viewer.uniqueId
        val user = runtime.user(viewer)
        if (user == null) {
            removeIndicator(viewer)
            return
        }
        knownViewers.add(uuid)
        val peLocation = toPELocation(location)

        indicatorDisplays.compute(uuid) { _, existing ->
            if (existing != null) {
                existing.teleport(peLocation)
                existing.updateText(text)
                existing
            } else {
                runtime.textDisplay().also { entity ->
                    entity.configureText(text, shadow = true, billboardCenter = true)
                    entity.addViewer(user)
                    entity.spawn(peLocation)
                }
            }
        }
    }

    override fun removeIndicator(viewer: Player) {
        val uuid = viewer.uniqueId
        val entity = indicatorDisplays.remove(uuid) ?: return
        entity.remove()
    }

    /** Cleanup excess displays after a frame. */
    override fun finishFrame(viewer: Player) {
        val uuid = viewer.uniqueId
        val pool = viewerDisplays[uuid] ?: return
        pool.finishFrame()
    }

    /** Cleanup displays for viewers that are no longer present. */
    override fun cleanupMissingViewers(currentViewerIds: Set<UUID>) {
        trackedViewerIds()
            .filter { it !in currentViewerIds }
            .forEach(::removeViewer)
    }

    /** Dispose all displays. */
    override fun dispose() {
        trackedViewerIds().forEach(::removeViewer)
    }

    private fun removeViewer(uuid: UUID) {
        viewerDisplays.remove(uuid)?.removeAll()
        indicatorDisplays.remove(uuid)?.remove()
        knownViewers.remove(uuid)
    }

    private fun trackedViewerIds(): Set<UUID> {
        return HashSet<UUID>().apply {
            addAll(knownViewers)
            addAll(viewerDisplays.keys)
            addAll(indicatorDisplays.keys)
        }
    }

    private fun toPELocation(location: Location): PELocation {
        return PELocation(location.x, location.y, location.z, location.yaw, location.pitch)
    }

    private companion object {
        const val MIN_LINE_LENGTH = 1.0E-6
    }

    private class ViewerDisplayPool {
        private val displays = mutableListOf<DisplayEntity>()
        private var nextIndex = 0

        @Synchronized
        fun prepareFrame() {
            nextIndex = 0
        }

        @Synchronized
        fun updatePoint(
            location: PELocation,
            user: DisplayViewer,
            runtime: DisplayRuntime,
            item: DisplayItem,
            displaySize: Float,
        ) {
            val scale = Vector3f(displaySize, displaySize, displaySize)
            val display = nextDisplay(user, runtime, item, scale, location)
            if (!display.isNew) {
                display.entity.teleport(location)
                display.entity.updateItemScale(scale)
            }
        }

        @Synchronized
        fun updateLine(
            location: PELocation,
            yaw: Float,
            pitch: Float,
            user: DisplayViewer,
            runtime: DisplayRuntime,
            item: DisplayItem,
            displaySize: Float,
            length: Float,
        ) {
            val scale = Vector3f(displaySize, displaySize, length)
            val display = nextDisplay(user, runtime, item, scale, location)
            if (!display.isNew) {
                display.entity.teleport(location)
                display.entity.updateItemScale(scale)
            }
            display.entity.rotateHead(yaw, pitch)
        }

        @Synchronized
        fun finishFrame() {
            while (displays.size > nextIndex) {
                displays.removeAt(displays.size - 1).remove()
            }
        }

        @Synchronized
        fun removeAll() {
            displays.forEach { it.remove() }
            displays.clear()
            nextIndex = 0
        }

        private fun nextDisplay(
            user: DisplayViewer,
            runtime: DisplayRuntime,
            item: DisplayItem,
            scale: Vector3f,
            location: PELocation,
        ): PooledDisplay {
            val index = nextIndex
            nextIndex = index + 1
            if (index < displays.size) {
                return PooledDisplay(displays[index], isNew = false)
            }

            val entity =
                runtime.itemDisplay().also { entity ->
                    entity.configureItem(item, scale)
                    entity.addViewer(user)
                    entity.spawn(location)
                    displays.add(entity)
                }
            return PooledDisplay(entity, isNew = true)
        }

        private data class PooledDisplay(
            val entity: DisplayEntity,
            val isNew: Boolean,
        )
    }
}
