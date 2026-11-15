package com.ntplayblow.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// TV ao Vivo: categorias, canais e o player ao lado. Os dados vêm da Library (carregada uma vez).
class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var catList: ListView
    private lateinit var chList: ListView
    private lateinit var now: TextView
    private val prefs get() = Store.prefs(this)
    private var current: String? = null

    private var shown: List<Ch> = emptyList()
    private var cats: List<String> = emptyList()      // "Favoritos", "Todos", categorias...
    private var counts: Map<String, Int> = emptyMap()
    private var favs: MutableSet<String> = HashSet()
    private var fullscreen = false
    private var offset = 0                            // posição de shown[0] dentro da fila do player
    private var startCat: String? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // vídeo aberto por outro app (link http): vai direto para o player
        intent?.data?.let { d ->
            Library.queue = listOf(d.toString() to d.toString()); Library.queueIndex = 0
            Library.queueType = ""; Library.queueTitle = ""
            startActivity(Intent(this, PlayerActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_main)
        view = findViewById(R.id.player)
        catList = findViewById(R.id.categories)
        chList = findViewById(R.id.channels)
        now = findViewById(R.id.now)
        view.resizeMode = when (prefs.getString("resize", "fit")) {
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        view.findViewById<TextView>(R.id.ctl_resize)?.let { b2 ->
            fun label() { b2.text = when (view.resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Preencher"
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Esticar"
                else -> "Original" } }
            label()
            b2.setOnClickListener {
                val next = when (view.resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT }
                view.resizeMode = next
                prefs.edit().putString("resize", when (next) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "zoom"
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "fill"
                    else -> "fit" }).apply()
                label()
            }
        }
        view.findViewById<View>(R.id.ctl_full)?.setOnClickListener { setFullscreen(!fullscreen) }
        favs = HashSet(prefs.getStringSet("favs", emptySet()) ?: emptySet())

        catList.setOnItemClickListener { _, _, i, _ -> selectCategory(i) }
        chList.setOnItemClickListener { _, _, i, _ -> playChannel(i) }
        findViewById<View>(R.id.back).setOnClickListener { onBackPressed() }
        startCat = intent?.getStringExtra("cat")

        title("Carregando lista…")
        Library.ensure(this, { n -> title("Carregando lista… $n itens") }) { onReady() }
    }

    private fun onReady() {
        if (Library.live.isEmpty()) {
            val why = Library.error?.let { "Erro ao abrir a lista: $it" } ?: "A lista não tem canais de TV."
            title(why); Toast.makeText(this, why, Toast.LENGTH_LONG).show()
            return
        }
        title("")
        counts = Library.live.groupingBy { it.group }.eachCount()
        cats = listOf("Favoritos", "Todos") + Library.order(counts.keys.toList())
        catList.adapter = CatAdapter()
        chList.adapter = ChAdapter()
        selectCategory(cats.indexOf(startCat).takeIf { it >= 0 } ?: 1)
    }

    private fun visible(c: Ch) = Pin.unlocked || !Store.adultGroup(c.group)

    private fun selectCategory(i: Int) {
        val c = cats[i]
        if (Store.adultGroup(c) && !Pin.unlocked) { Pin.require(this) { selectCategory(i) }; return }
        shown = when (c) {
            "Favoritos" -> Library.live.filter { it.url in favs && visible(it) }
            "Todos" -> Library.live.filter { visible(it) }
            else -> Library.live.filter { it.group == c }
        }
        catList.setItemChecked(i, true)
        (catList.adapter as? BaseAdapter)?.notifyDataSetChanged()
        (chList.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    private fun countOf(cat: String) = when (cat) {
        "Favoritos" -> Library.live.count { it.url in favs }
        "Todos" -> Library.live.size
        else -> counts[cat] ?: 0
    }

    private fun setFullscreen(on: Boolean) {
        fullscreen = on
        val v = if (on) View.GONE else View.VISIBLE
        for (id in intArrayOf(R.id.bar, R.id.categories, R.id.channels, R.id.now)) findViewById<View>(id).visibility = v
    }

    private fun title(s: String) {
        now.text = s
        view.findViewById<TextView>(R.id.ctl_title)?.text = s
    }

    // AO VIVO esconde a barra de progresso; contador "2 / 35" mostra a posição na lista
    private fun updateBar() {
        val p = player ?: return
        val live = p.isCurrentMediaItemLive || p.duration == C.TIME_UNSET
        view.findViewById<View>(R.id.ctl_live)?.visibility = if (live) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.ctl_seekrow)?.visibility = if (live) View.GONE else View.VISIBLE
        val n = shown.size
        view.findViewById<TextView>(R.id.ctl_count)?.apply {
            visibility = if (n > 1) View.VISIBLE else View.GONE
            text = "${offset + p.currentMediaItemIndex + 1} / $n"
        }
    }

    private fun exo(): ExoPlayer = player ?: ExoPlayer.Builder(this)
        .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build().also {
        player = it; view.player = it
        it.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) = updateBar()

            override fun onPlayerError(error: PlaybackException) =
                Toast.makeText(this@MainActivity, R.string.err_play, Toast.LENGTH_LONG).show()

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                current = item?.localConfiguration?.uri?.toString()
                val i = offset + it.currentMediaItemIndex
                if (i in shown.indices) {
                    chList.setItemChecked(i, true); chList.smoothScrollToPosition(i)
                    title(shown[i].title)
                }
            }
        })
    }

    private fun playChannel(i: Int) {
        saveProgress()
        val p = exo()
        // a fila do player é uma janela ao redor do canal (listas têm dezenas de milhares de itens)
        val from = maxOf(0, i - 100)
        val to = minOf(shown.size, i + 200)
        offset = from
        p.setMediaItems(shown.subList(from, to).map { MediaItem.fromUri(it.url) }, i - from, 0L)
        current = shown[i].url
        title(shown[i].title)
        p.prepare(); p.playWhenReady = true
    }

    private inner class CatAdapter : BaseAdapter() {
        override fun getCount() = cats.size
        override fun getItem(p: Int) = cats[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val row = v ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_category, parent, false)
            val c = cats[p]
            row.findViewById<TextView>(R.id.cname).text = if (Store.adultGroup(c)) "[18+] $c" else c
            row.findViewById<TextView>(R.id.ccount).text = countOf(c).toString()
            return row
        }
    }

    private inner class ChAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int) = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup): View {
            val row = v ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_channel, parent, false)
            val ch = shown[p]
            row.findViewById<TextView>(R.id.num).text = "#" + ch.num
            row.findViewById<TextView>(R.id.name).text = ch.title
            ImageLoader.load(ch.logo, row.findViewById<ImageView>(R.id.logo))
            val heart = row.findViewById<TextView>(R.id.heart)
            heart.text = if (ch.url in favs) "♥" else "♡"
            heart.setOnClickListener {
                if (!favs.remove(ch.url)) favs.add(ch.url)
                prefs.edit().putStringSet("favs", HashSet(favs)).apply()
                val fav = cats.indexOf("Favoritos")
                if (catList.checkedItemPosition == fav) selectCategory(fav)
                else { notifyDataSetChanged(); (catList.adapter as? BaseAdapter)?.notifyDataSetChanged() }
            }
            return row
        }
    }

    private fun saveProgress() {
        val p = player ?: return
        val c = current ?: return
        if (p.duration <= 0) return   // canal ao vivo não guarda posição
        val pos = if (p.currentPosition > p.duration - 5000) 0L else p.currentPosition
        prefs.edit().putLong(c, pos).apply()
    }

    override fun onStop() { saveProgress(); player?.pause(); super.onStop() }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }

    @Deprecated("back handling kept simple")
    override fun onBackPressed() {
        if (fullscreen) setFullscreen(false) else finish()
    }
}
