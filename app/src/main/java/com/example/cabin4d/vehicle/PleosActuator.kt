package com.example.cabin4d.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.cabin4d.effect.AirDirection
import com.example.cabin4d.effect.Channel
import com.example.cabin4d.effect.Command
import com.example.cabin4d.effect.EffectValue
import com.example.cabin4d.vehicle.PleosReflect.EnumOrInt
import com.example.cabin4d.vehicle.PleosReflect.TAG
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Pleos Vehicle SDK로 공조(풍량·송풍 방향)와 조명(무드등·실내등·독서등)을 제어한다.
 *
 * 원칙
 * 1. 시작할 때 사용자의 원래 설정을 저장하고, 효과가 끝나면 반드시 그 값으로 되돌린다.
 * 2. Capability로 지원 여부와 값 범위를 먼저 확인한다(차종마다 다름).
 * 3. 공조 AUTO 모드에서는 제어기가 수동 풍량 명령을 다시 덮어쓰므로, 바람 효과 동안만 AUTO를 끄고 끝나면 되돌린다.
 *
 * 주의: 제어(set) API(Vehicle Control SDK)는 포털 기준 '준비 중'이며 CONTROL_* 권한은 앱 마켓 사전 승인이 필요하다.
 *   공모전 설명회 안내에 따라 개발 단계에서는 권한 선언만으로 에뮬레이터에서 동작을 확인한다.
 */
class PleosActuator(context: Context) : CabinActuator {
    override val name = "Pleos Vehicle SDK"

    private val vehicle = Vehicle(context.applicationContext)
    private val hvac: HVAC by lazy { vehicle.getHVAC() }
    private val light: Light by lazy { vehicle.getLight() }

    @Volatile private var status: (String) -> Unit = {}
    @Volatile private var initialized = false

    // 풍량: capability로 받은 지원 영역과 값 범위(받기 전 기본값은 일반적인 1~8단 가정)
    @Volatile private var fanAreas: List<Any> = emptyList()
    @Volatile private var fanMin = 1
    @Volatile private var fanMax = 8
    // 공조 전원: 풍량과 지원 영역이 다를 수 있어 따로 받는다
    @Volatile private var powerAreas: List<Any> = emptyList()
    // 무드등: capability로 받은 지원 영역과 밝기 범위
    @Volatile private var ambientAreas: List<Any> = emptyList()
    @Volatile private var brightnessMin = 0
    @Volatile private var brightnessMax = 100

    /** 사용자 원래 설정. 키 = "fanSpeed@<area>", "fanDirection@<area>", "hvacAuto@<area>", "ambientSwitch@<area>", "ambientAutoByDriveMode" … */
    private val baseline = ConcurrentHashMap<String, Any>()
    private val autoDisabledAreas = ConcurrentHashMap.newKeySet<Any>()
    /** 바람 효과를 내려고 공조 전원을 켠 영역(끝나면 다시 끈다) */
    private val poweredOnAreas = ConcurrentHashMap.newKeySet<Any>()
    /** 되돌린 뒤에 늦게 도착한 명령이 실행되지 않도록 채널별로 세는 번호 */
    @Volatile private var windGeneration = 0
    @Volatile private var ambientGeneration = 0
    @Volatile private var ambientSwitchedOn = false
    @Volatile private var ambientAutoDisabled = false
    // 독서등: capability로 받은 지원 좌석 영역
    @Volatile private var readingAreas: List<Any> = emptyList()
    @Volatile private var cabinSupported = false
    @Volatile private var cabinChanged = false
    @Volatile private var readingChanged = false

    private val main = Handler(Looper.getMainLooper())
    /** 채널별로 마지막에 보낸 값(차량에서 다시 읽어 비교할 때 쓴다) */
    private val lastSent = ConcurrentHashMap<String, Any>()
    private val verified = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val acked = ConcurrentHashMap.newKeySet<String>()

    override fun start(onStatus: (String) -> Unit) {
        status = onStatus
        try {
            vehicle.initialize()
            initialized = true
        } catch (t: Throwable) {
            onStatus("Vehicle SDK 초기화 실패: ${t.message}")
            return
        }
        // 첫 실행 때 실제 시그니처를 Logcat에 남긴다: adb logcat -s Cabin4D
        PleosReflect.dumpSignatures(HVAC::class, listOf("FanSpeed", "FanDirection", "HvacAuto"))
        PleosReflect.dumpSignatures(Light::class, listOf("Ambient"))
        // SDK 2.3.2 소스 기준 시그니처(참고):
        //   setHvacFanSpeed(area: SeatArea, value: Int, …), setHvacFanDirection(area: SeatArea, value: FanDirectionType, …)
        //   setAmbientLightSwitch(area: SeatArea, value: LightSwitch, …), setAmbientLightBrightness(area: SeatArea, value: Int, …)
        //   setAmbientLightBrightnessColor(area: SeatArea, value: Int /* ARGB, 알파 0xFF */, …)

        probeFan()
        probePower()
        probeAmbient()
        probeCabinLight()
        probeReadingLight()
    }

    override fun apply(command: Command) {
        if (!initialized) return
        when (command) {
            is Command.Apply -> when (val v = command.value) {
                is EffectValue.Wind -> setWind(v.level)
                is EffectValue.Direction -> setDirection(v.direction)
                is EffectValue.Ambient -> setAmbient(v.rgb, v.brightness)
                is EffectValue.CabinLight -> setCabinLight(v.on)
                is EffectValue.ReadingLight -> setReadingLight(v.on)
            }
            is Command.Restore -> restore(command.channel)
        }
    }

    override fun restoreAll() {
        if (!initialized) return
        Channel.entries.forEach(::restore)
    }

    override fun release() {
        if (!initialized) return
        initialized = false
        // 복원 명령은 호출 측(스케줄러 stop)이 이미 보냈다. SDK 호출은 비동기라서,
        // 바로 release하면 복원이 전달되지 않을 수 있으므로 잠깐 기다린 뒤 해제한다.
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { vehicle.release() } }, RELEASE_DELAY_MS)
    }

    private companion object {
        const val RELEASE_DELAY_MS = 800L
        const val VERIFY_DELAY_MS = 1500L
        /** 전원·스위치를 켠 뒤 다음 명령을 보내기까지 기다리는 시간 */
        const val SETTLE_DELAY_MS = 600L
    }

    // ---------------------------------------------------------------- probe

    private fun probeFan() {
        PleosReflect.call(
            HVAC::class, hvac, "checkHvacFanSpeedCapabilities",
            onComplete = { values ->
                val config = values.filterIsInstance<CapabilityConfig>().firstOrNull()
                if (config?.accessType == AccessType.NONE) {
                    status("풍량: 이 차량에서 지원하지 않음")
                } else {
                    val map = values.filterIsInstance<Map<*, *>>().firstOrNull()
                    fanAreas = map?.keys?.filterNotNull().orEmpty()
                    val bounds = (map?.values?.firstOrNull() ?: values.firstOrNull { it is AreaBounds<*> }) as? AreaBounds<*>
                    (bounds?.minValue as? Number)?.toInt()?.let { fanMin = it }
                    (bounds?.maxValue as? Number)?.toInt()?.let { fanMax = it }
                    status("풍량 ${fanMin}~${fanMax}단, 영역 ${fanAreas.map(::areaName)}, 접근 ${config?.accessType}")
                    captureFanBaseline()
                }
            },
            onFailed = { status("풍량 capability 실패: ${it.message}") }
        )
    }

    private fun captureFanBaseline() {
        fanAreas.forEach { area ->
            capture("getHvacFanSpeed", "fanSpeed@$area", area)
            capture("getHvacFanDirection", "fanDirection@$area", area)
            capture("getHvacAutoState", "hvacAuto@$area", area)
        }
    }

    private fun probePower() {
        // 공조가 꺼져 있으면 풍량 명령이 거부되므로(NOT_AVAILABLE) 전원 상태를 알아 둔다.
        // 전원 속성은 풍량과 지원 영역이 다를 수 있다(에뮬레이터에서 풍량 영역으로 읽으면 거부됨).
        PleosReflect.call(
            HVAC::class, hvac, "checkHvacPowerCapabilities",
            onComplete = { values ->
                val config = values.filterIsInstance<CapabilityConfig>().firstOrNull()
                powerAreas = values.filterIsInstance<List<*>>().firstOrNull()?.filterNotNull().orEmpty()
                status("공조 전원: 접근 ${config?.accessType}, 영역 ${powerAreas.map(::areaName)}")
                powerAreas.forEach { area ->
                    PleosReflect.call(
                        HVAC::class, hvac, "getHvacPowerState", mapOf("area" to area),
                        onComplete = { got ->
                            val power = got.lastOrNull { it is Boolean } as? Boolean
                            if (power != null) baseline["hvacPower@$area"] = power
                            status("공조 전원 지금 ${if (power == null) "읽지 못함" else if (power) "켜짐" else "꺼짐"} (${areaName(area)})")
                        },
                        onFailed = { status("공조 전원 읽기 실패 (${areaName(area)}): ${it.message}") }
                    )
                }
            },
            onFailed = { status("공조 전원 capability 실패: ${it.message}") }
        )
    }

    /** 좌석 영역을 읽을 수 있는 이름으로(예: ROW_1_LEFT+ROW_1_RIGHT) */
    private fun areaName(area: Any?): String =
        (area as? SeatArea)?.values?.joinToString("+") { it.name } ?: area.toString()

    private fun probeAmbient() {
        // 무드등 API는 모두 좌석 영역(SeatArea)이 필수다. 지원 영역과 밝기 범위를 capability로 받는다.
        PleosReflect.call(
            Light::class, light, "checkAmbientLightBrightnessCapabilities",
            onComplete = { values ->
                val map = values.filterIsInstance<Map<*, *>>().firstOrNull()
                ambientAreas = map?.keys?.filterNotNull().orEmpty()
                val bounds = (map?.values?.firstOrNull() ?: values.firstOrNull { it is AreaBounds<*> }) as? AreaBounds<*>
                (bounds?.minValue as? Number)?.toInt()?.let { brightnessMin = it }
                (bounds?.maxValue as? Number)?.toInt()?.let { brightnessMax = it }
                val config = values.filterIsInstance<CapabilityConfig>().firstOrNull()
                status("무드등 밝기 ${brightnessMin}~${brightnessMax}, 영역 ${ambientAreas.map(::areaName)}, 접근 ${config?.accessType}")
                ambientAreas.forEach { area ->
                    captureLight("getAmbientLightSwitch", "ambientSwitch@$area", area)
                    captureLight("getAmbientLightBrightness", "ambientBrightness@$area", area)
                    captureLight("getAmbientLightBrightnessColor", "ambientColor@$area", area)
                }
            },
            onFailed = { status("무드등 capability 실패: ${it.message}") }
        )
        PleosReflect.call(
            Light::class, light, "checkAmbientLightBrightnessColorCapabilities",
            onComplete = { values ->
                val config = values.filterIsInstance<CapabilityConfig>().firstOrNull()
                val areas = values.filterIsInstance<List<*>>().firstOrNull()
                status("무드등 색: 접근 ${config?.accessType}, 영역 ${areas?.map(::areaName)}")
            },
            onFailed = { status("무드등 색 capability 실패: ${it.message}") }
        )
        // 주행 모드에 따라 무드등 색을 자동으로 바꾸는 기능이 켜져 있으면 효과 색을 덮어쓴다
        PleosReflect.call(
            Light::class, light, "getAmbientColorAutoByDriveModeEnabled",
            onComplete = { values ->
                val auto = values.firstOrNull { it is Boolean } as? Boolean
                if (auto != null) baseline["ambientAutoByDriveMode"] = auto
                status("주행 모드 자동 색: ${if (auto == null) "읽지 못함" else if (auto) "켜짐" else "꺼짐"}")
            },
            onFailed = { status("주행 모드 자동 색 읽기 실패: ${it.message}") }
        )
    }

    private fun probeCabinLight() {
        // 실내등은 좌석 영역 없이 차 전체에 하나다. 원래 스위치 값을 읽어 둬야 암전 뒤에 되돌릴 수 있다.
        PleosReflect.call(
            Light::class, light, "checkCabinLightSwitchCapabilities",
            onComplete = { values ->
                val config = values.filterIsInstance<CapabilityConfig>().firstOrNull()
                if (config?.accessType == AccessType.NONE) {
                    status("실내등: 이 차량에서 지원하지 않음")
                } else {
                    PleosReflect.call(
                        Light::class, light, "getCabinLightSwitch",
                        onComplete = { got ->
                            got.firstOrNull { it != null }?.let {
                                baseline["cabinSwitch"] = it
                                cabinSupported = true
                                status("실내등 제어 가능 (지금 $it)")
                            }
                        },
                        onFailed = { status("실내등 상태 읽기 실패: ${it.message}") }
                    )
                }
            },
            onFailed = { status("실내등 capability 실패: ${it.message}") }
        )
    }

    private fun probeReadingLight() {
        PleosReflect.call(
            Light::class, light, "checkReadingLightSwitchCapabilities",
            onComplete = { values ->
                val config = values.filterIsInstance<CapabilityConfig>().firstOrNull()
                val map = values.filterIsInstance<Map<*, *>>().firstOrNull()
                val areas = map?.keys?.filterNotNull().orEmpty()
                if (config?.accessType == AccessType.NONE || areas.isEmpty()) {
                    status("독서등: 이 차량에서 지원하지 않음")
                } else {
                    readingAreas = areas
                    status("독서등 영역 ${areas.map(::areaName)}")
                    areas.forEach { area -> captureLight("getReadingLightSwitch", "readingSwitch@$area", area) }
                }
            },
            onFailed = { status("독서등 capability 실패: ${it.message}") }
        )
    }

    private fun capture(getter: String, key: String, area: Any) {
        PleosReflect.call(
            HVAC::class, hvac, getter, mapOf("area" to area),
            // 콜백이 (area, value) 형태일 수 있으므로 영역 객체가 아닌 마지막 값을 저장
            onComplete = { values -> values.lastOrNull { it != null && it != area }?.let { baseline[key] = it } }
        )
    }

    private fun captureLight(getter: String, key: String, area: Any) {
        PleosReflect.call(
            Light::class, light, getter, mapOf("area" to area),
            // 콜백이 (area, value) 형태일 수 있으므로 영역 객체가 아닌 마지막 값을 저장
            onComplete = { values -> values.lastOrNull { it != null && it != area }?.let { baseline[key] = it } }
        )
    }

    // ---------------------------------------------------------------- set

    private fun setWind(level: Float) {
        if (fanAreas.isEmpty()) {
            Log.w(TAG, "풍량 영역 정보를 아직 받지 못해 바람 효과를 건너뜀")
            return
        }
        val speed = (fanMin + level * (fanMax - fanMin)).roundToInt().coerceIn(fanMin, fanMax)
        var justPowered = false
        // 공조가 꺼져 있으면 풍량 명령이 거부된다. 바람 효과 동안만 전원을 켜고 끝나면 다시 끈다.
        powerAreas.forEach { area ->
            if (baseline["hvacPower@$area"] == false && poweredOnAreas.add(area)) {
                justPowered = true
                PleosReflect.call(
                    HVAC::class, hvac, "setHvacPower", mapOf("area" to area, "value" to true),
                    onComplete = { ackOnce("공조 전원 켜기", it) },
                    onFailed = { reportFailure("공조 전원", it) }
                )
                verifyOnce("hvacPower", "공조 전원", HVAC::class, hvac, "getHvacPowerState", area, true)
            }
        }
        fanAreas.forEach { area ->
            // AUTO가 켜져 있으면 제어기가 수동 풍량을 덮어쓰므로 효과 동안만 끈다.
            if (baseline["hvacAuto@$area"] == true && autoDisabledAreas.add(area)) {
                setBool(HVAC::class, hvac, "setHvacAuto", area, false)
            }
        }
        val generation = windGeneration
        val send = {
            fanAreas.forEach { area ->
                PleosReflect.call(
                    HVAC::class, hvac, "setHvacFanSpeed",
                    mapOf("area" to area, "value" to speed, "speed" to speed, "level" to speed),
                    onComplete = { ackOnce("풍량", it) },
                    onFailed = { reportFailure("풍량", it) }
                )
            }
            fanAreas.firstOrNull()?.let { area -> verifyOnce("wind", "풍량", HVAC::class, hvac, "getHvacFanSpeed", area, speed) }
        }
        // 전원을 방금 켰으면 켜질 시간을 조금 준 뒤 풍량을 보낸다
        if (justPowered) {
            main.postDelayed({ if (initialized && generation == windGeneration) send() }, SETTLE_DELAY_MS)
        } else {
            send()
        }
    }

    private fun setDirection(direction: AirDirection) {
        // AAOS VehicleHvacFanDirection 값: FACE=1, FLOOR=2, FACE_AND_FLOOR=3, DEFROST=4
        val hint = when (direction) {
            AirDirection.FACE -> EnumOrInt(listOf("FACE"), 1)
            AirDirection.FLOOR -> EnumOrInt(listOf("FLOOR"), 2)
            AirDirection.FACE_FLOOR -> EnumOrInt(listOf("FACE_AND_FLOOR", "FACE_FLOOR", "BI_LEVEL"), 3)
            AirDirection.DEFROST -> EnumOrInt(listOf("DEFROST", "DEFROSTER"), 4)
        }
        fanAreas.forEach { area ->
            PleosReflect.call(
                HVAC::class, hvac, "setHvacFanDirection",
                mapOf("area" to area, "direction" to hint, "value" to hint)
            )
        }
    }

    private fun setAmbient(rgb: Int, brightness: Float) {
        if (ambientAreas.isEmpty()) {
            Log.w(TAG, "무드등 영역 정보를 아직 받지 못해 조명 효과를 건너뜀")
            return
        }
        if (!ambientAutoDisabled && baseline["ambientAutoByDriveMode"] == true) {
            ambientAutoDisabled = true
            PleosReflect.call(
                Light::class, light, "setAmbientColorAutoByDriveModeEnabled", mapOf("value" to false),
                onComplete = { ackOnce("주행 모드 자동 색 끄기", it) },
                onFailed = { reportFailure("주행 모드 자동 색 끄기", it) }
            )
        }
        val switchOn = !ambientSwitchedOn
        ambientSwitchedOn = true
        val level = (brightnessMin + brightness * (brightnessMax - brightnessMin)).roundToInt()
        val argb = (0xFF000000.toInt()) or (rgb and 0xFFFFFF) // SDK는 32비트 ARGB, 알파는 0xFF 고정
        val generation = ambientGeneration
        val send = {
            ambientAreas.forEach { area ->
                PleosReflect.call(
                    Light::class, light, "setAmbientLightBrightnessColor", mapOf("area" to area, "value" to argb),
                    onComplete = { ackOnce("무드등 색", it) },
                    onFailed = { reportFailure("무드등 색", it) }
                )
                PleosReflect.call(
                    Light::class, light, "setAmbientLightBrightness", mapOf("area" to area, "value" to level),
                    onComplete = { ackOnce("무드등 밝기", it) },
                    onFailed = { reportFailure("무드등 밝기", it) }
                )
            }
            ambientAreas.firstOrNull()?.let { area ->
                verifyOnce("ambientColor", "무드등 색", Light::class, light, "getAmbientLightBrightnessColor", area, argb)
                verifyOnce("ambientBrightness", "무드등 밝기", Light::class, light, "getAmbientLightBrightness", area, level)
            }
        }
        if (switchOn) {
            // 스위치를 먼저 켜고, 켜질 시간을 조금 준 뒤 색과 밝기를 보낸다
            ambientAreas.forEach { area ->
                PleosReflect.call(
                    Light::class, light, "setAmbientLightSwitch", mapOf("area" to area, "value" to EnumOrInt(listOf("ON"), 1)),
                    onFailed = { reportFailure("무드등 스위치", it) }
                )
            }
            ambientAreas.firstOrNull()?.let { area ->
                verifyOnce("ambientSwitch", "무드등 스위치", Light::class, light, "getAmbientLightSwitch", area, "ON")
            }
            main.postDelayed({ if (initialized && generation == ambientGeneration && ambientSwitchedOn) send() }, SETTLE_DELAY_MS)
        } else {
            send()
        }
    }

    private fun setCabinLight(on: Boolean) {
        // 원래 값을 모르면 되돌릴 수 없으므로 건드리지 않는다
        if (!cabinSupported) {
            Log.w(TAG, "실내등 원래 상태를 읽지 못해 암전 효과를 건너뜀")
            return
        }
        cabinChanged = true
        val name = if (on) "ON" else "OFF"
        PleosReflect.call(
            Light::class, light, "setCabinLightSwitch", mapOf("value" to EnumOrInt(listOf(name), if (on) 1 else 0)),
            onFailed = { reportFailure("실내등", it) }
        )
        verifyOnce("cabin", "실내등", Light::class, light, "getCabinLightSwitch", null, name)
    }

    private fun setReadingLight(on: Boolean) {
        val name = if (on) "ON" else "OFF"
        // 원래 값을 읽어 둔 좌석만 켠다(끝나고 되돌릴 수 있는 좌석)
        val areas = readingAreas.filter { baseline["readingSwitch@$it"] != null }
        if (areas.isEmpty()) {
            Log.w(TAG, "독서등 영역 정보를 아직 받지 못해 번쩍임 효과를 건너뜀")
            return
        }
        readingChanged = true
        areas.forEach { area ->
            PleosReflect.call(
                Light::class, light, "setReadingLightSwitch", mapOf("area" to area, "value" to EnumOrInt(listOf(name), if (on) 1 else 0)),
                onFailed = { reportFailure("독서등", it) }
            )
        }
    }

    // ---------------------------------------------------------------- 확인

    /** 같은 종류의 실패는 한 번만 상태 줄에 올린다(매 틱마다 쌓이지 않게). */
    private fun reportFailure(label: String, error: Throwable) {
        val reason = "${error::class.simpleName}: ${error.message}"
        Log.w(TAG, "$label 명령 실패: $reason")
        if (failed.add(label)) status("$label 명령 실패: $reason")
    }

    /** 명령에 대한 차량의 첫 응답(받아들인 값)을 한 번만 상태 줄에 올린다. */
    private fun ackOnce(label: String, values: List<Any?>) {
        if (acked.add(label)) status("$label 응답: ${values.joinToString(", ") { show(it) }}")
    }

    /**
     * 채널마다 첫 명령 뒤 한 번, 차량에서 값을 다시 읽어 실제로 반영됐는지 상태 줄에 남긴다.
     * 명령이 나갔다는 것과 차에 반영됐다는 것은 다르기 때문이다. 읽는 시점의 마지막 명령 값과 비교한다.
     */
    private fun verifyOnce(key: String, label: String, api: kotlin.reflect.KClass<*>, target: Any, getter: String, area: Any?, sent: Any) {
        lastSent[key] = sent
        if (!verified.add(key)) return
        main.postDelayed({
            if (!initialized) return@postDelayed
            PleosReflect.call(
                api, target, getter, if (area != null) mapOf("area" to area) else emptyMap(),
                onComplete = { values ->
                    val actual = values.lastOrNull { it != null && it != area }
                    val expected = lastSent[key]
                    status(
                        if (sameValue(actual, expected)) "$label 반영 확인"
                        else "$label 명령은 보냈지만 차량 값은 ${show(actual)} (보낸 값 ${show(expected)})"
                    )
                },
                onFailed = { status("$label 확인 실패: ${it.message}") }
            )
        }, VERIFY_DELAY_MS)
    }

    private fun sameValue(actual: Any?, expected: Any?): Boolean = when {
        actual == null || expected == null -> false
        actual is Number && expected is Number -> actual.toLong() == expected.toLong()
        else -> actual.toString() == expected.toString()
    }

    private fun show(value: Any?): String = when (value) {
        is SeatArea -> areaName(value)
        null -> "읽지 못함"
        is Int -> if (value > 0xFFFF || value < 0) "#%06X".format(value and 0xFFFFFF) else value.toString()
        else -> value.toString()
    }

    // ---------------------------------------------------------------- restore

    private fun restore(channel: Channel) {
        when (channel) {
            Channel.WIND -> {
                windGeneration++
                fanAreas.forEach { area ->
                    baseline["fanSpeed@$area"]?.let { v ->
                        PleosReflect.call(HVAC::class, hvac, "setHvacFanSpeed", mapOf("area" to area, "value" to v, "speed" to v, "level" to v))
                    }
                    // AUTO는 마지막에 되돌려 제어기가 다시 주도하게 한다.
                    if (autoDisabledAreas.remove(area)) setBool(HVAC::class, hvac, "setHvacAuto", area, true)
                }
                // 효과 때문에 켠 공조 전원은 다시 끈다
                powerAreas.forEach { area ->
                    if (poweredOnAreas.remove(area)) {
                        PleosReflect.call(HVAC::class, hvac, "setHvacPower", mapOf("area" to area, "value" to false))
                    }
                }
            }
            Channel.AIR_DIRECTION -> fanAreas.forEach { area ->
                baseline["fanDirection@$area"]?.let { v ->
                    PleosReflect.call(HVAC::class, hvac, "setHvacFanDirection", mapOf("area" to area, "direction" to v, "value" to v))
                }
            }
            Channel.AMBIENT -> {
                ambientGeneration++
                ambientAreas.forEach { area ->
                    baseline["ambientColor@$area"]?.let { v ->
                        PleosReflect.call(Light::class, light, "setAmbientLightBrightnessColor", mapOf("area" to area, "value" to v))
                    }
                    baseline["ambientBrightness@$area"]?.let { v ->
                        PleosReflect.call(Light::class, light, "setAmbientLightBrightness", mapOf("area" to area, "value" to v))
                    }
                    // 스위치는 원래 상태(LightSwitch 값 그대로)로 되돌린다
                    if (ambientSwitchedOn) {
                        baseline["ambientSwitch@$area"]?.let { v ->
                            PleosReflect.call(Light::class, light, "setAmbientLightSwitch", mapOf("area" to area, "value" to v))
                        }
                    }
                }
                ambientSwitchedOn = false
                if (ambientAutoDisabled) {
                    ambientAutoDisabled = false
                    PleosReflect.call(Light::class, light, "setAmbientColorAutoByDriveModeEnabled", mapOf("value" to true))
                }
            }
            Channel.CABIN_LIGHT -> if (cabinChanged) {
                cabinChanged = false
                baseline["cabinSwitch"]?.let { v ->
                    PleosReflect.call(Light::class, light, "setCabinLightSwitch", mapOf("value" to v))
                }
            }
            Channel.READING_LIGHT -> if (readingChanged) {
                readingChanged = false
                readingAreas.forEach { area ->
                    baseline["readingSwitch@$area"]?.let { v ->
                        PleosReflect.call(Light::class, light, "setReadingLightSwitch", mapOf("area" to area, "value" to v))
                    }
                }
            }
        }
    }

    private fun setBool(api: kotlin.reflect.KClass<*>, target: Any, fn: String, area: Any, value: Boolean) {
        PleosReflect.call(api, target, fn, mapOf("area" to area, "value" to value, "enable" to value, "enabled" to value, "state" to value))
    }
}
