package de.chaos.vision

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

internal data class IndicatorCacheKey(
    val filled: Int,
    val percent: Int,
    val complete: Boolean,
)

internal object DetectionIndicatorFormatter {
    private const val WIDTH = 12

    fun cacheKey(progress: Double): IndicatorCacheKey {
        val clamped = progress.coerceIn(0.0, 1.0)
        return IndicatorCacheKey(
            filled = (clamped * WIDTH).toInt().coerceIn(0, WIDTH),
            percent = (clamped * 100).toInt().coerceIn(0, 100),
            complete = clamped >= 1.0,
        )
    }

    fun text(cacheKey: IndicatorCacheKey): Component {
        val filledPart =
            Component.text(
                "\u2588".repeat(cacheKey.filled),
                when {
                    cacheKey.complete -> NamedTextColor.RED
                    cacheKey.percent >= 66 -> NamedTextColor.GOLD
                    cacheKey.percent >= 33 -> NamedTextColor.YELLOW
                    else -> NamedTextColor.GREEN
                },
            )
        val emptyPart = Component.text("\u2591".repeat(WIDTH - cacheKey.filled), NamedTextColor.DARK_GRAY)
        val symbol = if (cacheKey.complete) "!" else "?"
        val bar =
            Component.text(" [", NamedTextColor.GRAY)
                .append(filledPart)
                .append(emptyPart)
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text("${cacheKey.percent}%", NamedTextColor.GRAY))

        return Component.text("$symbol ", NamedTextColor.YELLOW).append(bar)
    }
}
