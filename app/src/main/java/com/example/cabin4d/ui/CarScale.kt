package com.example.cabin4d.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * 차량 화면에 맞춰 UI 전체를 키운다.
 *
 * Pleos Connect 에뮬레이터는 화면이 크고 밀도가 낮아(앱 창이 약 1700dp 폭) 휴대폰 기준 크기로 그리면
 * 글자와 버튼이 운전석에서 읽기 어려울 만큼 작게 나온다. 그래서 앱 창의 가로가 [designWidth]dp가 되도록
 * 밀도를 다시 정한다. 가로 창과 세로(반쪽) 창에서 글자 크기가 같도록 창의 긴 쪽을 기준으로 한다.
 */
@Composable
fun CarScale(designWidth: Float = 1100f, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val base = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val scaled = remember(widthPx, heightPx, base.density) {
            (maxOf(widthPx, heightPx * 1.4f) / designWidth).coerceIn(base.density, base.density * 3f)
        }
        CompositionLocalProvider(LocalDensity provides Density(scaled, base.fontScale)) {
            content()
        }
    }
}
