package com.behnamjalali.planb.core.model.rich

import kotlinx.serialization.Serializable

/**
 * A simple table (Plan-B Pro #15). [rows] are in reading order: column 0 is the first column
 * in the note's language (the right-most one in Persian), so the same data reads correctly in
 * both directions. With [header] the first row is the header row.
 */
@Serializable
data class TableData(
    val rows: List<List<String>> = listOf(listOf("", ""), listOf("", "")),
    val header: Boolean = true,
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    /** Every row has the same number of cells, and there is at least one cell. */
    fun normalized(): TableData {
        val columns = columnCount.coerceIn(1, MAX_COLUMNS)
        val fixed = rows.take(MAX_ROWS).map { row -> List(columns) { row.getOrElse(it) { "" } } }
        return copy(rows = fixed.ifEmpty { listOf(List(columns) { "" }) })
    }

    fun cell(row: Int, column: Int): String = rows.getOrNull(row)?.getOrNull(column).orEmpty()

    fun setCell(row: Int, column: Int, text: String): TableData {
        if (row !in rows.indices || column !in 0 until columnCount) return this
        return copy(rows = rows.mapIndexed { r, cells -> if (r == row) cells.mapIndexed { c, v -> if (c == column) text else v } else cells })
    }

    /** Adds an empty row at [index] (clamped; the end by default). */
    fun addRow(index: Int = rowCount): TableData {
        if (rowCount >= MAX_ROWS) return this
        val at = index.coerceIn(0, rowCount)
        return copy(rows = rows.toMutableList().apply { add(at, List(columnCount.coerceAtLeast(1)) { "" }) })
    }

    /** Removes a row; the last remaining row is cleared instead. */
    fun removeRow(index: Int): TableData {
        if (index !in rows.indices) return this
        if (rowCount == 1) return copy(rows = listOf(List(columnCount) { "" }))
        return copy(rows = rows.filterIndexed { i, _ -> i != index })
    }

    fun addColumn(index: Int = columnCount): TableData {
        if (columnCount >= MAX_COLUMNS) return this
        val at = index.coerceIn(0, columnCount)
        return copy(rows = rows.map { it.toMutableList().apply { add(at.coerceAtMost(size), "") } })
    }

    /** Removes a column; the last remaining column is cleared instead. */
    fun removeColumn(index: Int): TableData {
        if (index !in 0 until columnCount) return this
        if (columnCount == 1) return copy(rows = rows.map { listOf("") })
        return copy(rows = rows.map { cells -> cells.filterIndexed { i, _ -> i != index } })
    }

    /** Moves a column one step towards the start (-1) or the end (+1) of the reading order. */
    fun moveColumn(index: Int, delta: Int): TableData {
        val target = index + delta
        if (index !in 0 until columnCount || target !in 0 until columnCount) return this
        return copy(rows = rows.map { cells -> cells.toMutableList().apply { add(target, removeAt(index)) } })
    }

    fun moveRow(index: Int, delta: Int): TableData {
        val target = index + delta
        if (index !in rows.indices || target !in rows.indices) return this
        return copy(rows = rows.toMutableList().apply { add(target, removeAt(index)) })
    }

    companion object {
        const val MAX_ROWS = 500
        const val MAX_COLUMNS = 20
    }
}
