package com.behnamjalali.planb.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import com.behnamjalali.planb.R

/**
 * The Plan-B "B" split into the layers of the launcher artwork
 * (`ic_launcher_foreground`, the single source of the mark), so each part can move on its
 * own: the soft shadow, the cream stem, the warm upper lobe and the cool lower lobe. Every
 * layer keeps the full 108×108 viewport, so stacked at the same size they form the mark.
 */
@Immutable
internal class BrandLayers(val shadow: ImageVector, val stem: ImageVector, val upperLobe: ImageVector, val lowerLobe: ImageVector) {
    companion object {
        /** Path order in `ic_launcher_foreground`: shadow, stem, upper lobe + sheen, lower lobe + sheen. */
        const val PATH_COUNT = 6

        fun from(mark: ImageVector): BrandLayers = BrandLayers(
            shadow = mark.layer(0..0),
            stem = mark.layer(1..1),
            upperLobe = mark.layer(2..3),
            lowerLobe = mark.layer(4..5),
        )
    }
}

@Composable
internal fun rememberBrandLayers(): BrandLayers {
    val mark = ImageVector.vectorResource(R.drawable.ic_launcher_foreground)
    return remember(mark) { BrandLayers.from(mark) }
}

/** Draws one layer; the painter caches it in a bitmap, so moving it with graphicsLayer is cheap. */
@Composable
internal fun BrandLayer(layer: ImageVector, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(layer), contentDescription = null, modifier = modifier)
}

/** Number of paths in this vector (all groups). */
internal fun ImageVector.pathCount(): Int {
    fun count(group: VectorGroup): Int = group.sumOf { node -> if (node is VectorGroup) count(node) else if (node is VectorPath) 1 else 0 }
    return count(root)
}

/** A copy of this vector with only the paths whose overall index is in [indices], groups kept. */
private fun ImageVector.layer(indices: IntRange): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        autoMirror = false,
    )
    var index = 0
    fun copy(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorGroup -> {
                    builder.addGroup(
                        name = node.name,
                        rotate = node.rotation,
                        pivotX = node.pivotX,
                        pivotY = node.pivotY,
                        scaleX = node.scaleX,
                        scaleY = node.scaleY,
                        translationX = node.translationX,
                        translationY = node.translationY,
                        clipPathData = node.clipPathData,
                    )
                    copy(node)
                    builder.clearGroup()
                }
                is VectorPath -> {
                    if (index in indices) {
                        builder.addPath(
                            pathData = node.pathData,
                            pathFillType = node.pathFillType,
                            name = node.name,
                            fill = node.fill,
                            fillAlpha = node.fillAlpha,
                            stroke = node.stroke,
                            strokeAlpha = node.strokeAlpha,
                            strokeLineWidth = node.strokeLineWidth,
                            strokeLineCap = node.strokeLineCap,
                            strokeLineJoin = node.strokeLineJoin,
                            strokeLineMiter = node.strokeLineMiter,
                            trimPathStart = node.trimPathStart,
                            trimPathEnd = node.trimPathEnd,
                            trimPathOffset = node.trimPathOffset,
                        )
                    }
                    index++
                }
            }
        }
    }
    copy(root)
    return builder.build()
}
