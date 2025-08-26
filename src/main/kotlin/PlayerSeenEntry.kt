package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.TriggerEntry
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.engine.paper.entry.findDisplay
import com.typewritermc.engine.paper.utils.isLookable
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Entry(
    "on_player_seen",
    "Trigger entries when a player enters the NPC's vision",
    Colors.GREEN,
    "mdi:eye-check"
)
class PlayerSeenEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Maximum distance in blocks the NPC can see")
    val radius: Double = 5.0,
    @Help("Field of view in degrees")
    val fov: Double = 90.0,
    @Help("Shape of the vision area")
    val shape: VisionShape = VisionShape.CONE,
    override val triggers: List<Ref<TriggerableEntry>> = emptyList()
) : GenericEntityActivityEntry, TriggerEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<in ActivityContext> {
        return PlayerSeenActivity(radius, fov, shape, triggers, currentLocation)
    }
}

class PlayerSeenActivity(
    private val radius: Double,
    private val fov: Double,
    private val shape: VisionShape,
    private val triggers: List<Ref<TriggerableEntry>>,
    start: PositionProperty
) : EntityActivity<ActivityContext> {

    override var currentPosition: PositionProperty = start
    private val seenPlayers = mutableSetOf<UUID>()

    override fun initialize(context: ActivityContext) {}

    override fun tick(context: ActivityContext): TickResult {
        if (!context.isViewed) return TickResult.IGNORED

        context.viewers.filter { it.isLookable }.forEach { player ->
            val base =
                context.instanceRef.findDisplay<AudienceEntityDisplay>()
                    ?.position(player.uniqueId)
                    ?.toProperty()
                    ?: currentPosition

            currentPosition = base

            val eyeX = base.x
            val eyeY = base.y + context.entityState.eyeHeight
            val eyeZ = base.z

            val yaw = base.yaw
            val pitch = base.pitch
            val forward = fromYawPitch(yaw, pitch)

            val origin = org.bukkit.Location(player.world, eyeX, eyeY, eyeZ)

            val dir = player.eyeLocation.toVector().subtract(Vector(eyeX, eyeY, eyeZ))
            val distance = dir.length()
            val forwardNorm = forward.clone().normalize()

            val inside = when (shape) {
                VisionShape.CONE -> {
                    val dot = forwardNorm.dot(dir.clone().normalize()).coerceIn(-1.0, 1.0)
                    val angle = Math.toDegrees(acos(dot))
                    distance <= radius && angle <= fov / 2
                }
                VisionShape.LINE -> {
                    val projection = forwardNorm.dot(dir)
                    if (projection < 0 || projection > radius) false else {
                        val lateral = dir.clone().subtract(forwardNorm.clone().multiply(projection))
                        lateral.length() <= fov / 2
                    }
                }
                VisionShape.SPHERE -> distance <= radius
            }

            if (!inside) {
                seenPlayers.remove(player.uniqueId)
                return@forEach
            }

            val blocked = origin.world.rayTraceBlocks(origin, dir.clone().normalize(), distance) != null
            if (blocked) {
                seenPlayers.remove(player.uniqueId)
                return@forEach
            }

            if (seenPlayers.add(player.uniqueId)) {
                triggers.triggerEntriesFor(player) { }
            }
        }

        return TickResult.IGNORED
    }

    override fun dispose(context: ActivityContext) {}

    private fun fromYawPitch(yaw: Float, pitch: Float): Vector {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val xz = cos(pitchRad)
        return Vector(-sin(yawRad) * xz, -sin(pitchRad), cos(yawRad) * xz)
    }
}

