package de.chaos

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.world.Location as PELocation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.asin
import kotlin.math.atan2
import me.tofaa.entitylib.meta.display.ItemDisplayMeta
import me.tofaa.entitylib.meta.display.TextDisplayMeta
import me.tofaa.entitylib.wrapper.WrapperEntity
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

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
class ClientSideDisplayManager(
        private val material: Material,
        private val displaySize: Float,
) {
    // Pool of wrapper entities per viewer
    private val viewerDisplays = ConcurrentHashMap<UUID, MutableList<WrapperEntity>>()
    private val viewerDisplayIndex = ConcurrentHashMap<UUID, Int>()
    private val indicatorDisplays = ConcurrentHashMap<UUID, WrapperEntity>()
    private val knownViewers = ConcurrentHashMap.newKeySet<UUID>()

    // Cached item for ItemDisplayMeta
    private val cachedItem: com.github.retrooper.packetevents.protocol.item.ItemStack by lazy {
        SpigotConversionUtil.fromBukkitItemStack(ItemStack(material))
    }

    fun prepareFrame(viewer: Player) {
        viewerDisplayIndex[viewer.uniqueId] = 0
        knownViewers.add(viewer.uniqueId)
    }

    /** Spawn or update a point display for this viewer. */
    fun updatePointDisplay(location: Location, viewer: Player) {
        val uuid = viewer.uniqueId
        val list = viewerDisplays.computeIfAbsent(uuid) { mutableListOf() }
        val index = viewerDisplayIndex.getOrDefault(uuid, 0)
        val user = getUser(viewer) ?: return

        val peLocation = toPELocation(location)

        if (index < list.size) {
            // Update existing entity
            val entity = list[index]
            entity.teleport(peLocation)
        } else {
            // Create new entity
            val entity = WrapperEntity(EntityTypes.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.item = cachedItem
            meta.scale =
                    com.github.retrooper.packetevents.util.Vector3f(
                            displaySize,
                            displaySize,
                            displaySize
                    )

            entity.addViewer(user)
            entity.spawn(peLocation)
            list.add(entity)
        }

        viewerDisplayIndex[uuid] = index + 1
    }

    /** Spawn or update a line display (stretched in Z direction). */
    fun updateLineDisplay(start: Location, end: Location, viewer: Player) {
        val uuid = viewer.uniqueId
        val list = viewerDisplays.computeIfAbsent(uuid) { mutableListOf() }
        val index = viewerDisplayIndex.getOrDefault(uuid, 0)
        val user = getUser(viewer) ?: return

        val dir = end.clone().subtract(start).toVector()
        val length = dir.length()
        val mid = start.clone().add(dir.clone().multiply(0.5))
        val dirNorm = dir.clone().normalize()
        val yaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
        val pitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()

        val peLocation = PELocation(mid.x, mid.y, mid.z, yaw, pitch)

        if (index < list.size) {
            // Update existing entity
            val entity = list[index]
            entity.teleport(peLocation)
            entity.rotateHead(yaw, pitch)
            // Update scale if needed
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.scale =
                    com.github.retrooper.packetevents.util.Vector3f(
                            displaySize,
                            displaySize,
                            length.toFloat()
                    )
        } else {
            // Create new entity
            val entity = WrapperEntity(EntityTypes.ITEM_DISPLAY)
            val meta = entity.entityMeta as ItemDisplayMeta
            meta.item = cachedItem
            meta.scale =
                    com.github.retrooper.packetevents.util.Vector3f(
                            displaySize,
                            displaySize,
                            length.toFloat()
                    )

            entity.addViewer(user)
            entity.spawn(peLocation)
            entity.rotateHead(yaw, pitch)
            list.add(entity)
        }

        viewerDisplayIndex[uuid] = index + 1
    }

    /** Update or create text indicator display. */
    fun updateIndicator(
            viewer: Player,
            location: Location,
            text: Component,
    ) {
        val uuid = viewer.uniqueId
        val user = getUser(viewer) ?: return
        val peLocation = toPELocation(location)

        val existing = indicatorDisplays[uuid]

        if (existing != null) {
            // Update existing indicator
            existing.teleport(peLocation)
            val meta = existing.entityMeta as TextDisplayMeta
            meta.text = text
            // Note: Billboard updates persist, so no need to resend unless entity is respawned
        } else {
            // Create new indicator
            val entity = WrapperEntity(EntityTypes.TEXT_DISPLAY)
            val meta = entity.entityMeta as TextDisplayMeta
            meta.text = text
            meta.isShadow = true

            entity.addViewer(user)
            entity.spawn(peLocation) // Spawns the entity (sends spawn packet + meta with defaults)

            // Force billboard constraints via raw metadata packet
            sendBillboardPacket(entity, user)

            indicatorDisplays[uuid] = entity
        }
    }

    private fun sendBillboardPacket(entity: WrapperEntity, user: User) {
        val protocolVersion = PacketEvents.getAPI().serverManager.version.protocolVersion
        // 1.20.2 (764) and above typically shift indices.
        // Billboard Constraints Index:
        // 1.19.4: 14
        // 1.20.1: 14
        // 1.20.2+: 15 (Likely valid for 1.21 too)
        val index = if (protocolVersion >= 764) 15 else 14

        // Billboard value: 3 = CENTER (Rotate to face player)
        val data = EntityData(index, EntityDataTypes.BYTE, 3.toByte())
        val packet = WrapperPlayServerEntityMetadata(entity.entityId, listOf(data))
        user.sendPacket(packet)
    }

    fun removeIndicator(viewer: Player) {
        val uuid = viewer.uniqueId
        val entity = indicatorDisplays.remove(uuid) ?: return
        entity.remove()
    }

    /** Cleanup excess displays after a frame. */
    fun finishFrame(viewer: Player) {
        val uuid = viewer.uniqueId
        val used = viewerDisplayIndex[uuid] ?: 0
        val list = viewerDisplays[uuid] ?: return

        // Remove excess entities
        while (list.size > used) {
            val entity = list.removeAt(list.size - 1)
            entity.remove()
        }
    }

    /** Cleanup displays for viewers that are no longer present. */
    fun cleanupMissingViewers(currentViewerIds: Set<UUID>) {
        val tracked =
                HashSet<UUID>().apply {
                    addAll(knownViewers)
                    addAll(viewerDisplays.keys)
                    addAll(indicatorDisplays.keys)
                }
        val toRemove = tracked.filter { it !in currentViewerIds }

        toRemove.forEach { uuid ->
            viewerDisplays.remove(uuid)?.forEach { it.remove() }
            viewerDisplayIndex.remove(uuid)
            indicatorDisplays.remove(uuid)?.remove()
            knownViewers.remove(uuid)
        }
    }

    /** Dispose all displays. */
    fun dispose() {
        viewerDisplays.values.flatten().forEach { it.remove() }
        viewerDisplays.clear()
        viewerDisplayIndex.clear()
        knownViewers.clear()
        indicatorDisplays.values.forEach { it.remove() }
        indicatorDisplays.clear()
    }

    private fun getUser(player: Player): User? {
        return PacketEvents.getAPI().playerManager.getUser(player)
    }

    private fun toPELocation(location: Location): PELocation {
        return PELocation(location.x, location.y, location.z, location.yaw, location.pitch)
    }
}
