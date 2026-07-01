package de.chaos.display

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.player.User
import com.github.retrooper.packetevents.protocol.world.Location as PELocation
import com.github.retrooper.packetevents.util.Vector3f
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta
import me.tofaa.entitylib.meta.display.ItemDisplayMeta
import me.tofaa.entitylib.meta.display.TextDisplayMeta
import me.tofaa.entitylib.wrapper.WrapperEntity
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import com.github.retrooper.packetevents.protocol.item.ItemStack as PacketItemStack

internal interface DisplayViewer

internal interface DisplayItem

internal interface DisplayEntity {
    val entityId: Int

    fun addViewer(viewer: DisplayViewer)
    fun spawn(location: PELocation)
    fun teleport(location: PELocation)
    fun rotateHead(yaw: Float, pitch: Float)
    fun remove()
    fun configureItem(item: DisplayItem, scale: Vector3f)
    fun updateItemScale(scale: Vector3f)
    fun configureText(text: Component, shadow: Boolean, billboardCenter: Boolean)
    fun updateText(text: Component)
}

internal interface DisplayRuntime {
    fun user(player: Player): DisplayViewer?
    fun itemStack(material: Material): DisplayItem
    fun itemDisplay(): DisplayEntity
    fun textDisplay(): DisplayEntity
}

internal object PacketEventsDisplayRuntime : DisplayRuntime {
    override fun user(player: Player): DisplayViewer? {
        requirePrimaryThread()
        return PacketEvents.getAPI().playerManager.getUser(player)?.let(::PacketEventsDisplayViewer)
    }

    override fun itemStack(material: Material): DisplayItem {
        requirePrimaryThread()
        return PacketEventsDisplayItem(SpigotConversionUtil.fromBukkitItemStack(ItemStack(material)))
    }

    override fun itemDisplay(): DisplayEntity {
        requirePrimaryThread()
        return WrapperDisplayEntity(WrapperEntity(EntityTypes.ITEM_DISPLAY))
    }

    override fun textDisplay(): DisplayEntity {
        requirePrimaryThread()
        return WrapperDisplayEntity(WrapperEntity(EntityTypes.TEXT_DISPLAY))
    }
}

private data class PacketEventsDisplayViewer(val user: User) : DisplayViewer

private data class PacketEventsDisplayItem(val item: PacketItemStack) : DisplayItem

private class WrapperDisplayEntity(
    private val entity: WrapperEntity,
) : DisplayEntity {
    override val entityId: Int
        get() = entity.entityId

    override fun addViewer(viewer: DisplayViewer) {
        requirePrimaryThread()
        entity.addViewer(viewer.asPacketEventsUser())
    }

    override fun spawn(location: PELocation) {
        requirePrimaryThread()
        entity.spawn(location)
    }

    override fun teleport(location: PELocation) {
        requirePrimaryThread()
        entity.teleport(location)
    }

    override fun rotateHead(yaw: Float, pitch: Float) {
        requirePrimaryThread()
        entity.rotateHead(yaw, pitch)
    }

    override fun remove() {
        requirePrimaryThread()
        entity.remove()
    }

    override fun configureItem(item: DisplayItem, scale: Vector3f) {
        requirePrimaryThread()
        val meta = entity.entityMeta as ItemDisplayMeta
        meta.item = item.asPacketEventsItem()
        meta.scale = scale
    }

    override fun updateItemScale(scale: Vector3f) {
        requirePrimaryThread()
        val meta = entity.entityMeta as ItemDisplayMeta
        meta.scale = scale
    }

    override fun configureText(text: Component, shadow: Boolean, billboardCenter: Boolean) {
        requirePrimaryThread()
        val meta = entity.entityMeta as TextDisplayMeta
        meta.text = text
        meta.isShadow = shadow
        if (billboardCenter) {
            meta.billboardConstraints = AbstractDisplayMeta.BillboardConstraints.CENTER
        }
    }

    override fun updateText(text: Component) {
        requirePrimaryThread()
        val meta = entity.entityMeta as TextDisplayMeta
        meta.text = text
    }
}

private fun requirePrimaryThread() {
    check(Bukkit.isPrimaryThread()) {
        "PacketEvents display runtime must be used from the Bukkit primary thread."
    }
}

private fun DisplayViewer.asPacketEventsUser(): User {
    require(this is PacketEventsDisplayViewer) {
        "DisplayViewer was not created by PacketEventsDisplayRuntime."
    }
    return user
}

private fun DisplayItem.asPacketEventsItem(): PacketItemStack {
    require(this is PacketEventsDisplayItem) {
        "DisplayItem was not created by PacketEventsDisplayRuntime."
    }
    return item
}
