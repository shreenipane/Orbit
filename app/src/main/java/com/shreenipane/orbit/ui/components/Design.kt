@file:OptIn(ExperimentalMaterial3Api::class)

package com.shreenipane.orbit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shreenipane.orbit.ui.theme.NumberStyle
import com.shreenipane.orbit.ui.theme.Pastel
import com.shreenipane.orbit.ui.theme.pastelFor

/**
 * The signature look: pastel fill, black outline, hard black shadow offset
 * down-right. The shadow is drawn outside the bounds, so leave a few dp of
 * room to the right and below.
 */
fun Modifier.brutal(
    fill: Color,
    radius: Dp = 14.dp,
    shadow: Dp = 4.dp,
    border: Dp = 2.dp,
): Modifier = this
    .drawBehind {
        if (shadow > 0.dp) {
            val s = shadow.toPx()
            drawRoundRect(Pastel.Ink, topLeft = Offset(s, s), size = size, cornerRadius = CornerRadius(radius.toPx()))
        }
    }
    .clip(RoundedCornerShape(radius))
    .background(fill)
    .border(border, Pastel.Ink, RoundedCornerShape(radius))

/** Tile colours cycle through the pastel set so a row of stats doesn't look flat. */
val TILE_COLORS = listOf(Pastel.Yellow, Pastel.Green, Pastel.Lavender, Pastel.Sky, Pastel.Salmon)

/**
 * A screen with a large hand-drawn title that scrolls with the content.
 * Pull to refresh when [onRefresh] is given.
 */
@Composable
fun OrbitScreen(
    title: String,
    subtitle: String? = null,
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottomPadding: Dp = 32.dp,
    content: LazyListScope.() -> Unit,
) {
    val list = @Composable {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 8.dp, bottom = bottomPadding),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineLarge, color = Pastel.Ink)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.labelLarge, color = Pastel.InkMuted)
                        }
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
    if (onRefresh != null) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { list() }
    } else {
        Box(Modifier.fillMaxSize()) { list() }
    }
}

/** A grouped section: small label, outlined pastel card with a hard shadow, optional footer. */
fun LazyListScope.section(
    header: String? = null,
    footer: String? = null,
    action: Pair<String, () -> Unit>? = null,
    fill: Color = Pastel.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    item {
        Column(Modifier.padding(top = 20.dp)) {
            if (header != null || action != null) {
                Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        header.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Pastel.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (action != null) {
                        Text(
                            action.first, style = MaterialTheme.typography.labelLarge, color = Pastel.Ink,
                            modifier = Modifier.brutal(Pastel.Yellow, radius = 8.dp, shadow = 2.dp, border = 1.5.dp)
                                .clickable(onClick = action.second).padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Column(Modifier.fillMaxWidth().brutal(fill), content = content)
            if (footer != null) {
                Text(
                    footer, style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp),
                )
            }
        }
    }
}

/** A list row inside a [section]. Put [RowDivider] between rows. */
@Composable
fun ListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (chevron) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                tint = Pastel.Ink, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** Divider between rows: a solid ink line, like the cards' outlines but thinner. */
@Composable
fun RowDivider(inset: Dp = 0.dp) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = 1.5.dp, color = Pastel.Ink.copy(alpha = 0.85f))
}

/** Rows with dividers between them. */
@Composable
fun <T> ColumnScope.Rows(items: List<T>, inset: Dp = 0.dp, row: @Composable (T) -> Unit) {
    items.forEachIndexed { i, it ->
        if (i > 0) RowDivider(inset)
        row(it)
    }
}

/** A progress ring with a black outline. */
@Composable
fun ThinRing(fraction: Float, color: Color, label: String, size: Dp = 46.dp, stroke: Dp = 5.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color.White).border(2.dp, Pastel.Ink, CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size - 4.dp)) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(Pastel.Hairline, -90f, 360f, false, tl, arc, style = Stroke(s))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        Text(label, fontWeight = FontWeight.Bold, fontSize = (size.value / 4.2f).sp, color = Pastel.Ink)
    }
}

/** A small outlined pastel label. [color] is a status colour; the fill is its pastel twin. */
@Composable
fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Pastel.Ink,
        maxLines = 1,
        modifier = Modifier.brutal(pastelFor(color), radius = 8.dp, shadow = 2.dp, border = 1.5.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Dot(color: Color, size: Dp = 9.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, Pastel.Ink, CircleShape))
}

/** A stat tile: big number, small caption, pastel fill. */
@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified, fill: Color = Pastel.Yellow) {
    Column(modifier.brutal(fill).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, style = NumberStyle, color = if (valueColor == Color.Unspecified) Pastel.Ink else valueColor, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Segmented control: outlined bar, the selected segment filled yellow with its own outline. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().brutal(Pastel.Card, radius = 12.dp, shadow = 3.dp).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (on) Modifier.clip(RoundedCornerShape(9.dp)).background(Pastel.Yellow)
                            .border(1.5.dp, Pastel.Ink, RoundedCornerShape(9.dp))
                        else Modifier.clip(RoundedCornerShape(9.dp)),
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (on) Pastel.Ink else Pastel.InkMuted, maxLines = 1)
            }
        }
    }
}

/**
 * Chunky pastel button with a hard shadow that "presses" into the page:
 * on press the button slides onto its shadow.
 */
@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Pastel.Yellow,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shift = if (pressed) 3.dp else 0.dp
    Row(
        modifier
            .offset(x = shift, y = shift)
            .brutal(if (enabled) fill else Pastel.CardLight, radius = 12.dp, shadow = if (pressed) 1.dp else 4.dp)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) Pastel.Ink else Pastel.InkMuted)
    }
}

/** Quiet empty state inside an outlined card. */
@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(top = 16.dp).fillMaxWidth().brutal(Pastel.Card).padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = Pastel.Ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = Pastel.InkMuted)
    }
}

@Composable
fun Banner(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 8.dp).brutal(Pastel.Salmon, radius = 12.dp, shadow = 3.dp)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = Pastel.Ink, modifier = Modifier.weight(1f))
        Text(
            action, style = MaterialTheme.typography.labelLarge, color = Pastel.Ink,
            modifier = Modifier.brutal(Pastel.Yellow, radius = 8.dp, shadow = 2.dp, border = 1.5.dp)
                .clickable(onClick = onAction).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** A hero card: big pastel block for "now/next" and "next holiday". */
@Composable
fun Hero(fill: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().brutal(fill, radius = 18.dp, shadow = 5.dp).padding(20.dp), content = content)
}

/** A leading time column for class rows. */
@Composable
fun TimeColumn(start: String, end: String) {
    Column(Modifier.width(52.dp)) {
        Text(start, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Pastel.Ink)
        Text(end, style = MaterialTheme.typography.bodySmall, color = Pastel.InkMuted)
    }
}

fun labLabel(isLab: Boolean) = if (isLab) "Lab" else "Theory"
