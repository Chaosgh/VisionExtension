package de.chaos.event

import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.emptyRef
import com.typewritermc.engine.paper.entry.entity.ActivityContext
import com.typewritermc.engine.paper.entry.entries.EntityInstanceEntry
import com.typewritermc.engine.paper.entry.triggerEntriesFor
import com.typewritermc.engine.paper.plugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player

internal interface VisionEventSink {
    fun playerSeen(context: ActivityContext, player: Player)
    fun playerLost(context: ActivityContext, player: Player)
}

internal class VisionEventDispatcher : VisionEventSink {
    override fun playerSeen(context: ActivityContext, player: Player) {
        Query(PlayerSeenEntry::class)
            .findWhere {
                it.entity == context.instanceRef ||
                    it.entity == emptyRef<EntityInstanceEntry>()
            }
            .forEach { entry -> entry.triggers.triggerEntriesFor(player) {} }

        Bukkit.getScheduler()
            .runTask(
                plugin,
                Runnable {
                    Bukkit.getPluginManager()
                        .callEvent(PlayerSeenEvent(context.instanceRef, player))
                }
            )
    }

    override fun playerLost(context: ActivityContext, player: Player) {
        Query(PlayerLostEntry::class)
            .findWhere {
                it.entity == context.instanceRef ||
                    it.entity == emptyRef<EntityInstanceEntry>()
            }
            .forEach { entry -> entry.triggers.triggerEntriesFor(player) {} }

        Bukkit.getScheduler()
            .runTask(
                plugin,
                Runnable {
                    Bukkit.getPluginManager()
                        .callEvent(PlayerLostEvent(context.instanceRef, player))
                }
            )
    }
}
