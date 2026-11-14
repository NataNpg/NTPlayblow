package com.ntplayblow.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Login no servidor NTPlay (mesmo protocolo do painel admin):
//   chave = PBKDF2-HMAC-SHA1(senha, "ntplay-auth:"/"ntplay-enc:" + usuario, 40000, 256 bits)
//   POST {a:"get", u:usuario, k:chaveAuth}  ->  {ok, data}, data = base64(iv[12] + AES-GCM(json))
// O app só LÊ do servidor; nunca grava (não mexe em favoritos/progresso da conta).
object Account {
    private const val API = "https://baylcjfedxnjnjkvaufw.supabase.co/functions/v1/ntsync"

    class Result(val ok: Boolean, val msg: String, val offline: Boolean = false)

    private fun app(c: Context) = c.getSharedPreferences("nt_app", Context.MODE_PRIVATE)

    fun loggedIn(c: Context) = app(c).contains("acc_auth")
    fun user(c: Context): String? = app(c).getString("acc_user", null)
    fun expires(c: Context): String? = app(c).getString("srv_exp", null)?.takeIf { it.isNotBlank() }
    fun diag(c: Context): String = app(c).getString("srv_diag", "") ?: ""
    fun lists(c: Context): List<String> = (app(c).getString("srv_lists", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun logout(c: Context) = app(c).edit().remove("acc_user").remove("acc_auth").remove("acc_enc")
        .remove("srv_lists").remove("srv_exp").apply()

    private fun pbkdf2(pass: ByteArray, salt: ByteArray, iter: Int, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(pass, "HmacSHA1"))
        val out = ByteArray(len)
        var off = 0
        var block = 1
        while (off < len) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iter) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, len - off)
            System.arraycopy(t, 0, out, off, n)
            off += n; block++
        }
        return out
    }

    private fun b64u(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Roda em thread de fundo. */
    fun login(c: Context, userRaw: String, pass: String): Result {
        val u = userRaw.trim().lowercase()
        if (u.isEmpty() || pass.isEmpty()) return Result(false, "Digite usuário e senha.")
        val pw = pass.toByteArray(Charsets.UTF_8)
        val auth = b64u(pbkdf2(pw, "ntplay-auth:$u".toByteArray(), 40000, 32))
        val enc = pbkdf2(pw, "ntplay-enc:$u".toByteArray(), 40000, 32)
        return fetch(c, u, auth, enc)
    }

    /** Revalida a sessão salva e atualiza as listas. Roda em thread de fundo. */
    fun refresh(c: Context): Result {
        val p = app(c)
        val u = p.getString("acc_user", null) ?: return Result(false, "Sem sessão.")
        val auth = p.getString("acc_auth", null) ?: return Result(false, "Sem sessão.")
        val enc = Base64.decode(p.getString("acc_enc", "") ?: "", Base64.NO_WRAP)
        return fetch(c, u, auth, enc)
    }

    private fun fetch(c: Context, u: String, auth: String, enc: ByteArray): Result {
        val body = JSONObject().put("a", "get").put("u", u).put("k", auth).toString()
        val json: JSONObject
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 20000; doOutput = true
                setRequestProperty("content-type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            if (!json.optBoolean("ok")) {
                return Result(false, json.optString("msg").ifEmpty { "Usuário ou senha incorretos." })
            }
        } catch (e: IOException) {
            return Result(false, "Sem conexão com o servidor.", offline = true)
        }

        val urls = ArrayList<String>()
        var diag = "A conta não tem listas salvas."
        val data = json.optString("data")
        if (data.isNotEmpty()) {
            try {
                val all = Base64.decode(data, Base64.DEFAULT)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(enc, "AES"), GCMParameterSpec(128, all.copyOfRange(0, 12)))
                val payload = JSONObject(String(cipher.doFinal(all, 12, all.size - 12), Charsets.UTF_8))
                val arr = payload.optJSONArray("lists")
                if (arr != null) for (i in 0 until arr.length()) {
                    val l = arr.optJSONObject(i) ?: continue
                    val url = l.optString("url")
                    if (l.optBoolean("active", true) && url.startsWith("http")) urls.add(url)
                }
                diag = if (urls.isEmpty()) "A conta não tem lista ativa." else "${urls.size} lista(s) da conta."
            } catch (_: Exception) { diag = "Dados da conta ilegíveis (a senha foi trocada?)." }
        }
        app(c).edit().putString("acc_user", u).putString("acc_auth", auth)
            .putString("acc_enc", Base64.encodeToString(enc, Base64.NO_WRAP))
            .putString("srv_lists", urls.joinToString("\n"))
            .putString("srv_exp", json.optString("expires_at")).putString("srv_diag", diag).apply()
        return Result(true, "ok")
    }
}
