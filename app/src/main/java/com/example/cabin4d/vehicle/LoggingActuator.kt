package com.example.cabin4d.vehicle

import android.util.Log
import com.example.cabin4d.effect.Command

class LoggingActuator : CabinActuator {
    override val name = "로그 전용(차량 제어 없음)"

    override fun start(onStatus: (String) -> Unit) {
        onStatus("차량 SDK 없이 로그만 남깁니다")
    }

    override fun apply(command: Command) {
        Log.i(PleosReflect.TAG, "[log] $command")
    }

    override fun restoreAll() {
        Log.i(PleosReflect.TAG, "[log] restoreAll")
    }

    override fun release() = Unit
}
