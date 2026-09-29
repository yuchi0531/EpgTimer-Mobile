package com.starrow.epgtimer.ui.guide

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.starrow.epgtimer.data.guide.LogoImageDetector
import com.starrow.epgtimer.data.guide.LogoImageFormat
import com.starrow.epgtimer.data.repository.EpgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberServiceLogos(repository: EpgRepository): Map<String, ImageBitmap> =
    produceState<Map<String, ImageBitmap>>(initialValue = emptyMap(), repository) {
        val logos = repository.loadLogos().getOrElse { emptyMap() }
        value = withContext(Dispatchers.IO) { decodeLogos(logos) }
    }.value

fun decodeLogos(logos: Map<String, ByteArray>): Map<String, ImageBitmap> {
    if (logos.isEmpty()) return emptyMap()
    val decoded = LinkedHashMap<String, ImageBitmap>(logos.size)
    for ((serviceKey, bytes) in logos) {
        if (LogoImageDetector.detect(bytes) == LogoImageFormat.UNKNOWN) continue
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
        decoded[serviceKey] = bitmap.asImageBitmap()
    }
    return decoded
}
