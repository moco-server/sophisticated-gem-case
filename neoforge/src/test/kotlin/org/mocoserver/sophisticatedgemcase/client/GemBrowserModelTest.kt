package org.mocoserver.sophisticatedgemcase.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class GemBrowserModelTest {
    @Test
    fun `owned gems precede empty gems and scrolling stops at a complete last page`() {
        val model = GemBrowserModel()
        model.update((0..30).map { GemBrowserEntry("test:%02d".format(it), "Gem $it", if (it == 30) 32767L else 0L) })
        assertEquals("test:30", model.visible().first().id)
        model.scroll(999)
        assertEquals(3, model.rowOffset)
        assertEquals(13, model.visible().size)
        model.scroll(-999)
        assertEquals(0, model.rowOffset)
    }

    @Test
    fun `search matches translated name and id and resets scrolling without losing selection`() {
        val model = GemBrowserModel()
        model.update((0..30).map { GemBrowserEntry("test:$it", if (it == 20) "月のジェム" else "Gem $it", 1L) })
        model.select("test:20")
        model.scroll(4)
        model.search = "月"
        assertEquals(0, model.rowOffset)
        assertEquals(listOf("test:20"), model.visible().map { it.id })
        model.search = "TEST:20"
        assertEquals(listOf("test:20"), model.visible().map { it.id })
        model.search = "absent"
        assertEquals(emptyList<GemBrowserEntry>(), model.visible())
        assertEquals("test:20", model.selected)
    }

    @Test
    fun `refresh retains unresolved entries and drops only selection no longer present`() {
        val model = GemBrowserModel()
        model.update(listOf(GemBrowserEntry("missing:gem", "missing:gem", 9L, false)))
        model.select("missing:gem")
        assertEquals(false, model.visible().single().known)
        assertEquals(9L, model.visible().single().count)
        model.update(emptyList())
        assertNull(model.selected)
    }
}
