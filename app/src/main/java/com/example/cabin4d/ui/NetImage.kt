package com.example.cabin4d.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

/**
 * 네트워크 이미지를 받아 온다. [urls]를 앞에서부터 시도해 처음 성공한 것을 쓴다(큰 썸네일이 없는 영상 대비).
 * 받는 중이거나 모두 실패하면 null. 썸네일 몇 장만 쓰므로 외부 라이브러리 없이 메모리 캐시만 둔다.
 */
@Composable
fun rememberNetworkImage(urls: List<String>): ImageBitmap? {
    val key = urls.joinToString("|")
    val state = produceState<ImageBitmap?>(initialValue = imageCache[key], key1 = key) {
        if (value != null) return@produceState
        val loaded: ImageBitmap? = withContext(Dispatchers.IO) {
            var found: ImageBitmap? = null
            for (url in urls) {
                found = runCatching { download(url) }.getOrNull()
                if (found != null) break
            }
            found
        }
        if (loaded != null) imageCache[key] = loaded
        value = loaded
    }
    return state.value
}

private fun download(url: String): ImageBitmap? {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    return try {
        if (conn.responseCode != 200) null
        else conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } finally {
        conn.disconnect()
    }
}
