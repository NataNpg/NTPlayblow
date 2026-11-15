package com.ntplayblow.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

// Mostra um QR; o celular abre a página, cola o link da lista M3U e a TV recebe.
// Servidor simples na rede local (Wi-Fi), protegido por um código aleatório no endereço.
class ReceiveActivity : Activity() {
    private var server: ServerSocket? = null
    private val token = (100000..999999).random().toString()
    private lateinit var qr: ImageView
    private lateinit var info: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#05080B")); setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
        }
        root.addView(TextView(this).apply {
            text = "Enviar lista do celular"; textSize = 24f; setTextColor(Color.parseColor("#E6EDF2"))
            setTypeface(typeface, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Conecte o celular no mesmo Wi-Fi da TV e leia o QR com a câmera."
            textSize = 14f; setTextColor(Color.parseColor("#7F8E99")); gravity = Gravity.CENTER
            setPadding(0, (6 * d).toInt(), 0, (12 * d).toInt())
        })
        qr = ImageView(this).apply { setBackgroundColor(Color.WHITE); setPadding((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt()) }
        root.addView(qr, LinearLayout.LayoutParams((220 * d).toInt(), (220 * d).toInt()))
        info = TextView(this).apply {
            text = "Preparando…"; textSize = 14f; setTextColor(Color.parseColor("#4DD0E1")); gravity = Gravity.CENTER
            setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
        }
        root.addView(info)
        root.addView(Button(this).apply { text = "Cancelar"; setOnClickListener { finish() } })
        setContentView(root)
        startServer()
    }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (_: Exception) { null }

    private fun startServer() {
        Thread {
            val ss = (8080..8090).firstNotNullOfOrNull { p -> try { ServerSocket(p) } catch (_: Exception) { null } }
            val ip = localIp()
            if (ss == null || ip == null) {
                runOnUiThread { info.text = "Sem rede Wi-Fi disponível. Conecte a TV ao Wi-Fi e tente de novo." }
                return@Thread
            }
            server = ss
            val url = "http://$ip:${ss.localPort}/?t=$token"
            runOnUiThread { info.text = url; qr.setImageBitmap(qrBitmap(url)) }
            try { while (!ss.isClosed) handle(ss.accept()) } catch (_: Exception) { }
        }.start()
    }

    private fun qrBitmap(text: String): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) for (y in 0 until 512) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    private fun reply(s: Socket, code: String, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        s.getOutputStream().apply {
            write("HTTP/1.1 $code\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(body); flush()
        }
    }

    private fun page(msg: String, form: Boolean) = """<!doctype html><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1">
<body style="font-family:sans-serif;background:#05080B;color:#E6EDF2;padding:24px">
<h2>NTPlayblow</h2><p>$msg</p>""" + (if (form) """
<form method=post action="/add?t=$token">
<input name=url type=url required placeholder="Link da lista M3U (http://...)" style="width:100%;padding:12px;font-size:16px;box-sizing:border-box">
<button style="margin-top:12px;padding:12px 20px;font-size:16px;background:#4DD0E1;border:0">Enviar para a TV</button>
</form>""" else "") + "</body>"

    private fun handle(s: Socket) {
        try {
            s.soTimeout = 5000
            val r = s.getInputStream().bufferedReader(Charsets.UTF_8)
            val line = r.readLine() ?: return
            var len = 0
            while (true) {
                val h = r.readLine() ?: break
                if (h.isEmpty()) break
                if (h.startsWith("Content-Length:", ignoreCase = true)) len = h.substringAfter(':').trim().toIntOrNull() ?: 0
            }
            val parts = line.split(" ")
            val path = parts.getOrElse(1) { "/" }
            if ("t=$token" !in path) { reply(s, "403 Forbidden", page("Código inválido. Leia o QR da TV de novo.", false)); return }
            if (parts[0] == "POST" && path.startsWith("/add") && len in 1..4096) {
                val buf = CharArray(len); var n = 0
                while (n < len) { val k = r.read(buf, n, len - n); if (k < 0) break; n += k }
                val url = URLDecoder.decode(String(buf, 0, n).substringAfter("url=").substringBefore("&"), "UTF-8").trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    reply(s, "200 OK", page("Lista enviada! Pode fechar esta página.", false))
                    runOnUiThread {
                        Store.prefs(this).edit().putString("last_list", url).apply()
                        Toast.makeText(this, "Lista recebida", Toast.LENGTH_SHORT).show()
                        Library.invalidate(); finish()
                    }
                } else reply(s, "400 Bad Request", page("Link inválido. Use http:// ou https://", true))
            } else reply(s, "200 OK", page("Cole o link da sua lista M3U:", true))
        } catch (_: Exception) {
        } finally { try { s.close() } catch (_: Exception) { } }
    }

    override fun onDestroy() { try { server?.close() } catch (_: Exception) { }; super.onDestroy() }
}
