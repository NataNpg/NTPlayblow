package com.ntplayblow.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView

// Perfis: o perfil 0 usa o arquivo antigo "nt_player", assim nada se perde.
object Store {
    const val MAX = 4
    var chosen = false // true depois que o usuário escolheu um perfil nesta execução

    val AVATARS = listOf(
        "☺" to "#10B981", "★" to "#F59E0B", "♥" to "#EF4444", "⚡" to "#3B82F6",
        "▶" to "#8B5CF6", "♛" to "#F97316", "◆" to "#06B6D4", "♪" to "#EC4899",
        "✿" to "#22C55E", "☻" to "#EAB308", "Z" to "#64748B", "N" to "#4DD0E1"
    )

    private fun app(c: Context) = c.getSharedPreferences("nt_app", Context.MODE_PRIVATE)

    fun ids(c: Context): List<Int> =
        (app(c).getString("ids", "0") ?: "0").split(",").mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }

    fun cur(c: Context): Int = app(c).getInt("cur", 0).takeIf { it in ids(c) } ?: ids(c).first()
    fun setCur(c: Context, id: Int) = app(c).edit().putInt("cur", id).apply()
    fun name(c: Context, id: Int) = app(c).getString("n$id", "Perfil ${id + 1}") ?: "Perfil ${id + 1}"
    fun av(c: Context, id: Int) = app(c).getInt("a$id", 0).coerceIn(0, AVATARS.size - 1)

    fun prefs(c: Context): SharedPreferences {
        val id = cur(c)
        return c.getSharedPreferences(if (id == 0) "nt_player" else "nt_player_$id", Context.MODE_PRIVATE)
    }

    fun add(c: Context, name: String, av: Int): Int {
        val list = ids(c)
        val id = (0..MAX + 10).first { it !in list } // menor id livre
        app(c).edit().putString("ids", (list + id).joinToString(",")).putString("n$id", name).putInt("a$id", av).apply()
        return id
    }

    fun save(c: Context, id: Int, name: String, av: Int) =
        app(c).edit().putString("n$id", name).putInt("a$id", av).apply()

    fun delete(c: Context, id: Int) {
        val list = ids(c)
        if (list.size <= 1) return
        val rest = list - id
        c.getSharedPreferences(if (id == 0) "nt_player" else "nt_player_$id", Context.MODE_PRIVATE).edit().clear().apply()
        app(c).edit().putString("ids", rest.joinToString(",")).remove("n$id").remove("a$id").apply()
        if (cur(c) == id) setCur(c, rest.first())
    }

    // ---- controle parental (vale para todos os perfis) ----
    private fun hash(pin: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest("nt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun hasPin(c: Context) = app(c).contains("pin")
    fun checkPin(c: Context, pin: String) = app(c).getString("pin", null) == hash(pin)
    fun setPin(c: Context, pin: String) = app(c).edit().putString("pin", hash(pin)).apply()
    fun clearPin(c: Context) = app(c).edit().remove("pin").putBoolean("parental", false).apply()
    fun parentalOn(c: Context) = hasPin(c) && app(c).getBoolean("parental", false)
    fun setParental(c: Context, on: Boolean) = app(c).edit().putBoolean("parental", on).apply()

    private val ADULT = listOf("adulto", "adult", "xxx", "porn", "sexo", "erotic", "erótic", "+18", "18+", "hentai")
    fun adultGroup(g: String): Boolean { val s = g.lowercase(); return ADULT.any { it in s } }

    fun blocked(c: Context, group: String, title: String): Boolean {
        if (!parentalOn(c)) return false
        val s = (group + " " + title).lowercase()
        return ADULT.any { it in s }
    }

    fun qr(text: String, size: Int): android.graphics.Bitmap {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    fun avatarView(c: Context, idx: Int, sizeDp: Int, textSp: Float): TextView {
        val (sym, col) = AVATARS[idx]
        val d = c.resources.displayMetrics.density
        return TextView(c).apply {
            text = sym; textSize = textSp; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(col)) }
            layoutParams = android.widget.LinearLayout.LayoutParams((sizeDp * d).toInt(), (sizeDp * d).toInt())
        }
    }
}
