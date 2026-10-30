package org.mocoserver.sophisticatedgemcase.client

/** 表示用stackから独立させ、保存数と未解決IDを一覧に保持する。 */
internal data class GemBrowserEntry(
    val id: String,
    val name: String,
    val count: Long,
    val known: Boolean = true,
)

/** 検索で選択を失わず、スクロールは六列単位で最後の行までに制限する。 */
internal class GemBrowserModel {
    private var entries: List<GemBrowserEntry> = emptyList()
    var selected: String? = null
        private set
    var rowOffset: Int = 0
        private set
    var search: String = ""
        set(value) {
            if (field != value) rowOffset = 0
            field = value
        }

    fun update(values: List<GemBrowserEntry>) {
        entries = values.sortedWith(compareBy({ it.count == 0L }, { it.id }))
        if (entries.none { it.id == selected }) selected = null
        clampOffset()
    }

    fun select(id: String) {
        if (entries.any { it.id == id }) selected = id
    }

    fun filtered(): List<GemBrowserEntry> =
        entries.filter { it.name.contains(search, ignoreCase = true) || it.id.contains(search, ignoreCase = true) }

    fun visible(): List<GemBrowserEntry> = filtered().drop(rowOffset * COLUMNS).take(COLUMNS * ROWS)

    fun maxOffset(): Int = maxOf(0, (filtered().size + COLUMNS - 1) / COLUMNS - ROWS)

    fun scroll(rows: Int) {
        rowOffset = (rowOffset + rows).coerceIn(0, maxOffset())
    }

    fun scrollTo(fraction: Double) {
        rowOffset = (fraction.coerceIn(0.0, 1.0) * maxOffset()).toInt()
    }

    private fun clampOffset() {
        rowOffset = rowOffset.coerceIn(0, maxOffset())
    }

    companion object {
        const val COLUMNS = 6
        const val ROWS = 3
    }
}
