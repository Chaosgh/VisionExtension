package de.chaos.activity

import com.typewritermc.engine.paper.entry.entity.ActivityContext
import com.typewritermc.engine.paper.entry.entity.EntityActivity
import com.typewritermc.engine.paper.entry.entity.IdleActivity
import com.typewritermc.engine.paper.entry.entity.PositionProperty
import com.typewritermc.engine.paper.entry.entity.TickResult
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import de.chaos.vision.VisionActivity

/**
 * Wrapper that adds pause/stop functionality to any EntityActivity.
 * Used to pause patrol activities when the NPC is looking at a player.
 */
class PausableActivity<C : ActivityContext>(
    private val delegate: EntityActivity<in C>
) : EntityActivity<C> {
    private var paused = false
    private var idleActivity: IdleActivity? = null
    private var delegateDisposedForPause = false
    private var needsDelegateInitialize = false
    private var delegateResumePosition: PositionProperty? = null

    override val currentPosition: PositionProperty
        get() = if (paused) idleActivity?.currentPosition ?: delegate.currentPosition else delegate.currentPosition

    override val currentProperties: List<EntityProperty>
        get() = if (paused) idleActivity?.currentProperties ?: delegate.currentProperties else delegate.currentProperties

    override fun initialize(context: C, position: PositionProperty) {
        delegate.initialize(context, position)
    }

    override fun tick(context: C): TickResult {
        return if (paused) {
            idleActivity?.tick(context) ?: TickResult.IGNORED
        } else {
            initializeDelegateIfNeeded(context)
            delegate.tick(context)
        }
    }

    fun pause(context: C) {
        if (!paused) {
            // Create idle activity at current position and dispose delegate
            val pos = delegate.currentPosition
            delegate.dispose(context)
            delegateDisposedForPause = true
            idleActivity = IdleActivity(pos)
            idleActivity?.initialize(context, pos)
            needsDelegateInitialize = true
            delegateResumePosition = pos
        }
        paused = true
    }

    fun resume(context: C) {
        idleActivity?.dispose(context)
        idleActivity = null
        paused = false
        initializeDelegateIfNeeded(context)
    }

    val isPaused: Boolean get() = paused

    override fun dispose(context: C) {
        if (!delegateDisposedForPause) {
            delegate.dispose(context)
        }
        idleActivity?.dispose(context)
        idleActivity = null
        paused = false
        needsDelegateInitialize = false
        delegateResumePosition = null
        delegateDisposedForPause = false
    }

    private fun initializeDelegateIfNeeded(context: C) {
        if (!needsDelegateInitialize) return

        delegate.initialize(context, delegateResumePosition ?: delegate.currentPosition)
        delegateDisposedForPause = false
        needsDelegateInitialize = false
        delegateResumePosition = null
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
    private var ticksSinceLastSeen: Int = Int.MAX_VALUE
    private var hasDetectionPauseStarted: Boolean = false

    override var currentPosition: PositionProperty
        get() = combinedPosition()
        set(_) { /* Position is managed by patrol activity */ }

    override val currentProperties: List<EntityProperty>
        get() {
            val patrolProps = patrol.currentProperties.filterNot { it is PositionProperty }
            val visionProps = vision.currentProperties.filterNot { it is PositionProperty }
            return listOf(currentPosition) + patrolProps + visionProps
        }

    override fun initialize(context: ActivityContext, position: PositionProperty) {
        patrol.initialize(context, position)
        vision.initialize(context, position)
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
                patrol.pause(context)
                ticksSinceLastSeen = 0
                hasDetectionPauseStarted = true
                TickResult.CONSUMED
            }
            hasDetectionPauseStarted && ticksSinceLastSeen < resumeDelayTicks -> {
                ticksSinceLastSeen++
                TickResult.CONSUMED
            }
            else -> {
                patrol.resume(context)
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

    private fun combinedPosition(): PositionProperty {
        val patrolPosition = patrol.currentPosition
        return patrolPosition.withRotation(vision.currentPosition.yaw, vision.currentPosition.pitch)
    }
}
