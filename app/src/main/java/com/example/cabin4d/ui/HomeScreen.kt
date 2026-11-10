package com.example.cabin4d.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cabin4d.R
import com.example.cabin4d.UiState
import com.example.cabin4d.catalog.YouTubeCatalog
import com.example.cabin4d.catalog.YouTubeVideo
import com.example.cabin4d.effect.Channel
import com.example.cabin4d.effect.EffectTrack
import com.example.cabin4d.safety.GateState
import kotlinx.coroutines.delay

private val SidePadding = 40.dp
private const val HERO_ROTATE_MS = 9000L

/** 직접 재생하는 4D 영화의 스틸컷(영화 속 장면, Blender Foundation CC BY 3.0) */
private fun stillFor(trackId: String): Int? = when (trackId) {
    "sintel" -> R.drawable.still_sintel
    "tears-of-steel" -> R.drawable.still_tos
    "big-buck-bunny" -> R.drawable.still_bbb
    else -> null
}

/**
 * 홈 화면. 위에는 대표 영화를 크게 보여 주고, 아래에는 가로로 넘기는 선반을 둔다.
 * - 4D로 보는 영화: 앱이 직접 재생하는 영화(4D 트랙 있음)
 * - YouTube: 공식 YouTube 플레이어로 재생하는 영상. 4D 트랙이 있는 영상에는 4D 표시가 붙는다.
 */
@Composable
fun HomeScreen(
    ui: UiState,
    onOpenTrack: (String) -> Unit,
    onOpenYouTube: (YouTubeVideo) -> Unit
) {
    val films = remember(ui.library) { ui.library.filter { it.mediaUri != null } }
    val tools = remember(ui.library) { ui.library.filter { it.mediaUri == null } }

    // 대표 영화는 몇 초마다 다음 영화로 넘어간다
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(films.size) {
        if (films.size > 1) {
            while (true) {
                delay(HERO_ROTATE_MS)
                heroIndex = (heroIndex + 1) % films.size
            }
        }
    }
    val featured = if (films.isEmpty()) null else films[heroIndex % films.size]

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(CabinColors.Bg),
        contentPadding = PaddingValues(bottom = 48.dp)
    ) {
        item {
            Hero(
                track = featured,
                gate = ui.gate,
                count = films.size,
                index = if (films.isEmpty()) 0 else heroIndex % films.size,
                onSelect = { heroIndex = it },
                onPlay = { featured?.let { onOpenTrack(it.id) } },
                youtube = featured?.let { YouTubeCatalog.forTrack(it.id) },
                onOpenYouTube = onOpenYouTube
            )
        }
        item {
            Shelf("4D로 보는 영화", "장면에 맞춰 바람과 조명이 함께 움직입니다") {
                items(films, key = { it.id }) { track ->
                    FilmCard(track, onClick = { onOpenTrack(track.id) })
                }
                items(tools, key = { it.id }) { track ->
                    ToolCard(track, onClick = { onOpenTrack(track.id) })
                }
            }
        }
        item {
            Shelf("YouTube", "YouTube 플레이어로 재생합니다. 4D 표시가 있는 영상은 효과가 같이 나옵니다") {
                items(YouTubeCatalog.videos, key = { it.videoId }) { video ->
                    YouTubeCard(video, onClick = { onOpenYouTube(video) })
                }
            }
        }
        item {
            Text(
                "Sintel, Tears of Steel, Big Buck Bunny © Blender Foundation · CC BY 3.0. " +
                    "YouTube 영상은 YouTube 플레이어로 재생되며 권리는 각 채널에 있습니다.",
                color = CabinColors.Dim,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = SidePadding, end = SidePadding, top = 28.dp)
            )
        }
    }
}

// ---------------------------------------------------------------- 대표 영화

@Composable
private fun Hero(
    track: EffectTrack?,
    gate: GateState,
    count: Int,
    index: Int,
    onSelect: (Int) -> Unit,
    onPlay: () -> Unit,
    youtube: YouTubeVideo?,
    onOpenYouTube: (YouTubeVideo) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(430.dp)
    ) {
        Crossfade(targetState = track?.id, animationSpec = tween(600), label = "hero") { id ->
            val still = id?.let { stillFor(it) }
            if (still != null) {
                Image(
                    painter = painterResource(still),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(CabinColors.Card))
            }
        }
        // 글자가 놓이는 왼쪽과 위아래를 어둡게 덮는다
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF20B0D10),
                        0.42f to Color(0xA60B0D10),
                        0.78f to Color(0x000B0D10)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB30B0D10),
                        0.22f to Color(0x000B0D10),
                        0.6f to Color(0x000B0D10),
                        1f to CabinColors.Bg
                    )
                )
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SidePadding, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Wordmark()
            Spacer(Modifier.weight(1f))
            // 평소에는 아무것도 띄우지 않고, 주행 규제로 재생이 막혔을 때만 알린다
            if (gate == GateState.RESTRICTED) GateChip(gate)
        }

        if (track != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = SidePadding, bottom = 44.dp)
                    .width(560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FourDBadge()
                    Text("바람 · 무드등 · 천장 조명", color = CabinColors.SubText, fontSize = 14.sp)
                }
                Text(
                    track.title,
                    color = Color.White,
                    fontSize = 54.sp,
                    lineHeight = 58.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(metaLine(track), color = Color(0xFFD5DAE1), fontSize = 15.sp, maxLines = 1)
                Text(
                    track.meta.description,
                    color = Color(0xFFC3C9D1),
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroButton("4D로 재생", primary = true, onClick = onPlay)
                    if (youtube != null) {
                        HeroButton("YouTube로 보기", primary = false, onClick = { onOpenYouTube(youtube) })
                    }
                }
            }

            // 영화 전체의 무드등 색 흐름
            MoodStrip(
                track.moodTimeline(96),
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = SidePadding, vertical = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
            )
        }

        if (count > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = SidePadding, bottom = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0 until count) {
                    // 누르기 쉽게 점 둘레에 여백을 둔다
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(if (i == index) 12.dp else 9.dp)
                                .background(if (i == index) Color.White else Color(0x66FFFFFF), CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val content = if (primary) Color.Black else Color.White
    Row(
        Modifier
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) Color.White else Color(0x47FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        Text(label, color = content, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("CABIN", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        FourDBadge()
    }
}

/** 4D 트랙이 있다는 표시. 바람(하늘색)에서 빛(호박색)으로 넘어가는 띠. */
@Composable
fun FourDBadge(modifier: Modifier = Modifier) {
    Text(
        "4D",
        color = Color(0xFF10131A),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .background(
                Brush.horizontalGradient(listOf(CabinColors.FourDStart, CabinColors.FourDEnd)),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// ---------------------------------------------------------------- 선반과 카드

@Composable
private fun Shelf(title: String, caption: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.padding(top = 26.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = SidePadding)
        )
        Text(
            caption,
            color = CabinColors.Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = SidePadding, end = SidePadding, top = 2.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SidePadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun FilmCard(track: EffectTrack, onClick: () -> Unit) {
    Column(
        Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(CabinColors.Card)
        ) {
            val still = stillFor(track.id)
            if (still != null) {
                Image(
                    painter = painterResource(still),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            FourDBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            CornerLabel(lengthText(track.durationMs), Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 14.dp))
            MoodStrip(track.moodTimeline(48), Modifier.align(Alignment.BottomStart).fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        Text(track.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(effectLine(track), color = CabinColors.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
    }
}

/** 영상 없이 효과만 내 보는 테스트 트랙 카드 */
@Composable
private fun ToolCard(track: EffectTrack, onClick: () -> Unit) {
    val colors = remember(track) {
        track.moodTimeline(8).filterNotNull().map { rgbColor(it, 0.75f) }.ifEmpty { listOf(CabinColors.Card) }
    }
    Column(
        Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (colors.size > 1) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(colors[0], colors[0])))
        ) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
            Text(
                "효과만 켜 보기",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            CornerLabel(lengthText(track.durationMs), Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(track.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("영상 없이 바람·조명이 차례로 나옵니다", color = CabinColors.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun YouTubeCard(video: YouTubeVideo, onClick: () -> Unit) {
    val thumbnail = rememberNetworkImage(video.thumbnails)
    Column(
        Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(CabinColors.Card)
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (video.trackId != null) FourDBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
            CornerLabel("YouTube", Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(video.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${video.channel} · ${video.year}", color = CabinColors.Dim, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CornerLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(Color(0xB3000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ---------------------------------------------------------------- 문구

private fun lengthText(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    return if (min >= 1) "${min}분" else "${totalSec}초"
}

private fun windCount(track: EffectTrack) = track.cues.count { it.channel == Channel.WIND }

private fun lightCount(track: EffectTrack) =
    track.cues.count { it.channel == Channel.AMBIENT || it.channel == Channel.CABIN_LIGHT || it.channel == Channel.READING_LIGHT }

private fun effectLine(track: EffectTrack): String =
    listOfNotNull(track.meta.year?.toString(), "바람 ${windCount(track)}", "조명 ${lightCount(track)}").joinToString(" · ")

private fun metaLine(track: EffectTrack): String =
    listOfNotNull(
        track.meta.year?.toString(),
        lengthText(track.durationMs),
        "바람 효과 ${windCount(track)}개",
        "조명 효과 ${lightCount(track)}개",
        if (track.meta.highlights.isNotEmpty()) "명장면 ${track.meta.highlights.size}곳" else null
    ).joinToString(" · ")
