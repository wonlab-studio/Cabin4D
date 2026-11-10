package com.example.cabin4d.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.cabin4d.CinemaViewModel
import com.example.cabin4d.CinemaViewModel.Companion.formatClock
import com.example.cabin4d.UiState
import com.example.cabin4d.safety.GateState
import kotlinx.coroutines.delay

private const val SKIP_MS = 10_000L
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 오른쪽 아래 버튼을 눌렀을 때 펼쳐지는 선택 목록 */
private enum class PlayerMenu { NONE, SPEED, SUBTITLE }

private fun speedLabel(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

/**
 * 재생 화면. 영상이 앱 창 전체를 쓰고, 조작 버튼은 YouTube 앱처럼 영상 위에 겹쳐 나온다.
 * - 화면을 한 번 누르면 버튼이 나타나고 3.5초 뒤 사라진다.
 * - 가운데: 10초 뒤로 · 재생/일시정지 · 10초 앞으로. 아래: 재생 시간과 탐색 막대.
 * - 화면 왼쪽·오른쪽을 두 번 누르면 10초씩 이동한다.
 * - 오른쪽 아래: 자막과 재생 속도.
 * - 오른쪽 위 스위치 하나로 4D 효과를 켜고 끈다. 끄거나, 멈추거나, 영상이 끝나거나, 화면을 나가면
 *   공조와 조명은 재생 전 설정으로 돌아간다.
 */
@Composable
fun PlayerScreen(ui: UiState, player: ExoPlayer?, vm: CinemaViewModel) {
    BackHandler { vm.close() }
    ImmersiveMode()

    var controlsVisible by remember { mutableStateOf(true) }
    // 화면을 만질 때마다 올려서 자동 숨김 타이머를 다시 시작한다
    var touchSerial by remember { mutableIntStateOf(0) }
    fun poke() {
        controlsVisible = true
        touchSerial++
    }
    var menu by remember { mutableStateOf(PlayerMenu.NONE) }

    // 재생 중이면 3.5초 뒤 조작 버튼을 숨긴다(자막·배속 목록이 열려 있는 동안은 그대로 둔다)
    LaunchedEffect(controlsVisible, ui.isPlaying, touchSerial, menu) {
        if (controlsVisible && ui.isPlaying && menu == PlayerMenu.NONE) {
            delay(3_500)
            controlsVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1) 영상(또는 효과 테스트 화면)
        if (player != null) {
            val exo: ExoPlayer = player
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        // 기본 컨트롤러를 끄고 이 화면의 버튼만 쓴다(재생은 항상 주행 게이트를 거치게)
                        useController = false
                        keepScreenOn = true
                        this.player = exo
                    }
                },
                update = { it.player = exo },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            SimulationStage(ui)
        }

        // 2) 한 번 누르면 버튼 보이기/숨기기, 좌우를 두 번 누르면 10초 이동
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            when {
                                // 목록이 열려 있으면 바깥을 눌러 목록만 닫는다
                                menu != PlayerMenu.NONE -> { menu = PlayerMenu.NONE; poke() }
                                controlsVisible -> controlsVisible = false
                                else -> poke()
                            }
                        },
                        onDoubleTap = { offset ->
                            when {
                                offset.x < size.width * 0.4f -> vm.seekBy(-SKIP_MS)
                                offset.x > size.width * 0.6f -> vm.seekBy(SKIP_MS)
                                else -> vm.togglePlay()
                            }
                            poke()
                        }
                    )
                }
        )

        // 3) 영상 위에 겹쳐 나오는 조작 버튼
        AnimatedVisibility(
            visible = controlsVisible || !ui.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // 버튼이 잘 보이도록 영상을 살짝 어둡게
                Box(Modifier.fillMaxSize().background(Color(0x73000000)))

                TopRow(
                    title = ui.trackTitle,
                    subtitle = listOf(ui.meta.attribution, ui.meta.license).filter { it.isNotBlank() }.joinToString(" · "),
                    effectsOn = ui.effectsOn,
                    showEffectSwitch = true,
                    onBack = vm::close,
                    onEffectsOn = { poke(); vm.setEffectsOn(it) },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                if (ui.error == null) {
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(64.dp)
                    ) {
                        SkipButton(forward = false) { poke(); vm.seekBy(-SKIP_MS) }
                        PlayPauseButton(ui.isPlaying, ui.buffering) { poke(); vm.togglePlay() }
                        SkipButton(forward = true) { poke(); vm.seekBy(SKIP_MS) }
                    }
                }

                SeekRow(
                    ui = ui,
                    showOptions = player != null,
                    onSeekTo = { poke(); vm.seekTo(it) },
                    onSubtitleClick = { poke(); menu = if (menu == PlayerMenu.SUBTITLE) PlayerMenu.NONE else PlayerMenu.SUBTITLE },
                    onSpeedClick = { poke(); menu = if (menu == PlayerMenu.SPEED) PlayerMenu.NONE else PlayerMenu.SPEED },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                val menuModifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 112.dp)
                when (menu) {
                    PlayerMenu.SPEED -> OptionMenu(
                        title = "재생 속도",
                        options = SPEEDS.map { (if (it == 1f) "1x (보통)" else speedLabel(it)) to (it == ui.speed) },
                        emptyNote = null,
                        onPick = { index -> vm.setSpeed(SPEEDS[index]); menu = PlayerMenu.NONE; poke() },
                        modifier = menuModifier
                    )
                    PlayerMenu.SUBTITLE -> OptionMenu(
                        title = "자막",
                        options = if (ui.subtitles.isEmpty()) emptyList() else
                            listOf("끄기" to (ui.subtitleLanguage == null)) +
                                ui.subtitles.map { it.label to (it.language == ui.subtitleLanguage) },
                        emptyNote = "이 영상에는 자막이 없습니다",
                        onPick = { index ->
                            vm.setSubtitle(if (index == 0) null else ui.subtitles[index - 1].language)
                            menu = PlayerMenu.NONE
                            poke()
                        },
                        modifier = menuModifier
                    )
                    PlayerMenu.NONE -> Unit
                }
            }
        }

        // 버튼이 숨어 있는 동안 버퍼링이면 가운데에 표시
        if (ui.buffering && ui.error == null && !controlsVisible && ui.isPlaying) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White)
        }
        ui.error?.let { message -> ErrorBox(message, onRetry = vm::retry, modifier = Modifier.align(Alignment.Center)) }

        // 4) 주행 규제 시 영상 위를 덮는다
        if (ui.gate == GateState.RESTRICTED) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)),
                contentAlignment = Alignment.Center
            ) {
                Text("주행 중에는 재생할 수 없습니다", color = Color.White, fontSize = 28.sp)
            }
        }
    }
}

/** 재생 화면에 있는 동안 상태 표시줄·내비게이션 바를 숨긴다(플랫폼이 허용하는 범위에서). */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** 위쪽 줄: 뒤로 가기, 제목, 4D 효과 스위치. YouTube 재생 화면도 같이 쓴다. */
@Composable
internal fun TopRow(
    title: String,
    subtitle: String,
    effectsOn: Boolean,
    showEffectSwitch: Boolean,
    onBack: () -> Unit,
    onEffectsOn: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        RoundButton(size = 52.dp, onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "목록으로", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Color(0xFFC8CDD4), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showEffectSwitch) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x80000000))
                    .clickable { onEffectsOn(!effectsOn) }
                    .padding(start = 18.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "4D 효과",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Switch(checked = effectsOn, onCheckedChange = onEffectsOn)
            }
        }
    }
}

/** 아래쪽 줄: 재생 시간, 자막·배속 버튼, 탐색 막대 */
@Composable
private fun SeekRow(
    ui: UiState,
    showOptions: Boolean,
    onSeekTo: (Long) -> Unit,
    onSubtitleClick: () -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = ui.durationMs.coerceAtLeast(1)
    val progress = if (dragging) dragValue else (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock(if (dragging) (dragValue * duration).toLong() else ui.positionMs)} / ${formatClock(ui.durationMs)}",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            if (showOptions) {
                OptionChip(
                    label = "자막",
                    value = ui.subtitles.firstOrNull { it.language == ui.subtitleLanguage }?.label ?: "끔",
                    onClick = onSubtitleClick
                )
                Spacer(Modifier.width(10.dp))
                OptionChip(label = "배속", value = speedLabel(ui.speed), onClick = onSpeedClick)
            }
        }
        Slider(
            value = progress,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeekTo((dragValue * duration).toLong())
                dragging = false
            }
        )
    }
}

/** 오른쪽 아래의 작은 설정 버튼(이름 + 지금 값) */
@Composable
private fun OptionChip(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0x80000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = Color(0xFFC8CDD4), fontSize = 14.sp, maxLines = 1, softWrap = false)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** 설정 버튼 위로 펼쳐지는 선택 목록. options = (이름, 지금 선택됨) */
@Composable
private fun OptionMenu(
    title: String,
    options: List<Pair<String, Boolean>>,
    emptyNote: String?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .width(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF21C1F24))
            .padding(vertical = 8.dp)
    ) {
        Text(title, color = Color(0xFFAEB4BD), fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (options.isEmpty() && emptyNote != null) {
            Text(emptyNote, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        options.forEachIndexed { index, (name, selected) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(index) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(32.dp)) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = "선택됨", tint = CabinColors.Accent, modifier = Modifier.size(22.dp))
                }
                Text(
                    name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun RoundButton(size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x80000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** 10초 이동 버튼. 둥근 화살표 안에 10을 넣은 모양(아이콘 묶음에 없어서 직접 그린다). */
@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    RoundButton(size = 80.dp, onClick = onClick) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.5.dp.toPx()
                val radius = size.minDimension / 2f - stroke * 2f
                val head = radius * 0.5f
                // 뒤로 가기(반시계 방향 화살표)를 기준으로 그리고, 앞으로 가기는 좌우를 뒤집는다
                scale(scaleX = if (forward) -1f else 1f, scaleY = 1f, pivot = center) {
                    // 12시에서 시계 방향으로 돌아 10시 근처에서 끝나는 호
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 290f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // 12시 자리에서 왼쪽을 가리키는 화살촉
                    val top = center.y - radius
                    val arrow = Path().apply {
                        moveTo(center.x - head, top)
                        lineTo(center.x + head * 0.25f, top - head * 0.8f)
                        lineTo(center.x + head * 0.25f, top + head * 0.8f)
                        close()
                    }
                    drawPath(arrow, Color.White)
                }
            }
            Text(
                "10",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, buffering: Boolean, onClick: () -> Unit) {
    RoundButton(size = 104.dp, onClick = onClick) {
        when {
            buffering -> CircularProgressIndicator(Modifier.size(48.dp), color = Color.White)
            isPlaying -> PauseGlyph()
            else -> Icon(Icons.Filled.PlayArrow, contentDescription = "재생", tint = Color.White, modifier = Modifier.size(64.dp))
        }
    }
}

/** material-icons-core에 일시정지 아이콘이 없어서 막대 두 개로 그린다 */
@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(width = 14.dp, height = 44.dp).background(Color.White, RoundedCornerShape(3.dp)))
        Box(Modifier.size(width = 14.dp, height = 44.dp).background(Color.White, RoundedCornerShape(3.dp)))
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xE6111316), RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(message, color = Color.White, fontSize = 16.sp)
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("다시 시도")
        }
    }
}

/** 영상 없이 효과만 내 보는 테스트 화면. 지금 나가는 효과와, 차량이 명령을 받아들였는지 읽어 본 결과를 보여 준다. */
@Composable
private fun SimulationStage(ui: UiState) {
    val bg = ui.ambientRgb?.let { rgbColor(it, 0.35f) } ?: CabinColors.Bg
    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .padding(start = 40.dp, end = 40.dp, top = 88.dp, bottom = 96.dp)
    ) {
        Text(
            "영상 없이 타임라인만 재생합니다. 아래는 차량이 명령을 실제로 받아들였는지 다시 읽어 본 결과입니다.",
            color = CabinColors.SubText,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LinearProgressIndicator(
            progress = { if (ui.durationMs > 0) ui.positionMs.toFloat() / ui.durationMs else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.width(420.dp)) { EffectPreview(ui) }
        // 차량 연결·반영 확인 기록 전체(최근 것이 아래)
        Column(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0x99000000), RoundedCornerShape(12.dp))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            ui.actuatorStatus.forEach { line ->
                Text(
                    line,
                    color = when {
                        "실패" in line || "보냈지만" in line -> Color(0xFFFFB4A8)
                        "반영 확인" in line -> Color(0xFF9BE3A8)
                        else -> Color(0xFFD5DAE1)
                    },
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
