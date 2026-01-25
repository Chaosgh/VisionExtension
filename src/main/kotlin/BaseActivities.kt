package de.chaos

import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.EntityProperty

/**
 * Wrapper that adds pause/stop functionality to any EntityActivity.
 * Used to pause patrol activities when the NPC is looking at a player.
 */
class PausableActivity<C : ActivityContext>(
    private val delegate: EntityActivity<C>
) : EntityActivity<C> {
    private var paused = false
    private var idleActivity: IdleActivity? = null
    private var needsReinit = false

    override val currentPosition: PositionProperty
        get() = if (paused) idleActivity?.currentPosition ?: delegate.currentPosition else delegate.currentPosition

    override val currentProperties: List<EntityProperty>
        get() = if (paused) idleActivity?.currentProperties ?: delegate.currentProperties else delegate.currentProperties

    override fun initialize(context: C) {
        delegate.initialize(context)
    }

    override fun tick(context: C): TickResult {
        return if (paused) {
            // When paused, tick the idle activity instead
            idleActivity?.tick(context) ?: TickResult.IGNORED
        } else {
            // Reinitialize if we were previously paused and disposed
            if (needsReinit) {
                delegate.initialize(context)
                needsReinit = false
            }
            val result = delegate.tick(context)
            result
        }
    }

    fun pause(context: C) {
        if (!paused) {
            // Create idle activity at current position and dispose delegate
            val pos = delegate.currentPosition
            delegate.dispose(context)
            idleActivity = IdleActivity(pos)
            idleActivity?.initialize(context)
            needsReinit = true
        }
        paused = true
    }

    fun resume() {
        paused = false
        idleActivity = null
    }

    val isPaused: Boolean get() = paused

    override fun dispose(context: C) {
        delegate.dispose(context)
        idleActivity = null
    }
}

/**
 * Combined patrol + vision activity. Handles the coordination between
 * patrolling and vision detection. Works with any EntityActivity as patrol.
 */
class PatrolVisionActivity(
    private val patrol: PausableActivity<ActivityContext>,
    private val vision: VisionActivity,
    private val stopWhenLooking: Boolean,
    private val resumeDelayTicks: Int = 10,
) : EntityActivity<ActivityContext> {
    private var ticksSinceLastSeen: Int = Int.MAX_VALUE  // Start high so patrol runs immediately
    private var hasEverSeenPlayer: Boolean = false

    override var currentPosition: PositionProperty
        get() = if (vision.isSeeingPlayer) {
            patrol.currentPosition.withRotation(vision.currentPosition.yaw, vision.currentPosition.pitch)
        } else {
            patrol.currentPosition
        }
        set(_) { /* Position is managed by patrol activity */ }

    override val currentProperties: List<EntityProperty>
        get() {
            return if (vision.isSeeingPlayer) {
                // When seeing player: use combined position (patrol location + vision rotation)
                val visionProps = vision.currentProperties.filterNot { it is PositionProperty }
                listOf(currentPosition) + visionProps
            } else {
                // Normal: use patrol properties + vision non-position properties
                val visionProps = vision.currentProperties.filterNot { it is PositionProperty }
                patrol.currentProperties + visionProps
            }
        }

    override fun initialize(context: ActivityContext) {
        patrol.initialize(context)
        vision.initialize(context)
    }

    override fun tick(context: ActivityContext): TickResult {
        val patrolPos = patrol.currentPosition
        vision.currentPosition = if (vision.isSeeingPlayer) {
            patrolPos.withRotation(vision.currentPosition.yaw, vision.currentPosition.pitch)
        } else {
            patrolPos
        }
        val visionResult = vision.tick(context)

        val patrolResult = when {
            stopWhenLooking && vision.isSeeingPlayer -> {
                // Momentan Spieler gesehen - Patrol pausieren
                patrol.pause(context)
                ticksSinceLastSeen = 0
                hasEverSeenPlayer = true
                TickResult.CONSUMED
            }
            hasEverSeenPlayer && ticksSinceLastSeen < resumeDelayTicks -> {
                // Spieler kürzlich verloren - warte kurz bevor Patrol weiterläuft
                ticksSinceLastSeen++
                TickResult.CONSUMED
            }
            else -> {
                // Nichts gesehen oder Delay vorbei - Patrol fortsetzen
                patrol.resume()
                patrol.tick(context)
            }
        }

        return if (patrolResult == TickResult.CONSUMED || visionResult == TickResult.CONSUMED) {
            TickResult.CONSUMED
        } else {
            TickResult.IGNORED
        }
    }

    override fun dispose(context: ActivityContext) {
        patrol.dispose(context)
        vision.dispose(context)
    }
}
