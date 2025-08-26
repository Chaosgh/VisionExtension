package de.chaos

import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.story.entry.Entry
import com.typewritermc.core.story.interaction.InteractionContext
import org.bukkit.entity.Player

/**
 * Script entry that is executed when the owning NPC sees a player.
 * The player that was seen is provided as the interaction context.
 */
@Entry("on entity see")
class OnEntitySeeEntry : Entry<Unit> {
    override suspend fun execute(context: InteractionContext<Unit>) {
        // No-op placeholder. The actual actions are supplied by the script body.
    }
}
