package com.example.cabin4d.clock

import android.os.SystemClock

/**
 * YouTube IFrame 플레이어의 재생 위치를 기준으로 하는 시계.
 *
 * 플레이어(WebView 안의 자바스크립트)가 0.25초마다 위치·상태를 알려 주고([onReport]), 그 사이는 경과 시간으로
 * 이어 붙인다. 재생·정지·탐색은 화면이 붙여 준 [runJs]로 플레이어 API를 부른다.
 * 모든 호출은 메인 스레드에서 한다.
 */
class YouTubeClock(private val fallbackDurationMs: Long) : PlaybackClock {
    private var basePositionMs = 0L
    private var baseAt = 0L
    private var rate = 1f
    private var reportedDurationMs = 0L

    override var isPlaying = false
        private set

    /** 플레이어가 버퍼링 중인지 */
    var isBuffering = false
        private set

    /** 플레이어가 준비되어 첫 보고가 들어왔는지 */
    var isReady = false
        private set

    /** 화면의 WebView가 붙으면 자바스크립트 실행 함수가 들어온다. 화면이 사라지면 null. */
    var runJs: ((String) -> Unit)? = null

    override val positionMs: Long
        get() {
            val p = if (isPlaying) {
                basePositionMs + ((SystemClock.elapsedRealtime() - baseAt) * rate).toLong()
            } else {
                basePositionMs
            }
            val d = durationMs
            return if (d > 0) p.coerceIn(0, d) else p.coerceAtLeast(0)
        }

    override val durationMs: Long
        get() = if (reportedDurationMs > 0) reportedDurationMs else fallbackDurationMs

    /**
     * 플레이어 상태 보고.
     * @param state YT.PlayerState 값(-1 시작 전, 0 종료, 1 재생, 2 일시정지, 3 버퍼링, 5 준비됨)
     */
    fun onReport(positionSec: Double, durationSec: Double, state: Int, playbackRate: Double) {
        basePositionMs = (positionSec * 1000).toLong()
        baseAt = SystemClock.elapsedRealtime()
        if (durationSec > 0) reportedDurationMs = (durationSec * 1000).toLong()
        rate = if (playbackRate > 0) playbackRate.toFloat() else 1f
        isPlaying = state == 1
        isBuffering = state == 3
        isReady = true
    }

    override fun play() {
        runJs?.invoke("cabinPlay()")
    }

    override fun pause() {
        // 다음 보고를 기다리지 않고 바로 멈춘 것으로 본다(주행 전환 때 효과를 즉시 되돌리기 위해)
        basePositionMs = positionMs
        isPlaying = false
        runJs?.invoke("cabinPause()")
    }

    override fun seekTo(positionMs: Long) {
        basePositionMs = positionMs.coerceAtLeast(0)
        baseAt = SystemClock.elapsedRealtime()
        runJs?.invoke("cabinSeek(${basePositionMs / 1000.0})")
    }

    override fun release() {
        runJs = null
        isPlaying = false
    }
}
