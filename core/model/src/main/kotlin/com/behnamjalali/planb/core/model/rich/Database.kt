package com.behnamjalali.planb.core.model.rich

import java.math.BigDecimal
import java.text.Collator
import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ColumnType {
    @SerialName("text") TEXT,
    @SerialName("number") NUMBER,
    @SerialName("checkbox") CHECKBOX,
    @SerialName("date") DATE,
    @SerialName("select") SELECT,
}

/** A typed column. [options] are the choices of a [ColumnType.SELECT] column. */
@Serializable
data class DbColumn(
    val id: String,
    val name: String = "",
    val type: ColumnType = ColumnType.TEXT,
    val options: List<String> = emptyList(),
)

/**
 * A row: cell values by column id, stored canonically so they survive a language or calendar
 * change: numbers as ASCII decimals ("1250.5"), dates as ISO days ("2026-10-05", always
 * Gregorian; shown in the user's calendar), checkboxes as "true" (unchecked is absent).
 */
@Serializable
data class DbRow(val id: String, val cells: Map<String, String> = emptyMap())

@Serializable
data class DbSort(val columnId: String, val descending: Boolean = false)

/**
 * Shows only rows whose cell in [columnId] matches [query]: text and select cells contain it
 * (ignoring case), checkboxes match "true"/"false", numbers accept `>`, `<`, `>=`, `<=`, `=`
 * or a plain number, dates match their ISO prefix ("2026-10").
 */
@Serializable
data class DbFilter(val columnId: String, val query: String = "")

/** A simple database (Plan-B Pro #20), stored as JSON in its note block. */
@Serializable
data class DatabaseData(
    val columns: List<DbColumn> = emptyList(),
    val rows: List<DbRow> = emptyList(),
    val sort: DbSort? = null,
    val filter: DbFilter? = null,
) {
    fun column(id: String): DbColumn? = columns.firstOrNull { it.id == id }

    /** Drops cells, sort and filter of columns that no longer exist; at least one column. */
    fun normalized(): DatabaseData {
        val cols = columns.take(MAX_COLUMNS).ifEmpty { listOf(DbColumn(id = "c0")) }
        val ids = cols.map { it.id }.toSet()
        return copy(
            columns = cols,
            rows = rows.take(MAX_ROWS).map { row -> row.copy(cells = row.cells.filterKeys { it in ids }) },
            sort = sort?.takeIf { it.columnId in ids },
            filter = filter?.takeIf { it.columnId in ids },
        )
    }

    fun addColumn(column: DbColumn): DatabaseData = if (columns.size >= MAX_COLUMNS) this else copy(columns = columns + column)

    fun updateColumn(column: DbColumn): DatabaseData = copy(columns = columns.map { if (it.id == column.id) column else it })

    /** Changes a column's type and converts its cells where it makes sense (a cell that does not convert is kept as typed). */
    fun changeType(columnId: String, type: ColumnType): DatabaseData {
        val column = column(columnId) ?: return this
        val converted = rows.map { row ->
            val raw = row.cells[columnId] ?: return@map row
            val value = when (type) {
                ColumnType.NUMBER -> DbValues.parseNumber(raw)?.toPlainString() ?: raw
                ColumnType.CHECKBOX -> if (DbValues.isTruthy(raw)) "true" else null
                else -> raw
            }
            row.copy(cells = if (value == null) row.cells - columnId else row.cells + (columnId to value))
        }
        val options = if (type == ColumnType.SELECT && column.options.isEmpty()) {
            rows.mapNotNull { it.cells[columnId]?.trim()?.takeIf(String::isNotEmpty) }.distinct().take(MAX_OPTIONS)
        } else {
            column.options
        }
        return copy(columns = columns.map { if (it.id == columnId) it.copy(type = type, options = options) else it }, rows = converted)
    }

    fun removeColumn(columnId: String): DatabaseData = copy(columns = columns.filterNot { it.id == columnId }).normalized()

    fun moveColumn(columnId: String, delta: Int): DatabaseData {
        val index = columns.indexOfFirst { it.id == columnId }
        val target = index + delta
        if (index < 0 || target !in columns.indices) return this
        return copy(columns = columns.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun addRow(row: DbRow): DatabaseData = if (rows.size >= MAX_ROWS) this else copy(rows = rows + row)

    fun removeRow(rowId: String): DatabaseData = copy(rows = rows.filterNot { it.id == rowId })

    /** Sets a cell; a blank value removes it. */
    fun setCell(rowId: String, columnId: String, value: String): DatabaseData = copy(
        rows = rows.map { row ->
            if (row.id != rowId) row else row.copy(cells = if (value.isBlank()) row.cells - columnId else row.cells + (columnId to value))
        },
    )

    /** The rows to show: filtered, then sorted (stable; empty cells last in both directions). */
    fun visibleRows(): List<DbRow> {
        val filtered = filter?.takeIf { it.query.isNotBlank() }?.let { f ->
            val column = column(f.columnId) ?: return@let rows
            rows.filter { DbValues.matches(column, it.cells[column.id], f.query) }
        } ?: rows
        val s = sort ?: return filtered
        val column = column(s.columnId) ?: return filtered
        val (empty, filled) = filtered.partition { it.cells[column.id].isNullOrBlank() && column.type != ColumnType.CHECKBOX }
        val comparator = DbValues.comparator(column)
        val sorted = filled.sortedWith { a, b -> comparator.compare(a.cells[column.id], b.cells[column.id]) }
        return (if (s.descending) sorted.reversed() else sorted) + empty
    }

    /** Footer of a column over [rows] (normally [visibleRows]). */
    fun summary(columnId: String, rows: List<DbRow> = visibleRows()): ColumnSummary {
        val column = column(columnId) ?: return ColumnSummary(0, null)
        val values = rows.mapNotNull { it.cells[columnId]?.takeIf(String::isNotBlank) }
        return when (column.type) {
            ColumnType.NUMBER -> {
                val numbers = values.mapNotNull(DbValues::parseNumber)
                ColumnSummary(numbers.size, numbers.fold(BigDecimal.ZERO, BigDecimal::add))
            }
            ColumnType.CHECKBOX -> ColumnSummary(values.count(DbValues::isTruthy), null)
            else -> ColumnSummary(values.size, null)
        }
    }

    companion object {
        const val MAX_COLUMNS = 12
        const val MAX_ROWS = 500
        const val MAX_OPTIONS = 30

        fun empty(): DatabaseData = DatabaseData(columns = listOf(DbColumn(id = "c0")))
    }
}

/** [count] of filled cells (checked boxes, numbers); [sum] only for number columns. */
data class ColumnSummary(val count: Int, val sum: BigDecimal?)

/** Parsing and comparing canonical cell values. */
object DbValues {
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("fa")).apply { strength = Collator.SECONDARY }

    /** Accepts Persian/Arabic-Indic digits, Persian decimal and thousands separators, and commas. */
    fun parseNumber(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val latin = buildString {
            raw.trim().forEach { ch ->
                when (ch) {
                    in '۰'..'۹' -> append('0' + (ch - '۰'))
                    in '٠'..'٩' -> append('0' + (ch - '٠'))
                    '٫', '/' -> append('.')
                    '٬', ',', '،', ' ', '\u200C' -> Unit
                    '−' -> append('-')
                    else -> append(ch)
                }
            }
        }
        return runCatching { BigDecimal(latin) }.getOrNull()
    }

    fun parseDate(raw: String?): LocalDate? = raw?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    fun isTruthy(raw: String?): Boolean = raw?.trim()?.lowercase() in setOf("true", "1", "yes", "x", "✓", "بله")

    fun comparator(column: DbColumn): Comparator<String?> = when (column.type) {
        ColumnType.NUMBER -> compareBy(nullsLast()) { v: String? -> parseNumber(v) }
        ColumnType.CHECKBOX -> compareBy { v: String? -> isTruthy(v) }
        ColumnType.DATE -> compareBy(nullsLast()) { v: String? -> parseDate(v) }
        ColumnType.SELECT -> compareBy<String?> { v -> column.options.indexOf(v).let { if (it < 0) Int.MAX_VALUE else it } }
            .thenComparator { a, b -> collator.compare(a.orEmpty(), b.orEmpty()) }
        ColumnType.TEXT -> Comparator { a, b -> collator.compare(a.orEmpty(), b.orEmpty()) }
    }

    fun matches(column: DbColumn, value: String?, query: String): Boolean {
        val q = query.trim()
        return when (column.type) {
            ColumnType.CHECKBOX -> isTruthy(value) == (q.lowercase() in setOf("true", "1", "yes", "x", "✓", "بله"))
            ColumnType.NUMBER -> {
                val number = parseNumber(value) ?: return false
                val op = listOf(">=", "<=", ">", "<", "=").firstOrNull { q.startsWith(it) }
                val target = parseNumber(if (op == null) q else q.removePrefix(op)) ?: return false
                val c = number.compareTo(target)
                when (op) {
                    ">=" -> c >= 0
                    "<=" -> c <= 0
                    ">" -> c > 0
                    "<" -> c < 0
                    else -> c == 0
                }
            }
            ColumnType.DATE -> value.orEmpty().startsWith(q)
            else -> value.orEmpty().contains(q, ignoreCase = true)
        }
    }
}
