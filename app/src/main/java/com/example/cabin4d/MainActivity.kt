package com.example.cabin4d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.example.cabin4d.ui.CarScale
import com.example.cabin4d.ui.HomeScreen
import com.example.cabin4d.ui.PlayerScreen
import com.example.cabin4d.ui.YouTubeScreen

class MainActivity : ComponentActivity() {
    private val viewModel: CinemaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8AB4FF))) {
                // 차량 화면에 맞춰 글자와 버튼을 키운다
                CarScale {
                    val ui by viewModel.ui.collectAsState()
                    val player by viewModel.player.collectAsState()
                    val youtubeClock by viewModel.youtubeClock.collectAsState()
                    when {
                        ui.currentId == null -> HomeScreen(ui, onOpenTrack = viewModel::open, onOpenYouTube = viewModel::openYouTube)
                        ui.youtube != null -> YouTubeScreen(ui, youtubeClock, viewModel)
                        else -> PlayerScreen(ui, player, viewModel)
                    }
                }
            }
        }
    }

    // 앱을 떠나면 재생을 멈춘다. 다음 틱에서 스케줄러가 효과를 원래 설정으로 되돌린다.
    override fun onStop() {
        super.onStop()
        viewModel.pausePlayback()
    }
}
