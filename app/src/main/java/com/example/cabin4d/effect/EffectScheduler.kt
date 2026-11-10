package com.example.cabin4d.effect

/** 스케줄러가 차량 어댑터로 내보내는 명령. */
sealed interface Command {
    val channel: Channel

    data class Apply(override val channel: Channel, val value: EffectValue, val note: String) : Command

    /** 해당 채널을 사용자의 원래 설정으로 되돌린다. */
    data class Restore(override val channel: Channel) : Command
}

/**
 * @param leadMs 채널별 선행 보상 시간. 블로워 램프업·모드 도어 이동처럼 명령 후 체감까지 걸리는 시간만큼
 *   효과를 미리 쏜다. 아래 기본값은 예시이므로 에뮬레이터·실차에서 측정해 조정할 것.
 * @param minIntervalMs 채널별 최소 명령 간격. 같은 장치에 명령이 몰리는 것을 막는다.
 *   AMBIENT 334ms ≒ 초당 3회 이하: 광과민성 발작 예방을 위한 '초당 3회 이하 점멸' 권고를 따름.
 * @param maxWind 바람 세기 상한(0~1). 사용자 강도 설정과 곱한 뒤 이 값으로 자른다.
 * @param restoreOnPause 일시정지 중에는 모든 채널을 원래 설정으로 돌린다.
 */
data class SchedulerConfig(
    val leadMs: Map<Channel, Long> = mapOf(
        Channel.WIND to 1200L,
        Channel.AIR_DIRECTION to 1500L,
        Channel.AMBIENT to 100L,
        Channel.CABIN_LIGHT to 0L,
        Channel.READING_LIGHT to 100L
    ),
    val minIntervalMs: Map<Channel, Long> = mapOf(
        Channel.WIND to 400L,
        Channel.AIR_DIRECTION to 1000L,
        Channel.AMBIENT to 334L,
        Channel.CABIN_LIGHT to 1000L,
        // 독서등도 무드등과 같이 초당 3회 이하로만 바뀐다(켜기·끄기를 각각 한 번으로 센다)
        Channel.READING_LIGHT to 334L
    ),
    val maxWind: Float = 0.8f,
    val restoreOnPause: Boolean = true
)

/**
 * 재생 위치 → 차량 명령 변환기. 안드로이드 의존성이 없는 순수 Kotlin이라 JVM에서 단위 테스트한다.
 *
 * 방식: 매 틱마다 채널별로 "지금(재생 위치 + 선행 시간) 시점에 있어야 할 상태"를 계산하고,
 * 마지막으로 보낸 상태와 다를 때만 명령을 낸다. 상태 기반이라 앞뒤로 탐색(seek)해도 자연스럽게 맞춰진다.
 */
class EffectScheduler(
    track: EffectTrack,
    private val config: SchedulerConfig = SchedulerConfig()
) {
    private val cuesByChannel: Map<Channel, List<Cue>> =
        track.cues.groupBy { it.channel }.mapValues { (_, list) -> list.sortedBy { it.atMs } }

    /** 채널별 마지막으로 보낸 명령. 없으면 기준값 상태. */
    private val sent = mutableMapOf<Channel, Command.Apply>()
    private val lastSentAt = mutableMapOf<Channel, Long>()

    /** 사용자 강도(0~1). 바람 세기에 곱한다. */
    var intensity: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /**
     * @param positionMs 현재 재생 위치
     * @param isPlaying 재생 중 여부
     * @param nowMs 단조 증가하는 실제 시각(SystemClock.elapsedRealtime 등). 명령 간격 제한에 쓴다.
     */
    fun tick(positionMs: Long, isPlaying: Boolean, nowMs: Long): List<Command> {
        if (!isPlaying && config.restoreOnPause) return stop()

        val out = mutableListOf<Command>()
        for (channel in Channel.entries) {
            val wanted: Command.Apply? = desiredAt(channel, positionMs)
            val current = sent[channel]
            if (wanted == current) continue

            val last = lastSentAt[channel]
            val minGap = config.minIntervalMs[channel] ?: 0L
            if (last != null && nowMs - last < minGap) continue // 다음 틱에 다시 시도

            if (wanted == null) {
                sent.remove(channel)
                out += Command.Restore(channel)
            } else {
                sent[channel] = wanted
                out += wanted
            }
            lastSentAt[channel] = nowMs
        }
        return out
    }

    /** 재생 종료·주행 규제·효과 OFF 시 호출. 기준값이 아닌 채널만 되돌린다. 여러 번 불러도 안전. */
    fun stop(): List<Command> {
        val out = sent.keys.sortedBy { it.ordinal }.map { Command.Restore(it) }
        sent.clear()
        lastSentAt.clear()
        return out
    }

    private fun desiredAt(channel: Channel, positionMs: Long): Command.Apply? {
        val cues = cuesByChannel[channel] ?: return null
        val t = positionMs + (config.leadMs[channel] ?: 0L)
        val cue = cues.lastOrNull { it.atMs <= t } ?: return null
        val hold = cue.holdMs
        if (hold != null && t >= cue.atMs + hold) return null
        val value = scaled(cue.value) ?: return null
        return Command.Apply(channel, value, cue.note)
    }

    private fun scaled(value: EffectValue): EffectValue? = when (value) {
        is EffectValue.Wind -> {
            val level = (value.level * intensity).coerceIn(0f, config.maxWind)
            if (level <= 0f) null else EffectValue.Wind(level)
        }
        else -> value
    }
}
