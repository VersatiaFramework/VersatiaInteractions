package dev.versatia.interactions.menu

import kotlin.test.Test
import kotlin.test.assertEquals

class SlotsTest {
    @Test
    fun `configured slots are kept and the others take the first free ones in order`() {
        assertEquals(
            mapOf("trade" to 11, "duel" to 15, "inspect" to 0, "party" to 1),
            assignSlots(listOf("inspect", "trade", "party", "duel"), mapOf("trade" to 11, "duel" to 15), 27),
        )
    }

    @Test
    fun `a configured slot outside the window counts as none`() {
        assertEquals(mapOf("trade" to 0), assignSlots(listOf("trade"), mapOf("trade" to 30), 27))
    }

    @Test
    fun `actions beyond the free slots get none`() {
        assertEquals(mapOf("b" to 0, "a" to 1), assignSlots(listOf("a", "b", "c"), mapOf("b" to 0), 2))
    }
}
