package org.mocoserver.sophisticatedgemcase.network

import org.mocoserver.sophisticatedgemcase.storage.GemStorageCodec
import java.util.UUID

/** 表示用の世代別集約。古いページ、重複応答、不正なサイズを混ぜない。 */
internal class GemPageAccumulator {
    var sessionId: UUID? = null
        private set
    var revision: Long = -1
        private set
    var locked: Boolean = false
        private set
    private var totalPages = 0
    private val pages = sortedMapOf<Int, List<GemPageEntry>>()
    val complete: Boolean get() = totalPages > 0 && pages.size == totalPages
    val entries: List<GemPageEntry> get() = pages.values.flatten()

    fun accept(page: GemPage): Boolean {
        val shape = page.totalPages in 1..GemStorageCodec.MAX_ENTRIES && page.page in 0 until page.totalPages
        val generation = sessionId == page.sessionId && revision == page.revision
        val stale = sessionId == page.sessionId && page.revision < revision
        val eligible = shape && !stale && (generation || page.page == 0)
        return if (eligible) {
            if (!generation) {
                pages.clear()
                sessionId = page.sessionId
                revision = page.revision
                locked = page.locked
                totalPages = page.totalPages
            }
            record(page)
        } else {
            false
        }
    }

    private fun record(page: GemPage): Boolean {
        val sameHeader = page.totalPages == totalPages && page.locked == locked
        val budget = pages.values.sumOf { it.size } + page.entries.size <= GemStorageCodec.MAX_ENTRIES
        val accepted = sameHeader && budget && !pages.containsKey(page.page)
        if (accepted) pages[page.page] = page.entries.toList()
        return accepted
    }

    fun nextPage(): Int? = (0 until totalPages).firstOrNull { !pages.containsKey(it) }
}
