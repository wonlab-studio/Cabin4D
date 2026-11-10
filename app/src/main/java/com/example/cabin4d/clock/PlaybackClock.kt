package com.example.cabin4d.clock

import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * 이펙트 동기화의 기준 시계. 지금은 자체 플레이어와 시뮬레이션만 있다.
 * 외부 앱 연동(미디어 세션의 제목·재생 위치로 동기화)은 이 인터페이스를 하나 더 구현하면 된다.
 */
interface PlaybackClock {
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release() {}
}

/** 앱 안의 ExoPlayer 재생 위치를 기준으로 한다. 메인 스레드에서만 호출할 것. */
class ExoPlayerClock(val player: ExoPlayer) : PlaybackClock {
    override val positionMs get() = player.currentPosition
    override val durationMs get() = player.duration.takeIf { it > 0 } ?: 0L
    override val isPlaying get() = player.isPlaying
    override fun play() {
        // 끝까지 본 뒤 재생을 누르면 처음부터(시뮬레이션 모드와 같은 동작)
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }
    override fun pause() = player.pause()
    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun release() = player.release()

    val isReady get() = player.playbackState == Player.STATE_READY
}

/** 영상 없이 타임라인만 흐르는 테스트 모드. 에뮬레이터에서 효과만 빠르게 확인할 때 쓴다. */
class SimulatedClock(override val durationMs: Long) : PlaybackClock {
    private var startedAt = 0L
    private var offset = 0L
    override var isPlaying = false
        private set

    override val positionMs: Long
        get() {
            val p = if (isPlaying) offset + (SystemClock.elapsedRealtime() - startedAt) else offset
            if (p >= durationMs && isPlaying) {
                isPlaying = false
                offset = durationMs
            }
            return p.coerceAtMost(durationMs)
        }

    override fun play() {
        if (isPlaying) return
        if (offset >= durationMs) offset = 0
        startedAt = SystemClock.elapsedRealtime()
        isPlaying = true
    }

    override fun pause() {
        if (!isPlaying) return
        offset = positionMs
        isPlaying = false
    }

    override fun seekTo(positionMs: Long) {
        offset = positionMs.coerceIn(0, durationMs)
        startedAt = SystemClock.elapsedRealtime()
    }
}
