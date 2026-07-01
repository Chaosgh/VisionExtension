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
    private val delegate: EntityActivity<in C>,
) : EntityActivity<C> {
    private var state: PauseState = PauseState.Running

    override val currentPosition: PositionProperty
        get() =
            when (val current = state) {
                is PauseState.Paused -> current.idleActivity.currentPosition
                PauseState.Running -> delegate.currentPosition
            }

    override val currentProperties: List<EntityProperty>
        get() =
            when (val current = state) {
                is PauseState.Paused -> current.idleActivity.currentProperties
                PauseState.Running -> delegate.currentProperties
            }

    override fun initialize(
        context: C,
        position: PositionProperty,
    ) {
        disposeIdleIfPaused(context)
        state = PauseState.Running
        delegate.initialize(context, position)
    }

    override fun tick(context: C): TickResult {
        return when (val current = state) {
            is PauseState.Paused -> current.idleActivity.tick(context)
            PauseState.Running -> delegate.tick(context)
        }
    }

    fun pause(context: C) {
        if (state is PauseState.Paused) return

        val position = delegate.currentPosition
        delegate.dispose(context)
        val idleActivity = IdleActivity(position)
        idleActivity.initialize(context, position)
        state = PauseState.Paused(idleActivity, position)
    }

    fun resume(context: C) {
        val pausedState = state as? PauseState.Paused ?: return

        pausedState.idleActivity.dispose(context)
        state = PauseState.Running
        delegate.initialize(context, pausedState.resumePosition)
    }

    val isPaused: Boolean get() = state is PauseState.Paused

    override fun dispose(context: C) {
        when (val current = state) {
            is PauseState.Paused -> current.idleActivity.dispose(context)
            PauseState.Running -> delegate.dispose(context)
        }
        state = PauseState.Running
    }

    private fun disposeIdleIfPaused(context: C) {
        val pausedState = state as? PauseState.Paused ?: return
        pausedState.idleActivity.dispose(context)
    }

    private sealed interface PauseState {
        data object Running : PauseState

        data class Paused(
            val idleActivity: IdleActivity,
            val resumePosition: PositionProperty,
        ) : PauseState
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
    private val normalizedResumeDelayTicks = resumeDelayTicks.coerceAtLeast(0)

    private var state: PatrolVisionState = PatrolVisionState.Running

    override var currentPosition: PositionProperty
        get() = combinedPosition()
        set(_) { /* Position is managed by patrol activity */ }

    override val currentProperties: List<EntityProperty>
        get() {
            val patrolProps = patrol.currentProperties.filterNot { it is PositionProperty }
            val visionProps = vision.currentProperties.filterNot { it is PositionProperty }
            return listOf(currentPosition) + patrolProps + visionProps
        }

    override fun initialize(
        context: ActivityContext,
        position: PositionProperty,
    ) {
        patrol.initialize(context, position)
        vision.initialize(context, position)
    }

    override fun tick(context: ActivityContext): TickResult {
        val patrolPos = patrol.currentPosition
        vision.currentPosition =
            if (vision.isSeeingPlayer) {
                patrolPos.withRotation(vision.currentPosition.yaw, vision.currentPosition.pitch)
            } else {
                patrolPos
            }
        val visionResult = vision.tick(context)

        val patrolResult = tickPatrol(context)

        return if (patrolResult == TickResult.CONSUMED || visionResult == TickResult.CONSUMED) {
            TickResult.CONSUMED
        } else {
            TickResult.IGNORED
        }
    }

    override fun dispose(context: ActivityContext) {
        patrol.dispose(context)
        vision.dispose(context)
        state = PatrolVisionState.Running
    }

    private fun tickPatrol(context: ActivityContext): TickResult {
        if (stopWhenLooking && vision.isSeeingPlayer) {
            patrol.pause(context)
            state = PatrolVisionState.WaitingToResume(hiddenTicks = 0)
            return TickResult.CONSUMED
        }

        val current = state
        if (current is PatrolVisionState.WaitingToResume &&
            current.hiddenTicks < normalizedResumeDelayTicks
        ) {
            state = current.copy(hiddenTicks = current.hiddenTicks + 1)
            return TickResult.CONSUMED
        }

        state = PatrolVisionState.Running
        patrol.resume(context)
        return patrol.tick(context)
    }

    private fun combinedPosition(): PositionProperty {
        val patrolPosition = patrol.currentPosition
        return patrolPosition.withRotation(vision.currentPosition.yaw, vision.currentPosition.pitch)
    }

    private sealed interface PatrolVisionState {
        data object Running : PatrolVisionState

        data class WaitingToResume(
            val hiddenTicks: Int,
        ) : PatrolVisionState
    }
}
