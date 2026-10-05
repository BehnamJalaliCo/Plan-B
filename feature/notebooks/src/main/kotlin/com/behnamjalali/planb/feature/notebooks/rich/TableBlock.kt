package com.behnamjalali.planb.feature.notebooks.rich

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.model.rich.TableData
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.R

/**
 * An editable grid (#15). Columns follow the reading direction (the first column is on the
 * right in Persian). Each cell is announced with its row and column, and row and column
 * operations are TalkBack actions as well as menus.
 */
@Composable
internal fun TableBlock(block: EditorBlock, ui: RichUi?, editable: Boolean) {
    // The block owns its table while it is on screen; the editor's copy wins only when it was
    // changed elsewhere (a restored draft), which bumps the revision.
    var table by remember(block.id, block.revision) { mutableStateOf(RichBlocks.table(block.toNoteBlock())) }
    fun change(next: TableData) {
        table = next
        ui?.update(block.id, RichBlocks.encode(next))
    }
    val numbers = PlannerLocals.numbers
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        table.rows.forEachIndexed { r, cells ->
            val header = table.header && r == 0
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                cells.forEachIndexed { c, value ->
                    val label = stringResource(R.string.rich_table_cell_cd, numbers.format(r + 1), numbers.format(c + 1))
                    val actions = if (editable) tableActions(table, r, c, ::change) else emptyList()
                    Box(
                        Modifier
                            .widthIn(min = CellWidth)
                            .width(CellWidth)
                            .fillMaxHeight()
                            .border(BorderStroke(0.5.dp, outline))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                            .semantics {
                                contentDescription = label
                                if (actions.isNotEmpty()) customActions = actions
                            },
                    ) {
                        val style = (if (header) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyMedium)
                            .copy(color = MaterialTheme.colorScheme.onSurface)
                        if (editable) {
                            BasicTextField(
                                value = value,
                                onValueChange = { change(table.setCell(r, c, it)) },
                                textStyle = style,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            )
                        } else {
                            Text(value, style = style)
                        }
                    }
                }
                if (editable) RowMenu(table, r, ::change)
            }
        }
        if (editable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                (0 until table.columnCount).forEach { c -> Box(Modifier.width(CellWidth), contentAlignment = Alignment.Center) { ColumnMenu(table, c, ::change) } }
            }
        }
    }
    if (editable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { change(table.addRow()) }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.rich_table_add_row))
            }
            TextButton(onClick = { change(table.addColumn()) }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.rich_table_add_column))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = table.header, onCheckedChange = { change(table.copy(header = it)) })
                Text(stringResource(R.string.rich_table_header), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val CellWidth = 128.dp

@Composable
private fun tableActions(table: TableData, r: Int, c: Int, change: (TableData) -> Unit): List<CustomAccessibilityAction> {
    val below = stringResource(R.string.rich_table_insert_row_below)
    val deleteRow = stringResource(R.string.rich_table_delete_row)
    val after = stringResource(R.string.rich_table_insert_column_after)
    val deleteColumn = stringResource(R.string.rich_table_delete_column)
    return listOf(
        CustomAccessibilityAction(below) { change(table.addRow(r + 1)); true },
        CustomAccessibilityAction(deleteRow) { change(table.removeRow(r)); true },
        CustomAccessibilityAction(after) { change(table.addColumn(c + 1)); true },
        CustomAccessibilityAction(deleteColumn) { change(table.removeColumn(c)); true },
    )
}

@Composable
private fun RowMenu(table: TableData, r: Int, change: (TableData) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PlannerIconButton(Icons.Rounded.MoreVert, stringResource(R.string.rich_table_row_menu, PlannerLocals.numbers.format(r + 1)), { open = true })
        DropdownMenu(open, { open = false }) {
            listOf(
                R.string.rich_table_insert_row_above to { table.addRow(r) },
                R.string.rich_table_insert_row_below to { table.addRow(r + 1) },
                R.string.rich_table_delete_row to { table.removeRow(r) },
            ).forEach { (label, next) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    open = false
                    change(next())
                })
            }
        }
    }
}

@Composable
private fun ColumnMenu(table: TableData, c: Int, change: (TableData) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PlannerIconButton(Icons.Rounded.MoreVert, stringResource(R.string.rich_table_column_menu, PlannerLocals.numbers.format(c + 1)), { open = true })
        DropdownMenu(open, { open = false }) {
            listOf(
                R.string.rich_table_insert_column_before to { table.addColumn(c) },
                R.string.rich_table_insert_column_after to { table.addColumn(c + 1) },
                R.string.rich_table_move_column_start to { table.moveColumn(c, -1) },
                R.string.rich_table_move_column_end to { table.moveColumn(c, 1) },
                R.string.rich_table_delete_column to { table.removeColumn(c) },
            ).forEach { (label, next) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    open = false
                    change(next())
                })
            }
        }
    }
}
