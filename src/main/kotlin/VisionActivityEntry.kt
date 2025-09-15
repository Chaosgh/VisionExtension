package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.utils.isLookable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import org.bukkit.util.Transformation
import org.joml.Vector3f

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Display
import kotlin.math.max
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
    @Help("Size of the item displays")
    val displaySize: Float = 0.02f,
    @Help("Rotate NPC to face players inside the vision area")
    val lookAtPlayer: Boolean = true,
    @Help("Require progressive detection while the player is sneaking")
    @Default("true")
    val sneakProgressEnabled: Boolean = true,
    @Help("Apply progressive detection while walking (non-sneak)")
    @Default("false")
    val walkProgressEnabled: Boolean = false,
    @Help("Minimum seconds to detect a walking player at point-blank")
    @Default("0.3")
    val walkMinDetectSeconds: Double = 0.3,
    @Help("Maximum seconds to detect a walking player at max radius distance")
    @Default("1.5")
    val walkMaxDetectSeconds: Double = 1.5,
    @Help("Minimum seconds to detect a sneaking player at point-blank")
    @Default("0.6")
    val sneakMinDetectSeconds: Double = 0.6,
    @Help("Maximum seconds to detect a sneaking player at max radius distance")
    @Default("2.5")
    val sneakMaxDetectSeconds: Double = 2.5,
    @Help("Progress decay per second when not visible")
    @Default("1.2")
    val visionDecayPerSecond: Double = 1.2,
    @Help("Show a detection indicator above the NPC (two text displays)")
    @Default("true")
    val showDetectionIndicator: Boolean = true,
    @Help("Vertical offset for the detection indicator above head (blocks)")
    @Default("0.6")
    val indicatorOffsetY: Double = 0.6,
) : GenericEntityActivityEntry {
    override fun create(
        context: ActivityContext,
        currentLocation: PositionProperty
    ): EntityActivity<in ActivityContext> {
        return VisionActivity(
            radius,
            fov,
            shape,
            showDisplays,
            material,
            displaySize,
            lookAtPlayer,
            sneakProgressEnabled,
            walkProgressEnabled,
            sneakMinDetectSeconds,
            sneakMaxDetectSeconds,
            walkMinDetectSeconds,
            walkMaxDetectSeconds,
            visionDecayPerSecond,
            showDetectionIndicator,
            indicatorOffsetY,
            currentLocation
        )
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
    private val displaySize: Float,
    private val lookAtPlayer: Boolean,
    private val sneakProgressEnabled: Boolean,
    private val walkProgressEnabled: Boolean,
    private val sneakMinDetectSeconds: Double,
    private val sneakMaxDetectSeconds: Double,
    private val walkMinDetectSeconds: Double,
    private val walkMaxDetectSeconds: Double,
    private val visionDecayPerSecond: Double,
    private val showDetectionIndicator: Boolean,
    private val indicatorOffsetY: Double,
    start: PositionProperty,
) : EntityActivity<ActivityContext> {

    private val fov = fovDegrees.coerceIn(1.0, 170.0)

    override var currentPosition: PositionProperty = start
    private val seenPlayers = mutableSetOf<UUID>()
    private val viewerDisplays = mutableMapOf<UUID, MutableList<ItemDisplay>>()
    private val viewerDisplayIndex = mutableMapOf<UUID, Int>()
    private val knownViewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val detectionProgress = mutableMapOf<UUID, Double>()
    private val indicatorDisplays = mutableMapOf<UUID, TextDisplay>()
    private val pendingIndicators = mutableSetOf<UUID>()
    var isSeeingPlayer: Boolean = false
        private set

    override fun initialize(context: ActivityContext) {}

    override fun tick(context: ActivityContext): TickResult {
        if (!context.isViewed) {
            isSeeingPlayer = false
            return TickResult.IGNORED
        }

        var detectedAny = false

        val viewers = context.viewers.filter { it.isLookable }
        val currentViewerIds = viewers.map { it.uniqueId }.toSet()
        knownViewers.addAll(currentViewerIds)

        // Fixed NPC position for this tick (avoid per-viewer base jitter)
        val posX = currentPosition.x
        val posY = currentPosition.y
        val posZ = currentPosition.z
        val eyeY = posY + context.entityState.eyeHeight

        // Choose a single viewer to drive rotation (nearest)
        val lookViewer = viewers.minByOrNull { v ->
            val d = v.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
            d.lengthSquared()
        }

        viewers.forEach { player ->
            // Use the activity's rotation for vision math
            val currentYaw = currentPosition.yaw
            val currentPitch = currentPosition.pitch
            val forward = fromYawPitch(currentYaw, currentPitch)

            val origin = org.bukkit.Location(player.world, posX, eyeY, posZ)
            prepareDisplays(player)

            val dir = player.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
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
                    if (projection !in 0.0..radius) false
                    else {
                        val lateral = dir.clone().subtract(forwardNorm.clone().multiply(projection))
                        lateral.length() <= fov / 2
                    }
                }
                VisionShape.SPHERE -> distance <= radius
            }
            val blocked = origin.world.rayTraceBlocks(origin, dir.clone().normalize(), distance) != null
            val visible = inside && !blocked
            if (!visible) {
                // Not visible: remove 'seen' flag and apply optional decay
                seenPlayers.remove(player.uniqueId)

                val useProgress = (player.isSneaking && sneakProgressEnabled) || (!player.isSneaking && walkProgressEnabled)
                if (useProgress) {
                    val prev = detectionProgress.getOrDefault(player.uniqueId, 0.0)
                    if (prev > 0.0) {
                        val dec = visionDecayPerSecond / 20.0
                        val next = (prev - dec).coerceAtLeast(0.0)
                        if (next <= 0.0) {
                            detectionProgress.remove(player.uniqueId)
                            if (showDetectionIndicator) removeIndicator(player)
                        } else {
                            detectionProgress[player.uniqueId] = next
                            if (showDetectionIndicator) updateIndicator(context, player, posX, eyeY, posZ, next)
                        }
                    } else {
                        if (showDetectionIndicator) removeIndicator(player)
                    }
                } else {
                    detectionProgress.remove(player.uniqueId)
                    if (showDetectionIndicator) removeIndicator(player)
                }
            } else {
                val useProgress = (player.isSneaking && sneakProgressEnabled) || (!player.isSneaking && walkProgressEnabled)
                if (useProgress) {
                    // If already detected, maintain '!' and do not regress
                    if (seenPlayers.contains(player.uniqueId)) {
                        if (showDetectionIndicator) updateIndicator(context, player, posX, eyeY, posZ, 1.0)
                        if (lookAtPlayer && player == lookViewer) {
                            val toPlayer = player.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
                            val dirNorm = toPlayer.clone().normalize()
                            val targetYaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
                            val targetPitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()
                            val smoothed = smoothRotate(currentPosition.yaw, currentPosition.pitch, targetYaw, targetPitch)
                            currentPosition = currentPosition.withRotation(smoothed.first, smoothed.second)
                        }
                        detectedAny = true
                    } else {
                    val isSneak = player.isSneaking
                    val tMinBase = if (isSneak) max(0.05, sneakMinDetectSeconds) else max(0.05, walkMinDetectSeconds)
                    val tMaxBase = if (isSneak) max(tMinBase, sneakMaxDetectSeconds) else max(tMinBase, walkMaxDetectSeconds)

                    // Distance factor: closer => faster (shorter time)
                    val distFactor = (distance / max(0.0001, radius)).coerceIn(0.0, 1.0)
                    var tSec = tMinBase + (tMaxBase - tMinBase) * distFactor

                    // Face/center factor: nearer to center of vision => faster
                    val forwardNorm = fromYawPitch(currentPosition.yaw, currentPosition.pitch).normalize()
                    val dirNorm = dir.clone().normalize()

                    val centerFactor = when (shape) {
                        VisionShape.CONE -> {
                            val dot = forwardNorm.dot(dirNorm).coerceIn(-1.0, 1.0)
                            val ang = Math.toDegrees(acos(dot))
                            val half = (fov / 2.0).coerceAtLeast(0.0001)
                            (1.0 - (ang / half)).coerceIn(0.0, 1.0)
                        }
                        VisionShape.LINE -> {
                            val projection = forwardNorm.dot(dir)
                            val lateral = dir.clone().subtract(forwardNorm.clone().multiply(projection))
                            val halfW = (fov / 2.0).coerceAtLeast(0.0001)
                            (1.0 - (lateral.length() / halfW)).coerceIn(0.0, 1.0)
                        }
                        VisionShape.SPHERE -> 1.0
                    }
                    // Reduce time up to 50% when perfectly centered
                    val angleMultiplier = (1.0 - 0.5 * centerFactor).coerceIn(0.5, 1.0)
                    tSec *= angleMultiplier

                    val inc = 1.0 / (tSec * 20.0)
                    val now = (detectionProgress.getOrDefault(player.uniqueId, 0.0) + inc).coerceAtMost(1.0)
                    detectionProgress[player.uniqueId] = now
                    if (showDetectionIndicator) updateIndicator(context, player, posX, eyeY, posZ, now)
                    if (now >= 1.0 && seenPlayers.add(player.uniqueId)) {
                        Query(PlayerSeenEntry::class)
                            .findWhere { it.entity == context.instanceRef || it.entity == emptyRef<EntityInstanceEntry>() }
                            .forEach { entry ->
                                entry.triggers.triggerEntriesFor(player) { }
                            }
                        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter")
                        if (plugin != null) {
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                Bukkit.getPluginManager().callEvent(PlayerSeenEvent(context.instanceRef, player))
                            })
                        }
                        detectedAny = true
                        if (lookAtPlayer && player == lookViewer) {
                            val toPlayer = player.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
                            val dirNorm = toPlayer.clone().normalize()
                            val targetYaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
                            val targetPitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()
                            val smoothed = smoothRotate(currentPosition.yaw, currentPosition.pitch, targetYaw, targetPitch)
                            currentPosition = currentPosition.withRotation(smoothed.first, smoothed.second)
                        }
                    }
                    }
                } else {
                    // Immediate detection: show an exclamation indicator while visible
                    detectionProgress.remove(player.uniqueId)
                    if (showDetectionIndicator) updateIndicator(context, player, posX, eyeY, posZ, 1.0)
                    if (seenPlayers.add(player.uniqueId)) {
                        Query(PlayerSeenEntry::class)
                            .findWhere { it.entity == context.instanceRef || it.entity == emptyRef<EntityInstanceEntry>() }
                            .forEach { entry ->
                                entry.triggers.triggerEntriesFor(player) { }
                            }
                        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter")
                        if (plugin != null) {
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                Bukkit.getPluginManager().callEvent(PlayerSeenEvent(context.instanceRef, player))
                            })
                        }
                    }
                    detectedAny = true
                    if (lookAtPlayer && player == lookViewer) {
                        val toPlayer = player.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
                        val dirNorm = toPlayer.clone().normalize()
                        val targetYaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
                        val targetPitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()
                        val smoothed = smoothRotate(currentPosition.yaw, currentPosition.pitch, targetYaw, targetPitch)
                        currentPosition = currentPosition.withRotation(smoothed.first, smoothed.second)
                    }
                }
            }

            // After rotation updates, always draw vision for this viewer
            if (showDisplays) {
                spawnShapeDisplays(origin, currentPosition.yaw, currentPosition.pitch, player)
            }
            // Always cleanup to remove any leftover displays from previous ticks
            cleanupDisplays(player)
        }

        // Only mark as 'seeing' when detection has reached '!'
        isSeeingPlayer = detectedAny

        // Cleanup displays for viewers that are no longer present
        cleanupMissingViewers(currentViewerIds)

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

    private fun cleanupMissingViewers(currentViewerIds: Set<UUID>) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        // Determine all viewers we previously tracked (for displays or indicators) that are no longer present
        val tracked = HashSet<UUID>().apply {
            addAll(knownViewers)
            addAll(viewerDisplays.keys)
            addAll(indicatorDisplays.keys)
            addAll(pendingIndicators)
        }
        val toRemove = tracked.filter { it !in currentViewerIds }
        if (toRemove.isEmpty()) return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            toRemove.forEach { uuid ->
                viewerDisplays.remove(uuid)?.forEach { it.remove() }
                viewerDisplayIndex.remove(uuid)
                indicatorDisplays.remove(uuid)?.remove()
                pendingIndicators.remove(uuid)
                detectionProgress.remove(uuid)
                knownViewers.remove(uuid)
            }
        })
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
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        val list = viewerDisplays.computeIfAbsent(viewer.uniqueId) { mutableListOf() }
        val index = viewerDisplayIndex.getOrDefault(viewer.uniqueId, 0)

        val dir = end.clone().subtract(start).toVector()
        val length = dir.length()
        val mid = start.clone().add(dir.clone().multiply(0.5))
        val dirNorm = dir.clone().normalize()
        val yaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
        val pitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()

        if (index < list.size) {
            val display = list[index]
            val loc = mid.clone()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                display.teleport(loc)
                display.teleportDuration = 1
                display.interpolationDuration = 1
                display.setRotation(yaw, pitch)
                val t = display.transformation
                display.transformation = Transformation(
                    t.translation,
                    t.leftRotation,
                    Vector3f(displaySize, displaySize, length.toFloat()),
                    t.rightRotation
                )
                viewer.showEntity(plugin, display)
            })
        } else {
            val loc = mid.clone()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val display = viewer.world.spawn(loc, ItemDisplay::class.java) { disp ->
                    disp.setItemStack(ItemStack(material))
                    disp.setRotation(yaw, pitch)
                    disp.teleportDuration = 1
                    disp.interpolationDuration = 1
                    val t = disp.transformation
                    disp.transformation = Transformation(
                        t.translation,
                        t.leftRotation,
                        Vector3f(displaySize, displaySize, length.toFloat()),
                        t.rightRotation
                    )
                    disp.isVisibleByDefault = false
                }
                viewer.showEntity(plugin, display)
                list.add(display)
            })
        }

        viewerDisplayIndex[viewer.uniqueId] = index + 1
    }

    private fun spawnDisplay(point: org.bukkit.Location, viewer: Player) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        val list = viewerDisplays.computeIfAbsent(viewer.uniqueId) { mutableListOf() }
        val index = viewerDisplayIndex.getOrDefault(viewer.uniqueId, 0)

        if (index < list.size) {
            val display = list[index]
            val loc = point.clone()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                display.teleport(loc)
                display.teleportDuration = 1
                display.interpolationDuration = 1
                viewer.showEntity(plugin, display)
            })
        } else {
            val loc = point.clone()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val display = viewer.world.spawn(loc, ItemDisplay::class.java) { disp ->
                    disp.setItemStack(ItemStack(material))
                    val t = disp.transformation
                    disp.teleportDuration = 1
                    disp.interpolationDuration = 1
                    disp.transformation = Transformation(t.translation, t.leftRotation, Vector3f(displaySize, displaySize, displaySize), t.rightRotation)
                    disp.isVisibleByDefault = false
                }
                viewer.showEntity(plugin, display)
                list.add(display)
            })
        }

        viewerDisplayIndex[viewer.uniqueId] = index + 1
    }

    private fun updateIndicator(context: ActivityContext, viewer: Player, x: Double, yEye: Double, z: Double, progress: Double) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        val base = org.bukkit.Location(viewer.world, x, yEye + indicatorOffsetY, z)
        val uuid = viewer.uniqueId
        val existing = indicatorDisplays[uuid]

        val clamped = progress.coerceIn(0.0, 1.0)
        // Build a nicer bar: colored blocks and percent
        val width = 12
        val filled = (clamped * width).toInt().coerceIn(0, width)
        val percent = (clamped * 100).toInt().coerceIn(0, 100)
        val symbol = if (clamped >= 1.0) "!" else "?"

        val filledPart = Component.text("█".repeat(filled), when {
            clamped >= 1.0 -> NamedTextColor.RED
            clamped >= 0.66 -> NamedTextColor.GOLD
            clamped >= 0.33 -> NamedTextColor.YELLOW
            else -> NamedTextColor.GREEN
        })
        val emptyPart = Component.text("░".repeat(width - filled), NamedTextColor.DARK_GRAY)
        val bar = Component.text(" [", NamedTextColor.GRAY)
            .append(filledPart)
            .append(emptyPart)
            .append(Component.text("] ", NamedTextColor.GRAY))
            .append(Component.text("$percent%", NamedTextColor.GRAY))
        val text = Component.text("$symbol ", NamedTextColor.YELLOW).append(bar)

        if (existing == null) {
            if (uuid in pendingIndicators) return
            pendingIndicators.add(uuid)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!pendingIndicators.remove(uuid)) return@Runnable
                val indicator = viewer.world.spawn(base, TextDisplay::class.java) { td ->
                    td.text(text)
                    td.billboard = Display.Billboard.CENTER
                    td.teleportDuration = 1
                    td.interpolationDuration = 1
                    td.isShadowed = true
                    td.isSeeThrough = false
                }
                viewer.showEntity(plugin, indicator)
                indicatorDisplays[uuid] = indicator
            })
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                existing.teleport(base.clone())
                existing.text(text)
                viewer.showEntity(plugin, existing)
            })
        }
    }

    private fun removeIndicator(viewer: Player) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        val uuid = viewer.uniqueId
        pendingIndicators.remove(uuid)
        val disp = indicatorDisplays.remove(uuid) ?: return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            disp.vehicle?.removePassenger(disp)
            disp.remove()
        })
    }

    override fun dispose(context: ActivityContext) {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            viewerDisplays.values.flatten().forEach { it.remove() }
            viewerDisplays.clear()
            viewerDisplayIndex.clear()
            knownViewers.clear()
            indicatorDisplays.values.forEach { disp ->
                disp.remove()
            }
            indicatorDisplays.clear()
            pendingIndicators.clear()
            detectionProgress.clear()
        })
    }

    private fun fromYawPitch(yaw: Float, pitch: Float): Vector {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val xz = cos(pitchRad)
        return Vector(-sin(yawRad) * xz, -sin(pitchRad), cos(yawRad) * xz)
    }

    private fun smoothRotate(currentYaw: Float, currentPitch: Float, targetYaw: Float, targetPitch: Float): Pair<Float, Float> {
        val maxYawStep = 12f
        val maxPitchStep = 12f
        val eps = 0.2f

        fun wrapDelta(a: Float, b: Float): Float {
            var d = b - a
            while (d <= -180f) d += 360f
            while (d > 180f) d -= 360f
            return d
        }

        val dy = wrapDelta(currentYaw, targetYaw)
        val dp = targetPitch - currentPitch

        val stepYaw = when {
            kotlin.math.abs(dy) <= eps -> 0f
            dy > 0 -> kotlin.math.min(dy, maxYawStep)
            else -> -kotlin.math.min(-dy, maxYawStep)
        }
        val stepPitch = when {
            kotlin.math.abs(dp) <= eps -> 0f
            dp > 0 -> kotlin.math.min(dp, maxPitchStep)
            else -> -kotlin.math.min(-dp, maxPitchStep)
        }

        var newYaw = currentYaw + stepYaw
        // normalize yaw to [0, 360)
        if (newYaw < 0f) newYaw = (newYaw % 360f + 360f) % 360f
        if (newYaw >= 360f) newYaw %= 360f

        var newPitch = currentPitch + stepPitch
        newPitch = newPitch.coerceIn(-89.9f, 89.9f)

        return newYaw to newPitch
    }
}



