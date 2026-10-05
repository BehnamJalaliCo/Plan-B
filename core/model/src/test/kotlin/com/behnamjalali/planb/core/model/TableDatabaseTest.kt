package com.behnamjalali.planb.core.model

import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbFilter
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.DbSort
import com.behnamjalali.planb.core.model.rich.DbValues
import com.behnamjalali.planb.core.model.rich.TableData
import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import org.junit.Test

class TableDatabaseTest {
    @Test
    fun table_rowAndColumnOperations() {
        var t = TableData()
        assertThat(t.rowCount to t.columnCount).isEqualTo(2 to 2)
        t = t.setCell(0, 0, "A").setCell(0, 1, "B").addColumn(1).setCell(0, 1, "mid")
        assertThat(t.rows[0]).containsExactly("A", "mid", "B").inOrder()
        t = t.addRow(0)
        assertThat(t.rows[0]).containsExactly("", "", "")
        t = t.removeRow(0).moveColumn(2, -1)
        assertThat(t.rows[0]).containsExactly("A", "B", "mid").inOrder()
        t = t.removeColumn(0).removeColumn(0).removeColumn(0)
        // The last column is cleared, never removed.
        assertThat(t.columnCount).isEqualTo(1)
        assertThat(t.setCell(9, 9, "x")).isEqualTo(t)
    }

    @Test
    fun table_normalizesRaggedRowsAndLimits() {
        val ragged = TableData(listOf(listOf("a"), listOf("b", "c", "d"))).normalized()
        assertThat(ragged.rows).containsExactly(listOf("a", "", ""), listOf("b", "c", "d")).inOrder()
        var wide = TableData(listOf(List(TableData.MAX_COLUMNS) { "" }))
        wide = wide.addColumn()
        assertThat(wide.columnCount).isEqualTo(TableData.MAX_COLUMNS)
        assertThat(TableData(emptyList()).normalized().rows).containsExactly(listOf(""))
    }

    private val db = DatabaseData(
        columns = listOf(
            DbColumn("name", "Name"),
            DbColumn("price", "Price", ColumnType.NUMBER),
            DbColumn("done", "Done", ColumnType.CHECKBOX),
            DbColumn("day", "Day", ColumnType.DATE),
            DbColumn("kind", "Kind", ColumnType.SELECT, listOf("food", "books")),
        ),
        rows = listOf(
            DbRow("1", mapOf("name" to "پنیر", "price" to "120", "done" to "true", "day" to "2026-10-05", "kind" to "food")),
            DbRow("2", mapOf("name" to "Atlas", "price" to "۴۵٫۵", "day" to "2026-09-01", "kind" to "books")),
            DbRow("3", mapOf("name" to "بستنی", "kind" to "food")),
            DbRow("4", mapOf("name" to "apple", "price" to "-5", "done" to "true")),
        ),
    )

    @Test
    fun numbers_acceptPersianDigitsAndSeparators() {
        assertThat(DbValues.parseNumber("۱۲۳٬۴۵۶٫۵")).isEqualTo(BigDecimal("123456.5"))
        assertThat(DbValues.parseNumber("1,250")).isEqualTo(BigDecimal("1250"))
        assertThat(DbValues.parseNumber("abc")).isNull()
    }

    @Test
    fun sort_byNumberDateTextAndSelect_emptyLast() {
        fun order(sort: DbSort) = db.copy(sort = sort).visibleRows().map { it.id }
        assertThat(order(DbSort("price"))).containsExactly("4", "2", "1", "3").inOrder()
        assertThat(order(DbSort("price", descending = true))).containsExactly("1", "2", "4", "3").inOrder()
        assertThat(order(DbSort("day"))).containsExactly("2", "1", "3", "4").inOrder()
        assertThat(order(DbSort("kind"))).containsExactly("1", "3", "2", "4").inOrder()
        // Persian letters sort alphabetically (ب before پ), after Latin.
        assertThat(order(DbSort("name"))).containsExactly("4", "2", "3", "1").inOrder()
    }

    @Test
    fun filter_perColumnType() {
        fun ids(f: DbFilter) = db.copy(filter = f).visibleRows().map { it.id }
        assertThat(ids(DbFilter("price", ">40"))).containsExactly("1", "2")
        assertThat(ids(DbFilter("price", "<= 0"))).containsExactly("4")
        assertThat(ids(DbFilter("done", "true"))).containsExactly("1", "4")
        assertThat(ids(DbFilter("done", "false"))).containsExactly("2", "3")
        assertThat(ids(DbFilter("day", "2026-10"))).containsExactly("1")
        assertThat(ids(DbFilter("name", "AT"))).containsExactly("2")
        assertThat(ids(DbFilter("name", " "))).hasSize(4)
    }

    @Test
    fun summary_sumsNumbersAndCountsOthers() {
        val price = db.summary("price")
        assertThat(price.sum).isEqualTo(BigDecimal("160.5"))
        assertThat(price.count).isEqualTo(3)
        assertThat(db.summary("done").count).isEqualTo(2)
        assertThat(db.summary("name").count).isEqualTo(4)
        // The footer follows the filter.
        assertThat(db.copy(filter = DbFilter("kind", "food")).summary("price").sum).isEqualTo(BigDecimal("120"))
    }

    @Test
    fun columnEdits_keepCellsConsistent() {
        val changed = db.changeType("name", ColumnType.NUMBER).changeType("price", ColumnType.CHECKBOX)
        assertThat(changed.rows.first().cells["name"]).isEqualTo("پنیر")
        assertThat(changed.rows.map { it.cells.containsKey("price") }).containsExactly(false, false, false, false)
        val select = db.changeType("name", ColumnType.SELECT)
        assertThat(select.column("name")!!.options).containsExactly("پنیر", "Atlas", "بستنی", "apple").inOrder()
        val removed = db.copy(sort = DbSort("price")).removeColumn("price")
        assertThat(removed.sort).isNull()
        assertThat(removed.rows.none { it.cells.containsKey("price") }).isTrue()
        val cleared = db.setCell("1", "name", " ")
        assertThat(cleared.rows.first().cells).doesNotContainKey("name")
        assertThat(db.moveColumn("kind", -1).columns.map { it.id }).containsExactly("name", "price", "done", "kind", "day").inOrder()
        assertThat(db.removeRow("2").addRow(DbRow("5")).rows.map { it.id }).containsExactly("1", "3", "4", "5").inOrder()
    }
}
