package de.chaos.vision

import de.chaos.display.ClientSideDisplayManager
import de.chaos.display.VisionDisplayManager
import de.chaos.event.VisionEventDispatcher

import com.typewritermc.engine.paper.entry.entity.ActivityContext
import com.typewritermc.engine.paper.entry.entity.EntityActivity
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import com.typewritermc.engine.paper.entry.entity.TickResult
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import com.typewritermc.engine.paper.utils.isLookable
import java.util.UUID
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector

class VisionActivity private constructor(
    private val config: NormalizedVisionConfig,
    start: PositionProperty,
    dependencies: VisionActivityDependencies,
) : EntityActivity<ActivityContext> {
    constructor(
        rawConfig: VisionConfig,
        start: PositionProperty,
    ) : this(rawConfig.normalized(), start)

    private constructor(
        config: NormalizedVisionConfig,
        start: PositionProperty,
    ) : this(config, start, defaultVisionActivityDependencies(config))

    internal constructor(
        rawConfig: VisionConfig,
        start: PositionProperty,
        dependencies: VisionActivityDependencies,
    ) : this(rawConfig.normalized(), start, dependencies)

    override var currentPosition: PositionProperty = start

    override val currentProperties: List<EntityProperty>
        get() = listOf(currentPosition)

    private val displayManager = dependencies.displayManager
    private val sensor = dependencies.sensor
    private val tracker = dependencies.tracker
    private val targetSelector = dependencies.targetSelector
    private val debugRenderer = dependencies.debugRenderer

    private val showDisplays = config.showDisplays
    private val displayUpdateIntervalTicks = config.displayUpdateIntervalTicks
    private val lookAtPlayer = config.lookAtPlayer
    private val hasForcedLook = config.forcedLookEnabled
    private val forcedYawTarget = config.forcedYaw
    private val forcedPitchTarget = config.forcedPitch

    private val direction = Vector()
    private val normalizedDirection = Vector()

    private var tickIndex = 0L

    var isSeeingPlayer: Boolean = false
        private set

    override fun initialize(context: ActivityContext, position: PositionProperty) {
        currentPosition = position
        applyForcedRotation()
    }

    override fun tick(context: ActivityContext): TickResult {
        tickIndex++

        if (!context.isViewed) {
            cleanupMissingViewers(context, emptySet())
            if (hasForcedLook) {
                applyForcedRotation()
            }
            isSeeingPlayer = false
            return TickResult.IGNORED
        }

        applyRotationBeforeScan()

        val viewers = context.viewers.filter { it.isLookable }
        val currentViewerIds = viewers.mapTo(HashSet(viewers.size)) { it.uniqueId }
        val updateDisplays = showDisplays && tickIndex % displayUpdateIntervalTicks == 0L

        val posX = currentPosition.x
        val posY = currentPosition.y
        val posZ = currentPosition.z
        val eyeY = posY + context.entityState.eyeHeight
        val forward = VisionMath.fromYawPitch(currentPosition.yaw, currentPosition.pitch)

        var hasDetectedPlayer = false
        targetSelector.reset()

        for (player in viewers) {
            hasDetectedPlayer =
                processViewer(player, context, posX, eyeY, posZ, forward, updateDisplays) || hasDetectedPlayer
        }

        applyRotationFromTarget(posX, eyeY, posZ)
        isSeeingPlayer = hasDetectedPlayer
        cleanupMissingViewers(context, currentViewerIds)

        return TickResult.IGNORED
    }

    private fun processViewer(
        player: Player,
        context: ActivityContext,
        posX: Double,
        eyeY: Double,
        posZ: Double,
        forward: Vector,
        updateDisplays: Boolean,
    ): Boolean {
        val state = tracker.stateFor(player)
        val origin = Location(player.world, posX, eyeY, posZ)
        val check = checkPlayer(player, origin, forward, state)

        if (updateDisplays) {
            displayManager.prepareFrame(player)
        }

        val detected =
            if (check.visible) {
                handleVisiblePlayer(player, context, state, posX, eyeY, posZ, forward, check)
            } else {
                tracker.handleHidden(player, context, state, posX, eyeY, posZ)
            }
        targetSelector.consider(detected, player, check.distanceSquared)

        if (updateDisplays) {
            debugRenderer.render(origin, player, currentPosition)
            displayManager.finishFrame(player)
        }

        return detected
    }

    private fun handleVisiblePlayer(
        player: Player,
        context: ActivityContext,
        state: DetectionState,
        posX: Double,
        eyeY: Double,
        posZ: Double,
        forward: Vector,
        check: VisionCheckResult,
    ): Boolean {
        val centerFactor = sensor.centerFactor(forward, normalizedDirection, check.distance)
        return tracker.handleVisible(
            player,
            context,
            state,
            posX,
            eyeY,
            posZ,
            check.distance,
            centerFactor
        )
    }

    override fun dispose(context: ActivityContext) {
        cleanupMissingViewers(context, emptySet())
        displayManager.dispose()
        tracker.clear()
        isSeeingPlayer = false
    }

    private fun checkPlayer(
        player: Player,
        origin: Location,
        forward: Vector,
        state: DetectionState
    ): VisionCheckResult {
        val playerEye = player.eyeLocation
        direction.setX(playerEye.x - origin.x)
        direction.setY(playerEye.y - origin.y)
        direction.setZ(playerEye.z - origin.z)

        return sensor.check(origin, forward, direction, normalizedDirection, state, tickIndex)
    }

    private fun applyRotationFromTarget(posX: Double, eyeY: Double, posZ: Double) {
        val target = targetSelector.player
        if (lookAtPlayer && target != null) {
            smoothLookAt(target, posX, eyeY, posZ)
        } else if (hasForcedLook) {
            applyForcedRotation()
        }
    }

    private fun smoothLookAt(player: Player, posX: Double, eyeY: Double, posZ: Double) {
        val targetRotation =
            VisionMath.yawPitchTo(
                player,
                Vector(posX, eyeY, posZ),
                currentPosition.yaw,
                currentPosition.pitch
            )
        val smoothed =
            VisionMath.smoothRotate(
                currentPosition.yaw,
                currentPosition.pitch,
                targetRotation.first,
                targetRotation.second
            )
        currentPosition = currentPosition.withRotation(smoothed.first, smoothed.second)
    }

    private fun cleanupMissingViewers(context: ActivityContext, currentViewerIds: Set<UUID>) {
        displayManager.cleanupMissingViewers(currentViewerIds)
        tracker.cleanupMissingPlayers(context, currentViewerIds)
    }

    private fun applyForcedRotation() {
        if (!hasForcedLook) return
        if (currentPosition.yaw != forcedYawTarget || currentPosition.pitch != forcedPitchTarget) {
            currentPosition = currentPosition.withRotation(forcedYawTarget, forcedPitchTarget)
        }
    }

    private fun applyRotationBeforeScan() {
        if (lookAtPlayer && isSeeingPlayer) return
        applyForcedRotation()
    }
}

internal data class VisionActivityDependencies(
    val displayManager: VisionDisplayManager,
    val sensor: VisionSensor,
    val tracker: DetectionTracker,
    val targetSelector: VisionTargetSelector,
    val debugRenderer: VisionDebugRenderer,
)

internal fun defaultVisionActivityDependencies(config: NormalizedVisionConfig): VisionActivityDependencies {
    val displayManager = ClientSideDisplayManager(config.material, config.displaySize)
    val events = VisionEventDispatcher()
    val sensor = VisionSensor(config)
    val tracker = DetectionTracker(config, displayManager, events)
    val targetSelector = VisionTargetSelector()
    val debugRenderer = VisionDebugRenderer(config, displayManager)

    return VisionActivityDependencies(
        displayManager = displayManager,
        sensor = sensor,
        tracker = tracker,
        targetSelector = targetSelector,
        debugRenderer = debugRenderer,
    )
}
