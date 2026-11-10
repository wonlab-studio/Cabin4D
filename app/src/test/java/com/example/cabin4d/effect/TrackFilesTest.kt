package com.example.cabin4d.effect

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 앱에 들어가는 실제 트랙 파일을 파싱하고 스케줄러로 끝까지 재생해 본다 */
class TrackFilesTest {
    @Test
    fun allTracksParseAndPlay() {
        // Gradle 단위 테스트는 app/ 폴더에서 실행된다
        val dir = File(System.getProperty("tracks.dir") ?: "src/main/assets/tracks")
        val files = dir.listFiles { f -> f.name.endsWith(".json") }!!.sorted()
        assertTrue(files.size >= 4)
        for (f in files) {
            val track = EffectTrackParser.parse(f.readText())
            val s = EffectScheduler(track).also { it.intensity = 1f }
            var applied = 0; var restored = 0
            var now = 0L
            var pos = 0L
            while (pos <= track.durationMs) {
                s.tick(pos, true, now).forEach { if (it is Command.Apply) applied++ else restored++ }
                pos += 50; now += 50
            }
            restored += s.stop().size
            val mood = track.moodTimeline(48)
            println("${track.id}: ${track.cues.size} cues, ${track.meta.highlights.size} highlights, applied=$applied restored=$restored, mood=${mood.count { it != null }}/48, streaming=${track.isStreaming}, year=${track.meta.year}")
            assertTrue(track.id, applied > 10)
            assertTrue(track.meta.highlights.all { it.atMs in 0..track.durationMs })
        }
    }
}
