package de.chaos

import de.chaos.vision.DetectionIndicatorFormatter
import de.chaos.vision.IndicatorCacheKey
import kotlin.test.Test
import kotlin.test.assertEquals
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class DetectionIndicatorFormatterTest {
    @Test
    fun `cache key clamps progress into indicator range`() {
        assertEquals(IndicatorCacheKey(filled = 0, percent = 0, complete = false), DetectionIndicatorFormatter.cacheKey(-1.0))
        assertEquals(IndicatorCacheKey(filled = 6, percent = 50, complete = false), DetectionIndicatorFormatter.cacheKey(0.5))
        assertEquals(IndicatorCacheKey(filled = 12, percent = 100, complete = true), DetectionIndicatorFormatter.cacheKey(5.0))
    }

    @Test
    fun `text uses stable progress bar symbols`() {
        val text = PlainTextComponentSerializer.plainText().serialize(
            DetectionIndicatorFormatter.text(IndicatorCacheKey(filled = 3, percent = 25, complete = false))
        )

        assertEquals("?  [\u2588\u2588\u2588\u2591\u2591\u2591\u2591\u2591\u2591\u2591\u2591\u2591] 25%", text)
    }
}
