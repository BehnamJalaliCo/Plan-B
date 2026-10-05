package com.behnamjalali.planb.feature.notebooks.rich

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.common.Digits
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerIconButton
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.rich.ColumnType
import com.behnamjalali.planb.core.model.rich.DatabaseData
import com.behnamjalali.planb.core.model.rich.DbColumn
import com.behnamjalali.planb.core.model.rich.DbFilter
import com.behnamjalali.planb.core.model.rich.DbRow
import com.behnamjalali.planb.core.model.rich.DbSort
import com.behnamjalali.planb.core.model.rich.DbValues
import com.behnamjalali.planb.core.model.rich.RichBlocks
import com.behnamjalali.planb.core.ui.PlannerDatePickerDialog
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.feature.notebooks.EditorBlock
import com.behnamjalali.planb.feature.notebooks.R
import java.math.BigDecimal
import java.util.UUID

/**
 * A simple database (#20): typed columns, rows, sort, filter and a footer with sums of number
 * columns and counts. Numbers are stored with Latin digits and shown in the user's digits;
 * dates are stored as ISO days and shown in the user's calendar (Jalali or Gregorian).
 */
@Composable
internal fun DatabaseBlock(block: EditorBlock, ui: RichUi?, editable: Boolean) {
    var db by remember(block.id, block.revision) { mutableStateOf(RichBlocks.database(block.toNoteBlock())) }
    fun change(next: DatabaseData) {
        db = next
        ui?.update(block.id, RichBlocks.encode(next))
    }
    var editing by remember { mutableStateOf<String?>(null) }
    val rows = db.visibleRows()
    val numbers = PlannerLocals.numbers
    val outline = MaterialTheme.colorScheme.outlineVariant

    db.filter?.takeIf { it.query.isNotBlank() }?.let { filter ->
        val column = db.column(filter.columnId)
        AssistChip(
            onClick = { if (editable) change(db.copy(filter = null)) },
            label = { Text(stringResource(R.string.rich_db_filter_active, "${column?.name.orEmpty()} ${numbers.localize(filter.query)}")) },
            leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
            trailingIcon = if (editable) ({ Icon(Icons.Rounded.Close, stringResource(R.string.rich_db_filter_clear)) }) else null,
        )
    }
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        // Header
        Row(Modifier.height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.surfaceContainer)) {
            db.columns.forEach { column ->
                Row(
                    Modifier
                        .width(ColumnWidth)
                        .fillMaxHeight()
                        .border(BorderStroke(0.5.dp, outline))
                        .then(if (editable) Modifier.clickable { editing = column.id } else Modifier)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        column.name.ifBlank { stringResource(R.string.rich_db_column_default, numbers.format(db.columns.indexOf(column) + 1)) },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (db.sort?.columnId == column.id) {
                        Icon(
                            if (db.sort?.descending == true) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(if (db.sort?.descending == true) R.string.rich_db_sort_descending else R.string.rich_db_sort_ascending),
                            modifier = Modifier.width(16.dp),
                        )
                    }
                }
            }
        }
        rows.forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                db.columns.forEach { column ->
                    Box(
                        Modifier
                            .width(ColumnWidth)
                            .fillMaxHeight()
                            .heightIn(min = 44.dp)
                            .border(BorderStroke(0.5.dp, outline))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        DatabaseCell(column, row.cells[column.id], editable) { change(db.setCell(row.id, column.id, it)) }
                    }
                }
                if (editable) {
                    PlannerIconButton(Icons.Rounded.DeleteOutline, stringResource(R.string.rich_db_delete_row), { change(db.removeRow(row.id)) })
                }
            }
        }
        // Footer: sums of number columns, counts of the others.
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            db.columns.forEachIndexed { i, column ->
                val summary = db.summary(column.id, rows)
                val text = when {
                    column.type == ColumnType.NUMBER -> stringResource(R.string.rich_db_sum, numbers.localize(format(summary.sum ?: BigDecimal.ZERO)))
                    i == 0 -> pluralStringResource(R.plurals.rich_db_rows, rows.size, numbers.format(rows.size))
                    column.type == ColumnType.CHECKBOX -> "${numbers.format(summary.count)} ✓"
                    else -> ""
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(ColumnWidth).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
    if (editable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { change(db.addRow(DbRow(UUID.randomUUID().toString()))) }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.rich_db_add_row))
            }
            TextButton(onClick = {
                val id = "c" + UUID.randomUUID().toString().take(8)
                change(db.addColumn(DbColumn(id)))
                editing = id
            }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.rich_db_add_column))
            }
        }
    }
    editing?.let { id ->
        db.column(id)?.let { column ->
            ColumnDialog(db, column, onDismiss = { editing = null }) {
                change(it)
                editing = null
            }
        } ?: run { editing = null }
    }
}

private val ColumnWidth = 140.dp

private fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

/** Canonical form of typed numbers: Latin digits and a dot. */
private fun canonicalNumber(input: String) = Digits.toLatin(input).replace('٫', '.').replace('٬', ',')

@Composable
private fun DatabaseCell(column: DbColumn, value: String?, editable: Boolean, onChange: (String) -> Unit) {
    val numbers = PlannerLocals.numbers
    val formatter = PlannerLocals.formatter
    val style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    val label = column.name
    when (column.type) {
        ColumnType.CHECKBOX -> {
            val checked = DbValues.isTruthy(value)
            val state = stringResource(if (checked) R.string.rich_db_checked else R.string.rich_db_unchecked)
            Checkbox(
                checked = checked,
                onCheckedChange = if (editable) ({ onChange(if (it) "true" else "") }) else null,
                modifier = Modifier.semantics { contentDescription = "$label: $state" },
            )
        }
        ColumnType.DATE -> {
            var picking by remember { mutableStateOf(false) }
            val date = DbValues.parseDate(value)
            val text = date?.let(formatter::mediumDate) ?: if (editable) stringResource(R.string.rich_db_pick_date) else ""
            Text(
                text,
                style = style.copy(color = if (date == null) MaterialTheme.colorScheme.outline else style.color),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (editable) Modifier.clickable { picking = true } else Modifier)
                    .semantics { contentDescription = "$label: $text" },
            )
            if (picking) {
                PlannerDatePickerDialog(
                    initial = date,
                    onDismiss = { picking = false },
                    onConfirm = {
                        picking = false
                        onChange(it?.toString().orEmpty())
                    },
                )
            }
        }
        ColumnType.SELECT -> {
            var open by remember { mutableStateOf(false) }
            Box {
                Text(
                    value.orEmpty().ifBlank { if (editable) stringResource(R.string.rich_db_pick_option) else "" },
                    style = style.copy(color = if (value.isNullOrBlank()) MaterialTheme.colorScheme.outline else style.color),
                    modifier = Modifier.fillMaxWidth().then(if (editable) Modifier.clickable { open = true } else Modifier),
                )
                DropdownMenu(open, { open = false }) {
                    (listOf("") + column.options).forEach { option ->
                        DropdownMenuItem(text = { Text(option.ifBlank { "—" }) }, onClick = {
                            open = false
                            onChange(option)
                        })
                    }
                }
            }
        }
        ColumnType.NUMBER -> if (editable) {
            BasicTextField(
                value = numbers.localize(value.orEmpty()),
                onValueChange = { onChange(canonicalNumber(it)) },
                textStyle = style,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        } else {
            Text(numbers.localize(value.orEmpty()), style = style)
        }
        ColumnType.TEXT -> if (editable) {
            BasicTextField(
                value = value.orEmpty(),
                onValueChange = onChange,
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        } else {
            Text(value.orEmpty(), style = style)
        }
    }
}

/** Name, type, select options, sort, filter, order and deletion of a column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnDialog(db: DatabaseData, column: DbColumn, onDismiss: () -> Unit, onSave: (DatabaseData) -> Unit) {
    var name by remember { mutableStateOf(column.name) }
    var type by remember { mutableStateOf(column.type) }
    var options by remember { mutableStateOf(column.options.joinToString("\n")) }
    var sort by remember { mutableStateOf(db.sort?.takeIf { it.columnId == column.id }) }
    var filter by remember { mutableStateOf(db.filter?.takeIf { it.columnId == column.id }?.query.orEmpty()) }
    fun result(): DatabaseData {
        var next = if (type != column.type) db.changeType(column.id, type) else db
        val updated = next.column(column.id)!!.copy(
            name = name.trim(),
            options = if (type == ColumnType.SELECT) options.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(DatabaseData.MAX_OPTIONS) else emptyList(),
        )
        next = next.updateColumn(updated)
        next = next.copy(
            sort = sort ?: next.sort?.takeIf { it.columnId != column.id },
            filter = if (filter.isNotBlank()) DbFilter(column.id, filter.trim()) else next.filter?.takeIf { it.columnId != column.id },
        )
        return next
    }
    PlannerDialog(
        title = stringResource(R.string.rich_db_edit_column),
        onDismiss = onDismiss,
        confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_save),
        onConfirm = { onSave(result()) },
    ) {
        PlannerTextField(name, { name = it }, stringResource(R.string.rich_db_column_name))
        Text(stringResource(R.string.rich_db_column_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ColumnType.entries.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(typeLabel(t))) })
            }
        }
        if (type == ColumnType.SELECT) PlannerTextField(options, { options = it }, stringResource(R.string.rich_db_options), singleLine = false, minLines = 3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(selected = sort?.descending == false, onClick = { sort = DbSort(column.id, false) }, label = { Text(stringResource(R.string.rich_db_sort_ascending)) })
            FilterChip(selected = sort?.descending == true, onClick = { sort = DbSort(column.id, true) }, label = { Text(stringResource(R.string.rich_db_sort_descending)) })
            FilterChip(selected = sort == null, onClick = { sort = null }, label = { Text(stringResource(R.string.rich_db_sort_off)) })
        }
        PlannerTextField(
            filter,
            { filter = it },
            stringResource(if (type == ColumnType.NUMBER) R.string.rich_db_filter_number_hint else R.string.rich_db_filter_hint),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TextButton(onClick = { onSave(result().moveColumn(column.id, -1)) }) { Text(stringResource(R.string.rich_db_move_start)) }
            TextButton(onClick = { onSave(result().moveColumn(column.id, 1)) }) { Text(stringResource(R.string.rich_db_move_end)) }
            TextButton(onClick = { onSave(db.removeColumn(column.id)) }) {
                Text(stringResource(R.string.rich_db_delete_column), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun typeLabel(type: ColumnType) = when (type) {
    ColumnType.TEXT -> R.string.rich_db_type_text
    ColumnType.NUMBER -> R.string.rich_db_type_number
    ColumnType.CHECKBOX -> R.string.rich_db_type_checkbox
    ColumnType.DATE -> R.string.rich_db_type_date
    ColumnType.SELECT -> R.string.rich_db_type_select
}
