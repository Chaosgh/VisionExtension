package de.chaos

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.extension.annotations.EntryListener
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.entry.entries.EventEntry
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

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
    override fun getHandlers(): HandlerList = handlers

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }
}

@EntryListener(PlayerSeenEntry::class)
fun onPlayerSeen(event: PlayerSeenEvent, query: Query<PlayerSeenEntry>) {
    query.findWhere { it.entity == event.instance }
        .forEach { entry ->
            entry.triggers.triggerEntriesFor(event.player) { }
        }
}

