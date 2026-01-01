package de.chaos

import com.typewritermc.core.entries.Ref
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.EntityProperty
import com.typewritermc.entity.entries.activity.NavigationActivity
import com.typewritermc.roadnetwork.RoadNetwork
import com.typewritermc.roadnetwork.RoadNetworkEntry
import com.typewritermc.roadnetwork.RoadNetworkManager
import com.typewritermc.roadnetwork.gps.PointToPointGPS
import org.koin.java.KoinJavaComponent

/**
 * Abstract base class for patrol activities that use a road network. Provides common functionality
 * for navigation, stopping, and lifecycle management.
 */
abstract class BasePatrolActivity(
        protected val roadNetwork: Ref<RoadNetworkEntry>,
        startLocation: PositionProperty,
) : EntityActivity<ActivityContext> {
    protected var network: RoadNetwork? = null
    protected var activity: EntityActivity<in ActivityContext> = IdleActivity(startLocation)

    override val currentPosition: PositionProperty
        get() = activity.currentPosition

    override val currentProperties: List<EntityProperty>
        get() = activity.currentProperties

    /**
     * Template method to refresh the navigation activity. Subclasses must implement this to define
     * how the next destination is chosen.
     */
    abstract fun refreshActivity(context: ActivityContext, network: RoadNetwork): TickResult

    override fun initialize(context: ActivityContext) = setup(context)

    protected fun setup(context: ActivityContext) {
        network =
                KoinJavaComponent.get<RoadNetworkManager>(RoadNetworkManager::class.java)
                        .getNetworkOrNull(roadNetwork)
                        ?: return

        network?.let { refreshActivity(context, it) }
    }

    override fun tick(context: ActivityContext): TickResult {
        val net = network
        if (net == null) {
            setup(context)
            return TickResult.CONSUMED
        }

        val result = activity.tick(context)
        return if (result == TickResult.IGNORED) {
            refreshActivity(context, net)
        } else {
            TickResult.CONSUMED
        }
    }

    fun stop(context: ActivityContext) {
        if (activity !is IdleActivity) {
            val oldPosition = currentPosition
            activity.dispose(context)
            activity = IdleActivity(oldPosition)
        }
    }

    override fun dispose(context: ActivityContext) {
        val oldPosition = currentPosition
        activity.dispose(context)
        activity = IdleActivity(oldPosition)
    }

    /** Helper to create a NavigationActivity to the given destination. */
    protected fun navigateTo(
            context: ActivityContext,
            destination: com.typewritermc.core.utils.point.Position
    ): TickResult {
        activity.dispose(context)
        activity =
                NavigationActivity(
                        PointToPointGPS(roadNetwork, { currentPosition.toPosition() }) {
                            destination
                        },
                        currentPosition
                )
        activity.initialize(context)
        return TickResult.CONSUMED
    }
}

/**
 * Abstract base class for combined patrol + vision activities. Handles the coordination between
 * patrolling and vision detection.
 */
abstract class BasePatrolVisionActivity<P : BasePatrolActivity>(
        protected val patrol: P,
        protected val vision: VisionActivity,
        protected val stopWhenLooking: Boolean,
        protected val resumeDelayTicks: Int = 10,
) : EntityActivity<ActivityContext> {
    private var unseenTicks: Int = 0

    override var currentPosition: PositionProperty
        get() = patrol.currentPosition
        set(_) {
            /* Position is managed by patrol activity */
        }

    override val currentProperties: List<EntityProperty>
        get() =
                if (vision.isSeeingPlayer) {
                    val patrolProps = patrol.currentProperties.filterNot { it is PositionProperty }
                    patrolProps + vision.currentProperties
                } else {
                    val visionProps = vision.currentProperties.filterNot { it is PositionProperty }
                    patrol.currentProperties + visionProps
                }

    override fun initialize(context: ActivityContext) {
        patrol.initialize(context)
        vision.initialize(context)
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

        val patrolResult =
                if (stopWhenLooking && vision.isSeeingPlayer) {
                    patrol.stop(context)
                    unseenTicks = 0
                    TickResult.IGNORED
                } else {
                    if (unseenTicks < resumeDelayTicks) {
                        unseenTicks++
                        patrol.stop(context)
                        TickResult.IGNORED
                    } else {
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
