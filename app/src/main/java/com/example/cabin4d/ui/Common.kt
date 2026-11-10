package com.example.cabin4d.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cabin4d.UiState
import com.example.cabin4d.safety.GateState

/** 화면 전체에서 쓰는 색 */
object CabinColors {
    val Bg = Color(0xFF0B0D10)
    val Panel = Color(0xF0111316)
    val Card = Color(0xFF16191E)
    val Line = Color(0xFF2A2F37)
    val Text = Color(0xFFF2F4F7)
    val SubText = Color(0xFFB0B6BF)
    val Dim = Color(0xFF7D848E)
    val Accent = Color(0xFF8AB4FF)
    /** 4D 표시(배지·강조)에 쓰는 색. 바람(하늘색)에서 빛(호박색)으로 넘어가는 그라데이션의 양 끝. */
    val FourDStart = Color(0xFF5CC8FF)
    val FourDEnd = Color(0xFFFFC46B)
}

/** 0xRRGGBB → Compose Color */
fun rgbColor(rgb: Int, alpha: Float = 1f): Color = Color(0xFF000000 or (rgb.toLong() and 0xFFFFFF)).copy(alpha = alpha)

@Composable
fun GateChip(gate: GateState) {
    val (label, color) = when (gate) {
        GateState.ALLOWED -> "정차 중 · 재생 가능" to Color(0xFF2E7D32)
        GateState.RESTRICTED -> "주행 규제 중 · 재생 불가" to Color(0xFFC62828)
        GateState.UNKNOWN -> "주행 상태 확인 불가" to Color(0xFF6D6D6D)
    }
    Text(
        label,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** 지금 나가고 있는 효과(조명 색·바람 세기·송풍 방향) 미리보기 */
@Composable
fun EffectPreview(ui: UiState, swatch: Dp = 44.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(swatch)
                .background(ui.ambientRgb?.let { rgbColor(it) } ?: Color(0xFF22262B), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF3A3F46), RoundedCornerShape(10.dp))
        )
        Column(Modifier.weight(1f)) {
            Text("바람", color = CabinColors.SubText, fontSize = 12.sp)
            LinearProgressIndicator(
                progress = { ui.wind ?: 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text("송풍 ${ui.direction ?: "원래 설정"}", color = CabinColors.SubText, fontSize = 12.sp)
            Text(
                "실내등 ${if (ui.cabinLight == false) "암전" else "원래 설정"} · 독서등 ${if (ui.readingLight == true) "번쩍" else "원래 설정"}",
                color = if (ui.readingLight == true) Color(0xFFFFE7B0) else CabinColors.SubText,
                fontSize = 12.sp
            )
        }
    }
}

/** 무드등 타임라인(영상 전체의 조명 색 흐름)을 가로 띠로 그린다 */
@Composable
fun MoodStrip(colors: List<Int?>, modifier: Modifier = Modifier) {
    Row(modifier) {
        colors.forEach { rgb ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(rgb?.let { rgbColor(it, 0.9f) } ?: Color(0xFF22262B))
            )
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
