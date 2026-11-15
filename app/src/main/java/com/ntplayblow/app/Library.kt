package com.ntplayblow.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class Ch(val num: Int, val title: String, val url: String, val group: String, val logo: String = "")
class Ep(val season: Int, val ep: Int, val url: String, val label: String)
class Show(val title: String, var cover: String, val group: String) { val eps = ArrayList<Ep>() }

// Biblioteca única, carregada uma vez: canais ao vivo, filmes e séries (episódios agrupados por série).
object Library {
    const val UA = "Mozilla/5.0 (Linux; Android 11) NTPlayblow/1.0"
    val live = ArrayList<Ch>()
    val movies = ArrayList<Ch>()
    val shows = LinkedHashMap<String, Show>()      // chave = título em minúsculas
    val movieByTitle = HashMap<String, Ch>()

    @Volatile var state = 0                         // 0 vazio, 1 carregando, 2 pronto
    @Volatile var error: String? = null
    private var sig = ""
    private var head = ""                           // 1ª linha da resposta, para explicar quando não é M3U
    private var progressCb: ((Int) -> Unit)? = null
    private val waiters = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    // seleção e fila de reprodução passadas entre telas
    var selMovie: Ch? = null
    var selShow: Show? = null
    var queue: List<Pair<String, String>> = emptyList()   // título, url
    var queueIndex = 0
    var queueType = ""                                    // "movie", "series" ou ""
    var queueTitle = ""

    fun sources(c: Context): List<String> =
        Account.lists(c) + listOfNotNull(Store.prefs(c).getString("last_list", null))

    fun invalidate() { if (state != 1) state = 0 }

    /** Lançamentos e 4K primeiro; conteúdo adulto por último. A ordem original é mantida dentro de cada grupo. */
    fun order(groups: List<String>): List<String> = groups.sortedBy { g ->
        val s = g.lowercase()
        when {
            Store.adultGroup(g) -> 3
            "lançamento" in s || "lancamento" in s -> 0
            "4k" in s || "uhd" in s -> 1
            else -> 2
        }
    }

    fun epLabel(e: Ep) = if (e.label.isNotEmpty()) e.label else "Temporada ${e.season} · Episódio ${"%02d".format(e.ep)}"

    fun ensure(c: Context, progress: (Int) -> Unit, done: () -> Unit) {
        val app = c.applicationContext
        val srcs = sources(app)
        val s = srcs.joinToString("\n")
        if (state == 2 && s == sig) { done(); return }
        progressCb = progress
        waiters.add(done)
        if (state == 1) return
        sig = s; state = 1; error = null
        live.clear(); movies.clear(); shows.clear(); movieByTitle.clear()
        Xtream.init(srcs)
        Thread {
            var err: String? = null
            try {
                if (srcs.isEmpty()) err = "Nenhuma lista. " + Account.diag(app) + " Use Playlist na tela inicial ou peça ao administrador."
                val pool = HashMap<String, String>()
                for (src in srcs) {
                    try {
                        val seen = open(app, src).use { parse(it, pool) }
                        if (seen == 0) throw java.io.IOException("a resposta não é uma lista M3U: $head")
                    } catch (e: Exception) {
                        // esconde o link (leva usuário e senha da lista)
                        err = (e.message ?: e.javaClass.simpleName).replace(Regex("https?://\\S+"), "<link>")
                    }
                }
            } catch (t: Throwable) {
                err = if (t is OutOfMemoryError) "memória insuficiente (lista muito grande)" else (t.message ?: t.javaClass.simpleName)
            }
            for (sh in shows.values) sh.eps.sortWith(compareBy({ it.season }, { it.ep }))
            main.post {
                error = err; state = 2
                val w = ArrayList(waiters); waiters.clear()
                w.forEach { it() }
            }
        }.start()
    }

    private fun open(app: Context, s: String): BufferedReader {
        head = ""
        if (!s.startsWith("http")) return app.contentResolver.openInputStream(Uri.parse(s))!!.bufferedReader()
        val c = (URL(s).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 60000
            setRequestProperty("User-Agent", UA); setRequestProperty("Accept", "*/*")
        }
        val code = c.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code em ${URL(s).host}")
        return c.inputStream.bufferedReader()
    }

    private val groupRe = Regex("group-title=\"([^\"]*)\"")
    private val logoRe = Regex("tvg-logo=\"([^\"]*)\"")
    private val epRe = Regex("^(.*?)[\\s._-]*[Ss](\\d{1,2})\\s*[Ee](\\d{1,3})\\b.*$")
    private val tagRe = Regex("\\s*\\[[^\\]]*\\]\\s*")

    // Série = endereço com /series/ OU nome com S01E01. Filme = /movie/. Canal = o resto.
    // (Nome de categoria sozinho não decide mais: "24H SÉRIES" é canal de TV.)
    private fun parse(r: BufferedReader, pool: HashMap<String, String>): Int {
        var title: String? = null
        var group = "Sem categoria"
        var logo = ""
        var seen = 0
        while (true) {
            val l = (r.readLine() ?: break).trim()
            if (l.isEmpty()) continue
            if (head.isEmpty()) head = l.take(60)
            if (l.startsWith("#EXTINF")) {
                title = l.substringAfterLast(',').trim()
                val g0 = groupRe.find(l)?.groupValues?.get(1)?.ifEmpty { null } ?: "Sem categoria"
                group = pool.getOrPut(g0) { g0 }
                logo = logoRe.find(l)?.groupValues?.get(1) ?: ""
            } else if (!l.startsWith("#")) {
                seen++
                val t = title?.takeIf { it.isNotEmpty() } ?: l
                val m = epRe.find(t)
                val g = group.lowercase()
                when {
                    "/series/" in l || m != null -> {
                        val name = tagRe.replace(m?.groupValues?.get(1) ?: t, " ").trim().ifEmpty { t }
                        val show = shows.getOrPut(name.lowercase()) { Show(name, logo, group) }
                        if (show.cover.isEmpty() && logo.isNotEmpty()) show.cover = logo
                        val s = m?.groupValues?.get(2)?.toInt() ?: 1
                        val e = m?.groupValues?.get(3)?.toInt() ?: (show.eps.size + 1)
                        show.eps.add(Ep(s, e, l, if (m != null) "" else t))
                    }
                    "/movie/" in l -> addMovie(t, l, group, logo)
                    "/live/" in l -> live.add(Ch(live.size + 1, t, l, group, logo))
                    ("filme" in g || "movie" in g || "vod" in g) && "24h" !in g -> addMovie(t, l, group, logo)
                    else -> live.add(Ch(live.size + 1, t, l, group, logo))
                }
                title = null; group = "Sem categoria"; logo = ""
                if (seen % 3000 == 0) progressCb?.let { cb ->
                    val n = live.size + movies.size + shows.size
                    main.post { cb(n) }
                }
            }
        }
        return seen
    }

    private fun addMovie(t: String, url: String, group: String, logo: String) {
        val ch = Ch(movies.size + 1, t, url, group, logo)
        movies.add(ch); movieByTitle.putIfAbsent(t, ch)
    }
}

// "Continuar assistindo": últimos títulos (filme ou série) por perfil.
object Recent {
    fun list(p: SharedPreferences, type: String): List<String> = try {
        val a = JSONArray(p.getString("recent_$type", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }

    fun add(p: SharedPreferences, type: String, title: String) {
        val l = ArrayList(list(p, type))
        l.remove(title); l.add(0, title)
        p.edit().putString("recent_$type", JSONArray(l.take(20)).toString()).apply()
    }
}
