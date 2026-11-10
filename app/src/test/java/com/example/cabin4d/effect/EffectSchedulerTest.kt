package com.example.cabin4d.effect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectSchedulerTest {

    private val noLimits = SchedulerConfig(
        leadMs = mapOf(Channel.WIND to 1200L, Channel.AIR_DIRECTION to 0L, Channel.AMBIENT to 0L),
        minIntervalMs = emptyMap(),
        maxWind = 1f
    )

    private fun track(vararg cues: Cue) = EffectTrack("t", "t", null, 60_000, cues.toList())

    @Test
    fun windFiresEarlyByLeadTime() {
        val s = EffectScheduler(track(Cue(10_000, EffectValue.Wind(0.8f))), noLimits)
        assertTrue(s.tick(8_700, true, 0).isEmpty())
        val out = s.tick(8_800, true, 50)
        assertEquals(listOf(Command.Apply(Channel.WIND, EffectValue.Wind(0.8f), "")), out)
        assertTrue("같은 상태면 다시 보내지 않음", s.tick(9_000, true, 100).isEmpty())
    }

    @Test
    fun holdReturnsChannelToBaseline() {
        val s = EffectScheduler(track(Cue(5_000, EffectValue.Ambient(0xFF0000, 1f), holdMs = 1_000)), noLimits)
        assertEquals(1, s.tick(5_000, true, 0).size)
        assertEquals(listOf(Command.Restore(Channel.AMBIENT)), s.tick(6_000, true, 50))
    }

    @Test
    fun seekBackwardRecomputesState() {
        val s = EffectScheduler(track(Cue(5_000, EffectValue.Direction(AirDirection.FACE))), noLimits)
        s.tick(7_000, true, 0)
        assertEquals(listOf(Command.Restore(Channel.AIR_DIRECTION)), s.tick(1_000, true, 50))
    }

    @Test
    fun pauseRestoresAndResumeReapplies() {
        val s = EffectScheduler(track(Cue(0, EffectValue.Ambient(0x0000FF, 0.5f))), noLimits)
        assertEquals(1, s.tick(100, true, 0).size)
        assertEquals(listOf(Command.Restore(Channel.AMBIENT)), s.tick(200, false, 50))
        assertTrue("일시정지 중 반복 호출은 아무것도 안 보냄", s.tick(200, false, 100).isEmpty())
        assertEquals(1, s.tick(200, true, 150).size)
    }

    @Test
    fun ambientRateLimitedToThreePerSecond() {
        val config = noLimits.copy(minIntervalMs = mapOf(Channel.AMBIENT to 334L))
        val s = EffectScheduler(
            track(
                Cue(1_000, EffectValue.Ambient(0xFF0000, 1f)),
                Cue(1_100, EffectValue.Ambient(0x00FF00, 1f))
            ),
            config
        )
        assertEquals(1, s.tick(1_000, true, 10_000).size)
        assertTrue("100ms 뒤 두 번째 색은 보류", s.tick(1_100, true, 10_100).isEmpty())
        val late = s.tick(1_350, true, 10_350)
        assertEquals(EffectValue.Ambient(0x00FF00, 1f), (late.single() as Command.Apply).value)
    }

    @Test
    fun intensityAndCapScaleWind() {
        val s = EffectScheduler(track(Cue(0, EffectValue.Wind(1f))), noLimits.copy(maxWind = 0.7f))
        s.intensity = 0.5f
        assertEquals(EffectValue.Wind(0.5f), (s.tick(0, true, 0).single() as Command.Apply).value)
        s.intensity = 1f
        assertEquals(EffectValue.Wind(0.7f), (s.tick(0, true, 50).single() as Command.Apply).value)
    }

    @Test
    fun zeroIntensityMeansNoWind() {
        val s = EffectScheduler(track(Cue(0, EffectValue.Wind(1f))), noLimits)
        s.intensity = 0f
        assertTrue(s.tick(0, true, 0).isEmpty())
    }

    @Test
    fun stopRestoresOnlyActiveChannels() {
        val s = EffectScheduler(
            track(
                Cue(0, EffectValue.Wind(0.5f)),
                Cue(0, EffectValue.Ambient(0xFFFFFF, 1f))
            ),
            noLimits
        )
        s.tick(0, true, 0)
        assertEquals(
            listOf(Command.Restore(Channel.WIND), Command.Restore(Channel.AMBIENT)),
            s.stop()
        )
        assertTrue("두 번째 stop은 빈 결과", s.stop().isEmpty())
    }

    @Test
    fun cabinBlackoutLastsUntilHoldThenRestores() {
        val s = EffectScheduler(track(Cue(0, EffectValue.CabinLight(on = false), holdMs = 30_000)), noLimits)
        assertEquals(listOf(Command.Apply(Channel.CABIN_LIGHT, EffectValue.CabinLight(false), "")), s.tick(0, true, 0))
        assertTrue("암전 중에는 다시 보내지 않음", s.tick(15_000, true, 50).isEmpty())
        assertEquals(listOf(Command.Restore(Channel.CABIN_LIGHT)), s.tick(30_000, true, 100))
    }

    @Test
    fun readingFlashRespectsMinInterval() {
        // 기본 설정: 독서등은 0.1초 먼저 켜고, 334ms 안에는 다시 바꾸지 않는다(초당 3회 이하)
        val s = EffectScheduler(track(Cue(10_000, EffectValue.ReadingLight(on = true), holdMs = 200)))
        assertEquals(1, s.tick(9_900, true, 1_000).size)
        assertTrue("hold가 끝나도 최소 간격 전에는 끄지 않음", s.tick(10_150, true, 1_250).isEmpty())
        assertEquals(listOf(Command.Restore(Channel.READING_LIGHT)), s.tick(10_250, true, 1_350))
    }

    @Test
    fun parsesCabinAndReadingCues() {
        val json = """{"id":"x","duration":"00:10.0","cues":[
            {"at":"00:00.0","cabin":"off","hold":5000},
            {"at":"00:03.0","reading":"on","hold":400},
            {"at":"00:06.0","reading":false}
        ]}"""
        val cues = EffectTrackParser.parse(json).cues
        assertEquals(EffectValue.CabinLight(false), cues[0].value)
        assertEquals(EffectValue.ReadingLight(true), cues[1].value)
        assertEquals(EffectValue.ReadingLight(false), cues[2].value)
    }

    @Test
    fun parsesTimeFormats() {
        assertEquals(72_500L, EffectTrackParser.parseTime("01:12.5"))
        assertEquals(3_723_000L, EffectTrackParser.parseTime("1:02:03"))
        assertEquals(1500L, EffectTrackParser.parseTime(1500))
    }
}
