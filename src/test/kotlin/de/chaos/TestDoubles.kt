package de.chaos

import com.typewritermc.core.utils.point.World
import com.typewritermc.engine.paper.entry.entity.ActivityContext
import com.typewritermc.engine.paper.entry.entity.EntityState
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import de.chaos.display.DetectionDisplaySink
import de.chaos.display.VisionDisplayManager
import de.chaos.event.VisionEventSink
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player

internal fun fakePlayer(
    uniqueId: UUID = UUID.randomUUID(),
    sneaking: Boolean = false,
    eyeLocation: Location = Location(null, 0.0, 0.0, 0.0),
): Player {
    val handler =
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getUniqueId" -> uniqueId
                "isSneaking" -> sneaking
                "getEyeLocation" -> eyeLocation
                "getLocation" -> eyeLocation
                "getWorld" -> eyeLocation.world
                "isValid" -> true
                "getGameMode" -> GameMode.SURVIVAL
                "isInvisible" -> false
                "toString" -> "FakePlayer($uniqueId)"
                "hashCode" -> uniqueId.hashCode()
                "equals" -> args?.firstOrNull() === proxy
                else -> defaultReturnValue(method.returnType)
            }
        }

    return Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
        handler
    ) as Player
}

internal fun fakeActivityContext(
    viewed: Boolean = false,
    viewers: List<Player> = emptyList(),
    entityState: EntityState = EntityState(),
): ActivityContext {
    val handler =
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "isViewed" -> viewed
                "getViewers" -> viewers
                "getEntityState" -> entityState
                "toString" -> "FakeActivityContext"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> args?.firstOrNull() === proxy
                else -> defaultReturnValue(method.returnType)
            }
        }

    return Proxy.newProxyInstance(
        ActivityContext::class.java.classLoader,
        arrayOf(ActivityContext::class.java),
        handler
    ) as ActivityContext
}

internal fun testPosition(
    x: Double = 0.0,
    y: Double = 0.0,
    z: Double = 0.0,
    yaw: Float = 0f,
    pitch: Float = 0f,
): PositionProperty {
    return PositionProperty(World("test-world"), x, y, z, yaw, pitch)
}

private fun defaultReturnValue(type: Class<*>): Any? {
    return when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        java.lang.Void.TYPE -> null
        else -> null
    }
}

internal object NoopDetectionDisplaySink : DetectionDisplaySink {
    override fun updateIndicator(viewer: Player, location: Location, text: Component) = Unit

    override fun removeIndicator(viewer: Player) = Unit
}

internal object NoopVisionDisplayManager : VisionDisplayManager {
    override fun updateIndicator(viewer: Player, location: Location, text: Component) = Unit

    override fun removeIndicator(viewer: Player) = Unit

    override fun updatePointDisplay(location: Location, viewer: Player) = Unit

    override fun updateLineDisplay(start: Location, end: Location, viewer: Player) = Unit

    override fun prepareFrame(viewer: Player) = Unit

    override fun finishFrame(viewer: Player) = Unit

    override fun cleanupMissingViewers(currentViewerIds: Set<UUID>) = Unit

    override fun dispose() = Unit
}

internal class RecordingVisionEventSink : VisionEventSink {
    val seen = mutableListOf<UUID>()
    val lost = mutableListOf<UUID>()

    override fun playerSeen(context: ActivityContext, player: Player) {
        seen += player.uniqueId
    }

    override fun playerLost(context: ActivityContext, player: Player) {
        lost += player.uniqueId
    }
}
