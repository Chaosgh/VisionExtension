package de.chaos.vision

import de.chaos.display.DetectionDisplaySink
import de.chaos.event.VisionEventSink

import java.util.UUID
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import com.typewritermc.engine.paper.entry.entity.ActivityContext

internal data class DetectionState(
    var progress: Double = 0.0,
    var seen: Boolean = false,
    var hiddenTicks: Int = 0,
    var lastRaycastTick: Long = Long.MIN_VALUE / 2,
    var lastLineOfSight: Boolean = false,
    var lastSeenTarget: DetectedPlayerRef? = null,
    var cachedProgressKey: IndicatorCacheKey? = null,
    var cachedProgressText: Component? = null,
) {
    var lastSeenPlayer: Player?
        get() = lastSeenTarget?.player
        set(value) {
            lastSeenTarget = value?.let(DetectedPlayerRef::from)
        }
}

internal class DetectionTracker(
    private val config: NormalizedVisionConfig,
    private val displayManager: DetectionDisplaySink,
    private val events: VisionEventSink,
) {
    constructor(
        config: VisionConfig,
        displayManager: DetectionDisplaySink,
        events: VisionEventSink,
    ) : this(config.normalized(), displayManager, events)

    private val progressCalculator = DetectionProgressCalculator(config)

    private val states = HashMap<UUID, DetectionState>()

    fun stateFor(player: Player): DetectionState {
        return states.computeIfAbsent(player.uniqueId) { DetectionState() }
    }

    fun handleHidden(
        player: Player,
        context: ActivityContext,
        state: DetectionState,
        x: Double,
        eyeY: Double,
        z: Double
    ): Boolean {
        if (state.seen && state.hiddenTicks < config.lostDelayTicks) {
            state.hiddenTicks++
            if (config.showDetectionIndicator) {
                updateIndicator(player, x, eyeY, z, state.progress.coerceAtLeast(1.0), state)
            }
            return true
        }

        if (state.seen) {
            state.seen = false
            triggerLost(context, state, player)
        }

        if (progressCalculator.usesProgressDetection(player)) {
            if (state.progress > 0.0) {
                state.progress = progressCalculator.hiddenProgress(state.progress)
                if (state.progress <= 0.0) {
                    removeIndicator(player, state)
                } else if (config.showDetectionIndicator) {
                    updateIndicator(player, x, eyeY, z, state.progress, state)
                }
            } else {
                removeIndicator(player, state)
            }
        } else {
            state.progress = 0.0
            removeIndicator(player, state)
        }

        return false
    }

    fun handleVisible(
        player: Player,
        context: ActivityContext,
        state: DetectionState,
        x: Double,
        eyeY: Double,
        z: Double,
        distance: Double,
        centerFactor: Double
    ): Boolean {
        state.hiddenTicks = 0
        state.lastSeenTarget = DetectedPlayerRef.from(player)

        if (progressCalculator.usesProgressDetection(player)) {
            if (state.seen) {
                state.progress = 1.0
                if (config.showDetectionIndicator) {
                    updateIndicator(player, x, eyeY, z, state.progress, state)
                }
                return true
            }

            state.progress = progressCalculator.visibleProgress(player, state.progress, distance, centerFactor)
            if (config.showDetectionIndicator) {
                updateIndicator(player, x, eyeY, z, state.progress, state)
            }

            if (state.progress >= 1.0 && !state.seen) {
                state.seen = true
                events.playerSeen(context, player)
                return true
            }
            return false
        }

        state.progress = 1.0
        if (config.showDetectionIndicator) {
            updateIndicator(player, x, eyeY, z, state.progress, state)
        }
        if (!state.seen) {
            state.seen = true
            events.playerSeen(context, player)
        }
        return true
    }

    fun cleanupMissingPlayers(context: ActivityContext, currentViewerIds: Set<UUID>) {
        val missingPlayerIds = states.keys.filter { it !in currentViewerIds }
        missingPlayerIds.forEach { uuid ->
            val state = states.remove(uuid) ?: return@forEach
            if (state.seen) {
                triggerLost(context, state, state.lastSeenTarget?.player ?: Bukkit.getPlayer(uuid))
            }
        }
    }

    fun clear() {
        states.clear()
    }

    private fun updateIndicator(
        player: Player,
        x: Double,
        eyeY: Double,
        z: Double,
        progress: Double,
        state: DetectionState
    ) {
        val base = Location(player.world, x, eyeY + config.indicatorOffsetY, z)
        val cacheKey = DetectionIndicatorFormatter.cacheKey(progress)

        val cachedText = state.cachedProgressText
        val text = if (cachedText != null && state.cachedProgressKey == cacheKey) {
            cachedText
        } else {
            val newText = DetectionIndicatorFormatter.text(cacheKey)
            state.cachedProgressKey = cacheKey
            state.cachedProgressText = newText
            newText
        }

        displayManager.updateIndicator(player, base, text)
    }

    private fun removeIndicator(player: Player, state: DetectionState) {
        if (config.showDetectionIndicator) {
            displayManager.removeIndicator(player)
        }
        state.cachedProgressKey = null
        state.cachedProgressText = null
    }

    private fun triggerLost(context: ActivityContext, state: DetectionState, currentPlayer: Player?) {
        val player = currentPlayer ?: state.lastSeenTarget?.player ?: return
        events.playerLost(context, player)
        state.lastSeenTarget = null
    }
}
