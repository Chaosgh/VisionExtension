
package de.chaos

import com.typewritermc.core.extension.Initializable
import com.typewritermc.core.extension.annotations.Singleton
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList

@Singleton
object Initializer : Initializable {

    private val listeners = listOf(PlayerSeenListener)

    override suspend fun initialize() {
        val plugin = Bukkit.getPluginManager().getPlugin("Typewriter") ?: return
        listeners.forEach { Bukkit.getPluginManager().registerEvents(it, plugin) }
    }

    override suspend fun shutdown() {
        listeners.forEach { HandlerList.unregisterAll(it) }
    }
}
