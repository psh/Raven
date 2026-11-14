package raven.sample.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import raven.compose.foundation.Button
import raven.compose.foundation.ButtonDefaults
import raven.compose.foundation.Canvas
import raven.compose.foundation.Image
import raven.compose.foundation.OutlinedButton
import raven.compose.foundation.Surface
import raven.compose.foundation.Text
import raven.compose.foundation.TextButton
import raven.compose.foundation.layout.Arrangement
import raven.compose.foundation.layout.Box
import raven.compose.foundation.layout.Column
import raven.compose.foundation.layout.Grid
import raven.compose.foundation.layout.GridTrack
import raven.compose.foundation.layout.Row
import raven.compose.foundation.layout.RowScope
import raven.compose.foundation.layout.fillMaxWidth
import raven.compose.foundation.layout.height
import raven.compose.foundation.layout.offset
import raven.compose.foundation.layout.padding
import raven.compose.foundation.layout.size
import raven.compose.foundation.layout.width
import raven.compose.ui.Alignment
import raven.compose.ui.AppearTransition
import raven.compose.ui.Modifier
import raven.compose.ui.PaddingValues
import raven.compose.ui.alpha
import raven.compose.ui.appearTransition
import raven.compose.ui.clip
import raven.compose.ui.geometry.Offset
import raven.compose.ui.geometry.Size
import raven.compose.ui.graphics.CircleShape
import raven.compose.ui.graphics.Color
import raven.compose.ui.graphics.ColorFilter
import raven.compose.ui.graphics.ImageBitmap
import raven.compose.ui.graphics.RoundedCornerShape
import raven.compose.ui.graphics.isSpecified
import raven.compose.ui.input.pointer.PointerEventType
import raven.compose.ui.input.pointer.PointerIcon
import raven.compose.ui.input.pointer.onPointerEvent
import raven.compose.ui.input.pointer.pointerHoverIcon
import raven.compose.ui.layout.ContentScale
import raven.compose.ui.semantics.semantics
import raven.compose.ui.text.font.FontWeight
import raven.compose.ui.theme.RavenTheme
import raven.compose.ui.tooltip
import raven.compose.ui.unit.Dp
import raven.compose.ui.unit.dp
import raven.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// The player's widgets, as composables: the C example's tags of widgets.xml (IconButton, AlbumCard,
// TrackRow...) and of tags.c (Icon, Cover, Visualizer, Heading). Each reads the look's MusicStyle.

/** The icons, made once the application runs. */
val LocalIcons = staticCompositionLocalOf<Icons> { error("no icons") }

/** A glyph, tinted with the text colour where it sits unless [tint] is given. */
@Composable
fun Icon(glyph: Glyph, size: Dp = 20.dp, modifier: Modifier = Modifier, tint: Color = Color.Unspecified) {
    Image(
        LocalIcons.current[glyph],
        contentDescription = null,
        modifier.size(size),
        colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else ColorFilter.textColor(),
    )
}

/** An album's cover, cropped to a square with rounded corners. */
@Composable
fun Cover(image: ImageBitmap, size: Dp, radius: Dp = 6.dp, modifier: Modifier = Modifier) {
    Image(image, contentDescription = null, modifier.size(size).clip(RoundedCornerShape(radius)), contentScale = ContentScale.Crop)
}

/** A label in bold, a heading for the screen reader. */
@Composable
fun Heading(text: String, size: Float, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(text, modifier.semantics { heading() }, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold)
}

/** Secondary text, in the look's muted colour. */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Float = 14f, wrap: Boolean = false) {
    val style = LocalMusicStyle.current
    val alpha = if (style.muted.isSpecified) 1f else style.mutedAlpha
    Text(text, modifier.alpha(alpha), color = style.muted, fontSize = size.sp, softWrap = wrap)
}

/** The small capitals over a section. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val style = LocalMusicStyle.current
    val own = if (color.isSpecified) color else style.eyebrow
    Text(text, modifier.alpha(if (own.isSpecified) 1f else style.eyebrowAlpha), color = own, fontSize = 12.sp)
}

/**
 * A round flat button with a glyph: the transport buttons, the heart... Its glyph brightens under
 * the pointer, as the theme's hovered icon colour; [tint] is its colour when it is on.
 */
@Composable
fun IconButton(
    glyph: Glyph,
    tip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    val style = LocalMusicStyle.current
    var hovered by remember { mutableStateOf(false) }
    val content = when {
        tint.isSpecified -> tint
        hovered && style.iconHovered.isSpecified -> style.iconHovered
        else -> style.icon
    }
    TextButton(
        onClick,
        modifier.tooltip(tip).pointerHoverIcon(PointerIcon.Hand).semantics { contentDescription = tip }
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false },
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(contentColor = content),
        contentPadding = PaddingValues(6.dp),
    ) { Icon(glyph, size) }
}

/** The big round button: play or pause. [white] is the player's white one. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp, iconSize: Dp = 22.dp, white: Boolean = false) {
    val colors = if (white) LocalMusicStyle.current.white.let { ButtonDefaults.buttonColors(it.container, it.content, hoveredContainerColor = it.hovered, pressedContainerColor = it.pressed) } else ButtonDefaults.buttonColors()
    Button(
        onClick,
        modifier.size(size).tooltip("Play / pause (Space)").pointerHoverIcon(PointerIcon.Hand).semantics { contentDescription = if (playing) "Pause" else "Play" },
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(0.dp),
    ) { Box(Modifier.size(size), contentAlignment = Alignment.Center) { Icon(if (playing) Glyph.Pause else Glyph.Play, iconSize) } }
}

/** An entry of the side bar; the one of the page shown is brighter. */
@Composable
fun NavItem(glyph: Glyph, text: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalMusicStyle.current
    val color = if (active) style.navActive else style.nav
    TextButton(
        onClick,
        modifier.pointerHoverIcon(PointerIcon.Hand).alpha(if (active || color.isSpecified) 1f else style.navAlpha).semantics { contentDescription = text },
        colors = ButtonDefaults.buttonColors(contentColor = color),
    ) {
        Row(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(glyph, 24.dp)
            Heading(text, 15f)
        }
    }
}

/** A small rounded filter, a button of the default style; [on] for the one chosen. */
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalMusicStyle.current
    val b = if (on) style.chipOn else style.chip
    OutlinedButton(
        onClick, modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(b.container, b.content, hoveredContainerColor = b.hovered, pressedContainerColor = b.pressed),
    ) { Text(text) }
}

/** The title of a row of cards, and its link. */
@Composable
fun SectionHeader(title: String, onShowAll: (() -> Unit)? = null) {
    val style = LocalMusicStyle.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Heading(title, 22f, Modifier.weight(1f))
        if (onShowAll != null) {
            TextButton(onShowAll, Modifier.pointerHoverIcon(PointerIcon.Hand).alpha(if (style.link.isSpecified) 1f else style.linkAlpha), colors = ButtonDefaults.buttonColors(contentColor = style.link)) {
                Text("Show all")
            }
        }
    }
}

/** A button styled as one of the look's cards: flat, or of the default style when [defaultStyle]. */
@Composable
private fun CardButton(
    b: ButtonStyle,
    radius: Float,
    onClick: () -> Unit,
    modifier: Modifier,
    label: String,
    defaultStyle: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = ButtonDefaults.buttonColors(b.container, b.content, hoveredContainerColor = b.hovered, pressedContainerColor = b.pressed)
    val shape = RoundedCornerShape(radius.dp)
    val described = modifier.pointerHoverIcon(PointerIcon.Hand).semantics { contentDescription = label }
    if (defaultStyle) {
        OutlinedButton(onClick, described, shape = shape, colors = colors, contentPadding = PaddingValues(0.dp), content = content)
    } else {
        TextButton(onClick, described, shape = shape, colors = colors, contentPadding = PaddingValues(0.dp), content = content)
    }
}

/** An album or a playlist as a card; [round] for an artist. */
@Composable
fun AlbumCard(cover: ImageBitmap, title: String, subtitle: String, onClick: () -> Unit, round: Boolean = false) {
    val style = LocalMusicStyle.current
    CardButton(style.card, style.cardRadius, onClick, Modifier.width(188.dp).appearTransition(AppearTransition.Fade), title) {
        Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Cover(cover, 168.dp, if (round) 84.dp else 8.dp)
            Heading(title, 14f, Modifier.padding(start = 2.dp, top = 6.dp))
            Muted(subtitle, Modifier.padding(horizontal = 2.dp).fillMaxWidth(), 13f, wrap = true)
        }
    }
}

/** A tile at the top of Home: a cover and a name, and bars while its album plays. */
@Composable
fun QuickCard(cover: ImageBitmap, title: String, playingHere: Boolean, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalMusicStyle.current
    CardButton(style.quick, style.quickRadius, onClick, modifier.height(60.dp), title, defaultStyle = true) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(cover, 60.dp, 6.dp)
            Heading(title, 14f, Modifier.weight(1f))
            if (playingHere) Visualizer(playing, 4, 2f, Modifier.padding(end = 12.dp).size(16.dp, 14.dp))
        }
    }
}

/** One song in a list: number, cover, title and artist, album, heart, length; the one playing in [highlight]. */
@Composable
fun TrackRow(
    library: Library,
    track: Track,
    number: Int,
    showAlbum: Boolean,
    highlight: Color,
    accent: Color,
    onPlay: () -> Unit,
    onLike: () -> Unit,
) {
    val style = LocalMusicStyle.current
    val album = library.albums[track.album]
    CardButton(style.row.copy(content = highlight), style.rowRadius, onPlay, Modifier.fillMaxWidth().height(56.dp), "${track.title}, ${album.artist}") {
        Grid(
            if (showAlbum) listOf(GridTrack.Auto, GridTrack.Fill(4f), GridTrack.Fill(3f), GridTrack.Auto, GridTrack.Auto) else listOf(GridTrack.Auto, GridTrack.Fill(), GridTrack.Auto, GridTrack.Auto, GridTrack.Auto),
            emptyList(),
            Modifier.weight(1f).padding(horizontal = 6.dp),
            spacing = 14.dp,
        ) {
            Muted("$number", Modifier.cell(0, 0).align(Alignment.CenterVertically).width(26.dp))
            Row(Modifier.cell(1, 0).align(Alignment.CenterVertically), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showAlbum) Cover(album.cover, 40.dp, 4.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(track.title, fontSize = 15.sp)
                    Muted(album.artist, size = 13f)
                }
            }
            if (showAlbum) Muted(album.title, Modifier.cell(2, 0).align(Alignment.CenterStart), 13f)
            TextButton(
                onLike,
                Modifier.cell(3, 0).align(Alignment.CenterVertically).tooltip(if (track.liked) "Remove from Liked songs" else "Save to Liked songs"),
                shape = CircleShape,
                contentPadding = PaddingValues(6.dp),
            ) { Icon(if (track.liked) Glyph.HeartFilled else Glyph.Heart, 16.dp, Modifier.alpha(if (track.liked) 1f else 0.55f), tint = if (track.liked) accent else Color.Unspecified) }
            Muted(formatTime(track.seconds), Modifier.cell(4, 0).align(Alignment.CenterVertically).width(44.dp), 13f)
        }
    }
}

/** An entry of "Your library" in the side bar. */
@Composable
fun LibraryRow(cover: ImageBitmap, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier, round: Boolean = false) {
    val style = LocalMusicStyle.current
    CardButton(style.row, style.rowRadius, onClick, modifier.fillMaxWidth(), title) {
        Row(Modifier.weight(1f).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(cover, 46.dp, if (round) 23.dp else 5.dp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 14.sp)
                Muted(subtitle, size = 12f)
            }
        }
    }
}

/** A genre in Search: a coloured tile, a cover in its corner. */
@Composable
fun GenreTile(title: String, color: Color, cover: ImageBitmap, onClick: () -> Unit) {
    TextButton(onClick, Modifier.pointerHoverIcon(PointerIcon.Hand).semantics { contentDescription = title }, contentPadding = PaddingValues(0.dp)) {
        Surface(Modifier.size(184.dp, 112.dp), shape = RoundedCornerShape(10.dp), color = color) {
            Box(Modifier.size(184.dp, 112.dp)) {
                Heading(title, 22f, Modifier.align(Alignment.TopStart).padding(horizontal = 16.dp, vertical = 14.dp), color = Color.White)
                Cover(cover, 80.dp, 6.dp, Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 8.dp))
            }
        }
    }
}

/** A setting: its name and what it does on the left, the control on the right. */
@Composable
fun SettingRow(title: String, description: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 15.sp)
            Muted(description, Modifier.fillMaxWidth(), 13f, wrap = true)
        }
        control()
    }
}

/** Some music that is not there: a few waves per bar, a beat every half second. */
private fun fakeLevel(bar: Int, bars: Int, time: Double): Float {
    val x = bar.toFloat() / max(bars - 1, 1)
    val beat = (0.5f + 0.5f * cos((time % 0.5).toFloat() * 12.566f)).pow(6)
    val bass = (1f - x) * (0.55f + 0.45f * beat)
    val body = 0.5f + 0.25f * sin(time.toFloat() * (3.1f + bar * 0.37f) + bar * 1.7f) + 0.2f * sin(time.toFloat() * (7.3f + bar * 0.11f) + bar)
    return (0.15f + bass * 0.6f + body * (0.35f + x * 0.25f) * (1f - x * 0.35f)).coerceIn(0.05f, 1f)
}

/**
 * Bars that dance while it plays, in the accent: a Canvas whose levels a withFrameNanos loop moves
 * toward the music that is not there, and down to rest when it stops.
 */
@Composable
fun Visualizer(playing: Boolean, bars: Int, gap: Float, modifier: Modifier = Modifier) {
    val levels = remember(bars) { FloatArray(bars) }
    var frame by remember { mutableIntStateOf(0) }
    val accent = RavenTheme.colors.accent
    LaunchedEffect(playing, bars) {
        var last = withFrameNanos { it }
        var time = last / 1e9
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9).toFloat()
            last = now
            time += dt
            var moving = false
            for (i in 0 until bars) {
                val target = if (playing) fakeLevel(i, bars, time) else 0.08f
                val speed = if (target > levels[i]) 22f else if (playing) 9f else 4f
                levels[i] += (target - levels[i]) * (dt * speed).coerceIn(0f, 1f)
                if (kotlin.math.abs(target - levels[i]) > 0.002f) moving = true
            }
            frame++
            // Stopped and at rest: nothing moves, so no more frames are asked for.
            if (!playing && !moving) break
        }
    }
    Canvas(modifier) {
        frame
        val width = (size.width - gap * (bars - 1)) / bars
        val barWidth = max(width, 1f)
        for (i in 0 until bars) {
            val h = max(levels[i] * size.height, barWidth)
            drawRoundRect(
                Color(accent.red, accent.green, accent.blue, accent.alpha * (0.55f + 0.45f * levels[i])),
                Offset(i * (barWidth + gap), size.height - h), Size(barWidth, h), cornerRadius = barWidth * 0.5f,
            )
        }
    }
}
