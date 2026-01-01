package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Default
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.Help
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.entry.entries.GenericEntityActivityEntry
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.isLookable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.util.Vector

@Entry("vision_activity", "Detect players inside an NPC's field of view", Colors.GREEN, "mdi:eye")
class VisionActivityEntry(
        override val id: String = "",
        override val name: String = "",
        @Help("Maximum distance in blocks the NPC can see") val radius: Double = 5.0,
        @Help("Field of view in degrees (max 170)") val fov: Double = 90.0,
        @Help("Shape of the vision area") val shape: VisionShape = VisionShape.CONE,
        @Help("Display item displays to visualize the vision area")
        val showDisplays: Boolean = true,
        @Help("Material used when visualizing vision") val material: Material = Material.BARRIER,
        @Help("Size of the item displays") val displaySize: Float = 0.02f,
        @Help("Rotate NPC to face players inside the vision area") val lookAtPlayer: Boolean = true,
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
        @Help("Always face a specific yaw/pitch while this activity runs")
        val forcedLookEnabled: Boolean = false,
        @Help("Forced yaw (degrees, 0-360)") val forcedLookYaw: Float = 0f,
        @Help("Forced pitch (degrees, -90 to 90)") val forcedLookPitch: Float = 0f,
) : GenericEntityActivityEntry {
        override fun create(
                context: ActivityContext,
                currentLocation: PositionProperty
        ): EntityActivity<in ActivityContext> {
                return VisionActivity(
                        radius = radius,
                        fovDegrees = fov,
                        shape = shape,
                        showDisplays = showDisplays,
                        material = material,
                        displaySize = displaySize,
                        lookAtPlayer = lookAtPlayer,
                        sneakProgressEnabled = sneakProgressEnabled,
                        walkProgressEnabled = walkProgressEnabled,
                        sneakMinDetectSeconds = sneakMinDetectSeconds,
                        sneakMaxDetectSeconds = sneakMaxDetectSeconds,
                        walkMinDetectSeconds = walkMinDetectSeconds,
                        walkMaxDetectSeconds = walkMaxDetectSeconds,
                        visionDecayPerSecond = visionDecayPerSecond,
                        showDetectionIndicator = showDetectionIndicator,
                        indicatorOffsetY = indicatorOffsetY,
                        forcedLookEnabled = forcedLookEnabled,
                        forcedYaw = forcedLookYaw,
                        forcedPitch = forcedLookPitch,
                        start = currentLocation
                )
        }
}

enum class VisionShape {
        CONE,
        LINE,
        SPHERE,
}

/**
 * Vision activity that detects players in the NPC's field of view. Uses ClientSideDisplayManager
 * for packet-based client-side displays.
 */
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
        private val forcedLookEnabled: Boolean,
        private val forcedYaw: Float,
        private val forcedPitch: Float,
        start: PositionProperty,
) : EntityActivity<ActivityContext> {

        // Pre-computed values for performance
        private val fov = fovDegrees.coerceIn(1.0, 170.0)
        private val halfFov = fov / 2.0
        private val forcedYawTarget = normalizeYaw(forcedYaw)
        private val forcedPitchTarget = forcedPitch.coerceIn(-89.9f, 89.9f)
        private val hasForcedLook = forcedLookEnabled
        private val decayPerTick = visionDecayPerSecond / 20.0

        override var currentPosition: PositionProperty = start

        // State tracking - use ConcurrentHashMap for thread-safety
        private val seenPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
        private val detectionProgress = ConcurrentHashMap<UUID, Double>()
        private val knownViewers = ConcurrentHashMap.newKeySet<UUID>()

        // Client-side display manager for packet-based displays
        private val displayManager: ClientSideDisplayManager by lazy {
                ClientSideDisplayManager(material, displaySize)
        }

        var isSeeingPlayer: Boolean = false
                private set

        override fun initialize(context: ActivityContext) {
                applyForcedRotation()
        }

        override fun tick(context: ActivityContext): TickResult {
                if (!context.isViewed) {
                        if (hasForcedLook) {
                                applyForcedRotation()
                        }
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

                // Pre-compute forward vector once
                val forward = fromYawPitch(currentPosition.yaw, currentPosition.pitch)
                val forwardNorm = forward.clone().normalize()

                // Choose a single viewer to drive rotation (nearest)
                val lookViewer =
                        viewers.minByOrNull { v ->
                                val d = v.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
                                d.lengthSquared()
                        }

                viewers.forEach { player ->
                        // Prepare display frame for this viewer
                        if (showDisplays) {
                                displayManager.prepareFrame(player)
                        }

                        val origin = Location(player.world, posX, eyeY, posZ)
                        val dir = player.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
                        val distance = dir.length()

                        // Check if player is inside vision area
                        val inside = checkInsideVision(forwardNorm, dir, distance)

                        // Check for line-of-sight blocking
                        val blocked =
                                origin.world?.rayTraceBlocks(
                                        origin,
                                        dir.clone().normalize(),
                                        distance
                                ) != null
                        val visible = inside && !blocked

                        if (!visible) {
                                handleNotVisible(player, posX, eyeY, posZ)
                        } else {
                                detectedAny =
                                        handleVisible(
                                                player,
                                                context,
                                                posX,
                                                eyeY,
                                                posZ,
                                                dir,
                                                distance,
                                                forwardNorm,
                                                lookViewer
                                        ) || detectedAny
                        }

                        // Draw vision displays for this viewer
                        if (showDisplays) {
                                drawVisionDisplays(origin, player)
                                displayManager.finishFrame(player)
                        }
                }

                // Apply forced rotation if configured and not looking at player
                if (hasForcedLook && (!lookAtPlayer || lookViewer == null || !detectedAny)) {
                        applyForcedRotation()
                }

                // Only mark as 'seeing' when detection has reached '!'
                isSeeingPlayer = detectedAny

                // Cleanup displays for viewers that are no longer present
                displayManager.cleanupMissingViewers(currentViewerIds)

                // Clean up detection progress for missing viewers
                detectionProgress.keys.removeAll { it !in currentViewerIds }

                return TickResult.IGNORED
        }

        private fun checkInsideVision(forwardNorm: Vector, dir: Vector, distance: Double): Boolean {
                return when (shape) {
                        VisionShape.CONE -> {
                                val dot =
                                        forwardNorm.dot(dir.clone().normalize()).coerceIn(-1.0, 1.0)
                                val angle = Math.toDegrees(acos(dot))
                                distance <= radius && angle <= halfFov
                        }
                        VisionShape.LINE -> {
                                val projection = forwardNorm.dot(dir)
                                if (projection !in 0.0..radius) false
                                else {
                                        val lateral =
                                                dir.clone()
                                                        .subtract(
                                                                forwardNorm
                                                                        .clone()
                                                                        .multiply(projection)
                                                        )
                                        lateral.length() <= halfFov
                                }
                        }
                        VisionShape.SPHERE -> distance <= radius
                }
        }

        private fun handleNotVisible(player: Player, posX: Double, eyeY: Double, posZ: Double) {
                seenPlayers.remove(player.uniqueId)

                val useProgress =
                        (player.isSneaking && sneakProgressEnabled) ||
                                (!player.isSneaking && walkProgressEnabled)
                if (useProgress) {
                        val prev = detectionProgress.getOrDefault(player.uniqueId, 0.0)
                        if (prev > 0.0) {
                                val next = (prev - decayPerTick).coerceAtLeast(0.0)
                                if (next <= 0.0) {
                                        detectionProgress.remove(player.uniqueId)
                                        if (showDetectionIndicator)
                                                displayManager.removeIndicator(player)
                                } else {
                                        detectionProgress[player.uniqueId] = next
                                        if (showDetectionIndicator)
                                                updateIndicatorDisplay(
                                                        player,
                                                        posX,
                                                        eyeY,
                                                        posZ,
                                                        next
                                                )
                                }
                        } else {
                                if (showDetectionIndicator) displayManager.removeIndicator(player)
                        }
                } else {
                        detectionProgress.remove(player.uniqueId)
                        if (showDetectionIndicator) displayManager.removeIndicator(player)
                }
        }

        private fun handleVisible(
                player: Player,
                context: ActivityContext,
                posX: Double,
                eyeY: Double,
                posZ: Double,
                dir: Vector,
                distance: Double,
                forwardNorm: Vector,
                lookViewer: Player?
        ): Boolean {
                val useProgress =
                        (player.isSneaking && sneakProgressEnabled) ||
                                (!player.isSneaking && walkProgressEnabled)

                if (useProgress) {
                        // Already detected - maintain indicator
                        if (seenPlayers.contains(player.uniqueId)) {
                                if (showDetectionIndicator)
                                        updateIndicatorDisplay(player, posX, eyeY, posZ, 1.0)
                                if (lookAtPlayer && player == lookViewer) {
                                        smoothLookAt(player, posX, eyeY, posZ)
                                }
                                return true
                        }

                        // Progressive detection
                        val isSneak = player.isSneaking
                        val tMinBase =
                                if (isSneak) max(0.05, sneakMinDetectSeconds)
                                else max(0.05, walkMinDetectSeconds)
                        val tMaxBase =
                                if (isSneak) max(tMinBase, sneakMaxDetectSeconds)
                                else max(tMinBase, walkMaxDetectSeconds)

                        // Distance factor: closer => faster (shorter time)
                        val distFactor = (distance / max(0.0001, radius)).coerceIn(0.0, 1.0)
                        var tSec = tMinBase + (tMaxBase - tMinBase) * distFactor

                        // Center factor: nearer to center of vision => faster
                        val centerFactor = calculateCenterFactor(forwardNorm, dir)
                        val angleMultiplier = (1.0 - 0.5 * centerFactor).coerceIn(0.5, 1.0)
                        tSec *= angleMultiplier

                        val inc = 1.0 / (tSec * 20.0)
                        val now =
                                (detectionProgress.getOrDefault(player.uniqueId, 0.0) + inc)
                                        .coerceAtMost(1.0)
                        detectionProgress[player.uniqueId] = now

                        if (showDetectionIndicator)
                                updateIndicatorDisplay(player, posX, eyeY, posZ, now)

                        if (now >= 1.0 && seenPlayers.add(player.uniqueId)) {
                                triggerPlayerSeen(context, player)
                                if (lookAtPlayer && player == lookViewer) {
                                        smoothLookAt(player, posX, eyeY, posZ)
                                }
                                return true
                        }
                        return false
                } else {
                        // Immediate detection
                        detectionProgress.remove(player.uniqueId)
                        if (showDetectionIndicator)
                                updateIndicatorDisplay(player, posX, eyeY, posZ, 1.0)

                        if (seenPlayers.add(player.uniqueId)) {
                                triggerPlayerSeen(context, player)
                        }

                        if (lookAtPlayer && player == lookViewer) {
                                smoothLookAt(player, posX, eyeY, posZ)
                        }
                        return true
                }
        }

        private fun calculateCenterFactor(forwardNorm: Vector, dir: Vector): Double {
                val dirNorm = dir.clone().normalize()
                return when (shape) {
                        VisionShape.CONE -> {
                                val dot = forwardNorm.dot(dirNorm).coerceIn(-1.0, 1.0)
                                val ang = Math.toDegrees(acos(dot))
                                (1.0 - (ang / halfFov)).coerceIn(0.0, 1.0)
                        }
                        VisionShape.LINE -> {
                                val projection = forwardNorm.dot(dir)
                                val lateral =
                                        dir.clone()
                                                .subtract(forwardNorm.clone().multiply(projection))
                                (1.0 - (lateral.length() / halfFov)).coerceIn(0.0, 1.0)
                        }
                        VisionShape.SPHERE -> 1.0
                }
        }

        private fun smoothLookAt(player: Player, posX: Double, eyeY: Double, posZ: Double) {
                val toPlayer = player.eyeLocation.toVector().subtract(Vector(posX, eyeY, posZ))
                val dirNorm = toPlayer.clone().normalize()
                val targetYaw = Math.toDegrees(atan2(-dirNorm.x, dirNorm.z)).toFloat()
                val targetPitch = Math.toDegrees(-asin(dirNorm.y)).toFloat()
                val smoothed =
                        smoothRotate(
                                currentPosition.yaw,
                                currentPosition.pitch,
                                targetYaw,
                                targetPitch
                        )
                currentPosition = currentPosition.withRotation(smoothed.first, smoothed.second)
        }

        private fun triggerPlayerSeen(context: ActivityContext, player: Player) {
                Query(PlayerSeenEntry::class)
                        .findWhere {
                                it.entity == context.instanceRef ||
                                        it.entity == emptyRef<EntityInstanceEntry>()
                        }
                        .forEach { entry -> entry.triggers.triggerEntriesFor(player) {} }

                val plugin = plugin
                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                Runnable {
                                        Bukkit.getPluginManager()
                                                .callEvent(
                                                        PlayerSeenEvent(context.instanceRef, player)
                                                )
                                }
                        )
        }

        private fun updateIndicatorDisplay(
                player: Player,
                x: Double,
                yEye: Double,
                z: Double,
                progress: Double
        ) {
                val base = Location(player.world, x, yEye + indicatorOffsetY, z)
                val clamped = progress.coerceIn(0.0, 1.0)

                // Build progress bar
                val width = 12
                val filled = (clamped * width).toInt().coerceIn(0, width)
                val percent = (clamped * 100).toInt().coerceIn(0, 100)
                val symbol = if (clamped >= 1.0) "!" else "?"

                val filledPart =
                        Component.text(
                                "\u2588".repeat(filled),
                                when {
                                        clamped >= 1.0 -> NamedTextColor.RED
                                        clamped >= 0.66 -> NamedTextColor.GOLD
                                        clamped >= 0.33 -> NamedTextColor.YELLOW
                                        else -> NamedTextColor.GREEN
                                }
                        )
                val emptyPart =
                        Component.text("\u2591".repeat(width - filled), NamedTextColor.DARK_GRAY)
                val bar =
                        Component.text(" [", NamedTextColor.GRAY)
                                .append(filledPart)
                                .append(emptyPart)
                                .append(Component.text("] ", NamedTextColor.GRAY))
                                .append(Component.text("$percent%", NamedTextColor.GRAY))
                val text = Component.text("$symbol ", NamedTextColor.YELLOW).append(bar)

                displayManager.updateIndicator(player, base, text)
        }

        private fun drawVisionDisplays(origin: Location, viewer: Player) {
                when (shape) {
                        VisionShape.CONE -> drawCone(origin, viewer)
                        VisionShape.LINE -> drawLine(origin, viewer)
                        VisionShape.SPHERE -> drawSphere(origin, viewer)
                }
        }

        private fun drawCone(origin: Location, viewer: Player) {
                val yaw = currentPosition.yaw
                val pitch = currentPosition.pitch
                val forward = fromYawPitch(yaw, pitch).normalize()
                val right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
                val up = right.clone().crossProduct(forward).normalize()

                val baseRadius = radius * tan(Math.toRadians(halfFov))
                val center = origin.clone().add(forward.clone().multiply(radius))

                // Draw circle at cone base
                val steps = (fov / 5).toInt().coerceAtLeast(8).coerceAtMost(72)
                for (i in 0 until steps) {
                        val angle = 2 * PI * i / steps
                        val point =
                                center.clone()
                                        .add(right.clone().multiply(cos(angle) * baseRadius))
                                        .add(up.clone().multiply(sin(angle) * baseRadius))
                        displayManager.updatePointDisplay(point, viewer)
                }

                // Draw edge lines from origin to cone base
                val edgeAngles = listOf(0.0, PI / 2, PI, 3 * PI / 2)
                edgeAngles.forEach { ang ->
                        val point =
                                center.clone()
                                        .add(right.clone().multiply(cos(ang) * baseRadius))
                                        .add(up.clone().multiply(sin(ang) * baseRadius))
                        displayManager.updateLineDisplay(origin, point, viewer)
                }
        }

        private fun drawLine(origin: Location, viewer: Player) {
                val yaw = currentPosition.yaw
                val pitch = currentPosition.pitch
                val forward = fromYawPitch(yaw, pitch).normalize()
                val right = forward.clone().crossProduct(Vector(0.0, 1.0, 0.0)).normalize()
                val up = right.clone().crossProduct(forward).normalize()

                val halfW = halfFov
                val halfH = halfFov
                val end = origin.clone().add(forward.clone().multiply(radius))

                val startCorners =
                        arrayOf(
                                origin.clone()
                                        .add(right.clone().multiply(halfW))
                                        .add(up.clone().multiply(halfH)),
                                origin.clone()
                                        .add(right.clone().multiply(halfW))
                                        .add(up.clone().multiply(-halfH)),
                                origin.clone()
                                        .add(right.clone().multiply(-halfW))
                                        .add(up.clone().multiply(halfH)),
                                origin.clone()
                                        .add(right.clone().multiply(-halfW))
                                        .add(up.clone().multiply(-halfH))
                        )
                val endCorners =
                        arrayOf(
                                end.clone()
                                        .add(right.clone().multiply(halfW))
                                        .add(up.clone().multiply(halfH)),
                                end.clone()
                                        .add(right.clone().multiply(halfW))
                                        .add(up.clone().multiply(-halfH)),
                                end.clone()
                                        .add(right.clone().multiply(-halfW))
                                        .add(up.clone().multiply(halfH)),
                                end.clone()
                                        .add(right.clone().multiply(-halfW))
                                        .add(up.clone().multiply(-halfH))
                        )

                for (i in 0 until 4) {
                        displayManager.updateLineDisplay(
                                startCorners[i],
                                startCorners[(i + 1) % 4],
                                viewer
                        )
                        displayManager.updateLineDisplay(
                                endCorners[i],
                                endCorners[(i + 1) % 4],
                                viewer
                        )
                        displayManager.updateLineDisplay(startCorners[i], endCorners[i], viewer)
                }
        }

        private fun drawSphere(origin: Location, viewer: Player) {
                val points = (radius * 8).toInt().coerceAtLeast(16).coerceAtMost(200)
                for (i in 0 until points) {
                        val angle = 2 * PI * i / points
                        displayManager.updatePointDisplay(
                                origin.clone().add(cos(angle) * radius, 0.0, sin(angle) * radius),
                                viewer
                        )
                        displayManager.updatePointDisplay(
                                origin.clone().add(0.0, cos(angle) * radius, sin(angle) * radius),
                                viewer
                        )
                        displayManager.updatePointDisplay(
                                origin.clone().add(cos(angle) * radius, sin(angle) * radius, 0.0),
                                viewer
                        )
                }
        }

        override fun dispose(context: ActivityContext) {
                displayManager.dispose()
                seenPlayers.clear()
                detectionProgress.clear()
                knownViewers.clear()
        }

        private fun applyForcedRotation() {
                if (!hasForcedLook) return
                if (currentPosition.yaw != forcedYawTarget ||
                                currentPosition.pitch != forcedPitchTarget
                ) {
                        currentPosition =
                                currentPosition.withRotation(forcedYawTarget, forcedPitchTarget)
                }
        }

        private fun normalizeYaw(yaw: Float): Float {
                var value = yaw % 360f
                if (value < 0f) value += 360f
                return value
        }

        private fun fromYawPitch(yaw: Float, pitch: Float): Vector {
                val yawRad = Math.toRadians(yaw.toDouble())
                val pitchRad = Math.toRadians(pitch.toDouble())
                val xz = cos(pitchRad)
                return Vector(-sin(yawRad) * xz, -sin(pitchRad), cos(yawRad) * xz)
        }

        private fun smoothRotate(
                currentYaw: Float,
                currentPitch: Float,
                targetYaw: Float,
                targetPitch: Float
        ): Pair<Float, Float> {
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

                val stepYaw =
                        when {
                                abs(dy) <= eps -> 0f
                                dy > 0 -> minOf(dy, maxYawStep)
                                else -> -minOf(-dy, maxYawStep)
                        }
                val stepPitch =
                        when {
                                abs(dp) <= eps -> 0f
                                dp > 0 -> minOf(dp, maxPitchStep)
                                else -> -minOf(-dp, maxPitchStep)
                        }

                var newYaw = currentYaw + stepYaw
                if (newYaw < 0f) newYaw = (newYaw % 360f + 360f) % 360f
                if (newYaw >= 360f) newYaw %= 360f

                var newPitch = currentPitch + stepPitch
                newPitch = newPitch.coerceIn(-89.9f, 89.9f)

                return newYaw to newPitch
        }
}
