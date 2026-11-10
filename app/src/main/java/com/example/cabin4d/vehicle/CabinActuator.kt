package com.example.cabin4d.vehicle

import com.example.cabin4d.effect.Command

/**
 * 스케줄러 명령을 실제 장치로 보내는 어댑터.
 * - PleosActuator: Pleos Vehicle SDK로 공조·앰비언트 제어
 * - LoggingActuator: 장치 없이 로그만 남김(SDK가 없는 환경, UI 개발용)
 */
interface CabinActuator {
    val name: String

    /** SDK 연결, 지원 범위 확인, 사용자 원래 설정 저장. 결과는 onStatus로 알린다. */
    fun start(onStatus: (String) -> Unit)

    fun apply(command: Command)

    /** 모든 채널을 사용자 원래 설정으로 되돌린다. */
    fun restoreAll()

    fun release()
}
