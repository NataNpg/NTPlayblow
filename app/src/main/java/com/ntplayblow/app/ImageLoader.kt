package com.ntplayblow.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.URL
import java.util.concurrent.Executors

// Carregador de logos/capas minúsculo (sem biblioteca): baixa, reduz e guarda 60 imagens em memória.
object ImageLoader {
    private val cache = LruCache<String, Bitmap>(60)
    private val pool = Executors.newFixedThreadPool(3)

    fun load(url: String, iv: ImageView) {
        iv.tag = url
        iv.setImageDrawable(null)
        if (!url.startsWith("http")) return
        cache.get(url)?.let { iv.setImageBitmap(it); return }
        pool.execute {
            try {
                val conn = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                val bytes = conn.getInputStream().use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var s = 1
                while (bounds.outWidth / s > 200) s *= 2
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
                    ?: return@execute
                cache.put(url, bmp)
                iv.post { if (iv.tag == url) iv.setImageBitmap(bmp) }
            } catch (_: Exception) { /* sem logo: segue sem imagem */ }
        }
    }
}
