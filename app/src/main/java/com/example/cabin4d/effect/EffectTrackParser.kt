package com.example.cabin4d.effect

import org.json.JSONObject

/**
 * 트랙 JSON 파서. 형식은 README의 "이펙트 트랙 형식" 참고.
 *
 * 큐 한 줄에는 wind / direction / ambient / cabin / reading 중 하나만 둔다.
 * {"at": "00:00.0", "cabin": "off", "hold": 746000, "note": "실내등 암전"}
 * {"at": "09:20.0", "reading": "on", "hold": 400, "note": "불길 번쩍임"}
 * {"at": "01:12.5", "wind": 0.9, "hold": 1500, "note": "폭발 돌풍"}
 */
object EffectTrackParser {

    fun parse(json: String): EffectTrack {
        val root = JSONObject(json)
        val cuesJson = root.getJSONArray("cues")
        val cues = (0 until cuesJson.length()).map { i -> parseCue(cuesJson.getJSONObject(i), i) }
        return EffectTrack(
            id = root.getString("id"),
            title = root.optString("title", root.getString("id")),
            mediaUri = if (root.isNull("media")) null else root.optString("media").ifBlank { null },
            durationMs = parseTime(root.get("duration")),
            cues = cues.sortedBy { it.atMs },
            meta = parseMeta(root)
        )
    }

    private fun parseMeta(root: JSONObject): TrackMeta {
        val highlights = root.optJSONArray("highlights")?.let { arr ->
            (0 until arr.length()).map { i ->
                val h = arr.getJSONObject(i)
                Highlight(atMs = parseTime(h.get("at")), label = h.optString("label", ""))
            }
        }.orEmpty()
        return TrackMeta(
            order = root.optInt("order", 100),
            year = if (root.has("year")) root.getInt("year") else null,
            description = root.optString("description", ""),
            attribution = root.optString("attribution", ""),
            license = root.optString("license", ""),
            licenseUrl = root.optString("licenseUrl", ""),
            highlights = highlights.sortedBy { it.atMs },
            subtitles = root.optJSONArray("subtitles")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val s = arr.getJSONObject(i)
                    SubtitleSource(language = s.getString("lang"), label = s.optString("label", s.getString("lang")), uri = s.getString("uri"))
                }
            }.orEmpty()
        )
    }

    private fun parseCue(o: JSONObject, index: Int): Cue {
        val values = buildList {
            if (o.has("wind")) add(EffectValue.Wind(o.getDouble("wind").toFloat().coerceIn(0f, 1f)))
            if (o.has("direction")) add(EffectValue.Direction(AirDirection.valueOf(o.getString("direction").uppercase())))
            if (o.has("ambient")) add(
                EffectValue.Ambient(
                    rgb = parseColor(o.getString("ambient")),
                    brightness = o.optDouble("brightness", 1.0).toFloat().coerceIn(0f, 1f)
                )
            )
            if (o.has("cabin")) add(EffectValue.CabinLight(parseOnOff(o.get("cabin"), "cues[$index].cabin")))
            if (o.has("reading")) add(EffectValue.ReadingLight(parseOnOff(o.get("reading"), "cues[$index].reading")))
        }
        require(values.size == 1) { "cues[$index]: wind / direction / ambient / cabin / reading 중 정확히 하나가 필요합니다" }
        return Cue(
            atMs = parseTime(o.get("at")),
            value = values.single(),
            holdMs = if (o.has("hold")) o.getLong("hold") else null,
            note = o.optString("note", "")
        )
    }

    /** 숫자(ms) 또는 "mm:ss.s" / "hh:mm:ss.s" 문자열 */
    /** "on"/"off" 또는 true/false */
    private fun parseOnOff(value: Any, where: String): Boolean = when (value) {
        is Boolean -> value
        is String -> when (value.lowercase()) {
            "on", "true" -> true
            "off", "false" -> false
            else -> throw IllegalArgumentException("$where: on 또는 off여야 합니다: $value")
        }
        else -> throw IllegalArgumentException("$where: on 또는 off여야 합니다: $value")
    }

    fun parseTime(raw: Any): Long = when (raw) {
        is Number -> raw.toLong()
        is String -> {
            val parts = raw.trim().split(":")
            require(parts.size in 2..3) { "시간 형식 오류: $raw" }
            val seconds = parts.last().toDouble()
            val minutes = parts[parts.size - 2].toLong()
            val hours = if (parts.size == 3) parts[0].toLong() else 0L
            ((hours * 3600 + minutes * 60) * 1000 + Math.round(seconds * 1000))
        }
        else -> error("시간 형식 오류: $raw")
    }

    private fun parseColor(hex: String): Int {
        val clean = hex.removePrefix("#")
        require(clean.length == 6) { "색상은 #RRGGBB 형식: $hex" }
        return clean.toInt(16)
    }
}
