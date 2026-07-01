package de.chaos.display

import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.protocol.world.Location as PELocation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.atan2
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player

internal interface DetectionDisplaySink {
    fun updateIndicator(viewer: Player, location: Location, text: Component)
    fun removeIndicator(viewer: Player)
}

internal interface VisionDebugDisplaySink {
    fun updatePointDisplay(location: Location, viewer: Player)
    fun updateLineDisplay(start: Location, end: Location, viewer: Player)
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
    private class ViewerDisplayState {
        val displays = mutableListOf<DisplayEntity>()
        var nextIndex = 0
    }

    // Pool of wrapper entities per viewer.
    private val viewerDisplays = ConcurrentHashMap<UUID, ViewerDisplayState>()
    private val indicatorDisplays = ConcurrentHashMap<UUID, DisplayEntity>()
    private val knownViewers = ConcurrentHashMap.newKeySet<UUID>()

    // Cached item for ItemDisplayMeta
    private val cachedItem: DisplayItem by lazy {
        runtime.itemStack(material)
    }

    override fun prepareFrame(viewer: Player) {
        val uuid = viewer.uniqueId
        val state = viewerDisplays.computeIfAbsent(uuid) { ViewerDisplayState() }
        synchronized(state) {
            state.nextIndex = 0
        }
        knownViewers.add(uuid)
    }

    /** Spawn or update a point display for this viewer. */
    override fun updatePointDisplay(location: Location, viewer: Player) {
        val uuid = viewer.uniqueId
        val user = runtime.user(viewer) ?: run {
            removeViewer(uuid)
            return
        }
        val state = viewerDisplays.computeIfAbsent(uuid) { ViewerDisplayState() }
        knownViewers.add(uuid)

        val peLocation = toPELocation(location)

        synchronized(state) {
            val index = state.nextIndex
            if (index < state.displays.size) {
                val entity = state.displays[index]
                entity.teleport(peLocation)
            } else {
                val entity = runtime.itemDisplay()
                entity.configureItem(cachedItem, Vector3f(displaySize, displaySize, displaySize))

                entity.addViewer(user)
                entity.spawn(peLocation)
                state.displays.add(entity)
            }

            state.nextIndex = index + 1
        }
    }

    /** Spawn or update a line display (stretched in Z direction). */
    override fun updateLineDisplay(start: Location, end: Location, viewer: Player) {
        val uuid = viewer.uniqueId
        val user = runtime.user(viewer) ?: run {
            removeViewer(uuid)
            return
        }
        val state = viewerDisplays.computeIfAbsent(uuid) { ViewerDisplayState() }
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

        synchronized(state) {
            val index = state.nextIndex
            if (index < state.displays.size) {
                val entity = state.displays[index]
                entity.teleport(peLocation)
                entity.rotateHead(yaw, pitch)
                entity.updateItemScale(Vector3f(displaySize, displaySize, length.toFloat()))
            } else {
                val entity = runtime.itemDisplay()
                entity.configureItem(cachedItem, Vector3f(displaySize, displaySize, length.toFloat()))

                entity.addViewer(user)
                entity.spawn(peLocation)
                entity.rotateHead(yaw, pitch)
                state.displays.add(entity)
            }

            state.nextIndex = index + 1
        }
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
        val state = viewerDisplays[uuid] ?: return

        synchronized(state) {
            while (state.displays.size > state.nextIndex) {
                val entity = state.displays.removeAt(state.displays.size - 1)
                entity.remove()
            }
        }
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
        viewerDisplays.remove(uuid)?.let { state ->
            synchronized(state) {
                state.displays.forEach { it.remove() }
                state.displays.clear()
                state.nextIndex = 0
            }
        }
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
}
