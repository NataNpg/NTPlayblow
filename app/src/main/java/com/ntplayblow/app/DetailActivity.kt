package com.ntplayblow.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

// Cartão grande: capa, título, nota/ano/gênero, sinopse, Reproduzir e Favoritos.
// Em séries, mostra também as temporadas e os episódios ("Temporada 1 · Episódio 01").
class DetailActivity : Activity() {
    private val prefs get() = Store.prefs(this)
    private var movie: Ch? = null
    private var show: Show? = null
    private var season = 1
    private var seasonEps: List<Ep> = emptyList()
    private lateinit var favBtn: Button
    private lateinit var epList: ListView
    private lateinit var seasonsBox: LinearLayout

    private val type get() = if (movie != null) "movie" else "series"
    private val title get() = movie?.title ?: show!!.title
    private val favKey get() = "$type:$title"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        movie = Library.selMovie; show = Library.selShow
        if (movie == null && show == null) { finish(); return }
        setContentView(R.layout.activity_detail)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<TextView>(R.id.kind).text = if (movie != null) "FILME" else "SÉRIE"
        findViewById<TextView>(R.id.dtitle).text = title
        ImageLoader.load(movie?.logo ?: show!!.cover, findViewById<ImageView>(R.id.poster))
        val plot = findViewById<TextView>(R.id.dplot)
        val meta = findViewById<TextView>(R.id.dmeta)
        plot.text = "Carregando sinopse…"
        favBtn = findViewById(R.id.dfav)
        favBtn.setOnClickListener { toggleFav() }
        refreshFav()

        val play = findViewById<Button>(R.id.dplay)
        if (movie != null) {
            play.setOnClickListener { playQueue(listOf(movie!!.title to movie!!.url), 0) }
            meta.text = Regex("\\((\\d{4})\\)").find(movie!!.title)?.groupValues?.get(1) ?: ""
        } else {
            setupSeries(play)
        }

        // sinopse do painel do provedor (em segundo plano)
        Thread {
            val info = if (movie != null) Xtream.vodInfo(movie!!.url) else Xtream.seriesInfo(show!!.title)
            runOnUiThread {
                plot.text = info?.plot?.takeIf { it.isNotBlank() } ?: "Sinopse indisponível."
                val parts = listOf(
                    info?.year ?: "",
                    info?.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" } ?: "",
                    info?.genre ?: ""
                ).filter { it.isNotBlank() }
                if (parts.isNotEmpty()) meta.text = parts.joinToString("   •   ")
            }
        }.start()
    }

    private fun setupSeries(play: Button) {
        val sh = show!!
        findViewById<View>(R.id.serieswrap).visibility = View.VISIBLE
        epList = findViewById(R.id.eps)
        seasonsBox = findViewById(R.id.seasons)
        val last = prefs.getString("last_${sh.title}", null)
        val all = sh.eps
        if (last != null) play.text = "▶  Continuar"
        play.setOnClickListener { playQueue(all.map { Library.epLabel(it).let { l -> "${sh.title} · $l" } to it.url }, all.indexOfFirst { it.url == last }.coerceAtLeast(0)) }

        val seasons = all.map { it.season }.distinct().sorted()
        season = all.firstOrNull { it.url == last }?.season ?: seasons.firstOrNull() ?: 1
        for (s in seasons) {
            seasonsBox.addView(Button(this).apply {
                text = "Temporada $s"; tag = s
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = 10 }
                setOnClickListener { season = s; showSeason() }
            })
        }
        epList.setOnItemClickListener { _, _, i, _ ->
            val ep = seasonEps[i]
            playQueue(all.map { "${sh.title} · ${Library.epLabel(it)}" to it.url }, all.indexOf(ep))
        }
        showSeason()
    }

    private fun showSeason() {
        val sh = show!!
        seasonEps = sh.eps.filter { it.season == season }
        for (i in 0 until seasonsBox.childCount) seasonsBox.getChildAt(i).alpha = if (seasonsBox.getChildAt(i).tag == season) 1f else 0.5f
        epList.adapter = ArrayAdapter(this, R.layout.item_episode, R.id.etext, seasonEps.map { Library.epLabel(it) })
    }

    private fun playQueue(q: List<Pair<String, String>>, index: Int) {
        Library.queue = q; Library.queueIndex = index
        Library.queueType = type; Library.queueTitle = title
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun toggleFav() {
        val set = HashSet(prefs.getStringSet("vfavs", emptySet()) ?: emptySet())
        if (!set.remove(favKey)) set.add(favKey)
        prefs.edit().putStringSet("vfavs", set).apply()
        refreshFav()
    }

    private fun refreshFav() {
        val on = (prefs.getStringSet("vfavs", emptySet()) ?: emptySet()).contains(favKey)
        favBtn.text = if (on) "♥  Nos favoritos" else "+ Favoritos"
    }
}
