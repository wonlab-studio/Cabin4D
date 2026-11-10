package com.example.cabin4d.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.cabin4d.CinemaViewModel
import com.example.cabin4d.UiState
import com.example.cabin4d.clock.YouTubeClock
import com.example.cabin4d.safety.GateState

/** YouTube에 앱을 밝히는 주소(HTTP Referer). YouTube 규정에 따라 앱 ID를 도메인으로 쓴다. */
private const val APP_ORIGIN = "https://com.example.cabin4d"

/**
 * YouTube 재생 화면.
 *
 * YouTube 규정상 플레이어 위에 다른 화면 요소를 덮으면 안 되므로, 제목 줄은 플레이어 바깥(위)에 둔다.
 * 재생·탐색 버튼은 YouTube 플레이어의 것을 그대로 쓴다. 4D 트랙이 있는 영상은 제목 줄의 스위치로
 * 효과를 켜고 끄며, 효과는 플레이어 API가 알려 주는 재생 위치([YouTubeClock])에 맞춘다.
 */
@Composable
fun YouTubeScreen(ui: UiState, clock: YouTubeClock?, vm: CinemaViewModel) {
    val video = ui.youtube ?: return
    val restricted = ui.gate == GateState.RESTRICTED

    BackHandler { vm.close() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopRow(
            title = video.title,
            subtitle = "YouTube · ${video.channel}",
            effectsOn = ui.effectsOn,
            showEffectSwitch = ui.hasTrack,
            onBack = vm::close,
            onEffectsOn = vm::setEffectsOn,
            modifier = Modifier.background(Color(0xFF101216))
        )

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (clock != null) {
                // 플레이어 안의 버튼과 글자가 차량 화면에서 너무 작지 않도록 페이지 폭을 dp 기준으로 잡는다
                val pageWidth = maxWidth.value.toInt().coerceAtLeast(480)
                YouTubePlayer(
                    videoId = video.videoId,
                    pageWidth = pageWidth,
                    clock = clock,
                    hidden = restricted,
                    onError = vm::onYouTubeError
                )
            }
            when {
                restricted -> Notice("주행 중에는 영상을 볼 수 없습니다.\n정차하면 이어서 볼 수 있습니다.")
                ui.error != null -> Notice(ui.error)
                // 플레이어가 뜨기 전까지만 보여 준다(플레이어가 보이면 그 위에는 아무것도 올리지 않는다)
                ui.buffering -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }
}

@Composable
private fun Notice(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.White, fontSize = 20.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
    }
}

/** 플레이어(자바스크립트)가 앱으로 상태를 알려 주는 통로. 호출은 WebView 스레드에서 오므로 메인으로 넘긴다. */
private class YouTubeBridge(
    private val stateCallback: (Double, Double, Int, Double) -> Unit,
    private val errorCallback: (Int) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onState(positionSec: Double, durationSec: Double, state: Int, rate: Double) {
        main.post { stateCallback(positionSec, durationSec, state, rate) }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        main.post { errorCallback(code) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(
    videoId: String,
    pageWidth: Int,
    clock: YouTubeClock,
    hidden: Boolean,
    onError: (Int) -> Unit
) {
    val html = remember(videoId, pageWidth) { playerHtml(videoId, pageWidth) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    // 플레이어 안의 YouTube 링크(로고, 채널 등)는 이 화면에서 열지 않고 바깥 앱에 넘긴다
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        runCatching {
                            view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        return true
                    }
                }
                addJavascriptInterface(YouTubeBridge(clock::onReport, onError), "Cabin")
                clock.runJs = { script -> evaluateJavascript(script, null) }
                loadDataWithBaseURL(APP_ORIGIN, html, "text/html", "utf-8", null)
                webView = this
            }
        },
        update = { view ->
            // 주행 규제 중에는 플레이어를 아예 보이지 않게 한다
            view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        }
    )

    DisposableEffect(clock) {
        onDispose {
            clock.runJs = null
            webView?.let { view ->
                view.loadUrl("about:blank")
                view.removeJavascriptInterface("Cabin")
                view.destroy()
            }
            webView = null
        }
    }
}

/**
 * YouTube IFrame Player API로 플레이어를 띄우는 페이지.
 * 0.25초마다 재생 위치·길이·상태·배속을 앱에 알려 주고, 앱은 cabinPlay/cabinPause/cabinSeek로 조작한다.
 */
private fun playerHtml(videoId: String, pageWidth: Int): String = """
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=$pageWidth">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>
  html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
  #player { position: absolute; left: 0; top: 0; width: 100%; height: 100%; }
</style>
</head>
<body>
<div id="player"></div>
<script>
  var player = null;
  function report() {
    if (!player || !player.getCurrentTime) return;
    try {
      Cabin.onState(player.getCurrentTime() || 0, player.getDuration() || 0, player.getPlayerState(), player.getPlaybackRate() || 1);
    } catch (e) {}
  }
  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      width: '100%',
      height: '100%',
      videoId: '$videoId',
      // cc_lang_pref: 자막이 켜질 때 한국어를 먼저 고르게 한다(영상에 따라 엉뚱한 언어가 기본으로 뜨는 것을 막음)
      playerVars: { playsinline: 1, rel: 0, fs: 0, hl: 'ko', cc_lang_pref: 'ko', origin: '$APP_ORIGIN' },
      events: {
        onReady: function () { report(); setInterval(report, 250); },
        onStateChange: function () { report(); },
        onError: function (e) { Cabin.onError(e.data); }
      }
    });
  }
  function cabinPlay() { if (player && player.playVideo) player.playVideo(); }
  function cabinPause() { if (player && player.pauseVideo) player.pauseVideo(); }
  function cabinSeek(seconds) { if (player && player.seekTo) player.seekTo(seconds, true); }
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body>
</html>
""".trimIndent()
