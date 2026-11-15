package com.ntplayblow.app

import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Sinopse, gênero, ano e nota vêm do painel Xtream do próprio provedor (player_api.php),
// usando o mesmo usuário/senha que já estão no link da lista. Chamar fora da thread principal.
object Xtream {
    class Info(val plot: String, val genre: String, val year: String, val rating: String)

    private var base = ""
    private var user = ""
    private var pass = ""
    private val series = HashMap<String, Info>()
    private var seriesState = 0

    fun init(sources: List<String>) {
        base = ""; user = ""; pass = ""; series.clear(); seriesState = 0
        for (s in sources) {
            if (!s.startsWith("http")) continue
            val u = Uri.parse(s)
            val un = u.getQueryParameter("username")
            val pw = u.getQueryParameter("password")
            if (un != null && pw != null) { base = "${u.scheme}://${u.authority}"; user = un; pass = pw; break }
        }
    }

    private fun get(action: String, extra: String = ""): java.io.InputStream? {
        if (base.isEmpty()) return null
        val c = URL("$base/player_api.php?username=${Uri.encode(user)}&password=${Uri.encode(pass)}&action=$action$extra")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("User-Agent", Library.UA)
        return if (c.responseCode in 200..299) c.inputStream else null
    }

    fun norm(s: String) = s.lowercase()
        .replace(Regex("\\[[^\\]]*\\]|\\(\\d{4}\\)"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun seriesInfo(title: String): Info? {
        loadSeries()
        return series[norm(title)]
    }

    @Synchronized private fun loadSeries() {
        if (seriesState != 0) return
        seriesState = 1
        try {
            get("get_series")?.let { ins ->
                JsonReader(ins.bufferedReader()).use { r ->
                    r.beginArray()
                    while (r.hasNext()) {
                        r.beginObject()
                        var name = ""; var plot = ""; var genre = ""; var year = ""; var rating = ""
                        while (r.hasNext()) {
                            val k = r.nextName()
                            if (r.peek() == JsonToken.STRING || r.peek() == JsonToken.NUMBER) {
                                val v = r.nextString()
                                when (k) {
                                    "name" -> name = v
                                    "plot" -> plot = v
                                    "genre" -> genre = v
                                    "releaseDate", "release_date" -> year = v.take(4)
                                    "rating" -> rating = v
                                }
                            } else r.skipValue()
                        }
                        r.endObject()
                        if (name.isNotEmpty()) series[norm(name)] = Info(plot, genre, year, rating)
                    }
                }
            }
        } catch (_: Exception) { }
        seriesState = 2
    }

    // o id do filme é o nome do arquivo no endereço: /movie/usuario/senha/12345.mp4
    fun vodInfo(url: String): Info? {
        return try {
            val id = url.substringAfterLast('/').substringBefore('.')
            val text = get("get_vod_info", "&vod_id=$id")?.bufferedReader()?.use { it.readText() } ?: return null
            val i = JSONObject(text).optJSONObject("info") ?: return null
            Info(
                i.optString("plot").ifEmpty { i.optString("description") },
                i.optString("genre"),
                (i.optString("releasedate").ifEmpty { i.optString("release_date") }).take(4),
                i.optString("rating")
            )
        } catch (_: Exception) { null }
    }
}
