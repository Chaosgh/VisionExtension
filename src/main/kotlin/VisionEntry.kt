package de.chaos

import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.npc.Npc
import com.typewritermc.core.story.interaction.InteractionContext
import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * Entry that grants an NPC a configurable field of view. The field of view is
 * represented by a raycast which is blocked by solid blocks. Optionally the
 * raycast can be visualised using particles.
 */
@Entry("vision")
class VisionEntry : com.typewritermc.core.story.entry.Entry<VisionEntry.Config> {
    data class Config(
        val shape: Shape = Shape.CONE,
        val fov: Double = 90.0,
        val radius: Double = 10.0,
        val showParticles: Boolean = false
    )

    enum class Shape { CONE, SPHERE }

    override suspend fun execute(context: InteractionContext<*>, config: Config) {
        val npc = context.npc ?: return
        val world = npc.location.world
        Bukkit.getOnlinePlayers().forEach { player ->
            if (canSee(npc, player, config)) {
                context.call("on entity see", player)
                if (config.showParticles) {
                    drawLine(npc.location.toVector(), player.location.toVector(), world)
                }
            }
        }
    }

    private fun canSee(npc: Npc, player: Player, config: Config): Boolean {
        val npcLoc = npc.location
        if (npcLoc.world != player.world) return false
        if (npcLoc.distanceSquared(player.location) > config.radius * config.radius) return false
        val dir = npcLoc.direction.normalize()
        val toPlayer = player.location.toVector().subtract(npcLoc.toVector()).normalize()
        val angle = Math.toDegrees(dir.angle(toPlayer))
        if (config.shape == Shape.CONE && angle > config.fov / 2) return false
        return npc.entity.hasLineOfSight(player)
    }

    private fun drawLine(from: Vector, to: Vector, world: org.bukkit.World) {
        val steps = from.distance(to) * 4
        val delta = to.clone().subtract(from).multiply(1.0 / steps)
        var current = from.clone()
        repeat(steps.toInt()) {
            world.spawnParticle(Particle.CRIT, current.toLocation(world), 1, 0.0, 0.0, 0.0, 0.0)
            current.add(delta)
        }
    }
}
