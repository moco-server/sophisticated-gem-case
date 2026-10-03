package org.mocoserver.sophisticatedgemcase.network

import org.mocoserver.sophisticatedgemcase.core.GemFilterMode
import org.mocoserver.sophisticatedgemcase.storage.GemSettingsCodec
import java.util.UUID

/** page順や再送に依存せず、同一世代の設定一覧だけを公開する。 */
internal class GemSettingsPageAccumulator {
    var sessionId: UUID? = null
        private set
    var revision: Long = -1
        private set
    var locked: Boolean = true
        private set
    var depositEnabled: Boolean = false
        private set
    var restockEnabled: Boolean = false
        private set
    var depositMode: GemFilterMode = GemFilterMode.ALL
        private set
    var restockMode: GemFilterMode = GemFilterMode.ALL
        private set
    private var totalPages = 0
    private val pages = sortedMapOf<Int, List<GemSettingsEntry>>()
    val complete: Boolean get() = totalPages > 0 && pages.size == totalPages
    val entries: List<GemSettingsEntry> get() = pages.values.flatten()

    fun accept(page: GemSettingsPage): Boolean {
        val generation = sessionId == page.sessionId && revision == page.revision
        val stale = sessionId == page.sessionId && page.revision < revision
        return when {
            !validShape(page) || stale -> false
            !generation && page.page != 0 -> false
            !generation -> startGeneration(page)
            else -> record(page)
        }
    }

    private fun startGeneration(page: GemSettingsPage): Boolean {
        if (!hasUniqueEntries(page) || page.entries.size > GemSettingsCodec.MAX_ENTRIES) return false
        pages.clear()
        sessionId = page.sessionId
        revision = page.revision
        locked = page.locked
        depositEnabled = page.depositEnabled
        restockEnabled = page.restockEnabled
        depositMode = page.depositMode
        restockMode = page.restockMode
        totalPages = page.totalPages
        pages[page.page] = page.entries.toList()
        return true
    }

    private fun validShape(page: GemSettingsPage): Boolean =
        page.totalPages in 1..GemSettingsCodec.MAX_ENTRIES && page.page in 0 until page.totalPages

    private fun record(page: GemSettingsPage): Boolean {
        val existing = pages.values.flatten().toSet()
        val noDuplicateEntries = hasUniqueEntries(page) && page.entries.none { it in existing }
        val withinBudget = existing.size + page.entries.size <= GemSettingsCodec.MAX_ENTRIES
        val accepted = matchesHeader(page) && noDuplicateEntries && withinBudget && page.page !in pages
        return if (accepted) {
            pages[page.page] = page.entries.toList()
            true
        } else {
            false
        }
    }

    private fun matchesHeader(page: GemSettingsPage): Boolean =
        page.totalPages == totalPages &&
            page.locked == locked &&
            page.depositEnabled == depositEnabled &&
            page.restockEnabled == restockEnabled &&
            page.depositMode == depositMode &&
            page.restockMode == restockMode

    private fun hasUniqueEntries(page: GemSettingsPage): Boolean = page.entries.toSet().size == page.entries.size

    fun nextPage(): Int? = (0 until totalPages).firstOrNull { it !in pages }
}

/** 収納一覧と設定一覧が同じserver世代で揃った場合だけ編集可能とする。 */
internal object GemSettingsSync {
    fun canEdit(
        counts: GemPageAccumulator,
        settings: GemSettingsPageAccumulator,
    ): Boolean =
        counts.complete &&
            !counts.locked &&
            settings.complete &&
            !settings.locked &&
            counts.sessionId == settings.sessionId &&
            counts.revision == settings.revision
}
