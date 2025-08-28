package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.findDisplay
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.engine.paper.utils.isLookable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import org.bukkit.util.Transformation
import org.joml.Vector3f
import de.chaos.PlayerSeenEvent
import java.util.UUID
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

@Entry(
    "vision_activity",
    "Detect players inside an NPC's field of view",
    Colors.GREEN,
    "mdi:eye"
)
class VisionActivityEntry(
    override val id: String = "",
    override val name: String = "",
    @Help("Maximum distance in blocks the NPC can see")
    val radius: Double = 5.0,
    @Help("Field of view in degrees (max 170)")
    val fov: Double = 90.0,
    @Help("Shape of the vision area")
    val shape: VisionShape = VisionShape.CONE,
    @Help("Display item displays to visualize the vision area")
    val showDisplays: Boolean = true,
    @Help("Material used when visualizing vision")
    val material: Material = Material.BARRIER,
    @Help("Rotate NPC to face players inside the vision area")
    val lookAtPlayer: Boolean = true,
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<in ActivityContext> {
        return VisionActivity(radius, fov, shape, showDisplays, material, lookAtPlayer, currentLocation)
    }
}

enum class VisionShape {
    CONE,
    LINE,
    SPHERE,
}

class VisionActivity(
    private val radius: Double,
    fovDegrees: Double,
    private val shape: VisionShape,
    private val showDisplays: Boolean,
    private val material: Material,
    private val lookAtPlayer: Boolean,
    start: PositionProperty,
) : EntityActivity<ActivityContext> {

    private val fov = fovDegrees.coerceIn(1.0, 170.0)

    override var currentPosition: PositionProperty = start
    private val seenPlayers = mutableSetOf<UUID>()
    private val viewerDisplays = mutableMapOf<UUID, MutableList<ItemDisplay>>()
    private val viewerDisplayIndex = mutableMapOf<UUID, Int>()

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
            prepareDisplays(player)
            if (showDisplays) {
                spawnShapeDisplays(origin, yaw, pitch, player)
            }
            cleanupDisplays(player)

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
                    if (projection < 0 || projection > radius) false
                    else {
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
                val plugin = Bukkit.getPluginManager().getPlugin("Typewriter")
                if (plugin != null) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        Bukkit.getPluginManager().callEvent(PlayerSeenEvent(context.instanceRef, player))
                    })
                }
            }

            if (lookAtPlayer) {
                val dirNorm = dir.clone().normalize()
                val lookYaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
                val lookPitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()
                currentPosition = currentPosition.withRotation(lookYaw, lookPitch)
            }
        }

        return TickResult.IGNORED
    }

    private fun prepareDisplays(viewer: Player) {
        viewerDisplayIndex[viewer.uniqueId] = 0
    }

    private fun cleanupDisplays(viewer: Player) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        val used = viewerDisplayIndex[viewer.uniqueId] ?: 0
        val list = viewerDisplays[viewer.uniqueId] ?: return
        if (used < list.size) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val toRemove = list.subList(used, list.size)
                toRemove.forEach { it.remove() }
                toRemove.clear()
            })
        }
    }

    private fun spawnShapeDisplays(
        origin: org.bukkit.Location,
        yaw: Float,
        pitch: Float,
        viewer: Player
    ) {
        when (shape) {
            VisionShape.CONE -> spawnCone(origin, radius, yaw, pitch, viewer)
            VisionShape.LINE -> spawnLine(origin, radius, yaw, pitch, viewer)
            VisionShape.SPHERE -> spawnSphere(origin, radius, viewer)
        }
    }

    private fun spawnCone(
        origin: org.bukkit.Location,
        radius: Double,
        yaw: Float,
        pitch: Float,
        viewer: Player
    ) {
        val forward = fromYawPitch(yaw, pitch).normalize()
        val right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
        val up = right.clone().crossProduct(forward).normalize()

        val baseRadius = radius * tan(Math.toRadians(fov / 2))
        val center = origin.clone().add(forward.clone().multiply(radius))

        val steps = (fov / 5).toInt().coerceAtLeast(8).coerceAtMost(72)
        for (i in 0 until steps) {
            val angle = 2 * Math.PI * i / steps
            val point = center.clone()
                .add(right.clone().multiply(cos(angle) * baseRadius))
                .add(up.clone().multiply(sin(angle) * baseRadius))
            spawnDisplay(point, viewer)
        }

        val edgeAngles = listOf(0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2)
        edgeAngles.forEach { ang ->
            val point = center.clone()
                .add(right.clone().multiply(cos(ang) * baseRadius))
                .add(up.clone().multiply(sin(ang) * baseRadius))
            drawLine(origin, point, viewer)
        }
    }

    private fun spawnLine(
        origin: org.bukkit.Location,
        radius: Double,
        yaw: Float,
        pitch: Float,
        viewer: Player
    ) {
        val forward = fromYawPitch(yaw, pitch).normalize()
        val right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
        val up = right.clone().crossProduct(forward).normalize()

        val halfW = fov / 2
        val halfH = fov / 2

        val end = origin.clone().add(forward.clone().multiply(radius))

        val startCorners = arrayOf(
            origin.clone().add(right.clone().multiply(halfW)).add(up.clone().multiply(halfH)),
            origin.clone().add(right.clone().multiply(halfW)).add(up.clone().multiply(-halfH)),
            origin.clone().add(right.clone().multiply(-halfW)).add(up.clone().multiply(halfH)),
            origin.clone().add(right.clone().multiply(-halfW)).add(up.clone().multiply(-halfH))
        )
        val endCorners = arrayOf(
            end.clone().add(right.clone().multiply(halfW)).add(up.clone().multiply(halfH)),
            end.clone().add(right.clone().multiply(halfW)).add(up.clone().multiply(-halfH)),
            end.clone().add(right.clone().multiply(-halfW)).add(up.clone().multiply(halfH)),
            end.clone().add(right.clone().multiply(-halfW)).add(up.clone().multiply(-halfH))
        )

        for (i in 0 until 4) {
            drawLine(startCorners[i], startCorners[(i + 1) % 4], viewer)
            drawLine(endCorners[i], endCorners[(i + 1) % 4], viewer)
            drawLine(startCorners[i], endCorners[i], viewer)
        }
    }

    private fun spawnSphere(origin: org.bukkit.Location, radius: Double, viewer: Player) {
        val points = (radius * 8).toInt().coerceAtLeast(16).coerceAtMost(200)
        for (i in 0 until points) {
            val angle = 2 * PI * i / points
            val point1 = origin.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius)
            spawnDisplay(point1, viewer)
            val point2 = origin.clone().add(0.0, cos(angle) * radius, sin(angle) * radius)
            spawnDisplay(point2, viewer)
            val point3 = origin.clone().add(cos(angle) * radius, sin(angle) * radius, 0.0)
            spawnDisplay(point3, viewer)
        }
    }

    private fun drawLine(start: org.bukkit.Location, end: org.bukkit.Location, viewer: Player) {
        val distance = start.distance(end)
        val steps = (distance * 4).toInt().coerceAtLeast(1)
        val step = end.clone().subtract(start).toVector().multiply(1.0 / steps)
        val point = start.clone()
        for (i in 0..steps) {
            spawnDisplay(point, viewer)
            point.add(step)
        }
    }

    private fun spawnDisplay(point: org.bukkit.Location, viewer: Player) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        val list = viewerDisplays.computeIfAbsent(viewer.uniqueId) { mutableListOf() }
        val index = viewerDisplayIndex.getOrDefault(viewer.uniqueId, 0)

        if (index < list.size) {
            val display = list[index]
            val loc = point.clone()
            Bukkit.getScheduler().runTask(plugin, Runnable { display.teleport(loc) })
        } else {
            val loc = point.clone()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val display = viewer.world.spawn(loc, ItemDisplay::class.java) { disp ->
                    disp.setItemStack(ItemStack(material))
                    val t = disp.transformation
                    disp.transformation = Transformation(t.translation, t.leftRotation, Vector3f(0.02f, 0.02f, 0.02f), t.rightRotation)
                }
                list.add(display)
            })
        }

        viewerDisplayIndex[viewer.uniqueId] = index + 1
    }

    override fun dispose(context: ActivityContext) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            viewerDisplays.values.flatten().forEach { it.remove() }
            viewerDisplays.clear()
            viewerDisplayIndex.clear()
        })
    }

    private fun fromYawPitch(yaw: Float, pitch: Float): Vector {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val xz = cos(pitchRad)
        return Vector(-sin(yawRad) * xz, -sin(pitchRad), cos(yawRad) * xz)
    }
}