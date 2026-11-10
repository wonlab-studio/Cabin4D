package com.example.cabin4d.safety

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class GateState {
    /** 정차 등 영상 재생이 허용된 상태 */
    ALLOWED,

    /** 주행 규제 중(영상 금지). 재생과 효과를 즉시 멈춰야 함 */
    RESTRICTED,

    /** 차량 서비스에 연결하지 못함(일반 안드로이드 기기 등) */
    UNKNOWN
}

/**
 * 플랫폼의 CarUxRestrictions로 주행 규제 상태를 받는다(Pleos 주행 규제 개발 가이드 방식).
 * 에뮬레이터에서는 Virtual Console로 기어를 D로 바꾸거나 속도를 올려 규제 상태를 만들어 볼 수 있다.
 */
class DrivingGate(private val context: Context) {
    private val _state = MutableStateFlow(GateState.UNKNOWN)
    val state: StateFlow<GateState> = _state

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        try {
            val c = Car.createCar(context) ?: return
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            car = c
            manager = m
            update(m.currentCarUxRestrictions)
            m.registerListener { update(it) }
        } catch (t: Throwable) {
            Log.w("Cabin4D", "CarUxRestrictions 연결 실패: ${t.message}")
            // 차량에서 주행 상태를 모르면 안전하게 막는다(fail-closed). 일반 기기에서만 UNKNOWN으로 허용.
            val isCar = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
            _state.value = if (isCar) GateState.RESTRICTED else GateState.UNKNOWN
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private fun update(r: CarUxRestrictions?) {
        val noVideo = r != null && r.isRequiresDistractionOptimization &&
            (r.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0
        _state.value = if (noVideo) GateState.RESTRICTED else GateState.ALLOWED
    }
}
