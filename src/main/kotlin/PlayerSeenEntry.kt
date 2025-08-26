package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.entry.entries.EventEntry
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener

@Entry(
    "on_player_seen",
    "Trigger entries when a player enters an entity's vision",
    Colors.GREEN,
    "mdi:eye-check"
)
class PlayerSeenEntry(
    override val id: String = "",
    override val name: String = "",
    val entity: Ref<out EntityInstanceEntry> = emptyRef(),
    override val triggers: List<Ref<TriggerableEntry>> = emptyList()
) : EventEntry

class PlayerSeenEvent(
    val instance: Ref<out EntityInstanceEntry>,
    val player: Player
) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }
}

object PlayerSeenListener : Listener {
    @EventHandler
    fun onPlayerSeen(event: PlayerSeenEvent) {
        Query(PlayerSeenEntry::class)
            .findWhere { it.entity == event.instance }
            .forEach { entry ->
                entry.triggers.triggerEntriesFor(event.player) { }
            }
    }
}
