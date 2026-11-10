package com.example.cabin4d.effect

/**
 * 이펙트 트랙 데이터 모델.
 *
 * 4D 영화관 방식: 영화마다 "몇 분 몇 초에 바람·빛을 어떻게 낼지"를 미리 적어 둔 트랙을 만들고,
 * 재생 위치에 맞춰 차량 장치를 제어한다. 화면 영상을 실시간 분석하지 않는다.
 */

/** 효과가 나가는 차량 장치 채널. 채널마다 독립적으로 상태를 가진다. */
enum class Channel { WIND, AIR_DIRECTION, AMBIENT, CABIN_LIGHT, READING_LIGHT }

/** 송풍 방향. 실제 SDK 값으로의 변환은 PleosActuator에서 한다. */
enum class AirDirection { FACE, FLOOR, FACE_FLOOR, DEFROST }

sealed interface EffectValue {
    val channel: Channel

    /** 바람 세기. 0.0 = 바람 없음, 1.0 = 트랙이 허용하는 최대. 실제 풍량 단계는 차량 범위로 환산. */
    data class Wind(val level: Float) : EffectValue {
        override val channel get() = Channel.WIND
    }

    data class Direction(val direction: AirDirection) : EffectValue {
        override val channel get() = Channel.AIR_DIRECTION
    }

    /** 앰비언트(무드등). rgb = 0xRRGGBB, brightness = 0.0~1.0 */
    data class Ambient(val rgb: Int, val brightness: Float) : EffectValue {
        override val channel get() = Channel.AMBIENT
    }

    /** 실내등(천장 가운데 등). 영화 시작 때 꺼서 극장처럼 어둡게 하는 암전에 쓴다. 색·밝기 조절은 없다. */
    data class CabinLight(val on: Boolean) : EffectValue {
        override val channel get() = Channel.CABIN_LIGHT
    }

    /** 독서등(좌석별 맵등). 번개·폭발 순간에 hold와 함께 짧게 켜서 위에서 번쩍이게 한다. */
    data class ReadingLight(val on: Boolean) : EffectValue {
        override val channel get() = Channel.READING_LIGHT
    }
}

/**
 * 트랙의 한 줄.
 * @param atMs 영상 기준 시작 시각(ms)
 * @param holdMs 지정하면 이 시간 뒤 해당 채널을 사용자의 원래 설정(기준값)으로 돌린다. null이면 다음 큐까지 유지.
 */
data class Cue(
    val atMs: Long,
    val value: EffectValue,
    val holdMs: Long? = null,
    val note: String = ""
) {
    val channel: Channel get() = value.channel
}

/** 영상과 함께 고를 수 있는 자막 파일 하나(SubRip). uri는 asset:///… 또는 https://… */
data class SubtitleSource(val language: String, val label: String, val uri: String)

/** 명장면 바로가기(효과가 몰려 있는 장면) */
data class Highlight(val atMs: Long, val label: String)

/** 목록 화면과 저작자 표시에 쓰는 영화 정보. 트랙 JSON에 없으면 기본값. */
data class TrackMeta(
    /** 목록 정렬 순서(작을수록 앞) */
    val order: Int = 100,
    val year: Int? = null,
    val description: String = "",
    /** 화면에 그대로 표시할 저작자 표시 문구(CC 라이선스 조건) */
    val attribution: String = "",
    val license: String = "",
    val licenseUrl: String = "",
    val highlights: List<Highlight> = emptyList(),
    val subtitles: List<SubtitleSource> = emptyList()
)

data class EffectTrack(
    val id: String,
    val title: String,
    /** 재생할 영상 URI(https://…, asset:///…, file://…). null이면 영상 없이 타임라인만 도는 테스트 모드. */
    val mediaUri: String?,
    val durationMs: Long,
    val cues: List<Cue>,
    val meta: TrackMeta = TrackMeta()
) {
    /** 스트리밍 영상이면 true(네트워크 필요) */
    val isStreaming: Boolean get() = mediaUri?.startsWith("http") == true

    /**
     * 목록 카드에 그릴 무드등 타임라인. 영상 전체를 [samples]칸으로 나눠 각 칸 시점의 조명 색(없으면 null).
     * hold로 원래 설정에 돌아간 구간도 null.
     */
    fun moodTimeline(samples: Int = 48): List<Int?> {
        val ambient = cues.filter { it.channel == Channel.AMBIENT }
        if (durationMs <= 0 || samples <= 0) return emptyList()
        return (0 until samples).map { i ->
            val t = durationMs * (i + 0.5) / samples
            val cue = ambient.lastOrNull { it.atMs <= t } ?: return@map null
            val hold = cue.holdMs
            if (hold != null && t >= cue.atMs + hold) null else (cue.value as EffectValue.Ambient).rgb
        }
    }
}
