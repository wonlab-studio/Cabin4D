package com.example.cabin4d

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.cabin4d.catalog.YouTubeVideo
import com.example.cabin4d.clock.ExoPlayerClock
import com.example.cabin4d.clock.PlaybackClock
import com.example.cabin4d.clock.SimulatedClock
import com.example.cabin4d.clock.YouTubeClock
import com.example.cabin4d.effect.Channel
import com.example.cabin4d.effect.Command
import com.example.cabin4d.effect.EffectScheduler
import com.example.cabin4d.effect.EffectTrack
import com.example.cabin4d.effect.EffectTrackParser
import com.example.cabin4d.effect.EffectValue
import com.example.cabin4d.effect.SubtitleSource
import com.example.cabin4d.effect.TrackMeta
import com.example.cabin4d.safety.DrivingGate
import com.example.cabin4d.safety.GateState
import com.example.cabin4d.vehicle.CabinActuator
import com.example.cabin4d.vehicle.LoggingActuator
import com.example.cabin4d.vehicle.PleosActuator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    /** 목록 화면에 보여 줄 트랙들(assets/tracks 폴더의 json 파일) */
    val library: List<EffectTrack> = emptyList(),
    /** 재생 중인 트랙 id. null이면 목록 화면 */
    val currentId: String? = null,
    val trackTitle: String = "",
    val meta: TrackMeta = TrackMeta(),
    /** YouTube 플레이어로 재생 중이면 그 영상. 직접 재생이면 null */
    val youtube: YouTubeVideo? = null,
    /** 지금 여는 영상에 4D 이펙트 트랙이 있는지 */
    val hasTrack: Boolean = true,
    val videoMode: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val effectsOn: Boolean = true,
    val intensity: Float = 0.7f,
    /** 재생 속도(1.0 = 보통) */
    val speed: Float = 1f,
    /** 이 영상에서 고를 수 있는 자막 */
    val subtitles: List<SubtitleSource> = emptyList(),
    /** 켜 둔 자막의 언어 코드. null이면 자막 끔 */
    val subtitleLanguage: String? = null,
    val gate: GateState = GateState.UNKNOWN,
    val actuatorName: String = "",
    val actuatorStatus: List<String> = emptyList(),
    val log: List<String> = emptyList(),
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    /** 미리보기용 현재 효과 상태(null = 사용자 원래 설정) */
    val wind: Float? = null,
    val ambientRgb: Int? = null,
    val direction: String? = null,
    /** 실내등 효과 상태(false = 암전 중, null = 원래 설정) */
    val cabinLight: Boolean? = null,
    /** 독서등 효과 상태(true = 번쩍이는 중, null = 원래 설정) */
    val readingLight: Boolean? = null,
    val lastNote: String = ""
)

class CinemaViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /** 화면(PlayerView)에 붙일 플레이어. 시뮬레이션 모드거나 목록 화면이면 null */
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    /** YouTube 재생 화면이 WebView 플레이어와 이어 붙일 시계. YouTube 재생 중이 아니면 null */
    private val _youtubeClock = MutableStateFlow<YouTubeClock?>(null)
    val youtubeClock: StateFlow<YouTubeClock?> = _youtubeClock

    private val tracks: List<EffectTrack> = loadLibrary(app)
    private val actuator: CabinActuator =
        runCatching<CabinActuator> { PleosActuator(app) }.getOrElse { LoggingActuator() }
    private val gate = DrivingGate(app)

    /** 영화 한 편을 여는 동안의 상태 */
    private class Session(
        val track: EffectTrack,
        val scheduler: EffectScheduler,
        val clock: PlaybackClock,
        val player: ExoPlayer?
    )

    private var session: Session? = null

    init {
        _ui.update { it.copy(library = tracks, actuatorName = actuator.name) }

        actuator.start(::addStatus)
        gate.start()

        viewModelScope.launch {
            gate.state.collect { state ->
                _ui.update { it.copy(gate = state) }
                val s = session
                if (state == GateState.RESTRICTED && s != null) {
                    s.clock.pause()
                    emit(s.scheduler.stop(), s.clock.positionMs)
                    addLog("주행 규제 감지: 재생과 효과를 멈추고 원래 설정으로 복귀")
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    // ------------------------------------------------------------ 목록 ↔ 재생

    fun open(trackId: String) {
        close()
        val track = tracks.firstOrNull { it.id == trackId } ?: return
        val scheduler = EffectScheduler(track).also { it.intensity = _ui.value.intensity }
        val media = track.mediaUri?.takeIf(::mediaExists)
        val player = media?.let { createPlayer(it, track.meta.subtitles) }
        val clock: PlaybackClock = player?.let { ExoPlayerClock(it) } ?: SimulatedClock(track.durationMs)
        session = Session(track, scheduler, clock, player)
        _player.value = player
        _ui.update {
            it.copy(
                currentId = track.id,
                trackTitle = track.title,
                meta = track.meta,
                youtube = null,
                hasTrack = true,
                speed = 1f,
                subtitles = if (player != null) track.meta.subtitles else emptyList(),
                subtitleLanguage = null,
                videoMode = player != null,
                buffering = player != null,
                error = null,
                positionMs = 0,
                durationMs = track.durationMs,
                isPlaying = false,
                wind = null,
                ambientRgb = null,
                direction = null,
                cabinLight = null,
                readingLight = null,
                lastNote = "",
                log = emptyList()
            )
        }
    }

    /**
     * YouTube 영상을 공식 플레이어로 연다. 같은 영상의 4D 트랙이 있으면 플레이어의 재생 위치에 맞춰 효과를 낸다.
     * 트랙이 없는 영상은 효과 없이 재생만 한다.
     */
    fun openYouTube(video: YouTubeVideo) {
        close()
        val matched = video.trackId?.let { id -> tracks.firstOrNull { it.id == id } }
        val track = matched ?: EffectTrack(
            id = "youtube-" + video.videoId,
            title = video.title,
            mediaUri = null,
            durationMs = 0,
            cues = emptyList(),
            meta = TrackMeta(description = video.blurb, attribution = video.channel, license = "YouTube")
        )
        val scheduler = EffectScheduler(track).also { it.intensity = _ui.value.intensity }
        val clock = YouTubeClock(track.durationMs)
        session = Session(track, scheduler, clock, null)
        _youtubeClock.value = clock
        _ui.update {
            it.copy(
                currentId = track.id,
                trackTitle = video.title,
                meta = track.meta,
                youtube = video,
                hasTrack = matched != null,
                speed = 1f,
                subtitles = emptyList(),
                subtitleLanguage = null,
                videoMode = true,
                buffering = true,
                error = null,
                positionMs = 0,
                durationMs = track.durationMs,
                isPlaying = false,
                wind = null,
                ambientRgb = null,
                direction = null,
                cabinLight = null,
                readingLight = null,
                lastNote = "",
                log = emptyList()
            )
        }
    }

    /** YouTube 플레이어가 알려 준 오류(임베드 금지, 삭제된 영상 등) */
    fun onYouTubeError(code: Int) {
        val reason = when (code) {
            2 -> "영상 주소가 올바르지 않습니다"
            5 -> "이 기기에서 재생할 수 없는 형식입니다"
            100 -> "삭제되었거나 비공개인 영상입니다"
            101, 150 -> "게시자가 다른 앱에서의 재생을 막아 둔 영상입니다"
            153 -> "플레이어가 앱 정보를 확인하지 못했습니다"
            else -> "영상을 불러오지 못했습니다"
        }
        _ui.update { it.copy(buffering = false, error = reason + " (YouTube 오류 " + code + ")") }
    }

    /** 목록으로 돌아간다. 효과를 원래 설정으로 되돌리고 플레이어를 정리한다. */
    fun close() {
        val s = session ?: return
        session = null
        emit(s.scheduler.stop(), s.clock.positionMs)
        _player.value = null
        _youtubeClock.value = null
        s.clock.release()
        _ui.update { it.copy(currentId = null, youtube = null, hasTrack = true, isPlaying = false, buffering = false, error = null) }
    }

    // ------------------------------------------------------------ 재생 조작

    fun togglePlay() {
        val s = session ?: return
        if (s.clock.isPlaying) {
            s.clock.pause()
        } else if (_ui.value.gate == GateState.RESTRICTED) {
            addLog("주행 중에는 재생할 수 없습니다")
        } else {
            s.clock.play()
        }
    }

    fun pausePlayback() {
        session?.clock?.let { if (it.isPlaying) it.pause() }
    }

    fun seekTo(positionMs: Long) {
        val s = session ?: return
        val duration = s.clock.durationMs.takeIf { it > 0 } ?: s.track.durationMs
        s.clock.seekTo(positionMs.coerceIn(0, duration))
    }

    fun seekBy(deltaMs: Long) {
        val s = session ?: return
        seekTo(s.clock.positionMs + deltaMs)
    }

    /** 스트리밍 오류 뒤 다시 시도 */
    fun retry() {
        val p = session?.player ?: return
        _ui.update { it.copy(error = null, buffering = true) }
        p.prepare()
    }

    /** 재생 속도를 바꾼다. 4D 효과는 재생 위치를 따라가므로 속도가 달라져도 장면과 맞는다. */
    fun setSpeed(speed: Float) {
        val p = session?.player ?: return
        p.setPlaybackSpeed(speed)
        _ui.update { it.copy(speed = speed) }
    }

    /** 자막을 고른다. null이면 끈다. */
    fun setSubtitle(language: String?) {
        val p = session?.player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, language == null)
            .setPreferredTextLanguage(language)
            .build()
        _ui.update { it.copy(subtitleLanguage = language) }
    }

    fun setEffectsOn(on: Boolean) {
        _ui.update { it.copy(effectsOn = on) }
        if (!on) addLog("4D 효과 OFF: 원래 설정으로 복귀")
    }

    fun setIntensity(value: Float) {
        session?.scheduler?.intensity = value
        _ui.update { it.copy(intensity = value) }
    }

    // ------------------------------------------------------------ 내부

    private fun createPlayer(uri: String, subtitles: List<SubtitleSource>): ExoPlayer =
        ExoPlayer.Builder(getApplication())
            // 차량 오디오 포커스 정책을 따르도록 미디어 용도로 포커스를 요청한다
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _ui.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "재생 오류", error)
                        _ui.update {
                            it.copy(
                                buffering = false,
                                error = "영상을 불러오지 못했습니다. 네트워크 연결을 확인하세요.\n(${error.errorCodeName})"
                            )
                        }
                    }
                })
                // 자막은 처음에는 꺼 두고, 사용자가 고르면 켠다
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setSubtitleConfigurations(
                            subtitles.map { sub ->
                                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.uri))
                                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                    .setLanguage(sub.language)
                                    .setLabel(sub.label)
                                    .build()
                            }
                        )
                        .build()
                )
                prepare()
            }

    private fun tick() {
        val s = session ?: return
        val state = _ui.value
        // 주행 규제 중에는 어떤 경로로든(플레이어 안의 재생 버튼 포함) 재생이 시작되면 바로 멈춘다
        if (state.gate == GateState.RESTRICTED && s.clock.isPlaying) s.clock.pause()
        val position = s.clock.positionMs
        val commands = if (state.effectsOn && state.gate != GateState.RESTRICTED) {
            s.scheduler.tick(position, s.clock.isPlaying, SystemClock.elapsedRealtime())
        } else {
            s.scheduler.stop()
        }
        emit(commands, position)
        _ui.update {
            it.copy(
                positionMs = position,
                durationMs = s.clock.durationMs.takeIf { d -> d > 0 } ?: s.track.durationMs,
                isPlaying = s.clock.isPlaying,
                // YouTube는 플레이어가 뜨기 전까지만 로딩으로 본다(그 뒤의 버퍼링 표시는 플레이어가 직접 한다)
                buffering = (s.clock as? YouTubeClock)?.let { yt -> !yt.isReady } ?: it.buffering
            )
        }
    }

    private fun emit(commands: List<Command>, positionMs: Long) {
        commands.forEach { command ->
            actuator.apply(command)
            updatePreview(command)
            addLog(describe(command, positionMs))
        }
    }

    private fun updatePreview(command: Command) = _ui.update { s ->
        when (command) {
            is Command.Apply -> {
                val next = when (val v = command.value) {
                    is EffectValue.Wind -> s.copy(wind = v.level)
                    is EffectValue.Ambient -> s.copy(ambientRgb = v.rgb)
                    is EffectValue.Direction -> s.copy(direction = v.direction.name)
                    is EffectValue.CabinLight -> s.copy(cabinLight = v.on)
                    is EffectValue.ReadingLight -> s.copy(readingLight = v.on)
                }
                if (command.note.isNotBlank()) next.copy(lastNote = command.note) else next
            }
            is Command.Restore -> when (command.channel) {
                Channel.WIND -> s.copy(wind = null)
                Channel.AMBIENT -> s.copy(ambientRgb = null)
                Channel.AIR_DIRECTION -> s.copy(direction = null)
                Channel.CABIN_LIGHT -> s.copy(cabinLight = null)
                Channel.READING_LIGHT -> s.copy(readingLight = null)
            }
        }
    }

    private fun describe(command: Command, positionMs: Long): String {
        val at = formatTime(positionMs)
        return when (command) {
            is Command.Apply -> {
                val what = when (val v = command.value) {
                    is EffectValue.Wind -> "바람 ${"%.2f".format(v.level)}"
                    is EffectValue.Ambient -> "조명 #%06X 밝기 %.1f".format(v.rgb, v.brightness)
                    is EffectValue.Direction -> "송풍 ${v.direction}"
                    is EffectValue.CabinLight -> if (v.on) "실내등 켬" else "실내등 끔(암전)"
                    is EffectValue.ReadingLight -> if (v.on) "독서등 번쩍" else "독서등 끔"
                }
                "$at  ▶ $what${if (command.note.isNotBlank()) " · ${command.note}" else ""}"
            }
            is Command.Restore -> "$at  ↺ ${command.channel} 원래 설정"
        }
    }

    private fun addLog(line: String) = _ui.update { it.copy(log = (listOf(line) + it.log).take(40)) }

    private fun addStatus(line: String) = _ui.update { it.copy(actuatorStatus = (it.actuatorStatus + line).takeLast(40)) }

    private fun mediaExists(uri: String): Boolean {
        if (!uri.startsWith(ASSET_PREFIX)) return true
        return runCatching {
            getApplication<Application>().assets.open(uri.removePrefix(ASSET_PREFIX)).close()
        }.isSuccess
    }

    override fun onCleared() {
        close()
        actuator.release()
        gate.stop()
    }

    companion object {
        private const val TAG = "Cabin4D"
        private const val TRACK_DIR = "tracks"
        private const val ASSET_PREFIX = "asset:///"
        private const val TICK_MS = 50L

        /** assets/tracks/ 의 모든 트랙을 읽는다. 형식이 잘못된 파일은 건너뛰고 로그를 남긴다. */
        private fun loadLibrary(app: Application): List<EffectTrack> =
            (app.assets.list(TRACK_DIR) ?: emptyArray())
                .filter { it.endsWith(".json") }
                .mapNotNull { name ->
                    runCatching {
                        EffectTrackParser.parse(app.assets.open("$TRACK_DIR/$name").bufferedReader().use { it.readText() })
                    }.onFailure { Log.w(TAG, "트랙 $name 을(를) 읽지 못함", it) }.getOrNull()
                }
                .sortedWith(compareBy({ it.meta.order }, { it.title }))

        fun formatTime(ms: Long): String {
            val s = ms / 1000
            return "%02d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
        }

        /** 플레이어 표시용 m:ss 또는 h:mm:ss */
        fun formatClock(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
            else "%d:%02d".format(s / 60, s % 60)
        }
    }
}
