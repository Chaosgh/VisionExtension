package com.typewritermc.entity.entries.activity

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.Entry
import com.typewritermc.engine.paper.entry.entity.*
import com.typewritermc.engine.paper.entry.entries.EntityProperty

/**
 * Minimal stub of RandomPatrolActivity for compilation in the Vision extension.
 * The real implementation is provided by the Entity extension at runtime.
 */
class RandomPatrolActivity(
    roadNetwork: Ref<out Entry>,
    radiusSquared: Double,
    startLocation: PositionProperty,
) : EntityActivity<ActivityContext> {
    override var currentPosition: PositionProperty = startLocation
    override fun initialize(context: ActivityContext) {}
    override fun tick(context: ActivityContext): TickResult = TickResult.IGNORED
    override fun dispose(context: ActivityContext) {}
    override val currentProperties: List<EntityProperty> = emptyList()
}
